package dev.alloy.remap.mapping;

import dev.alloy.remap.hierarchy.MemberKey;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable "obfuscated (notch) names to game names" table, per class, built by
 * {@link NotchMappingsBuilder}. A notch name means nothing without its class, hence one table per
 * class. Only members <b>declared</b> by each class are known; inherited ones are resolved by
 * {@link NotchRemapper}.
 */
public final class NotchMappings {

    private static final char INNER_SEPARATOR = '$';

    private final Map<String, String> classNames;

    /** Per obfuscated class name: declared field to game name. */
    private final Map<String, Map<MemberKey, String>> fieldNames;

    /** Per obfuscated class name: declared method to game name. */
    private final Map<String, Map<MemberKey, String>> methodNames;

    /**
     * Reserved to {@link NotchMappingsBuilder}, which does not reuse its tables afterwards.
     */
    NotchMappings(
            Map<String, String> classNames,
            Map<String, Map<MemberKey, String>> fieldNames,
            Map<String, Map<MemberKey, String>> methodNames) {
        this.classNames = Map.copyOf(classNames);
        this.fieldNames = NotchMappings.frozen(fieldNames);
        this.methodNames = NotchMappings.frozen(methodNames);
    }

    private static Map<String, Map<MemberKey, String>> frozen(Map<String, Map<MemberKey, String>> membersByClass) {
        Map<String, Map<MemberKey, String>> copy = new HashMap<>();
        for (Map.Entry<String, Map<MemberKey, String>> entry : membersByClass.entrySet()) {
            copy.put(entry.getKey(), Map.copyOf(entry.getValue()));
        }
        return Map.copyOf(copy);
    }

    /**
     * Translates a class name. A class missing from the table but named {@code outer$rest} is
     * translated through its enclosing class (e.g. Forge-added {@code ady$FlowerEntry} must follow
     * {@code ady}).
     *
     * @param sourceName obfuscated internal name
     * @return the game name, or {@code sourceName} unchanged if the class is not renamed
     */
    public String className(String sourceName) {
        String known = this.classNames.get(sourceName);
        if (known != null) {
            return known;
        }
        int separator = sourceName.lastIndexOf(NotchMappings.INNER_SEPARATOR);
        if (separator <= 0) {
            return sourceName;
        }
        return this.className(sourceName.substring(0, separator)) + sourceName.substring(separator);
    }

    /**
     * Tells whether the class is in the table, i.e. is a Minecraft class.
     */
    public boolean knowsClass(String sourceName) {
        return this.classNames.containsKey(sourceName);
    }

    /**
     * Looks up the game name of a field <b>declared</b> by a class.
     *
     * @param owner obfuscated class name
     * @param field obfuscated field name and type
     * @return the new name, or empty if the class does not declare or rename the field
     */
    public Optional<String> fieldName(String owner, MemberKey field) {
        return Optional.ofNullable(this.fieldNames.getOrDefault(owner, Map.of()).get(field));
    }

    /**
     * Looks up the game name of a method <b>declared</b> by a class.
     *
     * @param owner  obfuscated class name
     * @param method obfuscated method name and descriptor
     * @return the new name, or empty if the class does not declare or rename the method
     */
    public Optional<String> methodName(String owner, MemberKey method) {
        return Optional.ofNullable(this.methodNames.getOrDefault(owner, Map.of()).get(method));
    }

    /**
     * Returns the game names of all methods of a class with a given obfuscated name, whatever
     * their parameters.
     *
     * @return the names found (none, one, or several for overloads)
     */
    public Set<String> methodNamesIgnoringDescriptor(String owner, String sourceName) {
        Set<String> names = new HashSet<>();
        for (Map.Entry<MemberKey, String> entry : this.methodNames.getOrDefault(owner, Map.of()).entrySet()) {
            if (entry.getKey().name().equals(sourceName)) {
                names.add(entry.getValue());
            }
        }
        return names;
    }

    /**
     * Returns the number of classes that are renamed or have renamed members.
     */
    public int classCount() {
        return this.classNames.size();
    }
}
