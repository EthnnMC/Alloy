package dev.alloy.remap.hierarchy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.commons.Remapper;

/**
 * Immutable table of class headers (typically all classes of a jar).
 */
public final class HeaderTable implements ClassHeaderSource {

    private static final String CLASS_SUFFIX = ".class";

    private static final HeaderTable EMPTY = new HeaderTable(Map.of());

    private final Map<String, ClassHeader> headers;

    private HeaderTable(Map<String, ClassHeader> headers) {
        this.headers = Map.copyOf(headers);
    }

    /**
     * Returns the empty table.
     */
    public static HeaderTable empty() {
        return HeaderTable.EMPTY;
    }

    /**
     * Builds a table from headers; if two headers share a name, the first is kept.
     */
    public static HeaderTable of(Collection<ClassHeader> headers) {
        Map<String, ClassHeader> byName = new HashMap<>();
        for (ClassHeader header : headers) {
            byName.putIfAbsent(header.name(), header);
        }
        return new HeaderTable(byName);
    }

    /**
     * Reads the headers of all classes in a jar, ignoring its other files.
     *
     * @throws IOException if the jar is unreadable or one of its classes is corrupt
     */
    public static HeaderTable ofJar(Path jar) throws IOException {
        List<ClassHeader> headers = new ArrayList<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().endsWith(HeaderTable.CLASS_SUFFIX)) {
                    headers.add(HeaderTable.readHeader(zip, entry, jar));
                }
            }
        }
        return HeaderTable.of(headers);
    }

    private static ClassHeader readHeader(ZipFile zip, ZipEntry entry, Path jar) throws IOException {
        try (InputStream input = zip.getInputStream(entry)) {
            return ClassHeader.read(input.readAllBytes());
        } catch (RuntimeException e) {
            throw new IOException("Unreadable class file '" + entry.getName() + "' in " + jar, e);
        }
    }

    /**
     * Translates the whole table into another namespace.
     *
     * @return a new table, keyed by translated names
     */
    public HeaderTable mapped(Remapper remapper) {
        List<ClassHeader> mappedHeaders = new ArrayList<>(this.headers.size());
        for (ClassHeader header : this.headers.values()) {
            mappedHeaders.add(header.mapped(remapper));
        }
        return HeaderTable.of(mappedHeaders);
    }

    @Override
    public Optional<ClassHeader> find(String internalName) {
        return Optional.ofNullable(this.headers.get(internalName));
    }

    /**
     * Returns all headers, as an unmodifiable view in no particular order.
     */
    public Collection<ClassHeader> headers() {
        return this.headers.values();
    }

    /**
     * Returns the number of known classes.
     */
    public int size() {
        return this.headers.size();
    }
}
