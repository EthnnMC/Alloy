package dev.alloy.remap.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alloy.remap.testkit.ClassBytes;
import dev.alloy.remap.testkit.InMemoryClassLoader;
import dev.alloy.remap.testkit.SourceCompiler;
import dev.alloy.remap.testkit.TestJars;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * A fake Minecraft full of private members is widened by the pass. Code touching those members
 * must compile against the result, then run.
 */
class AccessWideningPassTest {

    private static final String CLIENT_SOURCE = """
            package example.forge;
            import net.minecraft.world.Vault;
            public class Client extends Vault {
                public int read(Vault vault) { return vault.secret + vault.peek() + vault.count + Vault.LIMIT; }
                public int turns() { return new Vault.Lock().turns; }
                @Override public void seal() { }
                public net.minecraft.world.Opener opener() { return null; }
            }
            """;

    private static Map<String, byte[]> original;
    private static Map<String, byte[]> widened;

    @BeforeAll
    static void compileAndWiden() {
        AccessWideningPassTest.original = SourceCompiler.compile(8,
                """
                package net.minecraft.world;
                public final class Vault {
                    public static final int LIMIT = 5;
                    private int secret = 42;
                    protected final Object label = new Object();
                    int count;
                    public Vault() { }
                    private int peek() { return this.secret; }
                    protected final void seal() { }
                    static Object newLock() { return new Lock(); }
                    private static class Lock { int turns = 2; }
                }
                """,
                """
                package net.minecraft.world;
                interface Opener {
                    int KEY = 1;
                    void open();
                }
                """);
        AccessWideningPassTest.widened = ClassBytes.applyToAll(AccessWideningPassTest.original, new AccessWideningPass());
    }

    private static Path jarOf(Path directory, String name, Map<String, byte[]> classFiles) throws IOException {
        return TestJars.write(directory.resolve(name), TestJars.asEntries(classFiles));
    }

    @Test
    void codeUsingFormerlyHiddenMembersCompilesAndRunsAgainstWidenedClasses(@TempDir Path directory)
            throws IOException, ReflectiveOperationException {
        Path jar = AccessWideningPassTest.jarOf(directory, "widened.jar", AccessWideningPassTest.widened);

        Map<String, byte[]> client = SourceCompiler.compile(8, List.of(jar), AccessWideningPassTest.CLIENT_SOURCE);

        Map<String, byte[]> everything = new LinkedHashMap<>(AccessWideningPassTest.widened);
        everything.putAll(client);
        ClassLoader loader = new InMemoryClassLoader(everything);
        Object instance = loader.loadClass("example.forge.Client").getConstructor().newInstance();
        Class<?> vault = loader.loadClass("net.minecraft.world.Vault");
        assertEquals(89, instance.getClass().getMethod("read", vault).invoke(instance, instance));
        assertEquals(2, instance.getClass().getMethod("turns").invoke(instance));
    }

    @Test
    void sameCodeDoesNotCompileAgainstOriginalClasses(@TempDir Path directory) throws IOException {
        Path jar = AccessWideningPassTest.jarOf(directory, "original.jar", AccessWideningPassTest.original);

        assertThrows(AssertionError.class,
                () -> SourceCompiler.compile(8, List.of(jar), AccessWideningPassTest.CLIENT_SOURCE));
    }

    @Test
    void compileTimeConstantStaysFinalSoThatItsValueIsStillInlined() throws ReflectiveOperationException {
        Class<?> vault = new InMemoryClassLoader(AccessWideningPassTest.widened).loadClass("net.minecraft.world.Vault");

        assertTrue(Modifier.isFinal(vault.getField("LIMIT").getModifiers()));
        assertFalse(Modifier.isFinal(vault.getField("label").getModifiers()));
        assertFalse(Modifier.isFinal(vault.getModifiers()));
    }

    @Test
    void interfaceMembersKeepTheFlagsImposedByTheJvm() throws ReflectiveOperationException {
        Class<?> opener = new InMemoryClassLoader(AccessWideningPassTest.widened).loadClass("net.minecraft.world.Opener");

        int key = opener.getField("KEY").getModifiers();
        assertTrue(Modifier.isPublic(opener.getModifiers()));
        assertTrue(Modifier.isFinal(key) && Modifier.isStatic(key) && Modifier.isPublic(key));
    }

    @Test
    void syntheticMembersAreLeftAlone() {
        ClassNode before = ClassBytes.read(AccessWideningPassTest.original.get("net/minecraft/world/Vault$Lock"));
        ClassNode after = ClassBytes.read(AccessWideningPassTest.widened.get("net/minecraft/world/Vault$Lock"));

        int syntheticCount = 0;
        for (int i = 0; i < before.methods.size(); i++) {
            MethodNode method = before.methods.get(i);
            if ((method.access & Opcodes.ACC_SYNTHETIC) != 0) {
                syntheticCount++;
                assertEquals(method.access, after.methods.get(i).access, method.name + method.desc);
            }
        }
        // javac adds a synthetic constructor so Vault can create its private inner class.
        assertEquals(1, syntheticCount);
    }
}
