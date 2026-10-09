package dev.alloy.remap;

import java.util.Objects;
import java.util.Optional;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/**
 * Replaces an access to a missing member with a call to a static method written by Alloy (the
 * table's {@code invokestatic} action). The method receives what the original instruction found on
 * the stack, in the same order, and returns what it would have left, so its descriptor is derived
 * from the instruction:
 * <pre>
 *   INVOKEVIRTUAL / INVOKEINTERFACE / INVOKESPECIAL  C.m(args)R   becomes   shim(LC;args)R
 *   INVOKESTATIC                                     C.m(args)R   becomes   shim(args)R
 *   GETFIELD   C.f : T    becomes   shim(LC;)T          PUTFIELD   C.f : T    becomes   shim(LC;T)V
 *   GETSTATIC  C.f : T    becomes   shim()T             PUTSTATIC  C.f : T    becomes   shim(T)V
 * </pre>
 * {@code C} is always the class listed in the table, even when the instruction went through a
 * subclass, so the replacement method has a single signature to provide.
 *
 * @param className  internal name of the class holding the static method
 * @param methodName name of the static method
 */
public record CallStaticAction(String className, String methodName) implements ShimAction {

    public CallStaticAction {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(methodName, "methodName");
    }

    @Override
    public Optional<AbstractInsnNode> replacement(MemberShim shim, int opcode) {
        return CallStaticAction.staticDescriptor(shim, opcode).map(descriptor ->
                new MethodInsnNode(Opcodes.INVOKESTATIC, this.className, this.methodName, descriptor, false));
    }

    /**
     * Computes the static method descriptor for an instruction.
     *
     * @return the descriptor, or empty if the instruction does not match the member kind
     *         (e.g. a {@code GETFIELD} for a method)
     */
    private static Optional<String> staticDescriptor(MemberShim shim, int opcode) {
        String receiver = "L" + shim.owner() + ";";
        String descriptor = shim.descriptor();
        if (shim.kind() == MemberKind.METHOD) {
            return switch (opcode) {
                // A method descriptor starts with "(": insert the receiver right after it.
                case Opcodes.INVOKEVIRTUAL, Opcodes.INVOKEINTERFACE, Opcodes.INVOKESPECIAL ->
                        Optional.of("(" + receiver + descriptor.substring(1));
                case Opcodes.INVOKESTATIC -> Optional.of(descriptor);
                default -> Optional.empty();
            };
        }
        return switch (opcode) {
            case Opcodes.GETFIELD -> Optional.of("(" + receiver + ")" + descriptor);
            case Opcodes.PUTFIELD -> Optional.of("(" + receiver + descriptor + ")V");
            case Opcodes.GETSTATIC -> Optional.of("()" + descriptor);
            case Opcodes.PUTSTATIC -> Optional.of("(" + descriptor + ")V");
            default -> Optional.empty();
        };
    }
}
