package dev.alloy.agent.forge;

import dev.alloy.agent.config.AlloyHome;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Finds the Forge mods dropped by the user, in {@code mods/} (any version) and
 * {@code mods/<version>/} (one game version). Other subfolders are not searched.
 */
public final class ModDiscovery {

    private static final String JAR_SUFFIX = ".jar";

    private ModDiscovery() {
    }

    /**
     * Lists the mod jars to load for a game version.
     *
     * @param home             Alloy folder
     * @param minecraftVersion game version, for example {@code "1.8.9"}
     * @return the jars, sorted by name within each folder (reproducible load order)
     * @throws IOException if a folder cannot be listed
     */
    public static List<Path> findModJars(AlloyHome home, String minecraftVersion) throws IOException {
        List<Path> jars = new ArrayList<>(ModDiscovery.jarsIn(home.modsDirectory()));
        jars.addAll(ModDiscovery.jarsIn(home.modsDirectory(minecraftVersion)));
        return List.copyOf(jars);
    }

    private static List<Path> jarsIn(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(directory)) {
            return entries
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(ModDiscovery.JAR_SUFFIX))
                    .sorted()
                    .toList();
        }
    }
}
