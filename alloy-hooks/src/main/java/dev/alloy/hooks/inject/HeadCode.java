package dev.alloy.hooks.inject;

import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Shared helpers for strategies that inject at the head of a method. Head code goes before
 * everything, including the first label, so a body that starts with a loop does not re-run it.
 */
final class HeadCode {

    private HeadCode() {
        // Utility class.
    }

    /**
     * Returns the head injection site: the first instruction, or an empty list if the method has no body.
     *
     * @throws InjectionException for a constructor, where {@code this} is not yet initialized
     */
    static List<AbstractInsnNode> site(TargetMethod method) throws InjectionException {
        if (!method.hasCode()) {
            return List.of();
        }
        method.requireNotConstructor("a head injection");
        return List.of(method.instructions().getFirst());
    }

    /**
     * Appends {@code return <neutral value>;}: nothing for {@code void}, {@code false} or
     * {@code 0} for a primitive, {@code null} for a reference.
     */
    static void appendDefaultReturn(TargetMethod method, InsnList code) {
        Type returnType = method.returnType();
        switch (returnType.getSort()) {
            case Type.VOID -> {
                // Nothing to push before RETURN.
            }
            case Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT -> code.add(new InsnNode(Opcodes.ICONST_0));
            case Type.LONG -> code.add(new InsnNode(Opcodes.LCONST_0));
            case Type.FLOAT -> code.add(new InsnNode(Opcodes.FCONST_0));
            case Type.DOUBLE -> code.add(new InsnNode(Opcodes.DCONST_0));
            default -> code.add(new InsnNode(Opcodes.ACONST_NULL));
        }
        // IRETURN is a template: ASM derives RETURN, FRETURN, ARETURN... from the type.
        code.add(new InsnNode(returnType.getOpcode(Opcodes.IRETURN)));
    }

    /**
     * Appends the store of the hook's result into a parameter: {@code CHECKCAST} if needed, then the store.
     *
     * @param position index of the replaced parameter, starting at 1
     * @param hook     the hook that produced the value
     */
    static void appendStoreIntoParameter(TargetMethod method, int position, HookCall hook, InsnList code) {
        Type parameterType = method.parameterType(position);
        hook.appendCastTo(parameterType, code);
        code.add(new VarInsnNode(parameterType.getOpcode(Opcodes.ISTORE), method.parameterSlot(position)));
    }
}
