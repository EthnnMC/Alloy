package dev.alloy.agent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests reading the settings and creating the default file. */
class AlloyConfigTest {

    @TempDir
    Path directory;

    @Test
    void createsTheFileWithDefaultsOnFirstRun() throws IOException {
        Path file = this.directory.resolve("alloy.properties");

        AlloyConfig config = AlloyConfig.load(file);

        assertTrue(Files.isRegularFile(file));
        assertTrue(config.forgeEnabled());
        assertEquals(WeaveMode.AUTO, config.weaveMode());
        assertFalse(config.debug());
        assertEquals(Optional.empty(), config.forgeUniversalJar());
    }

    @Test
    void readsValuesWrittenByTheUser() throws IOException {
        Path file = this.directory.resolve("alloy.properties");
        Files.writeString(file, """
                forge.enabled=false
                weave.enabled=true
                log.debug = true
                weave.agent=C:/weave/agent.jar
                """);

        AlloyConfig config = AlloyConfig.load(file);

        assertFalse(config.forgeEnabled());
        assertEquals(WeaveMode.ALWAYS, config.weaveMode());
        assertTrue(config.debug());
        assertEquals(Optional.of(Path.of("C:/weave/agent.jar")), config.weaveAgent());
    }

    @Test
    void unknownWeaveModeFallsBackToAuto() {
        AlloyConfig config = AlloyConfig.of(Map.of(AlloyConfig.WEAVE_ENABLED, "maybe"));

        assertEquals(WeaveMode.AUTO, config.weaveMode());
    }

    @Test
    void emptyValueMeansDefault() {
        AlloyConfig config = AlloyConfig.of(Map.of(AlloyConfig.FORGE_ENABLED, ""));

        assertTrue(config.forgeEnabled());
    }
}
