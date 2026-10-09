package dev.alloy.bridge;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * A Forge mod ready to load: the agent has already rewritten its SRG names to the game's.
 *
 * @param originalJar   the jar the user put in the mods folder (never modified)
 * @param loadableJar   the rewritten copy in Alloy's cache, which is the one loaded
 * @param modClassNames dotted names of the {@code @Mod}-annotated classes found in the jar
 */
public record PreparedMod(Path originalJar, Path loadableJar, List<String> modClassNames) {

    /** Rejects {@code null} and freezes the list so the record stays immutable. */
    public PreparedMod {
        Objects.requireNonNull(originalJar, "originalJar");
        Objects.requireNonNull(loadableJar, "loadableJar");
        modClassNames = List.copyOf(modClassNames);
    }
}
