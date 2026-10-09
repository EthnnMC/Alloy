package dev.alloy.agent.weave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alloy.agent.config.AlloyConfig;
import dev.alloy.bridge.BridgeLogger;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests the decisions taken before chaining Weave, on a fake {@code ~/.weave} folder.
 * Actually starting Weave is not tested: it needs the game.
 */
class WeaveChainLoaderTest {

    private static final Optional<String> VERSION = Optional.of("1.8.9");

    @TempDir
    Path weaveHome;

    private final List<String> messages = new ArrayList<>();
    private final BridgeLogger logger = (level, message, error) -> this.messages.add(level + " " + message);

    @BeforeEach
    void installWeaveAgent() throws IOException {
        WeaveChainLoaderTest.writeJar(
                this.weaveHome.resolve("agents").resolve("Weave-Loader-Agent-1.4.0.jar"),
                "net.weavemc.loader.impl.bootstrap.AgentKt",
                null);
    }

    @Test
    void neverChainsWhenDisabled() throws IOException {
        this.addWeaveMod("mods/Example.jar");

        assertEquals(Optional.empty(), this.loaderWith("false").plan(WeaveChainLoaderTest.VERSION, List.of()));
    }

    @Test
    void autoModeSkipsWeaveWhenNoWeaveModExistsForTheVersion() throws IOException {
        this.addWeaveMod("mods/1.7.10/OldMod.jar");

        assertEquals(Optional.empty(), this.loaderWith("auto").plan(WeaveChainLoaderTest.VERSION, List.of()));
    }

    @Test
    void autoModeChainsWhenAWeaveModExistsForTheVersion() throws IOException {
        this.addWeaveMod("mods/1.8.9/Example.jar");

        Optional<WeavePlan> plan = this.loaderWith("auto").plan(WeaveChainLoaderTest.VERSION, List.of());

        assertTrue(plan.isPresent());
        assertEquals("net.weavemc.loader.impl.bootstrap.AgentKt", plan.get().premainClass());
        assertEquals(1, plan.get().modCount());
    }

    @Test
    void alwaysModeChainsEvenWithoutMods() throws IOException {
        assertTrue(this.loaderWith("true").plan(WeaveChainLoaderTest.VERSION, List.of()).isPresent());
    }

    @Test
    void refusesToChainWhenAForgeJarSitsInTheWeaveModsFolder() throws IOException {
        WeaveChainLoaderTest.writeJar(this.weaveHome.resolve("mods").resolve("forge-mod.jar"), null, "mcmod.info");

        Optional<WeavePlan> plan = this.loaderWith("true").plan(WeaveChainLoaderTest.VERSION, List.of());

        assertEquals(Optional.empty(), plan);
        assertTrue(this.messages.get(0).contains("is not a Weave mod"), this.messages.toString());
    }

    @Test
    void doesNotChainWhenWeaveIsAlreadyOnTheCommandLine() throws IOException {
        this.addWeaveMod("mods/Example.jar");
        Path ownAgent = this.weaveHome.resolve("agents").resolve("Weave-Loader-Agent-1.4.0.jar");

        Optional<WeavePlan> plan = this.loaderWith("true")
                .plan(WeaveChainLoaderTest.VERSION, List.of("-Xmx2g", "-javaagent:" + ownAgent));

        assertEquals(Optional.empty(), plan);
    }

    @Test
    void doesNotChainWhenTheVersionIsUnknown() throws IOException {
        assertEquals(Optional.empty(), this.loaderWith("true").plan(Optional.empty(), List.of()));
    }

    private WeaveChainLoader loaderWith(String weaveEnabled) {
        AlloyConfig config = AlloyConfig.of(Map.of(AlloyConfig.WEAVE_ENABLED, weaveEnabled));
        return new WeaveChainLoader(config, this.logger, this.weaveHome);
    }

    private void addWeaveMod(String relativePath) throws IOException {
        WeaveChainLoaderTest.writeJar(this.weaveHome.resolve(relativePath), null, "weave.mod.json");
    }

    /** Writes a small jar, optionally with an agent entry class and an empty entry. */
    private static void writeJar(Path jar, String premainClass, String entryName) throws IOException {
        Files.createDirectories(jar.getParent());
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (premainClass != null) {
            manifest.getMainAttributes().putValue("Premain-Class", premainClass);
        }
        try (OutputStream file = Files.newOutputStream(jar);
                JarOutputStream output = new JarOutputStream(file, manifest)) {
            if (entryName != null) {
                output.putNextEntry(new JarEntry(entryName));
                output.closeEntry();
            }
        }
    }
}
