package dev.alloy.remap.testkit;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Test helper: writes and reads jars without going through the code under test.
 */
public final class TestJars {

    private static final String CLASS_SUFFIX = ".class";

    private TestJars() {
        // Utility class.
    }

    /**
     * Writes a jar with the entries in exactly the given order.
     *
     * @param entries entry name to bytes (a name ending in {@code /} creates a directory)
     * @return {@code jar}
     */
    public static Path write(Path jar, Map<String, byte[]> entries) throws IOException {
        try (OutputStream file = Files.newOutputStream(jar); ZipOutputStream zip = new ZipOutputStream(file)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                if (!entry.getKey().endsWith("/")) {
                    zip.write(entry.getValue());
                }
                zip.closeEntry();
            }
        }
        return jar;
    }

    /**
     * Maps compiled classes to their usual entry names.
     *
     * @param classFiles internal class name to bytes
     * @return entry name ({@code a/b/C.class}) to bytes, mutable, in the same order
     */
    public static Map<String, byte[]> asEntries(Map<String, byte[]> classFiles) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        classFiles.forEach((name, bytes) -> entries.put(name + TestJars.CLASS_SUFFIX, bytes));
        return entries;
    }

    /** Reads all entries of a jar (entry name to bytes, in jar order). */
    public static Map<String, byte[]> read(Path jar) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> all = zip.entries();
            while (all.hasMoreElements()) {
                ZipEntry entry = all.nextElement();
                try (InputStream input = zip.getInputStream(entry)) {
                    entries.put(entry.getName(), input.readAllBytes());
                }
            }
        }
        return entries;
    }

    /** Reads the classes of a jar (internal class name to bytes). */
    public static Map<String, byte[]> readClasses(Path jar) throws IOException {
        Map<String, byte[]> classFiles = new LinkedHashMap<>();
        TestJars.read(jar).forEach((name, bytes) -> {
            if (name.endsWith(TestJars.CLASS_SUFFIX)) {
                classFiles.put(name.substring(0, name.length() - TestJars.CLASS_SUFFIX.length()), bytes);
            }
        });
        return classFiles;
    }
}
