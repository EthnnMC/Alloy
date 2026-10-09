package dev.alloy.remap.hierarchy;

import java.util.Objects;

/**
 * Identifies a field or method within a class by name and bytecode descriptor (overloads are
 * distinct members).
 *
 * @param name       member name
 * @param descriptor field or method descriptor, e.g. {@code I} or {@code (IF)V}
 */
public record MemberKey(String name, String descriptor) {

    public MemberKey {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(descriptor, "descriptor");
    }
}
