package dev.alloy.hooks;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alloy.bridge.GameHooks;
import dev.alloy.hooks.inject.HeadCall;
import dev.alloy.hooks.inject.HookArgument;
import dev.alloy.hooks.inject.HookCall;
import dev.alloy.hooks.testing.ByteClassLoader;
import dev.alloy.hooks.testing.SyntheticClass;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

/**
 * Tests the transformer's decisions: which classes it modifies, for which class loader, and when
 * during startup. The game loader is played by {@code FakeGenesisLoader}.
 */
class GameClassTransformerTest {

    private static final String FAKE_LOADER = "com/moonsworth/lunar/genesis/FakeGenesisLoader";
    private static final String MAIN_DESCRIPTOR = "([Ljava/lang/String;)V";

    private final List<String> logged = new ArrayList<>();
    private GameClassTransformer transformer;

    @BeforeEach
    void createTransformer() {
        this.transformer = new GameClassTransformer(
                GameClassTransformerTest.catalog(), (level, message, error) -> this.logged.add(level + " " + message));
    }

    // ------------------------------------------------------------------ classes never to touch

    @Test
    void jdkClassesAreIgnored() {
        assertNull(this.transformer.transform(null, "java/lang/String", null, null, new byte[0]));
    }

    @Test
    void classWithoutANameIsIgnored() {
        assertNull(this.transformer.transform(new ByteClassLoader(), null, null, null, new byte[0]));
    }

    @Test
    void classOutsideTheCatalogIsIgnored() {
        byte[] other = new SyntheticClass("com/example/Other", Opcodes.V17).defaultConstructor().toByteArray();

        assertNull(this.transformer.transform(new ByteClassLoader(), "com/example/Other", null, null, other));
    }

    @Test
    void unreadableBytecodeIsLoggedAndLeftUnchanged() {
        byte[] garbage = {1, 2, 3};

        byte[] result = this.transformer.transform(
                new ByteClassLoader(), "com/moonsworth/lunar/genesis/Broken", null, null, garbage);

        assertNull(result);
        assertEquals(1, this.logged.size());
        assertTrue(this.logged.get(0).startsWith("ERROR Cannot transform com/moonsworth/lunar/genesis/Broken"));
    }

    // ------------------------------------------------------------------ bridge and game loader

    @Test
    void bridgeIsNotInstalledBeforeTheGameLoaderClassIsSeen() {
        assertFalse(this.transformer.isLoaderBridgeInstalled());
    }

    @Test
    void bridgeIsInstalledInTheGameLoaderClass() throws Exception {
        this.newBridgedGameLoader(new HashMap<>());

        assertTrue(this.transformer.isLoaderBridgeInstalled());
    }

    @Test
    void mainClassRevealsTheGameLoader() throws Exception {
        ClassLoader gameLoader = this.newBridgedGameLoader(new HashMap<>());

        byte[] patchedMain = this.transformMain(gameLoader);

        assertNotNull(patchedMain);
        assertEquals(Optional.of(gameLoader), this.transformer.gameLoader());
    }

    @Test
    void mainClassReceivesItsHookBeforeGameHooksAreEnabled() throws Exception {
        this.transformMain(this.newBridgedGameLoader(new HashMap<>()));

        assertEquals(HookStatus.APPLIED, this.transformer.report().outcomeOf("boot.main").status());
    }

    @Test
    void mainClassOfALoaderWithoutTheBridgeIsLeftAlone() {
        byte[] result = this.transformMain(new ByteClassLoader());

        // Without the bridge the injected call would crash the game, so inject nothing.
        assertNull(result);
        assertEquals(Optional.empty(), this.transformer.gameLoader());
    }

    @Test
    void missingBridgeIsReportedOnce() {
        this.transformMain(new ByteClassLoader());
        this.transformMain(new ByteClassLoader());

        assertEquals(1, this.logged.stream().filter(line -> line.startsWith("WARN ")).count());
    }

    // ------------------------------------------------------------------ game hooks

    @Test
    void gameClassIsLeftAloneUntilGameHooksAreEnabled() throws Exception {
        ClassLoader gameLoader = this.newBridgedGameLoader(new HashMap<>());
        this.transformMain(gameLoader);

        assertNull(this.transformMinecraft(gameLoader));
        assertEquals(HookStatus.PENDING, this.transformer.report().outcomeOf("tick.client").status());
    }

    @Test
    void gameClassReceivesItsHooksOnceEnabled() throws Exception {
        ClassLoader gameLoader = this.newBridgedGameLoader(new HashMap<>());
        this.transformMain(gameLoader);
        this.transformer.enableGameHooks();

        assertNotNull(this.transformMinecraft(gameLoader));
        assertEquals(HookStatus.APPLIED, this.transformer.report().outcomeOf("tick.client").status());
    }

    @Test
    void sameClassNameInAnotherLoaderIsLeftAlone() throws Exception {
        this.transformMain(this.newBridgedGameLoader(new HashMap<>()));
        this.transformer.enableGameHooks();

        assertNull(this.transformMinecraft(new ByteClassLoader()));
    }

    @Test
    void gameClassIsLeftAloneWhileNoGameLoaderIsKnown() {
        this.transformer.enableGameHooks();

        assertNull(this.transformMinecraft(new ByteClassLoader()));
    }

    @Test
    void retransformationProducesTheSameBytecode() throws Exception {
        ClassLoader gameLoader = this.newBridgedGameLoader(new HashMap<>());
        this.transformMain(gameLoader);
        this.transformer.enableGameHooks();

        byte[] first = this.transformMinecraft(gameLoader);
        byte[] second = this.transformMinecraft(gameLoader);

        assertArrayEquals(first, second);
    }

    @Test
    void hookedClassNamesLeaveOutTheMainClass() {
        assertEquals(Set.of(GameClasses.MINECRAFT), this.transformer.hookedClassNames());
    }

    @Test
    void warmUpChangesNothingAndLogsNothing() {
        this.transformer.warmUp();

        assertFalse(this.transformer.isLoaderBridgeInstalled());
        assertEquals(Optional.empty(), this.transformer.gameLoader());
        assertEquals(List.of("boot.main", "tick.client"), this.transformer.report().hookIdsWith(HookStatus.PENDING));
        assertEquals(List.of(), this.logged);
    }

    // ------------------------------------------------------------------ end to end

    @Test
    void gameCodeReachesTheAgentThroughTheBridge() throws Exception {
        Map<String, byte[]> gameClasses = new HashMap<>();
        ClassLoader gameLoader = this.newBridgedGameLoader(gameClasses);
        gameClasses.put("net.minecraft.client.main.Main", this.transformMain(gameLoader));
        List<String> received = new ArrayList<>();
        GameHooks.setGameStartListener(arguments -> received.addAll(Arrays.asList(arguments)));

        // Main is defined by the fake game loader, which cannot see the agent: only the bridge
        // lets the injected call find GameHooks in the system loader.
        Class<?> main = gameLoader.loadClass("net.minecraft.client.main.Main");
        main.getMethod("main", String[].class).invoke(null, (Object) new String[] {"--version", "1.8.9"});

        assertEquals(List.of("--version", "1.8.9"), received);
        assertEquals(gameLoader, main.getClassLoader());
    }

    // ------------------------------------------------------------------ helpers

    /** Reduced catalog: the startup hook and one ordinary hook. */
    private static HookCatalog catalog() {
        return HookCatalog.of(List.of(
                Hook.inMethod("boot.main", GameClasses.MAIN, "main", GameClassTransformerTest.MAIN_DESCRIPTOR,
                        new HeadCall(HookCall.to("gameMain", GameClassTransformerTest.MAIN_DESCRIPTOR,
                                HookArgument.parameter(1)))).activeFromTheStart(),
                Hook.inMethod("tick.client", GameClasses.MINECRAFT, "runTick", "()V",
                        new HeadCall(HookCall.to("clientTickStart", "()V")))));
    }

    /**
     * Replays the start of a launch: the bootstrap loader defines the game loader class through
     * the transformer, then an instance is created.
     */
    private ClassLoader newBridgedGameLoader(Map<String, byte[]> gameClasses) throws Exception {
        ByteClassLoader bootstrapLoader = new ByteClassLoader();
        byte[] bridged = this.transformer.transform(
                bootstrapLoader, GameClassTransformerTest.FAKE_LOADER, null, null,
                GameLoaderBridgePatchTest.fakeLoaderBytes());
        Class<?> loaderClass = bootstrapLoader.define(bridged);
        return (ClassLoader) loaderClass
                .getConstructor(Map.class, ClassLoader.class)
                .newInstance(gameClasses, ClassLoader.getPlatformClassLoader());
    }

    private byte[] transformMain(ClassLoader loader) {
        // The body uses only the JDK: the fake game loader cannot see the test classes.
        byte[] main = new SyntheticClass(GameClasses.MAIN, Opcodes.V17)
                .method(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "main", GameClassTransformerTest.MAIN_DESCRIPTOR,
                        code -> code.visitInsn(Opcodes.RETURN))
                .toByteArray();
        return this.transformer.transform(loader, GameClasses.MAIN, null, null, main);
    }

    private byte[] transformMinecraft(ClassLoader loader) {
        byte[] minecraft = new SyntheticClass(GameClasses.MINECRAFT, Opcodes.V17)
                .defaultConstructor()
                .method(Opcodes.ACC_PUBLIC, "runTick", "()V", code -> code.visitInsn(Opcodes.RETURN))
                .toByteArray();
        return this.transformer.transform(loader, GameClasses.MINECRAFT, null, null, minecraft);
    }
}
