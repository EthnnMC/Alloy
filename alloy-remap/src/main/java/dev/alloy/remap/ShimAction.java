package dev.alloy.remap;

import java.util.Optional;

import org.objectweb.asm.tree.AbstractInsnNode;

/**
 * What replaces an access to a member that Forge adds to Minecraft and that does not exist in the
 * game run by Lunar. A replacement instruction must leave the stack exactly as the original would.
 */
public sealed interface ShimAction permits ReadFieldAction, CallStaticAction {

    /**
     * Builds the instruction replacing an access to the member described by {@code shim}.
     *
     * @param opcode opcode of the original instruction ({@code INVOKEVIRTUAL}, {@code GETFIELD}...)
     * @return the replacement, or empty if this action cannot replace that kind of instruction
     *         (it is then left as is)
     */
    Optional<AbstractInsnNode> replacement(MemberShim shim, int opcode);
}
