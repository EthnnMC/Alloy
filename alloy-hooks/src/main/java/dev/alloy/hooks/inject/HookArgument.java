package dev.alloy.hooks.inject;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;

/**
 * A value the injected code passes to a {@code GameHooks} hook: {@code this}, a parameter of
 * the modified method, or a constant string.
 */
public sealed interface HookArgument permits ThisArgument, ParameterArgument, ConstantArgument {

    /**
     * Returns the type of the loaded value.
     *
     * @throws InjectionException if the method cannot supply the value
     */
    Type typeIn(TargetMethod method) throws InjectionException;

    /** Appends the instruction that pushes the value; call only after {@link #typeIn(TargetMethod)}. */
    void load(TargetMethod method, InsnList code);

    /** Argument for {@code this}. */
    static HookArgument self() {
        return new ThisArgument();
    }

    /**
     * Argument for a parameter of the modified method.
     *
     * @param position parameter index, starting at 1
     */
    static HookArgument parameter(int position) {
        return new ParameterArgument(position);
    }

    /** Argument for a constant string. */
    static HookArgument constant(String value) {
        return new ConstantArgument(value);
    }
}
