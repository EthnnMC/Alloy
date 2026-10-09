package dev.alloy.remap.jar;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alloy.remap.testkit.InMemoryClassLoader;
import dev.alloy.remap.testkit.SourceCompiler;
import dev.alloy.remap.testkit.TestJars;
import dev.alloy.remap.transform.Verdict;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.commons.SimpleRemapper;

class WorkingJarTest {

    private static final byte[] TEXT = "hello".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SIGNED_MANIFEST = String.join("\r\n",
            "Manifest-Version: 1.0",
            "",
            "Name: demo/Old.class",
            "SHA-256-Digest: MdWIiX4OOfE2aefloA+1GaXaZKW52cSH/VI5Y1e2CuE=",
            "",
            "").getBytes(StandardCharsets.UTF_8);

    private static Map<String, byte[]> compiled;

    @BeforeAll
    static void compile() {
        WorkingJarTest.compiled = SourceCompiler.compile(8,
                """
                package demo;
                public class Old {
                    public static String greet() { return "hi"; }
                }
                """,
                """
                package demo;
                public class User {
                    public static String call() { return Old.greet(); }
                }
                """);
    }

    private static WorkingJar open(Path directory) throws IOException {
        Map<String, byte[]> entries = TestJars.asEntries(WorkingJarTest.compiled);
        entries.put("META-INF/MANIFEST.MF", WorkingJarTest.SIGNED_MANIFEST);
        entries.put("META-INF/TEST.SF", new byte[] {1});
        entries.put("META-INF/TEST.RSA", new byte[] {2});
        entries.put("assets/readme.txt", WorkingJarTest.TEXT);
        Path jar = TestJars.write(directory.resolve("input.jar"), entries);
        return SourceJar.read(jar, message -> { }).remap(new SimpleRemapper("demo/Old", "demo/New"), message -> { });
    }

    @Test
    void writtenClassesAreRenamedEverywhereAndStillRun(@TempDir Path directory) throws IOException,
            ReflectiveOperationException {
        Path output = directory.resolve("output.jar");

        WorkingJarTest.open(directory).writeTo(output);

        ClassLoader loader = new InMemoryClassLoader(TestJars.readClasses(output));
        assertEquals("hi", loader.loadClass("demo.User").getMethod("call").invoke(null));
        assertEquals("hi", loader.loadClass("demo.New").getMethod("greet").invoke(null));
    }

    @Test
    void resourcesAreKeptByteForByteAndTheSignatureIsDropped(@TempDir Path directory) throws IOException {
        Path output = directory.resolve("output.jar");

        WorkingJarTest.open(directory).writeTo(output);

        Map<String, byte[]> written = TestJars.read(output);
        assertEquals(Set.of("META-INF/MANIFEST.MF", "assets/readme.txt", "demo/New.class", "demo/User.class"),
                written.keySet());
        assertArrayEquals(WorkingJarTest.TEXT, written.get("assets/readme.txt"));
        assertFalse(new String(written.get("META-INF/MANIFEST.MF"), StandardCharsets.UTF_8).contains("Digest"));
    }

    @Test
    void removedClassesAreNotWritten(@TempDir Path directory) throws IOException {
        Path output = directory.resolve("output.jar");
        WorkingJar work = WorkingJarTest.open(directory);

        work.removeClasses(name -> name.equals("demo/New"));
        work.writeTo(output);

        assertEquals(Set.of("demo/User"), TestJars.readClasses(output).keySet());
    }

    @Test
    void classesDroppedByAPassAreNotWritten(@TempDir Path directory) throws IOException {
        Path output = directory.resolve("output.jar");
        WorkingJar work = WorkingJarTest.open(directory);

        work.apply(classNode -> classNode.name.equals("demo/User") ? Verdict.DROP : Verdict.KEEP);
        work.writeTo(output);

        assertEquals(Set.of("demo/New"), TestJars.readClasses(output).keySet());
    }

    @Test
    void headersDescribeTheRenamedClasses(@TempDir Path directory) throws IOException {
        WorkingJar work = WorkingJarTest.open(directory);

        assertTrue(work.headers().find("demo/New").isPresent());
        assertTrue(work.headers().find("demo/Old").isEmpty());
    }
}
