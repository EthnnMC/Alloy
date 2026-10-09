package dev.alloy.remap.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alloy.remap.MemberShimTable;
import dev.alloy.remap.hierarchy.ClassHeader;
import dev.alloy.remap.hierarchy.ClassHierarchy;
import dev.alloy.remap.hierarchy.HeaderTable;
import dev.alloy.remap.testkit.ClassBytes;
import dev.alloy.remap.testkit.ClassScan;
import dev.alloy.remap.testkit.InMemoryClassLoader;
import dev.alloy.remap.testkit.SourceCompiler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The mod is compiled against a Forge-patched Minecraft that has extra members, then run against
 * a Minecraft without them, as a Forge mod on Lunar. Without the pass each call ends in
 * {@code NoSuchMethodError} or {@code NoSuchFieldError}; with it, it must return the right value.
 */
class MemberShimPassTest {

    private static final String SHIMS = String.join("\n",
            "# kind\towner\tname\tdescriptor\taction\ttarget...",
            "method\tnet/minecraft/network/NetworkManager\tchannel\t()Ljava/lang/Object;\tgetfield\tchannel\tLjava/lang/Object;",
            "method\tnet/minecraft/client/gui/GuiScreen\tdescribe\t(Ljava/lang/String;I)Ljava/lang/String;"
                    + "\tinvokestatic\texample/shim/Shims\tdescribe",
            "method\tnet/minecraft/client/gui/GuiScreen\tinject\t(Ljava/lang/String;)Ljava/lang/String;"
                    + "\tinvokestatic\texample/shim/Shims\tinject",
            "field\tnet/minecraft/client/gui/GuiScreen\tsubHit\tI\tinvokestatic\texample/shim/Shims\tsubHit",
            "field\tnet/minecraft/client/gui/GuiScreen\tRANGE\tF\tinvokestatic\texample/shim/Shims\trange");

    /** The real Minecraft (without Forge's additions) and the replacement methods. */
    private static final String[] RUNTIME_SOURCES = {
        """
        package net.minecraft.network;
        public class NetworkManager {
            public Object channel = "the channel";
        }
        """,
        """
        package net.minecraft.client.gui;
        public class GuiScreen {
            public int width = 3;
        }
        """,
        """
        package example.shim;
        import net.minecraft.client.gui.GuiScreen;
        public class Shims {
            public static int lastSubHit;
            public static float lastRange = 1.5F;
            public static String describe(GuiScreen screen, String prefix, int count) { return prefix + count + "/" + screen.width; }
            public static String inject(String text) { return "injected " + text; }
            public static int subHit(GuiScreen screen) { return 40 + screen.width; }
            public static void subHit(GuiScreen screen, int value) { Shims.lastSubHit = value; }
            public static float range() { return Shims.lastRange; }
            public static void range(float value) { Shims.lastRange = value; }
        }
        """
    };

    /** Minecraft as Forge patches it: what the mod is compiled against. */
    private static final String[] FORGE_PATCHED_SOURCES = {
        """
        package net.minecraft.network;
        public class NetworkManager {
            public Object channel = "the channel";
            public Object channel() { return this.channel; }
        }
        """,
        """
        package net.minecraft.client.gui;
        public class GuiScreen {
            public int width = 3;
            public int subHit;
            public static float RANGE;
            public String describe(String prefix, int count) { return "forge"; }
            public static String inject(String text) { return "forge"; }
        }
        """
    };

    private static final String[] MOD_SOURCES = {
        """
        package example.mod;
        import net.minecraft.network.NetworkManager;
        public class MyManager extends NetworkManager {
        }
        """,
        """
        package example.mod;
        import net.minecraft.network.NetworkManager;
        public class OwnManager extends NetworkManager {
            @Override public Object channel() { return "own channel"; }
        }
        """,
        """
        package example.mod;
        import net.minecraft.client.gui.GuiScreen;
        public class ModScreen extends GuiScreen {
            public String viaSuper() { return super.describe("super", 1); }
            public String viaThis() { return this.describe("this", 2); }
        }
        """,
        """
        package example.mod;
        import net.minecraft.client.gui.GuiScreen;
        import net.minecraft.network.NetworkManager;
        public class Probe {
            public static Object channelOf(NetworkManager manager) { return manager.channel(); }
            public static Object channelOfSubclass(MyManager manager) { return manager.channel(); }
            public static Object channelOfOverridingSubclass(OwnManager manager) { return manager.channel(); }
            public static String describe(GuiScreen screen) { return screen.describe("w", 5); }
            public static String inject() { return GuiScreen.inject("text"); }
            public static int readSubHit(GuiScreen screen) { return screen.subHit; }
            public static void writeSubHit(GuiScreen screen) { screen.subHit = 12; }
            public static float readRange() { return GuiScreen.RANGE; }
            public static void writeRange() { GuiScreen.RANGE = 64.0F; }
        }
        """
    };

    /** Untransformed mod classes (internal name to bytes). */
    private static Map<String, byte[]> modClasses;

    private static Map<String, byte[]> runtimeClasses;

    @BeforeAll
    static void compile() {
        String[] patchedAndMod = new String[MemberShimPassTest.FORGE_PATCHED_SOURCES.length
                + MemberShimPassTest.MOD_SOURCES.length];
        System.arraycopy(MemberShimPassTest.FORGE_PATCHED_SOURCES, 0, patchedAndMod, 0,
                MemberShimPassTest.FORGE_PATCHED_SOURCES.length);
        System.arraycopy(MemberShimPassTest.MOD_SOURCES, 0, patchedAndMod,
                MemberShimPassTest.FORGE_PATCHED_SOURCES.length, MemberShimPassTest.MOD_SOURCES.length);

        MemberShimPassTest.modClasses = new LinkedHashMap<>(SourceCompiler.compile(8, patchedAndMod));
        MemberShimPassTest.modClasses.keySet().removeIf(name -> !name.startsWith("example/mod/"));
        MemberShimPassTest.runtimeClasses = SourceCompiler.compile(8, MemberShimPassTest.RUNTIME_SOURCES);
    }

    /** Loads the mod (transformed or not) next to the real Minecraft and the replacement methods. */
    private static Class<?> loadProbe(Map<String, byte[]> mod) throws ClassNotFoundException {
        Map<String, byte[]> everything = new LinkedHashMap<>(MemberShimPassTest.runtimeClasses);
        everything.putAll(mod);
        return new InMemoryClassLoader(everything).loadClass("example.mod.Probe");
    }

    private static Map<String, byte[]> shimmedMod() {
        List<ClassHeader> headers = new ArrayList<>();
        MemberShimPassTest.modClasses.values().forEach(classFile -> headers.add(ClassHeader.read(classFile)));
        MemberShimPassTest.runtimeClasses.values().forEach(classFile -> headers.add(ClassHeader.read(classFile)));
        MemberShimPass pass = new MemberShimPass(
                MemberShimTable.parse(MemberShimPassTest.SHIMS), new ClassHierarchy(HeaderTable.of(headers)));
        return ClassBytes.applyToAll(MemberShimPassTest.modClasses, pass);
    }

    private static Object newInstance(Class<?> probe, String className) throws ReflectiveOperationException {
        return probe.getClassLoader().loadClass(className).getConstructor().newInstance();
    }

    private static Object callWith(Class<?> probe, String method, String argumentClass) throws ReflectiveOperationException {
        Object argument = MemberShimPassTest.newInstance(probe, argumentClass);
        for (Method candidate : probe.getMethods()) {
            if (candidate.getName().equals(method)) {
                return candidate.invoke(null, argument);
            }
        }
        throw new NoSuchMethodException(method);
    }

    @Test
    void withoutThePassTheForgeAddedMethodIsMissingAtRuntime() throws ReflectiveOperationException {
        Class<?> probe = MemberShimPassTest.loadProbe(MemberShimPassTest.modClasses);

        InvocationTargetException error = assertThrows(InvocationTargetException.class,
                () -> MemberShimPassTest.callWith(probe, "channelOf", "net.minecraft.network.NetworkManager"));

        assertEquals(NoSuchMethodError.class, error.getCause().getClass());
    }

    @Test
    void accessorCallBecomesAFieldRead() throws ReflectiveOperationException {
        Map<String, byte[]> mod = MemberShimPassTest.shimmedMod();
        Class<?> probe = MemberShimPassTest.loadProbe(mod);

        assertEquals("the channel",
                MemberShimPassTest.callWith(probe, "channelOf", "net.minecraft.network.NetworkManager"));
        Set<String> lines = ClassScan.describe(mod.get("example/mod/Probe"));
        assertTrue(lines.contains("GETFIELD net/minecraft/network/NetworkManager.channel Ljava/lang/Object;"),
                lines.toString());
    }

    @Test
    void callWrittenThroughASubclassIsReplacedToo() throws ReflectiveOperationException {
        Class<?> probe = MemberShimPassTest.loadProbe(MemberShimPassTest.shimmedMod());

        assertEquals("the channel", MemberShimPassTest.callWith(probe, "channelOfSubclass", "example.mod.MyManager"));
    }

    @Test
    void subclassThatDeclaresTheMethodItselfIsNotTouched() throws ReflectiveOperationException {
        Class<?> probe = MemberShimPassTest.loadProbe(MemberShimPassTest.shimmedMod());

        assertEquals("own channel",
                MemberShimPassTest.callWith(probe, "channelOfOverridingSubclass", "example.mod.OwnManager"));
    }

    @Test
    void instanceCallBecomesAStaticCallReceivingTheObject() throws ReflectiveOperationException {
        Class<?> probe = MemberShimPassTest.loadProbe(MemberShimPassTest.shimmedMod());

        assertEquals("w5/3", MemberShimPassTest.callWith(probe, "describe", "net.minecraft.client.gui.GuiScreen"));
    }

    @Test
    void superCallAndInheritedCallAreReplaced() throws ReflectiveOperationException {
        Class<?> probe = MemberShimPassTest.loadProbe(MemberShimPassTest.shimmedMod());
        Object screen = MemberShimPassTest.newInstance(probe, "example.mod.ModScreen");

        assertEquals("super1/3", screen.getClass().getMethod("viaSuper").invoke(screen));
        assertEquals("this2/3", screen.getClass().getMethod("viaThis").invoke(screen));
    }

    @Test
    void staticCallKeepsItsDescriptor() throws ReflectiveOperationException {
        Class<?> probe = MemberShimPassTest.loadProbe(MemberShimPassTest.shimmedMod());

        assertEquals("injected text", probe.getMethod("inject").invoke(null));
    }

    @Test
    void instanceFieldReadAndWriteBecomeStaticCalls() throws ReflectiveOperationException {
        Class<?> probe = MemberShimPassTest.loadProbe(MemberShimPassTest.shimmedMod());
        Class<?> shims = probe.getClassLoader().loadClass("example.shim.Shims");

        Object read = MemberShimPassTest.callWith(probe, "readSubHit", "net.minecraft.client.gui.GuiScreen");
        MemberShimPassTest.callWith(probe, "writeSubHit", "net.minecraft.client.gui.GuiScreen");

        assertEquals(43, read);
        assertEquals(12, shims.getField("lastSubHit").get(null));
    }

    @Test
    void staticFieldReadAndWriteBecomeStaticCalls() throws ReflectiveOperationException {
        Class<?> probe = MemberShimPassTest.loadProbe(MemberShimPassTest.shimmedMod());

        Object before = probe.getMethod("readRange").invoke(null);
        probe.getMethod("writeRange").invoke(null);
        Object after = probe.getMethod("readRange").invoke(null);

        assertEquals(1.5F, before);
        assertEquals(64.0F, after);
    }

    @Test
    void emptyTableLeavesTheModUnchanged() {
        MemberShimPass pass = new MemberShimPass(MemberShimTable.empty(), new ClassHierarchy(HeaderTable.empty()));

        Map<String, byte[]> untouched = ClassBytes.applyToAll(MemberShimPassTest.modClasses, pass);

        Set<String> lines = ClassScan.describe(untouched.get("example/mod/Probe"));
        assertTrue(lines.contains("INVOKEVIRTUAL net/minecraft/network/NetworkManager.channel ()Ljava/lang/Object;"),
                lines.toString());
    }
}
