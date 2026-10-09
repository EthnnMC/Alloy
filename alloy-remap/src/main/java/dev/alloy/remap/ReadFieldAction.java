package dev.alloy.remap;

import java.util.Objects;
import java.util.Optional;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;

/**
 * Replaces a call to a no-argument getter with a direct read of a field of the same class
 * (the table's {@code getfield} action).
 *
 * <p>Example: Forge adds {@code NetworkManager.channel()}, which does not exist in the game run by
 * Lunar although the field is public, so
 * {@code INVOKEVIRTUAL NetworkManager.channel()Lio/netty/channel/Channel;} becomes
 * {@code GETFIELD NetworkManager.channel : Lio/netty/channel/Channel;}.</p>
 *
 * @param fieldName       name of the field to read
 * @param fieldDescriptor field type
 */
public record ReadFieldAction(String fieldName, String fieldDescriptor) implements ShimAction {

    public ReadFieldAction {
        Objects.requireNonNull(fieldName, "fieldName");
        Objects.requireNonNull(fieldDescriptor, "fieldDescriptor");
    }

    /**
     * Only an instance call is replaceable (an object is then on the stack for {@code GETFIELD}).
     * The field is read on the class listed in the table, even if the call went through a
     * subclass, so a same-named subclass field cannot hide it.
     */
    @Override
    public Optional<AbstractInsnNode> replacement(MemberShim shim, int opcode) {
        boolean instanceCall = opcode == Opcodes.INVOKEVIRTUAL || opcode == Opcodes.INVOKEINTERFACE
                || opcode == Opcodes.INVOKESPECIAL;
        if (shim.kind() != MemberKind.METHOD || !instanceCall) {
            return Optional.empty();
        }
        return Optional.of(new FieldInsnNode(Opcodes.GETFIELD, shim.owner(), this.fieldName, this.fieldDescriptor));
    }
}
