package dev.alloy.agent.launch;

import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Utility that locates on disk the game files Alloy needs. */
public final class GameFiles {

    private static final String GAME_DIRECTORY_ARGUMENT = "--gameDir";
    private static final String DEFAULT_GAME_DIRECTORY = ".minecraft";

    private GameFiles() {
    }

    /**
     * Finds Mojang's Minecraft client jar (obfuscated names): in the URL list of Lunar's game
     * loader, otherwise at the usual location.
     *
     * @param gameLoader class loader of the game
     * @param version    game version, for example {@code "1.8.9"}
     * @return the jar, or empty if not found
     */
    public static Optional<Path> vanillaJar(ClassLoader gameLoader, String version) {
        if (gameLoader instanceof URLClassLoader urlLoader) {
            for (URL url : urlLoader.getURLs()) {
                Optional<Path> path = GameFiles.toPath(url);
                if (path.isPresent() && GameFiles.isVersionJar(path.get(), version)) {
                    return path;
                }
            }
        }
        Path usual = GameFiles.defaultGameDirectory().resolve("versions").resolve(version).resolve(version + ".jar");
        return Files.isRegularFile(usual) ? Optional.of(usual) : Optional.empty();
    }

    /**
     * Returns the game folder: the {@code --gameDir} argument, otherwise the usual {@code .minecraft}.
     *
     * @param arguments Minecraft launch arguments
     */
    public static Path gameDirectory(String[] arguments) {
        for (int i = 0; i + 1 < arguments.length; i++) {
            if (GameFiles.GAME_DIRECTORY_ARGUMENT.equals(arguments[i])) {
                return Path.of(arguments[i + 1]).toAbsolutePath().normalize();
            }
        }
        return GameFiles.defaultGameDirectory();
    }

    private static Path defaultGameDirectory() {
        String appData = System.getenv("APPDATA");
        Path parent = appData != null && !appData.isBlank() ? Path.of(appData) : Path.of(System.getProperty("user.home"));
        return parent.resolve(GameFiles.DEFAULT_GAME_DIRECTORY);
    }

    /** Matches {@code .../versions/1.8.9/1.8.9.jar}. */
    private static boolean isVersionJar(Path path, String version) {
        Path fileName = path.getFileName();
        Path parent = path.getParent();
        return fileName != null
                && parent != null
                && parent.getFileName() != null
                && fileName.toString().equals(version + ".jar")
                && parent.getFileName().toString().equals(version);
    }

    private static Optional<Path> toPath(URL url) {
        if (!"file".equals(url.getProtocol())) {
            return Optional.empty();
        }
        try {
            return Optional.of(Path.of(url.toURI()));
        } catch (URISyntaxException | IllegalArgumentException e) {
            // Malformed URL built by Lunar: ignore it, the usual location remains.
            return Optional.empty();
        }
    }
}
