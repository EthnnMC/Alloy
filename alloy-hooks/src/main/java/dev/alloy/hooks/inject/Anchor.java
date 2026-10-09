package dev.alloy.hooks.inject;

import org.objectweb.asm.tree.AbstractInsnNode;

/**
 * A recognizable game instruction used as a landmark for placing code. Lunar's own names and
 * instruction positions change every version, so anchors rely only on what Minecraft imposes:
 * the method called, a constant, or the class of a created object.
 */
public sealed interface Anchor permits InvokeAnchor, ConstantAnchor, NewAnchor {

    /** Returns whether the instruction is this anchor. */
    boolean matches(AbstractInsnNode instruction);

    /** Describes the anchor for logs, for example {@code INVOKE org/lwjgl/input/Mouse.next()Z}. */
    String describe();

    /** Anchor on a method call. */
    static InvokeAnchor invoke(String owner, String name, String descriptor) {
        return new InvokeAnchor(owner, name, descriptor);
    }

    /**
     * Anchor on a constant load.
     *
     * @param value the constant loaded by {@code LDC} (usually a string)
     */
    static ConstantAnchor constant(Object value) {
        return new ConstantAnchor(value);
    }

    /**
     * Anchor on an object creation.
     *
     * @param type internal name of the class instantiated by {@code NEW}
     */
    static NewAnchor newInstance(String type) {
        return new NewAnchor(type);
    }
}
