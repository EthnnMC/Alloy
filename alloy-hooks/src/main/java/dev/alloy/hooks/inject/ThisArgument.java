package dev.alloy.hooks.inject;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.VarInsnNode;

/** Loads {@code this}, always local variable 0 of an instance method. */
public record ThisArgument() implements HookArgument {

    private static final int THIS_SLOT = 0;

    @Override
    public Type typeIn(TargetMethod method) throws InjectionException {
        if (method.isStatic()) {
            throw new InjectionException("'this' is requested but " + method.describe() + " is static");
        }
        return Type.getObjectType(method.ownerInternalName());
    }

    @Override
    public void load(TargetMethod method, InsnList code) {
        code.add(new VarInsnNode(Opcodes.ALOAD, ThisArgument.THIS_SLOT));
    }
}
