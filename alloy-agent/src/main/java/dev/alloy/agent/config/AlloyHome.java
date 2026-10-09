package dev.alloy.agent.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * The Alloy folder on this machine ({@code ~/.alloy} by default). Immutable: it only computes
 * paths, except {@link #createDirectories()}.
 *
 * <pre>
 *   ~/.alloy/
 *     alloy.properties      settings
 *     mods/                 the user's Forge mods (never modified)
 *     mods/1.8.9/           mods for one game version only
 *     libraries/            Forge universal jar, downloaded once
 *     cache/                jars rewritten to the game's names (safe to delete)
 *     logs/latest.log       log of the last launch
 * </pre>
 */
public final class AlloyHome {

    /** System property that moves the Alloy folder (useful for tests). */
    public static final String HOME_PROPERTY = "alloy.home";

    private static final String DEFAULT_DIRECTORY_NAME = ".alloy";

    private final Path root;

    /**
     * Creates a handle on an Alloy folder.
     *
     * @param root root of the folder
     */
    public AlloyHome(Path root) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
    }

    /**
     * Returns the folder given by {@code -Dalloy.home}, otherwise {@code ~/.alloy}.
     *
     * @return the Alloy folder of this machine
     */
    public static AlloyHome resolve() {
        String configured = System.getProperty(AlloyHome.HOME_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            return new AlloyHome(Path.of(configured));
        }
        return new AlloyHome(Path.of(System.getProperty("user.home"), AlloyHome.DEFAULT_DIRECTORY_NAME));
    }

    /**
     * Creates the missing subfolders.
     *
     * @throws IOException if a folder cannot be created
     */
    public void createDirectories() throws IOException {
        for (Path directory : List.of(
                this.modsDirectory(), this.librariesDirectory(), this.cacheDirectory(), this.logsDirectory())) {
            Files.createDirectories(directory);
        }
    }

    /** Returns the absolute root of the Alloy folder. */
    public Path root() {
        return this.root;
    }

    /** Returns {@code alloy.properties}. */
    public Path configFile() {
        return this.root.resolve("alloy.properties");
    }

    /** Returns {@code mods/}, where the user drops Forge mods. */
    public Path modsDirectory() {
        return this.root.resolve("mods");
    }

    /**
     * Returns {@code mods/<version>/}, for mods of one game version.
     *
     * @param minecraftVersion game version, for example {@code "1.8.9"}
     */
    public Path modsDirectory(String minecraftVersion) {
        return this.modsDirectory().resolve(minecraftVersion);
    }

    /** Returns {@code libraries/}. */
    public Path librariesDirectory() {
        return this.root.resolve("libraries");
    }

    /** Returns {@code cache/}. */
    public Path cacheDirectory() {
        return this.root.resolve("cache");
    }

    /** Returns {@code logs/}. */
    public Path logsDirectory() {
        return this.root.resolve("logs");
    }

    @Override
    public String toString() {
        return this.root.toString();
    }
}
