package dev.alloy.hooks.inject;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Loads a parameter of the modified method.
 *
 * @param position parameter index, starting at 1
 */
public record ParameterArgument(int position) implements HookArgument {

    @Override
    public Type typeIn(TargetMethod method) throws InjectionException {
        if (this.position < 1 || this.position > method.parameterCount()) {
            throw new InjectionException(
                    "parameter " + this.position + " is requested but " + method.describe()
                            + " has " + method.parameterCount() + " parameter(s)");
        }
        return method.parameterType(this.position);
    }

    @Override
    public void load(TargetMethod method, InsnList code) {
        // ILOAD is a template: ASM derives FLOAD, DLOAD, ALOAD... from the parameter type.
        int loadOpcode = method.parameterType(this.position).getOpcode(Opcodes.ILOAD);
        code.add(new VarInsnNode(loadOpcode, method.parameterSlot(this.position)));
    }
}
