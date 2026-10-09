package dev.alloy.remap.jar;

import dev.alloy.remap.hierarchy.ClassHeader;
import dev.alloy.remap.hierarchy.HeaderTable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;

/**
 * An immutable jar read into memory, split into its classes and everything else ("resources").
 * A <b>class</b> is an entry {@code a/b/C.class} whose content really declares class {@code a/b/C};
 * an unreadable or misplaced {@code .class} entry is reported and kept as a resource. The original
 * jar is never modified; further work happens on the {@link WorkingJar} returned by {@link #remap}.
 */
public final class SourceJar {

    static final String CLASS_SUFFIX = ".class";

    private static final byte[] NO_CONTENT = new byte[0];

    /** Internal class name to {@code .class} bytes, in alphabetical order. */
    private final Map<String, byte[]> classFiles;

    /** Entry name to bytes, for all other entries (directories included). */
    private final Map<String, byte[]> resources;

    private final HeaderTable headers;

    private SourceJar(Map<String, byte[]> classFiles, Map<String, byte[]> resources, HeaderTable headers) {
        this.classFiles = Collections.unmodifiableMap(classFiles);
        this.resources = Collections.unmodifiableMap(resources);
        this.headers = headers;
    }

    /**
     * Reads a whole jar.
     *
     * @param warnings receives the anomalies found (duplicate entry, unreadable class...)
     * @throws IOException if the file is not a readable jar
     */
    public static SourceJar read(Path jar, Consumer<String> warnings) throws IOException {
        Map<String, byte[]> classFiles = new TreeMap<>();
        Map<String, byte[]> resources = new TreeMap<>();
        List<ClassHeader> headers = new ArrayList<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                byte[] content = entry.isDirectory() ? SourceJar.NO_CONTENT : SourceJar.readEntry(zip, entry);
                Optional<ClassHeader> header = SourceJar.classHeader(name, content, warnings);
                boolean duplicate;
                if (header.isPresent()) {
                    duplicate = classFiles.putIfAbsent(header.get().name(), content) != null;
                    headers.add(header.get());
                } else {
                    duplicate = resources.putIfAbsent(name, content) != null;
                }
                if (duplicate) {
                    warnings.accept("Duplicate jar entry '" + name + "': only the first one is kept");
                }
            }
        }
        return new SourceJar(classFiles, resources, HeaderTable.of(headers));
    }

    private static byte[] readEntry(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream input = zip.getInputStream(entry)) {
            return input.readAllBytes();
        }
    }

    /**
     * Reads an entry's header if it is a class stored at its proper path.
     *
     * @return the header, or empty if the entry must be treated as a resource
     */
    private static Optional<ClassHeader> classHeader(String entryName, byte[] content, Consumer<String> warnings) {
        if (!entryName.endsWith(SourceJar.CLASS_SUFFIX)) {
            return Optional.empty();
        }
        try {
            ClassHeader header = ClassHeader.read(content);
            if (entryName.equals(header.name() + SourceJar.CLASS_SUFFIX)) {
                return Optional.of(header);
            }
            warnings.accept("Entry '" + entryName + "' holds the class " + header.name()
                    + ", which does not match its path: kept as a plain resource");
        } catch (RuntimeException e) {
            // ASM reports an invalid class file with various unchecked exceptions.
            warnings.accept("Entry '" + entryName + "' is not a readable class file (" + e
                    + "): kept as a plain resource");
        }
        return Optional.empty();
    }

    /**
     * Returns the headers of the jar's classes, under their original names.
     */
    public HeaderTable headers() {
        return this.headers;
    }

    /**
     * Returns the jar's class files as an unmodifiable view: internal class name to bytes.
     */
    public Map<String, byte[]> classFiles() {
        return this.classFiles;
    }

    /**
     * Returns the jar's non-class entries as an unmodifiable view: entry name to bytes.
     */
    public Map<String, byte[]> resources() {
        return this.resources;
    }

    /**
     * Translates all classes into another namespace and returns the {@link WorkingJar} holding them
     * as modifiable ASM trees. ASM's {@code ClassRemapper} applies {@code remapper} wherever a name
     * appears (declarations, instructions, descriptors, signatures, annotations, inner class table,
     * debug info).
     *
     * @param warnings receives the anomalies found
     * @return the working jar; this source jar is unchanged
     */
    public WorkingJar remap(Remapper remapper, Consumer<String> warnings) {
        Map<String, ClassNode> classes = new TreeMap<>();
        Map<String, byte[]> otherEntries = new TreeMap<>(this.resources);
        for (Map.Entry<String, byte[]> classFile : this.classFiles.entrySet()) {
            String originalName = classFile.getKey();
            try {
                ClassNode classNode = new ClassNode();
                new ClassReader(classFile.getValue()).accept(new ClassRemapper(classNode, remapper), 0);
                if (classes.putIfAbsent(classNode.name, classNode) != null) {
                    warnings.accept("Class " + originalName + " is renamed to " + classNode.name
                            + ", which already exists in the jar: it is left out");
                }
            } catch (RuntimeException e) {
                // The header was readable but not the body: keep the original, untranslated file.
                warnings.accept("Class " + originalName + " cannot be parsed (" + e + "): kept unchanged");
                otherEntries.put(originalName + SourceJar.CLASS_SUFFIX, classFile.getValue());
            }
        }
        return new WorkingJar(classes, otherEntries);
    }
}
