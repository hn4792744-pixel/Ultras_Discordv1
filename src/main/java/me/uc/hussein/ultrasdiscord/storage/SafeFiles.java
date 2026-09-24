package me.uc.hussein.ultrasdiscord.storage;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

/** Atomic writes with .bak safety copies and corruption-tolerant YAML reads. */
final class SafeFiles {
    private SafeFiles() {
    }

    static Path bak(Path p) {
        return p.resolveSibling(p.getFileName() + ".bak");
    }

    static void write(Path target, String content) throws IOException {
        Path parent = target.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);
        if (Files.exists(target)) {
            Files.copy(target, bak(target), StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static void delete(Path p) throws IOException {
        Files.deleteIfExists(p);
        Files.deleteIfExists(bak(p));
        Files.deleteIfExists(p.resolveSibling(p.getFileName() + ".tmp"));
    }

    /** Reads a YAML file; falls back to the .bak copy; quarantines corrupt files. Returns null if unusable. */
    static YamlConfiguration read(Path p, Logger log, java.util.function.Consumer<String> errorSink) {
        if (!Files.exists(p)) return null;
        YamlConfiguration y = tryLoad(p);
        if (y != null) return y;

        String msg = "Data file is corrupt: " + p.getFileName();
        log.severe(msg);
        Path b = bak(p);
        if (Files.exists(b)) {
            y = tryLoad(b);
            if (y != null) {
                log.warning("Recovered " + p.getFileName() + " from its backup copy.");
                if (errorSink != null) errorSink.accept(msg + " (recovered from backup)");
                return y;
            }
        }
        try {
            Path q = p.resolveSibling(p.getFileName() + ".corrupt-" + System.currentTimeMillis());
            Files.move(p, q, StandardCopyOption.REPLACE_EXISTING);
            log.severe("Moved unreadable file to " + q.getFileName() + " - starting with empty data for it.");
        } catch (IOException e) {
            log.severe("Could not quarantine corrupt file: " + e.getMessage());
        }
        if (errorSink != null) errorSink.accept(msg + " (could not recover, quarantined)");
        return null;
    }

    private static YamlConfiguration tryLoad(Path p) {
        try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            YamlConfiguration y = new YamlConfiguration();
            y.load(r);
            return y;
        } catch (IOException | InvalidConfigurationException | RuntimeException e) {
            return null;
        }
    }
}
