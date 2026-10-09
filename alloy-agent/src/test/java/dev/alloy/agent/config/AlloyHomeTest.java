package dev.alloy.agent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Tests where Alloy decides its folder is. */
class AlloyHomeTest {

    /** Root of the current drive, so the test reads the same on every system. */
    private static final Path ROOT = Path.of("").toAbsolutePath().getRoot();

    @Test
    void homeIsTheFolderTheAgentJarIsInstalledIn() {
        Path jar = AlloyHomeTest.ROOT.resolve(".alloy").resolve("agents").resolve("Alloy-Agent.jar");

        assertEquals(AlloyHomeTest.ROOT.resolve(".alloy"), AlloyHome.installedAround(jar).orElseThrow().root());
    }

    @Test
    void jarOutsideAnAgentsFolderDoesNotDecideTheHome() {
        Path jar = AlloyHomeTest.ROOT.resolve("project").resolve("target").resolve("alloy-agent.jar");

        assertTrue(AlloyHome.installedAround(jar).isEmpty());
    }

    @Test
    void defaultHomeAvoidsAUserFolderWithASpace() {
        Path plain = AlloyHomeTest.ROOT.resolve("Users").resolve("Admin");
        Path spaced = AlloyHomeTest.ROOT.resolve("Users").resolve("John Doe");

        assertEquals(plain.resolve(".alloy"), AlloyHome.defaultRoot(plain));
        assertEquals(AlloyHomeTest.ROOT.resolve(".alloy"), AlloyHome.defaultRoot(spaced));
    }
}
