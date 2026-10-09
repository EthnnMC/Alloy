package dev.alloy.hooks;

import java.util.Objects;

/**
 * Selects the method(s) of a game class where a hook installs: usually one exact method, or
 * every method when Lunar's mixins moved the target instruction into a synthetic method with an
 * unpredictable name.
 */
public final class MethodSelector {

    /** The single stateless "any method" selector. */
    private static final MethodSelector ANY_METHOD = new MethodSelector(null, null);

    /** Target method name; {@code null} for any method. */
    private final String name;

    /** Target method descriptor; {@code null} for any method. */
    private final String descriptor;

    private MethodSelector(String name, String descriptor) {
        this.name = name;
        this.descriptor = descriptor;
    }

    /**
     * Selects one exact method.
     *
     * @param name       method name, for example {@code runTick}
     * @param descriptor its descriptor, for example {@code ()V}
     */
    public static MethodSelector exactly(String name, String descriptor) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(descriptor, "descriptor");
        return new MethodSelector(name, descriptor);
    }

    /** Selects every method of the class. */
    public static MethodSelector anyMethod() {
        return MethodSelector.ANY_METHOD;
    }

    /** Returns whether this selector matches every method (the one from {@link #anyMethod()}). */
    public boolean isAnyMethod() {
        return this.name == null;
    }

    /** Returns whether the given method is targeted. */
    public boolean matches(String methodName, String methodDescriptor) {
        return this.isAnyMethod() || (this.name.equals(methodName) && this.descriptor.equals(methodDescriptor));
    }

    @Override
    public String toString() {
        return this.isAnyMethod() ? "any method" : this.name + this.descriptor;
    }
}
