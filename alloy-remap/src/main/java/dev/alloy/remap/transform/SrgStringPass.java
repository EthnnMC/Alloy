package dev.alloy.remap.transform;

import dev.alloy.remap.SrgNameTable;

import java.util.Objects;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Translates SRG names written as <b>strings</b>, as reflection does
 * ({@code getDeclaredField("field_73840_e")}); ordinary remapping only sees bytecode names. Only a
 * string <b>exactly equal</b> to a known SRG name is replaced, in {@code LDC} constants and in
 * constant field values.
 */
public final class SrgStringPass implements ClassPass {

    private final SrgNameTable names;

    public SrgStringPass(SrgNameTable names) {
        this.names = Objects.requireNonNull(names, "names");
    }

    @Override
    public Verdict apply(ClassNode classNode) {
        for (FieldNode field : classNode.fields) {
            if (field.value instanceof String text) {
                field.value = this.names.runtimeName(text);
            }
        }
        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof LdcInsnNode constant && constant.cst instanceof String text) {
                    constant.cst = this.names.runtimeName(text);
                }
            }
        }
        return Verdict.KEEP;
    }
}
