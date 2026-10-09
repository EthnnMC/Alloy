package dev.alloy.remap.mapping;

import dev.alloy.remap.hierarchy.ClassHeader;
import dev.alloy.remap.hierarchy.ClassHierarchy;
import dev.alloy.remap.hierarchy.MemberKey;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Remapper;

/**
 * Remapper for obfuscated code (Minecraft jar, Forge universal jar): notch names to game names,
 * classes <b>and</b> members.
 *
 * <p>The table lists a member only on its declaring class, but bytecode names the class the
 * programmer saw (e.g. a call on a subclass to an inherited method). So each name is looked up in
 * the ancestor that declares it, following the JVM's own field and method resolution order (JVMS
 * 5.4.3.2 and 5.4.3.3). Inheritance comes from class headers ({@link ClassHierarchy}), never from
 * loading classes.</p>
 */
public final class NotchRemapper extends Remapper {

    /** First character of {@code <init>} and {@code <clinit>}, which are never renamed. */
    private static final char SPECIAL_NAME_PREFIX = '<';

    private final NotchMappings mappings;
    private final ClassHierarchy hierarchy;

    /**
     * @param mappings  the obfuscated names table
     * @param hierarchy inheritance of the <b>obfuscated</b> classes: Minecraft's and those of the
     *                  jar being translated
     */
    public NotchRemapper(NotchMappings mappings, ClassHierarchy hierarchy) {
        this.mappings = Objects.requireNonNull(mappings, "mappings");
        this.hierarchy = Objects.requireNonNull(hierarchy, "hierarchy");
    }

    @Override
    public String map(String internalName) {
        return this.mappings.className(internalName);
    }

    @Override
    public String mapFieldName(String owner, String name, String descriptor) {
        return this.findField(owner, new MemberKey(name, descriptor), new HashSet<>()).orElse(name);
    }

    @Override
    public String mapMethodName(String owner, String name, String descriptor) {
        if (name.charAt(0) == NotchRemapper.SPECIAL_NAME_PREFIX) {
            return name;
        }
        MemberKey method = new MemberKey(name, descriptor);
        // JVM order for a method: the class and its super-classes first, then interfaces.
        List<String> candidates = new ArrayList<>(this.hierarchy.superclassChain(owner));
        candidates.addAll(this.hierarchy.allInterfaces(owner));
        for (String className : candidates) {
            Optional<String> found = this.methodDeclaredIn(className, method);
            if (found.isPresent()) {
                return found.get();
            }
        }
        return name;
    }

    /**
     * For a lambda, the {@code invokedynamic} name is the single abstract method of the produced
     * interface (the descriptor's return type). The instruction does not give that method's
     * descriptor, so we translate only if the interface has exactly one possible name.
     */
    @Override
    public String mapInvokeDynamicMethodName(String name, String descriptor) {
        Type produced = Type.getReturnType(descriptor);
        if (produced.getSort() != Type.OBJECT) {
            return name;
        }
        String interfaceName = produced.getInternalName();
        Set<String> names = new HashSet<>(this.mappings.methodNamesIgnoringDescriptor(interfaceName, name));
        for (String parent : this.hierarchy.allInterfaces(interfaceName)) {
            names.addAll(this.mappings.methodNamesIgnoringDescriptor(parent, name));
        }
        return names.size() == 1 ? names.iterator().next() : name;
    }

    /**
     * Resolves a field like the JVM: the class, then its interfaces, then its super-class.
     *
     * @param visited classes already examined (avoids looping on corrupt classes)
     * @return the field name found (translated, or unchanged if not renamed); empty if no known
     *         class declares it
     */
    private Optional<String> findField(String className, MemberKey field, Set<String> visited) {
        if (!visited.add(className)) {
            return Optional.empty();
        }
        Optional<String> renamed = this.mappings.fieldName(className, field);
        if (renamed.isPresent()) {
            return renamed;
        }
        Optional<ClassHeader> header = this.hierarchy.find(className);
        if (header.isEmpty()) {
            return Optional.empty();
        }
        if (header.get().declaresField(field)) {
            // Fields are not overridden: the one the class declares is the one reached.
            return Optional.of(field.name());
        }
        for (String parentInterface : header.get().interfaces()) {
            Optional<String> inherited = this.findField(parentInterface, field, visited);
            if (inherited.isPresent()) {
                return inherited;
            }
        }
        String superName = header.get().superName();
        return superName == null ? Optional.empty() : this.findField(superName, field, visited);
    }

    /**
     * Checks whether a class gives a method its final name. A Minecraft class declaring the method
     * without renaming it ends the search (the name is already right). A class unknown to the table
     * (a Forge class) does not, even if it declares the method: it may override a Minecraft method
     * and must then share its name, or the override would be lost.
     */
    private Optional<String> methodDeclaredIn(String className, MemberKey method) {
        Optional<String> renamed = this.mappings.methodName(className, method);
        if (renamed.isPresent()) {
            return renamed;
        }
        boolean keptAsIs = this.mappings.knowsClass(className)
                && this.hierarchy.find(className).map(header -> header.declaresMethod(method)).orElse(false);
        return keptAsIs ? Optional.of(method.name()) : Optional.empty();
    }
}
