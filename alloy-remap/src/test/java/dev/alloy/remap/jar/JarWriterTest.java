package dev.alloy.remap.jar;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.alloy.remap.testkit.TestJars;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JarWriterTest {

    private static final byte[] MANIFEST = "Manifest-Version: 1.0\r\n\r\n".getBytes(StandardCharsets.UTF_8);

    private static Map<String, byte[]> sampleEntries() {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("z/Last.class", new byte[] {1, 2, 3});
        entries.put("assets/mod/lang/en_US.lang", "key=value\n".getBytes(StandardCharsets.UTF_8));
        entries.put("META-INF/MANIFEST.MF", JarWriterTest.MANIFEST);
        entries.put("assets/", new byte[0]);
        entries.put("META-INF/", new byte[0]);
        entries.put("a/First.class", new byte[] {4, 5});
        return entries;
    }

    @Test
    void writesManifestFirstThenEntriesInAlphabeticalOrder(@TempDir Path directory) throws IOException {
        Path jar = directory.resolve("out.jar");

        JarWriter.write(jar, JarWriterTest.sampleEntries());

        assertEquals(
                List.of("META-INF/", "META-INF/MANIFEST.MF", "a/First.class", "assets/", "assets/mod/lang/en_US.lang",
                        "z/Last.class"),
                new ArrayList<>(TestJars.read(jar).keySet()));
    }

    @Test
    void keepsEntryContentByteForByte(@TempDir Path directory) throws IOException {
        Path jar = directory.resolve("out.jar");
        Map<String, byte[]> entries = JarWriterTest.sampleEntries();

        JarWriter.write(jar, entries);

        Map<String, byte[]> written = TestJars.read(jar);
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            assertArrayEquals(entry.getValue(), written.get(entry.getKey()), entry.getKey());
        }
    }

    @Test
    void sameEntriesAlwaysGiveTheSameFile(@TempDir Path directory) throws IOException {
        Path first = directory.resolve("first.jar");
        Path second = directory.resolve("second.jar");
        Map<String, byte[]> reversed = new LinkedHashMap<>();
        List<String> names = new ArrayList<>(JarWriterTest.sampleEntries().keySet());
        for (int i = names.size() - 1; i >= 0; i--) {
            reversed.put(names.get(i), JarWriterTest.sampleEntries().get(names.get(i)));
        }

        JarWriter.write(first, JarWriterTest.sampleEntries());
        JarWriter.write(second, reversed);

        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
    }

    @Test
    void manifestIsFoundByToolsThatOnlyReadTheBeginningOfTheJar(@TempDir Path directory) throws IOException {
        Path jar = directory.resolve("out.jar");

        JarWriter.write(jar, JarWriterTest.sampleEntries());

        try (JarInputStream stream = new JarInputStream(Files.newInputStream(jar))) {
            assertNotNull(stream.getManifest());
        }
    }

    @Test
    void replacesAnExistingJar(@TempDir Path directory) throws IOException {
        Path jar = directory.resolve("out.jar");
        Files.writeString(jar, "not a jar");

        JarWriter.write(jar, Map.of("only.txt", new byte[] {7}));

        assertEquals(List.of("only.txt"), new ArrayList<>(TestJars.read(jar).keySet()));
    }
}
