package dev.alloy.hooks.inject;

import java.util.Objects;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;

/**
 * A constant string fixed by the catalog, for example the overlay name {@code "HOTBAR"}.
 *
 * @param value the string passed to the hook
 */
public record ConstantArgument(String value) implements HookArgument {

    /** Rejects {@code null}, since {@code LDC} can only load a real constant. */
    public ConstantArgument {
        Objects.requireNonNull(value, "value");
    }

    @Override
    public Type typeIn(TargetMethod method) {
        return Type.getType(String.class);
    }

    @Override
    public void load(TargetMethod method, InsnList code) {
        code.add(new LdcInsnNode(this.value));
    }
}
