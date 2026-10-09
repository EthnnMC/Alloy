package dev.alloy.remap.testkit;

import dev.alloy.remap.RemapToolkit;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;

/**
 * Test helper: locates machine-specific files (Lunar, Mojang and Forge jars, reference mod, real
 * game classes). Each is set by a system property, with a default when a usual location exists; if
 * the file is missing, the test is skipped rather than failed.
 */
public final class LocalArtifacts {

    /** Minecraft version targeted by Alloy. */
    public static final String MINECRAFT_VERSION = "1.8.9";

    private static final String FORGE_UNIVERSAL_NAME = "forge-1.8.9-11.15.1.2318-1.8.9-universal.jar";

    private LocalArtifacts() {
        // Utility class.
    }

    /** Lunar mappings jar (property {@code alloy.test.lunarMappings}). */
    public static Path lunarMappings() {
        Path byDefault = Path.of(System.getProperty("user.home"), ".lunarclient", "offline", "multiver",
                "lunar-platform-mappings-v1_8.jar");
        return LocalArtifacts.existing("alloy.test.lunarMappings", byDefault);
    }

    /** Minecraft 1.8.9 client jar (property {@code alloy.test.vanillaJar}). */
    public static Path vanillaJar() {
        String appData = System.getenv("APPDATA");
        Path byDefault = appData == null
                ? null
                : Path.of(appData, ".minecraft", "versions", "1.8.9", "1.8.9.jar");
        return LocalArtifacts.existing("alloy.test.vanillaJar", byDefault);
    }

    /** Forge universal jar (property {@code alloy.test.forgeUniversal}). */
    public static Path forgeUniversal() {
        Path byDefault = Path.of(System.getProperty("user.home"), ".alloy", "libraries",
                LocalArtifacts.FORGE_UNIVERSAL_NAME);
        return LocalArtifacts.existing("alloy.test.forgeUniversal", byDefault);
    }

    /** Reference mod (property {@code alloy.test.modJar}, no default). */
    public static Path modJar() {
        return LocalArtifacts.existing("alloy.test.modJar", null);
    }

    /** Directory of the real game classes run by Lunar (property {@code alloy.test.bakeClasses}). */
    public static Path bakeClasses() {
        return LocalArtifacts.existing("alloy.test.bakeClasses", null);
    }

    /**
     * A reverse-engineering report file (directory from property {@code alloy.test.researchDir},
     * default {@code <temp dir>/alloy-sp/research}).
     *
     * @param relativePath path of the file inside that directory
     */
    public static Path research(String relativePath) {
        String configured = System.getProperty("alloy.test.researchDir");
        Path directory = configured != null
                ? Path.of(configured)
                : Path.of(System.getProperty("java.io.tmpdir"), "alloy-sp", "research");
        Path file = directory.resolve(relativePath);
        Assumptions.assumeTrue(Files.exists(file), "research file not found: " + file);
        return file;
    }

    /** A new toolkit wired to the real Lunar and Mojang files. */
    public static RemapToolkit toolkit() {
        return RemapToolkit.forLunar(
                LocalArtifacts.lunarMappings(), LocalArtifacts.MINECRAFT_VERSION, LocalArtifacts.vanillaJar());
    }

    /** Prints a measurement made on the real files, with a prefix easy to find in the Maven output. */
    public static void report(String line) {
        System.out.println("[alloy-remap local] " + line);
    }

    private static Path existing(String property, Path byDefault) {
        String configured = System.getProperty(property);
        Path path = configured != null && !configured.isBlank() ? Path.of(configured) : byDefault;
        Assumptions.assumeTrue(path != null && Files.exists(path),
                "local artifact not available (set -D" + property + "=...): " + path);
        return path;
    }
}
