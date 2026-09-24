package me.uc.hussein.ultrasdiscord.config;

import me.uc.hussein.ultrasdiscord.UltrasDiscord;
import me.uc.hussein.ultrasdiscord.utility.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Loads messages_ar.yml / messages_en.yml (selected by "language" in config.yml). */
public final class Messages {
    private final UltrasDiscord plugin;
    private volatile YamlConfiguration cfg = new YamlConfiguration();
    private volatile String lang = "en";

    public Messages(UltrasDiscord plugin) {
        this.plugin = plugin;
    }

    public void load() {
        ensureFile("messages_ar.yml");
        ensureFile("messages_en.yml");
        lang = plugin.cfg().language();
        String file = "messages_" + lang + ".yml";
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), file));
        try (InputStream in = plugin.getResource(file)) {
            if (in != null) {
                y.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not read bundled " + file + ": " + e.getMessage());
        }
        cfg = y;
    }

    private void ensureFile(String name) {
        File f = new File(plugin.getDataFolder(), name);
        if (!f.exists()) {
            plugin.saveResource(name, false);
        }
    }

    public String language() {
        return lang;
    }

    /** Raw MiniMessage string (the key itself when missing so problems are visible). */
    public String raw(String key) {
        String s = cfg.getString(key);
        return s == null ? key : s;
    }

    public List<String> rawList(String key) {
        List<String> l = cfg.getStringList(key);
        if (l.isEmpty()) {
            String s = cfg.getString(key);
            if (s != null) return List.of(s);
        }
        return l;
    }

    public Component c(String key, Map<String, String> ph) {
        return Text.mm(Text.fillMm(raw(key), ph));
    }

    public Component c(String key) {
        return c(key, null);
    }

    public List<Component> list(String key, Map<String, String> ph) {
        List<Component> out = new ArrayList<>();
        for (String line : rawList(key)) {
            out.add(Text.mm(Text.fillMm(line, ph)));
        }
        return out;
    }

    /** Plain text (no MiniMessage tags), placeholders replaced verbatim. */
    public String plain(String key, Map<String, String> ph) {
        return Text.fillPlain(Text.strip(raw(key)), ph);
    }

    public String plain(String key) {
        return plain(key, null);
    }

    public List<String> plainList(String key, Map<String, String> ph) {
        List<String> out = new ArrayList<>();
        for (String line : rawList(key)) {
            out.add(Text.fillPlain(Text.strip(line), ph));
        }
        return out;
    }

    /** Send prefix + message. */
    public void send(CommandSender to, String key, Map<String, String> ph) {
        to.sendMessage(Text.mm(raw("prefix") + Text.fillMm(raw(key), ph)));
    }

    public void send(CommandSender to, String key) {
        send(to, key, null);
    }

    /** Send a message list without prefix. */
    public void sendList(CommandSender to, String key, Map<String, String> ph) {
        for (Component c : list(key, ph)) {
            to.sendMessage(c);
        }
    }
}
