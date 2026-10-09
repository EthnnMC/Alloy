package dev.alloy.remap.hierarchy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Answers inheritance questions from class headers, without loading any class. A class with an
 * unknown header (JDK, library) ends the walk: its own ancestors are not explored.
 */
public final class ClassHierarchy {

    private final ClassHeaderSource headers;

    public ClassHierarchy(ClassHeaderSource headers) {
        this.headers = Objects.requireNonNull(headers, "headers");
    }

    /**
     * Looks up a class header; empty if the class is unknown.
     */
    public Optional<ClassHeader> find(String internalName) {
        return this.headers.find(internalName);
    }

    /**
     * Returns the super-class chain: the class itself, then its super-class, and so on, up to the
     * first unknown class (included) or {@code java/lang/Object}.
     *
     * @return the chain, never empty, without duplicates
     */
    public List<String> superclassChain(String internalName) {
        // LinkedHashSet keeps order and stops the loop if corrupt classes form a cycle.
        Set<String> chain = new LinkedHashSet<>();
        String current = internalName;
        while (current != null && chain.add(current)) {
            current = this.headers.find(current).map(ClassHeader::superName).orElse(null);
        }
        return new ArrayList<>(chain);
    }

    /**
     * Returns all interfaces the class inherits: its own, its super-classes', and their parents.
     *
     * @return the interfaces, nearest first; the starting class is not included
     */
    public Set<String> allInterfaces(String internalName) {
        Deque<String> pending = new ArrayDeque<>();
        for (String className : this.superclassChain(internalName)) {
            pending.addAll(this.directInterfaces(className));
        }
        Set<String> interfaces = new LinkedHashSet<>();
        while (!pending.isEmpty()) {
            String candidate = pending.removeFirst();
            if (interfaces.add(candidate)) {
                pending.addAll(this.directInterfaces(candidate));
            }
        }
        return interfaces;
    }

    /**
     * Tells whether a class is {@code ancestor} or inherits from it (via super-class or interface).
     */
    public boolean isSameOrSubclassOf(String internalName, String ancestor) {
        return this.superclassChain(internalName).contains(ancestor)
                || this.allInterfaces(internalName).contains(ancestor);
    }

    private List<String> directInterfaces(String internalName) {
        return this.headers.find(internalName).map(ClassHeader::interfaces).orElse(List.of());
    }
}
