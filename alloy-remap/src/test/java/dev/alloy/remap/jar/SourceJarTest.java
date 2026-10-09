package dev.alloy.remap.jar;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alloy.remap.testkit.SourceCompiler;
import dev.alloy.remap.testkit.TestJars;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.commons.SimpleRemapper;

class SourceJarTest {

    private static final byte[] TEXT = "hello".getBytes(StandardCharsets.UTF_8);
    private static final byte[] GARBAGE = {1, 2, 3, 4};

    private static Map<String, byte[]> compiled;

    @BeforeAll
    static void compile() {
        SourceJarTest.compiled = SourceCompiler.compile(8,
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

    private static Path sampleJar(Path directory) throws IOException {
        Map<String, byte[]> entries = TestJars.asEntries(SourceJarTest.compiled);
        entries.put("demo/", new byte[0]);
        entries.put("assets/readme.txt", SourceJarTest.TEXT);
        entries.put("broken/NotAClass.class", SourceJarTest.GARBAGE);
        entries.put("META-INF/versions/9/demo/Old.class", SourceJarTest.compiled.get("demo/Old"));
        return TestJars.write(directory.resolve("sample.jar"), entries);
    }

    @Test
    void separatesClassesFromResources(@TempDir Path directory) throws IOException {
        SourceJar jar = SourceJar.read(SourceJarTest.sampleJar(directory), message -> { });

        assertEquals(Set.of("demo/Old", "demo/User"), jar.classFiles().keySet());
        assertEquals(
                Set.of("demo/", "assets/readme.txt", "broken/NotAClass.class", "META-INF/versions/9/demo/Old.class"),
                jar.resources().keySet());
        assertArrayEquals(SourceJarTest.TEXT, jar.resources().get("assets/readme.txt"));
    }

    @Test
    void reportsClassFilesItCannotTreatAsClasses(@TempDir Path directory) throws IOException {
        List<String> warnings = new ArrayList<>();

        SourceJar.read(SourceJarTest.sampleJar(directory), warnings::add);

        assertEquals(2, warnings.size(), warnings.toString());
        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("broken/NotAClass.class")));
        assertTrue(warnings.stream().anyMatch(warning -> warning.contains("META-INF/versions/9/demo/Old.class")));
    }

    @Test
    void exposesHeadersOfItsClasses(@TempDir Path directory) throws IOException {
        SourceJar jar = SourceJar.read(SourceJarTest.sampleJar(directory), message -> { });

        assertEquals(2, jar.headers().size());
        assertEquals("java/lang/Object", jar.headers().find("demo/User").orElseThrow().superName());
    }

    @Test
    void remapRenamesClassesAndKeepsResources(@TempDir Path directory) throws IOException {
        SourceJar jar = SourceJar.read(SourceJarTest.sampleJar(directory), message -> { });

        WorkingJar work = jar.remap(new SimpleRemapper("demo/Old", "demo/New"), message -> { });

        assertEquals(Set.of("demo/New", "demo/User"), work.classNames());
        // The source jar itself is unchanged.
        assertEquals(Set.of("demo/Old", "demo/User"), jar.classFiles().keySet());
    }

    @Test
    void reportsClassesThatCollideAfterRenaming(@TempDir Path directory) throws IOException {
        SourceJar jar = SourceJar.read(SourceJarTest.sampleJar(directory), message -> { });
        List<String> warnings = new ArrayList<>();

        WorkingJar work = jar.remap(new SimpleRemapper("demo/Old", "demo/User"), warnings::add);

        assertEquals(Set.of("demo/User"), work.classNames());
        assertEquals(1, warnings.size(), warnings.toString());
    }
}
