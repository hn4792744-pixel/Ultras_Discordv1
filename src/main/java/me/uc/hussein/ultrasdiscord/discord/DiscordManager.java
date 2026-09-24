package me.uc.hussein.ultrasdiscord.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.uc.hussein.ultrasdiscord.UltrasDiscord;
import me.uc.hussein.ultrasdiscord.utility.Text;
import me.uc.hussein.ultrasdiscord.utility.TimeUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * Discord integration through a Bot Token (REST, no gateway needed to post) and/or a Webhook.
 *
 * - Everything is sent from a dedicated daemon thread: the main thread only builds a JSON string and queues it.
 * - Rate limits (HTTP 429) are honoured, network errors use exponential back-off and never crash the plugin.
 * - The token / webhook URL are never written to the log (all messages are sanitised).
 * - When both are enabled the webhook is preferred; the bot is used as automatic fallback.
 */
public final class DiscordManager {
    private static final String API = "https://discord.com/api/v10";
    private static final String USER_AGENT = "DiscordBot (https://github.com/UC-Hussein/Ultras_discord, 1.0.0)";

    private record Outgoing(String json, boolean webhook, int attempts) {
    }

    private final UltrasDiscord plugin;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final LinkedBlockingDeque<Outgoing> queue = new LinkedBlockingDeque<>();

    private volatile Thread worker;
    private volatile boolean master;
    private volatile boolean webhookReady;
    private volatile boolean botReady;
    private volatile String token = "";
    private volatile String channelId = "";
    private volatile String webhookUrl = "";
    private volatile long minIntervalMs = 600;
    private volatile int maxQueue = 300;
    private volatile Set<String> events = Set.of();

    private int failures;
    private long lastFailLog;
    private long lastBotWarn;

    public DiscordManager(UltrasDiscord plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ lifecycle
    public void start() {
        reload();
    }

    public synchronized void reload() {
        stopWorker();
        FileConfiguration c = plugin.getConfig();
        master = plugin.cfg().module("discord");
        token = c.getString("discord.bot-token", "").trim();
        channelId = c.getString("discord.channel-id", "").trim();
        webhookUrl = c.getString("webhook.url", "").trim();
        minIntervalMs = Math.max(0, c.getLong("discord.min-interval-ms", 600));
        maxQueue = Math.max(20, c.getInt("discord.queue-size", 300));
        Set<String> ev = new HashSet<>();
        ConfigurationSection es = c.getConfigurationSection("discord.events");
        if (es != null) {
            for (String k : es.getKeys(false)) {
                if (es.getBoolean(k, false)) ev.add(k);
            }
        }
        events = Set.copyOf(ev);
        webhookReady = false;
        botReady = false;
        failures = 0;

        if (!master) {
            plugin.getLogger().info("Discord integration is disabled (modules.discord: false).");
            return;
        }
        if (c.getBoolean("webhook.enabled", false)) {
            if (validWebhook(webhookUrl)) {
                webhookReady = true;
            } else {
                plugin.getLogger().warning("webhook.enabled is true but webhook.url is missing or invalid - webhook disabled.");
            }
        }
        if (c.getBoolean("discord.enabled", false)) {
            if (token.isEmpty() || token.equals("PUT_TOKEN_HERE")) {
                plugin.getLogger().warning("discord.enabled is true but discord.bot-token is not set - bot disabled.");
            } else if (!channelId.matches("\\d{15,25}")) {
                plugin.getLogger().warning("discord.channel-id must be the numeric ID of the channel - bot disabled.");
            } else {
                botReady = true;
            }
        }
        if (!webhookReady && !botReady) {
            plugin.getLogger().info("Discord integration is idle (no webhook / bot configured).");
            return;
        }
        startWorker();
        if (webhookReady) {
            plugin.getLogger().info("Discord webhook is configured.");
        }
        if (botReady) {
            Thread t = new Thread(this::verifyBot, "UltrasDiscord-Verify");
            t.setDaemon(true);
            t.start();
        }
    }

    public void shutdown() {
        long end = System.currentTimeMillis() + 3000;
        while (!queue.isEmpty() && worker != null && System.currentTimeMillis() < end) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        stopWorker();
    }

    private void startWorker() {
        Thread t = new Thread(this::runLoop, "UltrasDiscord-Sender");
        t.setDaemon(true);
        worker = t;
        t.start();
    }

    private void stopWorker() {
        Thread t = worker;
        worker = null;
        if (t != null) t.interrupt();
    }

    public boolean isReady() {
        return master && (webhookReady || botReady);
    }

    public boolean isWebhookReady() {
        return webhookReady;
    }

    public boolean isBotReady() {
        return botReady;
    }

    public boolean eventEnabled(String key) {
        return events.contains(key);
    }

    // ------------------------------------------------------------------ public API
    public void sendEvent(String eventKey, Map<String, String> placeholders) {
        if (!isReady() || !eventEnabled(eventKey)) return;
        enqueue(eventKey, placeholders);
    }

    /** Ignores the per-event toggle (used by /ucsecurity discordtest). */
    public void sendForced(String eventKey, Map<String, String> placeholders) {
        if (!isReady()) return;
        enqueue(eventKey, placeholders);
    }

    public void sendError(String title, String message) {
        if (!isReady() || !eventEnabled("errors")) return;
        Map<String, String> ph = new HashMap<>();
        ph.put("title", title);
        ph.put("message", sanitize(message));
        enqueue("errors", ph);
    }

    private void enqueue(String eventKey, Map<String, String> ph) {
        try {
            boolean hook = webhookReady;
            String json = buildPayload(eventKey, ph == null ? Map.of() : ph, hook);
            while (queue.size() >= maxQueue) {
                queue.pollFirst();
            }
            queue.offerLast(new Outgoing(json, hook, 0));
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Could not build Discord message: " + sanitize(String.valueOf(e.getMessage())));
        }
    }

    // ------------------------------------------------------------------ payload
    private String buildPayload(String eventKey, Map<String, String> ph, boolean forWebhook) {
        FileConfiguration c = plugin.getConfig();
        Map<String, String> p = new HashMap<>(ph);
        long now = System.currentTimeMillis();
        p.putIfAbsent("server", c.getString("general.server-name", "Server"));
        p.putIfAbsent("time", TimeUtil.time(now));
        p.putIfAbsent("date", TimeUtil.date(now));
        p.putIfAbsent("datetime", TimeUtil.dateTime(now));

        ConfigurationSection sec = c.getConfigurationSection("discord.embeds." + eventKey);
        JsonObject embed = new JsonObject();
        if (sec == null) {
            embed.addProperty("title", "ULTRAS SECURITY");
            embed.addProperty("description", cut(Text.fillPlain("{player} {reason}", p), 4000));
            embed.addProperty("color", 0x95A5A6);
        } else {
            embed.addProperty("title", cut(Text.fillPlain(sec.getString("title", "ULTRAS SECURITY"), p), 256));
            String desc = Text.fillPlain(sec.getString("description", ""), p);
            if (!desc.isBlank()) embed.addProperty("description", cut(desc, 4000));
            embed.addProperty("color", parseColor(sec.getString("color", "#95A5A6")));
            String footer = Text.fillPlain(sec.getString("footer", ""), p);
            if (!footer.isBlank()) {
                JsonObject f = new JsonObject();
                f.addProperty("text", cut(footer, 2000));
                embed.add("footer", f);
            }
            if (sec.getBoolean("timestamp", true)) {
                embed.addProperty("timestamp", Instant.now().toString());
            }
            String thumbTpl = c.getString("discord.thumbnail-url", "");
            if (sec.getBoolean("thumbnail", false) && thumbTpl != null && !thumbTpl.isBlank() && p.containsKey("uuid")) {
                JsonObject t = new JsonObject();
                t.addProperty("url", Text.fillPlain(thumbTpl, p));
                embed.add("thumbnail", t);
            }
            JsonArray fields = new JsonArray();
            for (Map<?, ?> m : sec.getMapList("fields")) {
                if (fields.size() >= 25) break;
                Object n = m.get("name");
                Object v = m.get("value");
                Object in = m.get("inline");
                String name = Text.fillPlain(n == null ? "" : n.toString(), p);
                String value = Text.fillPlain(v == null ? "" : v.toString(), p);
                if (name.isBlank()) name = "\u200b";
                if (value.isBlank()) value = "-";
                JsonObject f = new JsonObject();
                f.addProperty("name", cut(name, 256));
                f.addProperty("value", cut(value, 1000));
                f.addProperty("inline", in != null && Boolean.parseBoolean(in.toString()));
                fields.add(f);
            }
            if (!fields.isEmpty()) embed.add("fields", fields);
        }

        JsonObject payload = new JsonObject();
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        payload.add("embeds", embeds);

        String role = c.getString("discord.mention.role-id", "");
        if (role != null && role.matches("\\d{15,25}")) {
            double min = c.getDouble("discord.mention.min-suspicion", 85);
            double s = 0;
            try {
                s = Double.parseDouble(p.getOrDefault("suspicion", "0"));
            } catch (NumberFormatException ignored) {
                // no suspicion value
            }
            if (p.containsKey("suspicion") && s >= min) {
                payload.addProperty("content", "<@&" + role + ">");
                JsonObject am = new JsonObject();
                JsonArray roles = new JsonArray();
                roles.add(role);
                am.add("roles", roles);
                payload.add("allowed_mentions", am);
            }
        }
        if (forWebhook) {
            String user = c.getString("webhook.username", "Ultras Security");
            if (user != null && !user.isBlank()) payload.addProperty("username", cut(user, 80));
            String avatar = c.getString("webhook.avatar-url", "");
            if (avatar != null && avatar.startsWith("http")) payload.addProperty("avatar_url", avatar);
        }
        return payload.toString();
    }

    private static String cut(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static int parseColor(String s) {
        try {
            if (s == null) return 0x95A5A6;
            String t = s.trim();
            if (t.startsWith("#")) return Integer.parseInt(t.substring(1), 16);
            return Integer.parseInt(t);
        } catch (NumberFormatException e) {
            return 0x95A5A6;
        }
    }

    // ------------------------------------------------------------------ worker
    private void runLoop() {
        long lastSend = 0;
        Thread me = Thread.currentThread();
        while (worker == me && !me.isInterrupted()) {
            Outgoing o;
            try {
                o = queue.poll(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                return;
            }
            if (o == null) continue;
            try {
                long wait = minIntervalMs - (System.currentTimeMillis() - lastSend);
                if (wait > 0) Thread.sleep(wait);
                deliver(o);
                lastSend = System.currentTimeMillis();
            } catch (InterruptedException e) {
                return;
            } catch (Throwable t) {
                plugin.getLogger().warning("Discord sender error: " + sanitize(String.valueOf(t.getMessage())));
            }
        }
    }

    private void deliver(Outgoing o) throws InterruptedException {
        boolean hook = o.webhook();
        if (hook && !webhookReady) {
            if (botReady) queue.addFirst(new Outgoing(o.json(), false, o.attempts()));
            return;
        }
        if (!hook && !botReady) return;

        HttpRequest req;
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder()
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .POST(HttpRequest.BodyPublishers.ofString(o.json(), StandardCharsets.UTF_8));
            if (hook) {
                b.uri(URI.create(webhookUrl));
            } else {
                b.uri(URI.create(API + "/channels/" + channelId + "/messages"));
                b.header("Authorization", "Bot " + token);
            }
            req = b.build();
        } catch (RuntimeException e) {
            logFailure("invalid request configuration");
            return;
        }

        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            int sc = resp.statusCode();
            if (sc >= 200 && sc < 300) {
                failures = 0;
                return;
            }
            if (sc == 429) {
                long ms = Math.min(60_000L, retryAfterMs(resp));
                queue.addFirst(o);
                Thread.sleep(ms);
                return;
            }
            if (sc == 401 || sc == 404 && hook) {
                if (hook) {
                    webhookReady = false;
                    plugin.getLogger().severe("Discord rejected the webhook (HTTP " + sc + "). Webhook disabled until /ucsecurity reload.");
                    if (botReady) queue.addFirst(new Outgoing(o.json(), false, o.attempts()));
                } else {
                    botReady = false;
                    plugin.getLogger().severe("Discord rejected the bot token (HTTP 401). Bot disabled until /ucsecurity reload.");
                }
                return;
            }
            if (sc == 403 || sc == 404) {
                long now = System.currentTimeMillis();
                if (now - lastBotWarn > 300_000L) {
                    lastBotWarn = now;
                    plugin.getLogger().warning("Discord returned HTTP " + sc + ": the bot cannot see/write in the configured channel. Check the channel ID and the bot permissions (View Channel, Send Messages, Embed Links).");
                }
                return;
            }
            if (sc >= 500) {
                backoff(o, "HTTP " + sc);
                return;
            }
            plugin.getLogger().warning("Discord rejected a message (HTTP " + sc + "): " + cut(sanitize(resp.body()), 200));
        } catch (IOException e) {
            backoff(o, e.getClass().getSimpleName());
        }
    }

    private long retryAfterMs(HttpResponse<String> r) {
        try {
            JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
            if (o.has("retry_after")) {
                return (long) (o.get("retry_after").getAsDouble() * 1000) + 250;
            }
        } catch (RuntimeException ignored) {
            // fall back to header
        }
        return r.headers().firstValue("retry-after").map(s -> {
            try {
                return (long) (Double.parseDouble(s) * 1000) + 250;
            } catch (NumberFormatException e) {
                return 2000L;
            }
        }).orElse(2000L);
    }

    private void backoff(Outgoing o, String why) throws InterruptedException {
        failures++;
        logFailure(why);
        if (o.attempts() < 3) {
            queue.addFirst(new Outgoing(o.json(), o.webhook(), o.attempts() + 1));
        }
        long delay = Math.min(300_000L, 2000L << Math.min(failures, 7));
        Thread.sleep(delay);
    }

    private void logFailure(String why) {
        long now = System.currentTimeMillis();
        if (now - lastFailLog > 60_000L) {
            lastFailLog = now;
            plugin.getLogger().warning("Discord is unreachable or failing (" + sanitize(why) + "). Messages are queued and will be retried.");
        }
    }

    // ------------------------------------------------------------------ verification
    private void verifyBot() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(API + "/users/@me"))
                    .timeout(Duration.ofSeconds(15))
                    .header("User-Agent", USER_AGENT)
                    .header("Authorization", "Bot " + token)
                    .GET().build();
            HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 200) {
                String n = "bot";
                try {
                    n = JsonParser.parseString(r.body()).getAsJsonObject().get("username").getAsString();
                } catch (RuntimeException ignored) {
                    // keep default
                }
                plugin.getLogger().info("Discord bot connected as " + sanitize(n) + ".");
                checkChannel();
            } else if (r.statusCode() == 401) {
                botReady = false;
                plugin.getLogger().severe("Discord bot token is INVALID (HTTP 401). Bot logging disabled - fix discord.bot-token and run /ucsecurity reload.");
            } else {
                plugin.getLogger().warning("Discord bot verification returned HTTP " + r.statusCode() + ". Will keep trying when sending.");
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not reach Discord to verify the bot (" + e.getClass().getSimpleName() + "). Messages will be queued and retried.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Discord bot verification failed: " + sanitize(String.valueOf(e.getMessage())));
        }
    }

    private void checkChannel() throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(API + "/channels/" + channelId))
                .timeout(Duration.ofSeconds(15))
                .header("User-Agent", USER_AGENT)
                .header("Authorization", "Bot " + token)
                .GET().build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() == 404) {
            plugin.getLogger().severe("Discord channel not found - check discord.channel-id.");
        } else if (r.statusCode() == 403) {
            plugin.getLogger().warning("The bot cannot access the configured channel - give it View Channel + Send Messages + Embed Links.");
        }
    }

    // ------------------------------------------------------------------ helpers
    private static boolean validWebhook(String url) {
        if (url == null || url.isBlank() || url.equals("PUT_WEBHOOK_URL_HERE")) return false;
        if (!url.startsWith("https://")) return false;
        if (!(url.contains("discord.com/api/webhooks/") || url.contains("discordapp.com/api/webhooks/"))) return false;
        try {
            URI.create(url);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Removes secrets from any text before it is logged or sent. */
    private String sanitize(String s) {
        if (s == null) return "";
        String out = s;
        String t = token;
        String w = webhookUrl;
        if (t != null && t.length() > 6) out = out.replace(t, "***");
        if (w != null && w.length() > 10) out = out.replace(w, "***");
        return out.replaceAll("webhooks/\\d+/[A-Za-z0-9_\\-]+", "webhooks/***");
    }
}
