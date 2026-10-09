package dev.alloy.hooks.inject;

import java.util.Objects;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/**
 * Matches any {@code INVOKE*} instruction by owner class, name and descriptor of the called
 * method. The opcode is deliberately not compared: Lunar makes private methods public without
 * touching their calls, which stay {@code INVOKESPECIAL} where {@code INVOKEVIRTUAL} is expected.
 *
 * @param owner      internal name of the class named by the call
 * @param name       name of the called method
 * @param descriptor descriptor of the called method
 */
public record InvokeAnchor(String owner, String name, String descriptor) implements Anchor {

    public InvokeAnchor {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(descriptor, "descriptor");
    }

    @Override
    public boolean matches(AbstractInsnNode instruction) {
        return instruction instanceof MethodInsnNode call
                && this.owner.equals(call.owner)
                && this.name.equals(call.name)
                && this.descriptor.equals(call.desc);
    }

    @Override
    public String describe() {
        return "INVOKE " + this.owner + "." + this.name + this.descriptor;
    }
}
