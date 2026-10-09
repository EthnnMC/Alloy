package dev.alloy.bridge;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Everything the agent passes to the Forge runtime when starting it.
 *
 * <p>The runtime lives in a different class loader than the agent, so this record uses only JDK
 * and {@code alloy-bridge} types.</p>
 *
 * @param minecraftVersion game version, e.g. {@code "1.8.9"}
 * @param gameDirectory    game directory ({@code .minecraft}), where mods read {@code config/}
 * @param alloyHome        Alloy's directory ({@code ~/.alloy})
 * @param forgeJar         Forge jar rewritten to the game's names (in Alloy's cache)
 * @param mods             mods ready to load, in discovery order
 * @param options          settings read from {@code alloy.properties} (key to value)
 * @param logger           Alloy's logger
 */
public record RuntimeContext(
        String minecraftVersion,
        Path gameDirectory,
        Path alloyHome,
        Path forgeJar,
        List<PreparedMod> mods,
        Map<String, String> options,
        BridgeLogger logger) {

    /** Rejects {@code null} and freezes the collections. */
    public RuntimeContext {
        Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        Objects.requireNonNull(gameDirectory, "gameDirectory");
        Objects.requireNonNull(alloyHome, "alloyHome");
        Objects.requireNonNull(forgeJar, "forgeJar");
        Objects.requireNonNull(logger, "logger");
        mods = List.copyOf(mods);
        options = Map.copyOf(options);
    }

    /** Reads a boolean setting, returning {@code defaultValue} when it is absent. */
    public boolean booleanOption(String key, boolean defaultValue) {
        String value = this.options.get(key);
        return value == null ? defaultValue : Boolean.parseBoolean(value.trim());
    }
}
