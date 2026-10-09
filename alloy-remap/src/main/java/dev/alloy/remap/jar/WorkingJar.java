package dev.alloy.remap.jar;

import dev.alloy.remap.hierarchy.ClassHeader;
import dev.alloy.remap.hierarchy.HeaderTable;
import dev.alloy.remap.transform.ClassPass;
import dev.alloy.remap.transform.Verdict;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.jar.JarFile;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

/**
 * A jar being rewritten: its classes as modifiable ASM trees already translated to game names, and
 * its untouched resources. {@link ClassPass}es are applied to it, classes may be removed, then it is
 * written to disk. Mutable, single-threaded.
 */
public final class WorkingJar {

    /** Internal class name to class tree, in alphabetical order. */
    private final Map<String, ClassNode> classes;

    /** Entry name to bytes, for everything that is not a class. */
    private final Map<String, byte[]> resources;

    /**
     * Reserved to {@link SourceJar#remap}, which hands over its two tables.
     */
    WorkingJar(Map<String, ClassNode> classes, Map<String, byte[]> resources) {
        this.classes = classes;
        this.resources = resources;
    }

    /**
     * Computes the class headers in their current state.
     *
     * @return a table independent of this jar (later changes do not show in it)
     */
    public HeaderTable headers() {
        List<ClassHeader> headers = new ArrayList<>(this.classes.size());
        for (ClassNode classNode : this.classes.values()) {
            headers.add(ClassHeader.of(classNode));
        }
        return HeaderTable.of(headers);
    }

    /**
     * Returns the names of the remaining classes, as an unmodifiable alphabetical view.
     */
    public Set<String> classNames() {
        return Collections.unmodifiableSet(this.classes.keySet());
    }

    /**
     * Removes classes by name.
     *
     * @param unwanted true for the internal names of the classes to remove
     */
    public void removeClasses(Predicate<String> unwanted) {
        this.classes.keySet().removeIf(unwanted);
    }

    /**
     * Returns the non-class entries as an unmodifiable view: entry name to bytes.
     */
    public Map<String, byte[]> resources() {
        return Collections.unmodifiableMap(this.resources);
    }

    /**
     * Adds a resource to the jar, replacing an entry of the same name.
     */
    public void putResource(String entryName, byte[] content) {
        this.resources.put(entryName, content);
    }

    /**
     * Applies a pass to each class, in alphabetical name order, removing those the pass drops.
     */
    public void apply(ClassPass pass) {
        Iterator<ClassNode> remaining = this.classes.values().iterator();
        while (remaining.hasNext()) {
            if (pass.apply(remaining.next()) == Verdict.DROP) {
                remaining.remove();
            }
        }
    }

    /**
     * Writes the jar: resources byte for byte (except the signature, removed because it no longer
     * matches the rewritten classes), then the classes.
     *
     * @param output file to create or replace
     * @throws IOException if writing fails; {@code output} is then unchanged
     */
    public void writeTo(Path output) throws IOException {
        Map<String, byte[]> entries = new TreeMap<>();
        for (Map.Entry<String, byte[]> resource : this.resources.entrySet()) {
            String name = resource.getKey();
            if (JarSanitizer.isSignatureFile(name)) {
                continue;
            }
            boolean manifest = name.equals(JarFile.MANIFEST_NAME);
            entries.put(name, manifest ? JarSanitizer.cleanManifest(resource.getValue()) : resource.getValue());
        }
        for (Map.Entry<String, ClassNode> entry : this.classes.entrySet()) {
            entries.put(entry.getKey() + SourceJar.CLASS_SUFFIX, WorkingJar.toBytes(entry.getValue()));
        }
        JarWriter.write(output, entries);
    }

    /**
     * Turns a tree into a {@code .class} file. ASM is asked to recompute nothing (option 0): stack
     * sizes and frames are those read from the original class or set by the passes, which
     * guarantees no class is loaded while writing.
     */
    private static byte[] toBytes(ClassNode classNode) throws IOException {
        try {
            ClassWriter writer = new ClassWriter(0);
            classNode.accept(writer);
            return writer.toByteArray();
        } catch (RuntimeException e) {
            // ASM rejects a class it cannot write (method too large, inconsistent attribute) with
            // unchecked exceptions: name the offending class.
            throw new IOException("Cannot write class " + classNode.name + ": " + e, e);
        }
    }
}
