package me.uc.hussein.ultrasdiscord.utility;

import org.bukkit.Keyed;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Resolves sound names from config (either the Bukkit constant name like ENTITY_WITHER_SPAWN or a
 * namespaced key like entity.wither.spawn). Reflection keeps this compatible with both the old enum
 * Sound and the newer registry-based Sound on every 1.21.x version.
 */
public final class SoundUtil {
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();
    private static final String NONE = "";

    private SoundUtil() {
    }

    public static void clearCache() {
        CACHE.clear();
    }

    /** Returns the namespaced sound key or null when the name cannot be resolved. */
    public static String resolve(String name, Logger log) {
        if (name == null || name.isBlank()) return null;
        String cached = CACHE.get(name);
        if (cached != null) return cached.isEmpty() ? null : cached;
        String key = null;
        try {
            if (name.contains(".") || name.contains(":")) {
                key = name.toLowerCase(Locale.ROOT);
            } else {
                Field f = Sound.class.getField(name.toUpperCase(Locale.ROOT));
                Object o = f.get(null);
                if (o instanceof Keyed k) {
                    key = k.getKey().toString();
                }
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // fall through
        }
        if (key == null && log != null) {
            log.warning("Unknown sound in config: " + name);
        }
        CACHE.put(name, key == null ? NONE : key);
        return key;
    }

    public static void play(Player player, String name, float volume, float pitch, Logger log) {
        String key = resolve(name, log);
        if (key == null) return;
        try {
            player.playSound(player.getLocation(), key, volume, pitch);
        } catch (RuntimeException ignored) {
            // never let a sound break an alert
        }
    }
}
