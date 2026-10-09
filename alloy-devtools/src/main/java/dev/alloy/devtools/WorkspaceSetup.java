package dev.alloy.devtools;

import dev.alloy.remap.RemapToolkit;
import dev.alloy.remap.fetch.ForgeDistribution;
import dev.alloy.remap.io.UserFolders;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

/**
 * The {@code setup-workspace} command: builds the two jars the {@code alloy-forge} module
 * compiles against, in the names Lunar's game uses ({@code minecraft-named} and
 * {@code forge-named}). Neither Minecraft nor Forge can be redistributed, so they are produced
 * locally from the developer's installation into a Maven-style repository
 * ({@code workspace/repository}).
 */
public final class WorkspaceSetup {

    /** Maven group of the locally built jars. */
    public static final String GROUP_ID = "dev.alloy.workspace";

    private static final String MINECRAFT_VERSION = "1.8.9";
    private static final String MINECRAFT_ARTIFACT = "minecraft-named";
    private static final String FORGE_ARTIFACT = "forge-named";

    private final PrintStream output;

    /**
     * Creates the command.
     *
     * @param output stream on which the command reports what it does
     */
    public WorkspaceSetup(PrintStream output) {
        this.output = Objects.requireNonNull(output, "output");
    }

    /**
     * Runs the command.
     *
     * @param options command-line options ({@code lunar-mappings}, {@code vanilla-jar}, {@code forge-universal}, {@code out})
     * @throws IOException if an input file is missing or a write fails
     */
    public void run(Options options) throws IOException {
        Path userHome = Path.of(System.getProperty("user.home"));
        Path mappingsJar = WorkspaceSetup.existing(
                options.path("lunar-mappings").orElse(
                        userHome.resolve(".lunarclient/offline/multiver/lunar-platform-mappings-v1_8.jar")),
                "Lunar's mapping jar (launch Lunar Client 1.8.9 once, or pass --lunar-mappings)");
        Path vanillaJar = WorkspaceSetup.existing(
                options.path("vanilla-jar").orElse(WorkspaceSetup.defaultVanillaJar(userHome)),
                "the Minecraft " + WorkspaceSetup.MINECRAFT_VERSION + " client jar (or pass --vanilla-jar)");
        Path universalJar = this.forgeUniversalJar(options, userHome);
        Path repository = options.path("out").orElse(Path.of("workspace")).resolve("repository").toAbsolutePath();

        RemapToolkit toolkit = RemapToolkit.forLunar(mappingsJar, WorkspaceSetup.MINECRAFT_VERSION, vanillaJar);

        Path minecraftJar = this.artifactFile(repository, WorkspaceSetup.MINECRAFT_ARTIFACT, WorkspaceSetup.MINECRAFT_VERSION);
        this.output.println("Renaming Minecraft " + WorkspaceSetup.MINECRAFT_VERSION + " -> " + minecraftJar);
        toolkit.prepareVanilla(minecraftJar);

        Path forgeJar = this.artifactFile(repository, WorkspaceSetup.FORGE_ARTIFACT, ForgeDistribution.SHORT_VERSION);
        this.output.println("Renaming Forge " + ForgeDistribution.SHORT_VERSION + " -> " + forgeJar);
        // No class removed: compiling needs the whole Forge API.
        toolkit.prepareForge(universalJar, forgeJar, Set.of());
        toolkit.lastForgeWarnings().forEach(warning -> this.output.println("  warning: " + warning));

        this.output.println("Workspace ready. Build the whole project again: alloy-forge and alloy-dist are now included.");
    }

    private Path forgeUniversalJar(Options options, Path userHome) throws IOException {
        if (options.path("forge-universal").isPresent()) {
            return WorkspaceSetup.existing(options.path("forge-universal").get(), "the Forge universal jar");
        }
        // The agent's own library folder, so the jar is downloaded once for both.
        Path target = UserFolders.spaceFree(userHome, ".alloy").resolve("libraries").resolve(ForgeDistribution.FILE_NAME);
        if (!ForgeDistribution.download().matches(target)) {
            this.output.println("Downloading " + ForgeDistribution.FILE_NAME + " from " + ForgeDistribution.DOWNLOAD_URI.getHost());
        }
        return ForgeDistribution.download().ensure(target);
    }

    /** Creates the artifact folder and its small {@code .pom}, then returns the jar location. */
    private Path artifactFile(Path repository, String artifactId, String version) throws IOException {
        Path directory = repository
                .resolve(WorkspaceSetup.GROUP_ID.replace('.', '/'))
                .resolve(artifactId)
                .resolve(version);
        Files.createDirectories(directory);
        String baseName = artifactId + "-" + version;
        Files.writeString(directory.resolve(baseName + ".pom"), WorkspaceSetup.pom(artifactId, version), StandardCharsets.UTF_8);
        return directory.resolve(baseName + ".jar");
    }

    private static String pom(String artifactId, String version) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>%s</groupId>
                  <artifactId>%s</artifactId>
                  <version>%s</version>
                  <packaging>jar</packaging>
                  <description>Generated locally by alloy-devtools. Never publish or commit this artifact.</description>
                </project>
                """.formatted(WorkspaceSetup.GROUP_ID, artifactId, version);
    }

    private static Path defaultVanillaJar(Path userHome) {
        String appData = System.getenv("APPDATA");
        Path gameDirectory = (appData != null && !appData.isBlank() ? Path.of(appData) : userHome).resolve(".minecraft");
        return gameDirectory.resolve("versions").resolve(WorkspaceSetup.MINECRAFT_VERSION)
                .resolve(WorkspaceSetup.MINECRAFT_VERSION + ".jar");
    }

    private static Path existing(Path file, String description) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IOException("Cannot find " + description + ": " + file);
        }
        return file;
    }
}
