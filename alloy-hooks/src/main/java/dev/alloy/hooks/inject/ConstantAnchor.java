package dev.alloy.hooks.inject;

import java.util.Objects;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;

/**
 * Matches the {@code LDC} that pushes the given value, for example the profiler section name
 * {@code "hand"}.
 *
 * @param value the expected constant
 */
public record ConstantAnchor(Object value) implements Anchor {

    public ConstantAnchor {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public boolean matches(AbstractInsnNode instruction) {
        return instruction instanceof LdcInsnNode constant && this.value.equals(constant.cst);
    }

    @Override
    public String describe() {
        return "LDC \"" + this.value + "\"";
    }
}
