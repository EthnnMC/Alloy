package dev.alloy.hooks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moonsworth.lunar.genesis.FakeGenesisLoader;
import dev.alloy.bridge.GameHooks;
import dev.alloy.hooks.testing.ByteClassLoader;
import dev.alloy.hooks.testing.BytecodeChecks;
import dev.alloy.hooks.testing.ClassHierarchy;
import dev.alloy.hooks.testing.FakeGame;
import dev.alloy.hooks.testing.SyntheticClass;
import dev.alloy.hooks.testing.WeaveLikePatch;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

/**
 * Tests the bridge between the game class loader and the system class loader on
 * {@link FakeGenesisLoader}, which imitates Lunar's: the patched copy is defined in a test
 * loader, instantiated, then asked to load classes.
 */
class GameLoaderBridgePatchTest {

    private static final String BRIDGE_CLASS = "dev.alloy.bridge.GameHooks";
    private static final String GAME_CLASS = FakeGame.NAME.replace('/', '.');

    private final GameLoaderBridgePatch patch = new GameLoaderBridgePatch();

    @Test
    void unpatchedLoaderCannotSeeTheBridgeClasses() throws Exception {
        ClassLoader gameLoader = GameLoaderBridgePatchTest.newGameLoader(GameLoaderBridgePatchTest.fakeLoaderBytes());

        assertThrows(ClassNotFoundException.class, () -> gameLoader.loadClass(GameLoaderBridgePatchTest.BRIDGE_CLASS));
    }

    @Test
    void patchedLoaderTakesTheBridgeClassesFromTheSystemLoader() throws Exception {
        byte[] patched = this.patch.patch(GameLoaderBridgePatchTest.fakeLoaderBytes()).orElseThrow();
        ClassLoader gameLoader = GameLoaderBridgePatchTest.newGameLoader(patched);

        Class<?> seenFromTheGame = gameLoader.loadClass(GameLoaderBridgePatchTest.BRIDGE_CLASS);

        ClassLoader agentLoader = ClassLoader.getSystemClassLoader();
        assertSame(agentLoader.loadClass(GameLoaderBridgePatchTest.BRIDGE_CLASS), seenFromTheGame);
    }

    @Test
    void patchedLoaderStillDefinesTheGameClassesItself() throws Exception {
        byte[] patched = this.patch.patch(GameLoaderBridgePatchTest.fakeLoaderBytes()).orElseThrow();
        ClassLoader gameLoader = GameLoaderBridgePatchTest.newGameLoader(patched);

        Class<?> gameClass = gameLoader.loadClass(GameLoaderBridgePatchTest.GAME_CLASS);

        assertSame(gameLoader, gameClass.getClassLoader());
    }

    @Test
    void patchedLoaderStillRefusesOtherClassesOfTheAgent() throws Exception {
        byte[] patched = this.patch.patch(GameLoaderBridgePatchTest.fakeLoaderBytes()).orElseThrow();
        ClassLoader gameLoader = GameLoaderBridgePatchTest.newGameLoader(patched);

        // Only the bridge package is shared: the rest of the agent stays invisible to the game.
        assertThrows(ClassNotFoundException.class, () -> gameLoader.loadClass("dev.alloy.hooks.HookCatalog"));
    }

    @Test
    void patchedLoaderAsksThePublishedClassSourceFirst() throws Exception {
        byte[] patched = this.patch.patch(GameLoaderBridgePatchTest.fakeLoaderBytes()).orElseThrow();
        ClassLoader gameLoader = GameLoaderBridgePatchTest.newGameLoader(patched);
        // Stands for a mod class: any class the game loader could not find by itself.
        Function<String, Class<?>> source =
                name -> name.equals("example.mod.Helper") ? GameLoaderBridgePatchTest.class : null;
        gameLoader.getClass().getField(GameHooks.CLASS_SOURCE_FIELD).set(null, source);

        assertSame(GameLoaderBridgePatchTest.class, gameLoader.loadClass("example.mod.Helper"));
        // A name the source does not know goes on to the loader's own lookup.
        assertSame(gameLoader, gameLoader.loadClass(GameLoaderBridgePatchTest.GAME_CLASS).getClassLoader());
        assertThrows(ClassNotFoundException.class, () -> gameLoader.loadClass("example.mod.Missing"));
    }

    @Test
    void patchComposesWithTheDetourWeaveInsertsFirst() throws Exception {
        byte[] weavePatched = GameLoaderBridgePatchTest.weaveLikeLoaderBytes();
        byte[] patched = this.patch.patch(weavePatched).orElseThrow();
        ClassLoader gameLoader = GameLoaderBridgePatchTest.newGameLoader(patched);

        // Both detours work: Alloy's, then the one placed before it.
        assertEquals(GameLoaderBridgePatchTest.BRIDGE_CLASS,
                gameLoader.loadClass(GameLoaderBridgePatchTest.BRIDGE_CLASS).getName());
        assertEquals("org.junit.jupiter.api.Test", gameLoader.loadClass("org.junit.jupiter.api.Test").getName());
    }

    @Test
    void patchedLoaderPassesTheBytecodeChecks() throws Exception {
        byte[] patched = this.patch.patch(GameLoaderBridgePatchTest.fakeLoaderBytes()).orElseThrow();
        ClassHierarchy hierarchy = new ClassHierarchy().withClass(patched).withSystemResources();

        assertEquals(List.of(), BytecodeChecks.problems(patched, hierarchy));
    }

    @Test
    void patchedWeaveLikeLoaderPassesTheBytecodeChecks() throws Exception {
        byte[] weavePatched = GameLoaderBridgePatchTest.weaveLikeLoaderBytes();
        byte[] patched = this.patch.patch(weavePatched).orElseThrow();
        ClassHierarchy hierarchy = new ClassHierarchy().withClass(patched).withSystemResources();

        assertEquals(List.of(), BytecodeChecks.problems(patched, hierarchy));
    }

    @Test
    void onlyDirectMembersOfTheGenesisPackageAreCandidates() {
        assertTrue(this.patch.isCandidate("com/moonsworth/lunar/genesis/ORCOIHCCICICIHRCCCCHHHRICHHICI"));
        assertFalse(this.patch.isCandidate("com/moonsworth/lunar/genesis/lib/Something"));
        assertFalse(this.patch.isCandidate("com/moonsworth/lunar/ichor/CICOHIIRRHHIIHRROHOICOCRHHHHOO"));
        assertFalse(this.patch.isCandidate("net/minecraft/client/Minecraft"));
    }

    @Test
    void classThatIsNotAUrlClassLoaderIsLeftAlone() {
        byte[] notALoader = new SyntheticClass("com/moonsworth/lunar/genesis/Genesis", Opcodes.V17)
                .defaultConstructor()
                .toByteArray();

        assertTrue(this.patch.patch(notALoader).isEmpty());
    }

    @Test
    void urlClassLoaderWithoutLoadClassIsLeftAlone() {
        // Shape of Lunar's "Bootstrap#" loader: extends URLClassLoader but only overrides findClass.
        byte[] bootstrapLoader = new SyntheticClass(
                "com/moonsworth/lunar/genesis/BootstrapLike", Opcodes.V17, "java/net/URLClassLoader")
                .method(Opcodes.ACC_PROTECTED, "findClass", "(Ljava/lang/String;)Ljava/lang/Class;", code -> {
                    code.visitInsn(Opcodes.ACONST_NULL);
                    code.visitInsn(Opcodes.ARETURN);
                })
                .toByteArray();

        assertTrue(this.patch.patch(bootstrapLoader).isEmpty());
    }

    /** Re-reads the bytecode javac produced for {@link FakeGenesisLoader}. */
    static byte[] fakeLoaderBytes() throws IOException {
        try (InputStream in = FakeGenesisLoader.class.getResourceAsStream("FakeGenesisLoader.class")) {
            return in.readAllBytes();
        }
    }

    /** The same loader after a Weave-style detour that delegates JUnit classes to the system loader. */
    private static byte[] weaveLikeLoaderBytes() throws IOException {
        return WeaveLikePatch.apply(GameLoaderBridgePatchTest.fakeLoaderBytes(), "org.junit.jupiter.api.");
    }

    /**
     * Defines the given loader class and creates an instance, isolated as in Lunar: its parent is
     * the platform loader, which sees neither the agent nor the tests.
     */
    static ClassLoader newGameLoader(byte[] loaderClassBytes) throws ReflectiveOperationException {
        Class<?> loaderClass = new ByteClassLoader().define(loaderClassBytes);
        Map<String, byte[]> gameClasses = Map.of(GameLoaderBridgePatchTest.GAME_CLASS, FakeGame.bytes(Opcodes.V17));
        return (ClassLoader) loaderClass
                .getConstructor(Map.class, ClassLoader.class)
                .newInstance(gameClasses, ClassLoader.getPlatformClassLoader());
    }
}
