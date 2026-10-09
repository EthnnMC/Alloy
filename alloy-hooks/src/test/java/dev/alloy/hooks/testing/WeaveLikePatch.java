package dev.alloy.hooks.testing;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Reproduces for tests what Weave's {@code URLClassLoaderTransformer} does to a class loader: a
 * detour to the system loader at the head of {@code loadClass}, then a rewrite that recomputes
 * all frames.
 *
 * <p>Weave runs before Alloy when chained, so Alloy's bridge receives a class already modified
 * this way and must add to it without breaking anything.</p>
 */
public final class WeaveLikePatch {

    private WeaveLikePatch() {
    }

    /**
     * Adds at the head of {@code loadClass(String, boolean)}:
     * {@code if (name.startsWith(prefix)) return ClassLoader.getSystemClassLoader().loadClass(name);}
     * and returns the modified bytecode, frames recomputed.
     */
    public static byte[] apply(byte[] loaderClassBytes, String delegatedPrefix) {
        ClassNode classNode = new ClassNode();
        new ClassReader(loaderClassBytes).accept(classNode, 0);
        for (MethodNode method : classNode.methods) {
            if (method.name.equals("loadClass") && method.desc.equals("(Ljava/lang/String;Z)Ljava/lang/Class;")) {
                method.instructions.insert(WeaveLikePatch.newPrelude(delegatedPrefix));
            }
        }
        // Like Weave: COMPUTE_FRAMES. Acceptable in a test, where every class named is loadable.
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private static InsnList newPrelude(String delegatedPrefix) {
        LabelNode notDelegated = new LabelNode();
        InsnList code = new InsnList();
        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new LdcInsnNode(delegatedPrefix));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/lang/String", "startsWith", "(Ljava/lang/String;)Z", false));
        code.add(new JumpInsnNode(Opcodes.IFEQ, notDelegated));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "java/lang/ClassLoader", "getSystemClassLoader", "()Ljava/lang/ClassLoader;", false));
        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/lang/ClassLoader", "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;", false));
        code.add(new InsnNode(Opcodes.ARETURN));
        code.add(notDelegated);
        return code;
    }
}
