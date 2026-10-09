package dev.alloy.remap.testkit;

import java.util.LinkedHashSet;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Printer;

/**
 * Test helper: summarizes a class as text lines that are easy to compare in an assertion.
 */
public final class ClassScan {

    private ClassScan() {
        // Utility class.
    }

    /**
     * Describes a class: its declaration, declared members and everything its code references.
     *
     * @return the lines, without duplicates, in order of appearance
     */
    public static Set<String> describe(byte[] classFile) {
        ClassNode classNode = ClassBytes.read(classFile);
        Set<String> lines = new LinkedHashSet<>();
        lines.add("class " + classNode.name + " extends " + classNode.superName);
        for (FieldNode field : classNode.fields) {
            lines.add("field " + field.name + " " + field.desc);
        }
        for (MethodNode method : classNode.methods) {
            lines.add("method " + method.name + " " + method.desc);
            for (AbstractInsnNode instruction : method.instructions) {
                ClassScan.describe(instruction, lines);
            }
        }
        return lines;
    }

    /** Collects the field and method references made by a class's code (access instructions and lambda method handles). */
    public static Set<MemberRef> memberReferences(byte[] classFile) {
        Set<MemberRef> references = new LinkedHashSet<>();
        for (MethodNode method : ClassBytes.read(classFile).methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof FieldInsnNode access) {
                    references.add(new MemberRef(true, access.owner, access.name, access.desc));
                } else if (instruction instanceof MethodInsnNode call) {
                    references.add(new MemberRef(false, call.owner, call.name, call.desc));
                } else if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                    for (Object argument : dynamic.bsmArgs) {
                        if (argument instanceof Handle handle) {
                            boolean field = handle.getTag() <= Opcodes.H_PUTSTATIC;
                            references.add(new MemberRef(field, handle.getOwner(), handle.getName(), handle.getDesc()));
                        }
                    }
                }
            }
        }
        return references;
    }

    /** Collects all member names in a class: declared members, members the code references, and {@code invokedynamic} names. */
    public static Set<String> memberNames(byte[] classFile) {
        ClassNode classNode = ClassBytes.read(classFile);
        Set<String> names = new LinkedHashSet<>();
        classNode.fields.forEach(field -> names.add(field.name));
        for (MethodNode method : classNode.methods) {
            names.add(method.name);
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                    names.add(dynamic.name);
                }
            }
        }
        ClassScan.memberReferences(classFile).forEach(reference -> names.add(reference.name()));
        return names;
    }

    /** Collects every class name a class mentions anywhere: header, descriptors, signatures, instructions, annotations, inner class table. */
    public static Set<String> referencedClasses(byte[] classFile) {
        Set<String> names = new LinkedHashSet<>();
        Remapper collector = new Remapper() {
            @Override
            public String map(String internalName) {
                names.add(internalName);
                return internalName;
            }
        };
        new ClassReader(classFile).accept(new ClassRemapper(new ClassNode(), collector), 0);
        return names;
    }

    private static void describe(AbstractInsnNode instruction, Set<String> lines) {
        if (instruction instanceof FieldInsnNode access) {
            lines.add(Printer.OPCODES[access.getOpcode()] + " " + access.owner + "." + access.name + " " + access.desc);
        } else if (instruction instanceof MethodInsnNode call) {
            lines.add(Printer.OPCODES[call.getOpcode()] + " " + call.owner + "." + call.name + " " + call.desc);
        } else if (instruction instanceof InvokeDynamicInsnNode dynamic) {
            lines.add("INVOKEDYNAMIC " + dynamic.name + " " + dynamic.desc);
            for (Object argument : dynamic.bsmArgs) {
                if (argument instanceof Handle handle) {
                    lines.add("HANDLE " + handle.getOwner() + "." + handle.getName() + " " + handle.getDesc());
                }
            }
        } else if (instruction instanceof LdcInsnNode constant && constant.cst instanceof String text) {
            lines.add("LDC " + text);
        }
    }
}
