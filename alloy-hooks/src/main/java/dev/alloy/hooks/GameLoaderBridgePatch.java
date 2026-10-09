package dev.alloy.hooks;

import dev.alloy.bridge.GameHooks;
import dev.alloy.hooks.inject.TargetMethod;
import java.util.Optional;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
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
 *   Class found = alloy$findInClassSource(name);   // the mods' classes, see newClassSourceLookup()
 *   if (found != null) {
 *       return found;
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

    /** Name of the method added to the loader; see {@link #newClassSourceLookup()}. */
    private static final String FIND_IN_SOURCE = "alloy$findInClassSource";
    private static final String OBJECT = "java/lang/Object";
    private static final String FUNCTION = "java/util/function/Function";
    private static final String FUNCTION_DESCRIPTOR = "Ljava/util/function/Function;";

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
        method.instructions().insert(GameLoaderBridgePatch.newPrelude(method, classNode.name, loadClass.get().maxLocals));
        classNode.fields.add(new FieldNode(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_VOLATILE | Opcodes.ACC_SYNTHETIC,
                GameHooks.CLASS_SOURCE_FIELD, GameLoaderBridgePatch.FUNCTION_DESCRIPTOR, null, null));
        classNode.methods.add(GameLoaderBridgePatch.newClassSourceLookup(classNode.name));

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
    private static InsnList newPrelude(TargetMethod method, String loaderName, int freeSlot) {
        LabelNode notBridged = new LabelNode();
        LabelNode notInSource = new LabelNode();
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
        // notBridged: Class found = alloy$findInClassSource(name); if (found != null) return found;
        method.appendResumePoint(code, notBridged, false);
        code.add(new VarInsnNode(Opcodes.ALOAD, GameLoaderBridgePatch.NAME_SLOT));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, loaderName,
                GameLoaderBridgePatch.FIND_IN_SOURCE, GameLoaderBridgePatch.LOAD_CLASS_DESCRIPTOR, false));
        // The result lives in a variable the original code never uses, so both resume points keep
        // the method-entry frame.
        code.add(new VarInsnNode(Opcodes.ASTORE, freeSlot));
        code.add(new VarInsnNode(Opcodes.ALOAD, freeSlot));
        code.add(new JumpInsnNode(Opcodes.IFNULL, notInSource));
        code.add(new VarInsnNode(Opcodes.ALOAD, freeSlot));
        code.add(new InsnNode(Opcodes.ARETURN));
        // notInSource: the original code resumes here.
        method.appendResumePoint(code, notInSource, true);
        return code;
    }

    /**
     * Builds the method that asks the agent for a class the game does not have (a mod's class
     * reached from code a mixin put in the game). The agent stores a {@code Function} in a static
     * field added to the loader; only JDK types are used, as in the detour.
     *
     * <pre>
     *   public static volatile Function alloy$classSource;
     *
     *   private static Class alloy$findInClassSource(String name) {
     *       Function source = alloy$classSource;
     *       if (source == null) { return null; }
     *       return (Class) source.apply(name);
     *   }
     * </pre>
     */
    private static MethodNode newClassSourceLookup(String loaderName) {
        MethodNode lookup = new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                GameLoaderBridgePatch.FIND_IN_SOURCE, GameLoaderBridgePatch.LOAD_CLASS_DESCRIPTOR, null, null);
        LabelNode hasSource = new LabelNode();
        InsnList code = lookup.instructions;
        code.add(new FieldInsnNode(Opcodes.GETSTATIC, loaderName,
                GameHooks.CLASS_SOURCE_FIELD, GameLoaderBridgePatch.FUNCTION_DESCRIPTOR));
        code.add(new VarInsnNode(Opcodes.ASTORE, 1));
        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new JumpInsnNode(Opcodes.IFNONNULL, hasSource));
        code.add(new InsnNode(Opcodes.ACONST_NULL));
        code.add(new InsnNode(Opcodes.ARETURN));
        code.add(hasSource);
        // Frame of the jump target: the parameter and the source, empty stack.
        code.add(new FrameNode(Opcodes.F_NEW, 2,
                new Object[] {GameLoaderBridgePatch.STRING, GameLoaderBridgePatch.FUNCTION}, 0, new Object[0]));
        code.add(new VarInsnNode(Opcodes.ALOAD, 1));
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, GameLoaderBridgePatch.FUNCTION, "apply",
                "(L" + GameLoaderBridgePatch.OBJECT + ";)L" + GameLoaderBridgePatch.OBJECT + ";", true));
        code.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Class"));
        code.add(new InsnNode(Opcodes.ARETURN));
        return lookup;
    }
}
