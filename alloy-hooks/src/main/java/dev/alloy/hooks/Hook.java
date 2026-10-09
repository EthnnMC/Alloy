package dev.alloy.hooks;

import dev.alloy.hooks.inject.InjectionStrategy;
import java.util.List;
import java.util.Objects;

/**
 * A catalog hook: where it installs and with which injections. Its injections are all-or-nothing:
 * either all are applied or none.
 *
 * @param id           stable identifier used in the report (for example {@code tick.client})
 * @param className    internal name of the target game class
 * @param method       the target method(s) in that class
 * @param alwaysActive {@code true} if injected even before the Forge runtime starts
 * @param injections   the injections to apply, in order (at least one)
 */
public record Hook(
        String id, String className, MethodSelector method, boolean alwaysActive, List<InjectionStrategy> injections) {

    public Hook {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(method, "method");
        injections = List.copyOf(injections);
        if (injections.isEmpty()) {
            throw new IllegalArgumentException("Hook '" + id + "' has no injection");
        }
    }

    /** Creates a hook installed in one exact method. */
    public static Hook inMethod(
            String id, String className, String methodName, String methodDescriptor, InjectionStrategy... injections) {
        MethodSelector method = MethodSelector.exactly(methodName, methodDescriptor);
        return new Hook(id, className, method, false, List.of(injections));
    }

    /** Creates a hook whose anchor is searched in every method of the class. */
    public static Hook inAnyMethod(String id, String className, InjectionStrategy... injections) {
        return new Hook(id, className, MethodSelector.anyMethod(), false, List.of(injections));
    }

    /** Returns a copy that installs even before the Forge runtime starts. */
    public Hook activeFromTheStart() {
        return new Hook(this.id, this.className, this.method, true, this.injections);
    }
}
