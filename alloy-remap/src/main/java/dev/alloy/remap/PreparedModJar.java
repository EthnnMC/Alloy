package dev.alloy.remap;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Result of preparing a Forge mod with {@link RemapToolkit#prepareMod}.
 *
 * @param jar           the rewritten jar, ready to be loaded in the game
 * @param modClassNames dotted names of the {@code @Mod} classes found in the jar, alphabetical
 * @param warnings      anomalies found while rewriting; empty if all went as expected. None
 *                      prevents the jar from existing: they flag what may not work.
 */
public record PreparedModJar(Path jar, List<String> modClassNames, List<String> warnings) {

    public PreparedModJar {
        Objects.requireNonNull(jar, "jar");
        modClassNames = List.copyOf(modClassNames);
        warnings = List.copyOf(warnings);
    }
}
