package dev.alloy.hooks.inject;

import java.util.Objects;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * Matches the {@code NEW} of a given class, i.e. the start of a {@code new GuiIngame(...)}.
 *
 * @param type internal name of the instantiated class
 */
public record NewAnchor(String type) implements Anchor {

    public NewAnchor {
        Objects.requireNonNull(type, "type");
    }

    @Override
    public boolean matches(AbstractInsnNode instruction) {
        return instruction.getOpcode() == Opcodes.NEW
                && instruction instanceof TypeInsnNode creation
                && this.type.equals(creation.desc);
    }

    @Override
    public String describe() {
        return "NEW " + this.type;
    }
}
