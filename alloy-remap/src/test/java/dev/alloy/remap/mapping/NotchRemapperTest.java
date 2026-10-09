package dev.alloy.remap.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alloy.remap.hierarchy.ClassHeader;
import dev.alloy.remap.hierarchy.ClassHierarchy;
import dev.alloy.remap.hierarchy.HeaderTable;
import dev.alloy.remap.testkit.ClassBytes;
import dev.alloy.remap.testkit.ClassScan;
import dev.alloy.remap.testkit.InMemoryClassLoader;
import dev.alloy.remap.testkit.KinClassSpec;
import dev.alloy.remap.testkit.KinFixture;
import dev.alloy.remap.testkit.SourceCompiler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The "Minecraft" here is four obfuscated classes ({@code a} to {@code d}); the {@code Forge...}
 * classes stand for the Forge universal jar: they are not in the table and use obfuscated names.
 * After remapping, everything is loaded and run to check the names still point to the same members.
 */
class NotchRemapperTest {

    /** Remapped internal name to remapped class. */
    private static Map<String, byte[]> remapped;

    private static ClassLoader loader;

    @BeforeAll
    static void compileAndRemap() throws IOException {
        Map<String, byte[]> compiled = SourceCompiler.compile(8,
                """
                public class a {
                    public int a = 1;
                    public void a() { this.a += 10; }
                    public static int e() { return 1; }
                }
                """,
                """
                public class b extends a {
                }
                """,
                """
                public interface c {
                    String a(String text);
                }
                """,
                """
                public class d extends a {
                    public static int e() { return 2; }
                }
                """,
                """
                public class ForgeConfig extends b {
                    @Override public void a() { super.a(); this.a += 100; }
                    public int read() { return this.a; }
                    public c listener() { return text -> text + "!"; }
                }
                """,
                """
                public class ForgeCalls {
                    public static int viaDerived() { return d.e(); }
                    public static int viaBase() { return a.e(); }
                }
                """,
                """
                public class ForgeShadow extends a {
                    public int a = 5;
                    public int own() { return this.a; }
                    public int inherited() { return ((a) this).a; }
                }
                """,
                """
                public class ForgeImpl implements c {
                    @Override public String a(String text) { return "impl:" + text; }
                }
                """);

        KinFixture kin = new KinFixture()
                .add(KinClassSpec.type("a", "net/minecraft/Screen")
                        .field("a", "I", "width")
                        .method("a", "()V", "draw")
                        .method("e", "()I", "baseValue"))
                .add(KinClassSpec.type("b", "net/minecraft/ChatScreen"))
                .add(KinClassSpec.type("c", "net/minecraft/Listener")
                        .method("a", "(Ljava/lang/String;)Ljava/lang/String;", "accept"))
                // d is in the table but its static method e() is not, so it keeps its name.
                .add(KinClassSpec.type("d", "net/minecraft/Derived"));
        NotchMappingsBuilder builder = new NotchMappingsBuilder();
        new KinReader(builder).read(new ByteArrayInputStream(kin.toBytes()));

        List<ClassHeader> headers = new ArrayList<>();
        compiled.values().forEach(classFile -> headers.add(ClassHeader.read(classFile)));
        NotchRemapper remapper = new NotchRemapper(builder.build(), new ClassHierarchy(HeaderTable.of(headers)));

        NotchRemapperTest.remapped = ClassBytes.remapAll(compiled, remapper);
        NotchRemapperTest.loader = new InMemoryClassLoader(NotchRemapperTest.remapped);
    }

    private static Set<String> scan(String className) {
        return ClassScan.describe(NotchRemapperTest.remapped.get(className));
    }

    private static Object newInstance(String className) throws ReflectiveOperationException {
        return NotchRemapperTest.loader.loadClass(className).getConstructor().newInstance();
    }

    private static Object call(Object target, String declaringClass, String method) throws ReflectiveOperationException {
        return NotchRemapperTest.loader.loadClass(declaringClass).getMethod(method).invoke(target);
    }

    @Test
    void renamesClassesAndKeepsForeignOnes() {
        assertEquals(
                Set.of("net/minecraft/Screen", "net/minecraft/ChatScreen", "net/minecraft/Listener",
                        "net/minecraft/Derived", "ForgeConfig", "ForgeCalls", "ForgeShadow", "ForgeImpl"),
                NotchRemapperTest.remapped.keySet());
    }

    @Test
    void renamesDeclaredMembersOfMinecraftClasses() {
        Set<String> screen = NotchRemapperTest.scan("net/minecraft/Screen");

        assertTrue(screen.contains("field width I"), screen.toString());
        assertTrue(screen.contains("method draw ()V"), screen.toString());
        assertTrue(screen.contains("method baseValue ()I"), screen.toString());
        assertTrue(screen.contains("method <init> ()V"), screen.toString());
    }

    @Test
    void renamesReferenceWrittenThroughASubclassOwner() {
        Set<String> config = NotchRemapperTest.scan("ForgeConfig");

        // The compiler wrote "ForgeConfig.a", but the field is declared two classes up.
        assertTrue(config.contains("GETFIELD ForgeConfig.width I"), config.toString());
        assertTrue(config.contains("INVOKESPECIAL net/minecraft/ChatScreen.draw ()V"), config.toString());
    }

    @Test
    void overrideDeclaredOutsideMinecraftStillOverridesAfterRenaming() throws ReflectiveOperationException {
        Object config = NotchRemapperTest.newInstance("ForgeConfig");

        // Virtual call by the Minecraft name must reach ForgeConfig's override.
        NotchRemapperTest.call(config, "net.minecraft.Screen", "draw");

        assertEquals(111, NotchRemapperTest.call(config, "ForgeConfig", "read"));
    }

    @Test
    void fieldDeclaredOutsideMinecraftKeepsItsNameEvenIfAnAncestorHasTheSame() throws ReflectiveOperationException {
        Object shadow = NotchRemapperTest.newInstance("ForgeShadow");

        assertEquals(5, NotchRemapperTest.call(shadow, "ForgeShadow", "own"));
        assertEquals(1, NotchRemapperTest.call(shadow, "ForgeShadow", "inherited"));
        assertTrue(NotchRemapperTest.scan("ForgeShadow").contains("field a I"));
    }

    @Test
    void memberThatAMinecraftClassDeclaresWithoutRenamingStopsTheSearch() throws ReflectiveOperationException {
        // Without this stop, d.e() would take a.e()'s name (baseValue) and call the wrong method.
        assertEquals(2, NotchRemapperTest.call(null, "ForgeCalls", "viaDerived"));
        assertEquals(1, NotchRemapperTest.call(null, "ForgeCalls", "viaBase"));
        assertTrue(NotchRemapperTest.scan("ForgeCalls").contains("INVOKESTATIC net/minecraft/Derived.e ()I"));
        assertTrue(NotchRemapperTest.scan("ForgeCalls").contains("INVOKESTATIC net/minecraft/Screen.baseValue ()I"));
    }

    @Test
    void interfaceMethodImplementedOutsideMinecraftIsRenamed() throws ReflectiveOperationException {
        Object implementation = NotchRemapperTest.newInstance("ForgeImpl");

        Object answer = NotchRemapperTest.loader.loadClass("net.minecraft.Listener")
                .getMethod("accept", String.class).invoke(implementation, "x");

        assertEquals("impl:x", answer);
    }

    @Test
    void lambdaTakesTheNewNameOfTheInterfaceMethod() throws ReflectiveOperationException {
        Object config = NotchRemapperTest.newInstance("ForgeConfig");
        Object listener = NotchRemapperTest.call(config, "ForgeConfig", "listener");

        Object answer = NotchRemapperTest.loader.loadClass("net.minecraft.Listener")
                .getMethod("accept", String.class).invoke(listener, "x");

        assertEquals("x!", answer);
        assertTrue(NotchRemapperTest.scan("ForgeConfig").contains("INVOKEDYNAMIC accept ()Lnet/minecraft/Listener;"));
    }

    @Test
    void noObfuscatedOwnerRemains() {
        for (Map.Entry<String, byte[]> entry : NotchRemapperTest.remapped.entrySet()) {
            for (String line : ClassScan.describe(entry.getValue())) {
                assertFalse(line.matches(".* [a-d]\\..*"), entry.getKey() + ": " + line);
            }
        }
    }
}
