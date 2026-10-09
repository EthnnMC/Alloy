package dev.alloy.hooks.inject;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;

/** Finds the return instructions for strategies that inject at method exit. */
final class ReturnSites {

    private ReturnSites() {
        // Utility class.
    }

    /** Lists the return instructions in order; {@code ATHROW} exits are not included. */
    static List<AbstractInsnNode> in(TargetMethod method) {
        List<AbstractInsnNode> returns = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions()) {
            // The six return opcodes are contiguous, from IRETURN to RETURN.
            int opcode = instruction.getOpcode();
            if (opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN) {
                returns.add(instruction);
            }
        }
        return returns;
    }
}
