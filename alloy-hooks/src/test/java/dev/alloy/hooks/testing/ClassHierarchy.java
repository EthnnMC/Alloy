package dev.alloy.hooks.testing;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;

/**
 * Class hierarchy rebuilt from class files <b>without loading any class</b>: only the header of
 * each file is read (superclass, interfaces, whether it is an interface).
 *
 * <p>Game classes are untrusted data, so their hierarchy is read but they are never run. Sources
 * are queried in the order added; a class that cannot be found is recorded in
 * {@link #unresolvedNames()} and treated leniently by {@link #isAssignable(String, String)}.</p>
 */
public final class ClassHierarchy {

    private static final String OBJECT = "java/lang/Object";

    /** Bytecode sources: internal name to bytes, or {@code null} if the source does not know the class. */
    private final List<Function<String, byte[]>> sources = new ArrayList<>();

    /** Headers already read; empty for a class that cannot be found. */
    private final Map<String, Optional<ClassHeader>> headers = new HashMap<>();

    private final Set<String> unresolved = new TreeSet<>();

    /** Adds a class known by its bytes (e.g. the class being verified). */
    public ClassHierarchy withClass(byte[] classBytes) {
        String name = new ClassReader(classBytes).getClassName();
        this.sources.add(requested -> requested.equals(name) ? classBytes : null);
        return this;
    }

    /** Adds a directory of class files laid out by package. */
    public ClassHierarchy withDirectory(Path directory) {
        this.sources.add(name -> ClassHierarchy.readFile(directory.resolve(name + ".class")));
        return this;
    }

    /** Adds a jar opened by the caller, who stays responsible for closing it. */
    public ClassHierarchy withJar(ZipFile jar) {
        this.sources.add(name -> ClassHierarchy.readEntry(jar, name + ".class"));
        return this;
    }

    /**
     * Adds the system loader's resources (the JDK and the test classpath); reading
     * {@code java/lang/String.class} as a resource loads nothing.
     */
    public ClassHierarchy withSystemResources() {
        this.sources.add(name -> ClassHierarchy.readStream(ClassLoader.getSystemResourceAsStream(name + ".class")));
        return this;
    }

    /** Tells whether a class is an interface; {@code false} for a class that cannot be found. */
    public boolean isInterface(String internalName) {
        return this.header(internalName).map(ClassHeader::isInterface).orElse(false);
    }

    /** Returns the superclass: empty for {@code Object}, {@code Object} for a class that cannot be found. */
    public Optional<String> superNameOf(String internalName) {
        if (ClassHierarchy.OBJECT.equals(internalName)) {
            return Optional.empty();
        }
        return Optional.of(this.header(internalName).map(ClassHeader::superName).orElse(ClassHierarchy.OBJECT));
    }

    /**
     * Tells whether a {@code source} value fits a {@code target} slot by the JVM verifier's rules:
     * an interface accepts any object, a class only its subclasses.
     *
     * <p>The answer is "yes" if {@code target} cannot be found or if the superclass chain of
     * {@code source} goes through a class that cannot be found: the opposite cannot be proven.</p>
     */
    public boolean isAssignable(String target, String source) {
        if (target.equals(source) || ClassHierarchy.OBJECT.equals(target)) {
            return true;
        }
        Optional<ClassHeader> targetHeader = this.header(target);
        if (targetHeader.isEmpty() || targetHeader.get().isInterface()) {
            return true;
        }
        String current = source;
        while (!ClassHierarchy.OBJECT.equals(current)) {
            Optional<ClassHeader> header = this.header(current);
            if (header.isEmpty()) {
                return true;
            }
            current = header.get().superName();
            if (target.equals(current)) {
                return true;
            }
        }
        return false;
    }

    /** Returns the sorted internal names of the requested classes that no source knows. */
    public Set<String> unresolvedNames() {
        return Set.copyOf(this.unresolved);
    }

    private Optional<ClassHeader> header(String internalName) {
        return this.headers.computeIfAbsent(internalName, this::readHeader);
    }

    private Optional<ClassHeader> readHeader(String internalName) {
        for (Function<String, byte[]> source : this.sources) {
            byte[] classBytes = source.apply(internalName);
            if (classBytes != null) {
                ClassReader reader = new ClassReader(classBytes);
                String superName = reader.getSuperName() == null ? ClassHierarchy.OBJECT : reader.getSuperName();
                boolean isInterface = (reader.getAccess() & Opcodes.ACC_INTERFACE) != 0;
                return Optional.of(new ClassHeader(superName, isInterface));
            }
        }
        this.unresolved.add(internalName);
        return Optional.empty();
    }

    private static byte[] readFile(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] readEntry(ZipFile jar, String entryName) {
        ZipEntry entry = jar.getEntry(entryName);
        if (entry == null) {
            return null;
        }
        try {
            return ClassHierarchy.readStream(jar.getInputStream(entry));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] readStream(InputStream stream) {
        if (stream == null) {
            return null;
        }
        try (InputStream in = stream) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** What is kept from the header of a class file. */
    private record ClassHeader(String superName, boolean isInterface) {
    }
}
