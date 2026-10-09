package dev.alloy.remap.jar;

import dev.alloy.remap.io.AtomicFiles;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a jar <b>reproducibly</b>: the same entries always give the same bytes (fixed entry order
 * and one date for all). The write is atomic ({@link AtomicFiles}).
 */
public final class JarWriter {

    private static final String META_INF_DIRECTORY = "META-INF/";

    /** Date for all entries: ZIP requires one, and a constant keeps the output day-independent. */
    private static final LocalDateTime ENTRY_TIME = LocalDateTime.of(2000, 1, 1, 0, 0);

    /**
     * Entry order: {@code META-INF/} then the manifest first (some tools only find the manifest if
     * it opens the jar), the rest alphabetical.
     */
    private static final Comparator<String> ENTRY_ORDER =
            Comparator.comparingInt(JarWriter::rank).thenComparing(Comparator.naturalOrder());

    private JarWriter() {
        // Utility class: no instances.
    }

    /**
     * Writes a jar.
     *
     * @param output  file to create or replace
     * @param entries jar content: entry name to bytes; a name ending in {@code /} is a directory
     *                (its bytes are ignored)
     * @throws IOException if writing fails; {@code output} is then unchanged
     */
    public static void write(Path output, Map<String, byte[]> entries) throws IOException {
        List<String> names = new ArrayList<>(entries.keySet());
        names.sort(JarWriter.ENTRY_ORDER);
        AtomicFiles.replace(output, temporary -> {
            try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary)))) {
                for (String name : names) {
                    ZipEntry entry = new ZipEntry(name);
                    entry.setTimeLocal(JarWriter.ENTRY_TIME);
                    zip.putNextEntry(entry);
                    if (!entry.isDirectory()) {
                        zip.write(entries.get(name));
                    }
                    zip.closeEntry();
                }
            }
        });
    }

    private static int rank(String entryName) {
        if (entryName.equals(JarWriter.META_INF_DIRECTORY)) {
            return 0;
        }
        if (entryName.equals(JarFile.MANIFEST_NAME)) {
            return 1;
        }
        return 2;
    }
}
