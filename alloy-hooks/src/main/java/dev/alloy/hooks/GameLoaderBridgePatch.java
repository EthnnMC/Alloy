package dev.alloy.hooks;

import dev.alloy.bridge.GameHooks;
import dev.alloy.hooks.inject.TargetMethod;
import java.util.Optional;
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
 * Bridges the game class loader to the agent's. Lunar's loader (the "GenesisClassLoader")
 * resolves almost every class itself and the system loader holding the agent is not among its
 * ancestors, so the game could never find {@code dev.alloy.bridge.GameHooks}. The fix, taken
 * from Weave's {@code URLClassLoaderTransformer}, prepends this to its
 * {@code loadClass(String, boolean)}:
 *
 * <pre>
 *   if (name.startsWith("dev.alloy.bridge.")) {
 *       return ClassLoader.getSystemClassLoader().loadClass(name);
 *   }
 * </pre>
 *
 * <p>The loader class has a scrambled name that changes every Lunar version, so it is recognized
 * by shape: in package {@code com.moonsworth.lunar.genesis}, directly extending
 * {@code java.net.URLClassLoader} and declaring {@code loadClass(String, boolean)}. Only
 * {@code String}, {@code ClassLoader} and {@code Class} appear in the added code, never an Alloy
 * type that Lunar's parent loader could not resolve.</p>
 */
final class GameLoaderBridgePatch {

    /** Internal-notation package of Lunar's bootstrap classes. */
    private static final String GENESIS_PACKAGE = "com/moonsworth/lunar/genesis/";

    private static final String URL_CLASS_LOADER = "java/net/URLClassLoader";
    private static final String CLASS_LOADER = "java/lang/ClassLoader";
    private static final String STRING = "java/lang/String";

    private static final String LOAD_CLASS = "loadClass";
    private static final String LOAD_CLASS_WITH_RESOLVE_DESCRIPTOR = "(Ljava/lang/String;Z)Ljava/lang/Class;";
    private static final String LOAD_CLASS_DESCRIPTOR = "(Ljava/lang/String;)Ljava/lang/Class;";

    /** Local variable holding the first parameter, {@code name}; slot 0 is {@code this}. */
    private static final int NAME_SLOT = 1;

    /**
     * Cheap name test: whether the class sits directly in Lunar's bootstrap package, so the
     * bytecode of every other class need not be read.
     *
     * @return {@code true} if the bytecode should be examined with {@link #patch(byte[])}
     */
    boolean isCandidate(String internalClassName) {
        return internalClassName.startsWith(GameLoaderBridgePatch.GENESIS_PACKAGE)
                && internalClassName.indexOf('/', GameLoaderBridgePatch.GENESIS_PACKAGE.length()) < 0;
    }

    /**
     * Adds the detour to the game loader.
     *
     * @return the modified bytecode, or empty if the class is not the game loader
     */
    Optional<byte[]> patch(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        if (!GameLoaderBridgePatch.URL_CLASS_LOADER.equals(reader.getSuperName())) {
            return Optional.empty();
        }
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, ClassReader.EXPAND_FRAMES);
        Optional<MethodNode> loadClass = GameLoaderBridgePatch.findLoadClass(classNode);
        if (loadClass.isEmpty()) {
            return Optional.empty();
        }
        TargetMethod method = new TargetMethod(classNode.name, classNode.version, loadClass.get());
        method.instructions().insert(GameLoaderBridgePatch.newPrelude(method));

        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        return Optional.of(writer.toByteArray());
    }

    /** Finds {@code loadClass(String, boolean)}; a declaration without a body (abstract) does not count. */
    private static Optional<MethodNode> findLoadClass(ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            boolean rightMethod = GameLoaderBridgePatch.LOAD_CLASS.equals(method.name)
                    && GameLoaderBridgePatch.LOAD_CLASS_WITH_RESOLVE_DESCRIPTOR.equals(method.desc);
            if (rightMethod && method.instructions.size() > 0) {
                return Optional.of(method);
            }
        }
        return Optional.empty();
    }

    /**
     * Builds the detour. It adds a jump, so its target needs a frame: the method-entry frame
     * ({@code this}, {@code name}, {@code resolve}, empty stack), added by
     * {@link TargetMethod#appendResumePoint}.
     */
    private static InsnList newPrelude(TargetMethod method) {
        LabelNode notBridged = new LabelNode();
        InsnList code = new InsnList();
        // if (!name.startsWith("dev.alloy.bridge.")) goto notBridged;
        code.add(new VarInsnNode(Opcodes.ALOAD, GameLoaderBridgePatch.NAME_SLOT));
        code.add(new LdcInsnNode(GameHooks.BRIDGE_PACKAGE_PREFIX));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                GameLoaderBridgePatch.STRING, "startsWith", "(Ljava/lang/String;)Z", false));
        code.add(new JumpInsnNode(Opcodes.IFEQ, notBridged));
        // return ClassLoader.getSystemClassLoader().loadClass(name);
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                GameLoaderBridgePatch.CLASS_LOADER, "getSystemClassLoader", "()Ljava/lang/ClassLoader;", false));
        code.add(new VarInsnNode(Opcodes.ALOAD, GameLoaderBridgePatch.NAME_SLOT));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                GameLoaderBridgePatch.CLASS_LOADER, GameLoaderBridgePatch.LOAD_CLASS,
                GameLoaderBridgePatch.LOAD_CLASS_DESCRIPTOR, false));
        code.add(new InsnNode(Opcodes.ARETURN));
        // notBridged: the original code resumes here.
        method.appendResumePoint(code, notBridged, true);
        return code;
    }
}
