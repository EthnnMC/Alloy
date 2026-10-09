package dev.alloy.agent.launch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Tests what the agent derives from the command line and the class path. */
class LaunchInfoTest {

    @Test
    void findsTheVersionArgumentInTheCommandLine() {
        String commandLine = "com.moonsworth.lunar.genesis.Genesis --version 1.8.9 --assetIndex 1.8";

        assertEquals(Optional.of("1.8.9"), LaunchInfo.versionIn(commandLine));
    }

    @Test
    void versionIsEmptyWhenTheArgumentIsMissing() {
        assertEquals(Optional.empty(), LaunchInfo.versionIn("some.Main --width 854"));
    }

    @Test
    void forgeIsSupportedOnlyOnLunarOneEightNine() {
        assertTrue(new LaunchInfo(Optional.of("1.8.9"), true, List.of()).supportsForge());
        assertFalse(new LaunchInfo(Optional.of("1.7.10"), true, List.of()).supportsForge());
        assertFalse(new LaunchInfo(Optional.of("1.8.9"), false, List.of()).supportsForge());
        assertFalse(new LaunchInfo(Optional.empty(), true, List.of()).supportsForge());
    }

    @Test
    void picksTheMappingJarOfTheRequestedVersion() {
        Path wanted = Path.of("multiver", "lunar-platform-mappings-v1_8.jar");
        LaunchInfo launch = new LaunchInfo(Optional.of("1.8.9"), true, List.of(
                Path.of("multiver", "lunar.jar"),
                Path.of("multiver", "lunar-platform-mappings-v1_7.jar"),
                wanted));

        assertEquals(Optional.of(wanted), launch.lunarMappingsJar("1.8.9"));
    }

    @Test
    void mappingJarIsEmptyWhenNotOnTheClassPath() {
        LaunchInfo launch = new LaunchInfo(Optional.of("1.8.9"), true, List.of(Path.of("lunar.jar")));

        assertEquals(Optional.empty(), launch.lunarMappingsJar("1.8.9"));
    }

    @Test
    void versionTokenUsesMajorAndMinorOnly() {
        assertEquals("v1_8", LaunchInfo.versionToken("1.8.9"));
        assertEquals("v1_21", LaunchInfo.versionToken("1.21.11"));
    }

    @Test
    void gameDirectoryComesFromTheArguments() {
        Path directory = Path.of("games", "minecraft").toAbsolutePath().normalize();
        String[] arguments = {"--width", "854", "--gameDir", directory.toString(), "--height", "480"};

        assertEquals(directory, GameFiles.gameDirectory(arguments));
    }
}
