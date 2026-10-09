package dev.alloy.remap.testkit;

import dev.alloy.remap.hierarchy.ClassHeader;
import dev.alloy.remap.hierarchy.ClassHeaderSource;
import dev.alloy.remap.hierarchy.ClassHierarchy;
import dev.alloy.remap.hierarchy.LayeredHeaderSource;
import dev.alloy.remap.hierarchy.MemberKey;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Test helper: the classes Lunar really runs (a directory of {@code .class} files), plus the JDK's,
 * to check that a reference really exists. Nothing is loaded; headers are read from the bytes.
 */
public final class RuntimeClasses {

    private static final String CONSTRUCTOR = "<init>";

    private final Path root;
    private final ClassHierarchy hierarchy;
    private final Map<String, Optional<ClassHeader>> cache = new HashMap<>();

    /**
     * @param root         root directory of the game classes
     * @param extraClasses extra classes consulted first (those of the jar under test)
     */
    public RuntimeClasses(Path root, ClassHeaderSource extraClasses) {
        this.root = root;
        this.hierarchy = new ClassHierarchy(new LayeredHeaderSource(List.of(extraClasses, this::find)));
    }

    /** Tells whether the game contains this class (the JDK does not count). */
    public boolean containsGameClass(String internalName) {
        return Files.isRegularFile(this.root.resolve(internalName + ".class"));
    }

    /** Looks a class up in the game, then in the JDK. */
    public Optional<ClassHeader> find(String internalName) {
        return this.cache.computeIfAbsent(internalName, this::read);
    }

    private Optional<ClassHeader> read(String internalName) {
        Path file = this.root.resolve(internalName + ".class");
        try {
            if (Files.isRegularFile(file)) {
                return Optional.of(ClassHeader.read(Files.readAllBytes(file)));
            }
            try (InputStream input = ClassLoader.getSystemResourceAsStream(internalName + ".class")) {
                return input == null ? Optional.empty() : Optional.of(ClassHeader.read(input.readAllBytes()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Resolves a reference as the JVM would: the member must be declared by the named class or an ancestor. */
    public Resolution resolve(MemberRef reference) {
        if (reference.owner().startsWith("[")) {
            // Methods called on an array (clone, length...) come from java/lang/Object.
            return Resolution.FOUND;
        }
        MemberKey key = new MemberKey(reference.name(), reference.descriptor());
        Set<String> candidates = new LinkedHashSet<>(this.hierarchy.superclassChain(reference.owner()));
        if (reference.name().equals(RuntimeClasses.CONSTRUCTOR)) {
            candidates.retainAll(Set.of(reference.owner()));
        } else {
            candidates.addAll(this.hierarchy.allInterfaces(reference.owner()));
        }
        boolean unknownAncestor = false;
        for (String className : candidates) {
            Optional<ClassHeader> header = this.hierarchy.find(className);
            if (header.isEmpty()) {
                unknownAncestor = true;
            } else if (reference.field() ? header.get().declaresField(key) : header.get().declaresMethod(key)) {
                return Resolution.FOUND;
            }
        }
        return unknownAncestor ? Resolution.UNKNOWN_ANCESTOR : Resolution.MISSING;
    }

    /** Returns the known ancestors of a class (superclasses then interfaces), itself included. */
    public Set<String> ancestors(String internalName) {
        Set<String> ancestors = new LinkedHashSet<>(this.hierarchy.superclassChain(internalName));
        ancestors.addAll(this.hierarchy.allInterfaces(internalName));
        return ancestors;
    }

    /** Outcome of resolving a reference. */
    public enum Resolution {
        /** The member exists. */
        FOUND,
        /** The whole hierarchy is known and no class declares the member. */
        MISSING,
        /** An ancestor is unknown: no conclusion possible. */
        UNKNOWN_ANCESTOR
    }
}
