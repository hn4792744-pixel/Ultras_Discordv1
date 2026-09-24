package me.uc.hussein.ultrasdiscord.utility;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** MiniMessage / placeholder helpers. */
public final class Text {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Pattern LEFTOVER = Pattern.compile("\\{[a-z0-9\\-]+}");

    private Text() {
    }

    /** Build a placeholder map from key,value,key,value... */
    public static Map<String, String> map(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    public static Component mm(String s) {
        return MM.deserialize(s == null ? "" : s);
    }

    /** Component for item names/lore: never italic. */
    public static Component item(String s) {
        return mm(s).decoration(TextDecoration.ITALIC, false);
    }

    /** Replace {key} with the escaped value (keys ending in "-mm" are inserted raw, as MiniMessage). */
    public static String fillMm(String tpl, Map<String, String> ph) {
        if (tpl == null) return "";
        if (ph == null || ph.isEmpty()) return tpl;
        String out = tpl;
        for (Map.Entry<String, String> e : ph.entrySet()) {
            String v = e.getValue() == null ? "" : e.getValue();
            if (!e.getKey().endsWith("-mm")) {
                v = MM.escapeTags(v);
            }
            out = out.replace("{" + e.getKey() + "}", v);
        }
        return out;
    }

    /** Replace {key} with raw values and blank out unknown placeholders. */
    public static String fillPlain(String tpl, Map<String, String> ph) {
        if (tpl == null) return "";
        String out = tpl;
        if (ph != null) {
            for (Map.Entry<String, String> e : ph.entrySet()) {
                out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
            }
        }
        return LEFTOVER.matcher(out).replaceAll("-");
    }

    /** Remove MiniMessage tags. */
    public static String strip(String mm) {
        if (mm == null) return "";
        return PlainTextComponentSerializer.plainText().serialize(mm(mm));
    }

    public static String escape(String s) {
        return MM.escapeTags(s == null ? "" : s);
    }

    public static String num(double v, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", v);
    }

    public static double clamp01(double v) {
        if (Double.isNaN(v)) return 0.0;
        return v < 0 ? 0 : Math.min(v, 1.0);
    }
}
