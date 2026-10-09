package dev.alloy.agent.config;

import dev.alloy.remap.io.UserFolders;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The Alloy folder on this machine: the one the agent jar is installed in, by default
 * {@code ~/.alloy}, or {@code .alloy} at the root of the drive when the user folder has a space in
 * its path. Immutable: it only computes paths, except {@link #createDirectories()}.
 *
 * <pre>
 *   ~/.alloy/
 *     agents/               the agent jar named in the launcher's JVM argument
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
    private static final String AGENTS_DIRECTORY_NAME = "agents";

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
     * Returns the folder given by {@code -Dalloy.home}; otherwise the folder the running agent jar
     * is installed in; otherwise the default one (see {@link #defaultRoot(Path)}).
     *
     * @return the Alloy folder of this machine
     */
    public static AlloyHome resolve() {
        String configured = System.getProperty(AlloyHome.HOME_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            return new AlloyHome(Path.of(configured));
        }
        return AlloyHome.ownJar().flatMap(AlloyHome::installedAround)
                .orElseGet(() -> new AlloyHome(AlloyHome.defaultRoot(Path.of(System.getProperty("user.home")))));
    }

    /**
     * Returns the Alloy folder an agent jar belongs to, that is {@code <folder>/agents/<jar>}; empty
     * for a jar that sits elsewhere, such as a build output.
     */
    public static Optional<AlloyHome> installedAround(Path agentJar) {
        Path directory = agentJar.toAbsolutePath().getParent();
        boolean installed = directory != null && directory.getParent() != null
                && directory.getFileName().toString().equals(AlloyHome.AGENTS_DIRECTORY_NAME);
        return installed ? Optional.of(new AlloyHome(directory.getParent())) : Optional.empty();
    }

    /**
     * Returns where Alloy installs itself for a user: {@code ~/.alloy}, or {@code .alloy} at the
     * root of the drive when the user folder has a space in its path.
     */
    public static Path defaultRoot(Path userHome) {
        return UserFolders.spaceFree(userHome, AlloyHome.DEFAULT_DIRECTORY_NAME);
    }

    /** The jar this class was loaded from; empty when it runs from loose class files. */
    private static Optional<Path> ownJar() {
        try {
            CodeSource source = AlloyHome.class.getProtectionDomain().getCodeSource();
            return source == null ? Optional.empty() : Optional.of(Path.of(source.getLocation().toURI()));
        } catch (URISyntaxException | RuntimeException e) {
            return Optional.empty();
        }
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
