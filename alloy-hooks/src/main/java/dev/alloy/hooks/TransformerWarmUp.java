package dev.alloy.hooks;

import dev.alloy.hooks.inject.Anchor;
import dev.alloy.hooks.inject.AnchorCall;
import dev.alloy.hooks.inject.AnchorPosition;
import dev.alloy.hooks.inject.HeadCall;
import dev.alloy.hooks.inject.HeadCancel;
import dev.alloy.hooks.inject.HeadCancelThenReplaceParameter;
import dev.alloy.hooks.inject.HeadReplaceParameter;
import dev.alloy.hooks.inject.HookArgument;
import dev.alloy.hooks.inject.HookCall;
import dev.alloy.hooks.inject.Occurrence;
import dev.alloy.hooks.inject.RedirectInvoke;
import dev.alloy.hooks.inject.ReturnCall;
import dev.alloy.hooks.inject.ReturnFilter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Transformer rehearsal: applies each injection strategy and the loader bridge once to two
 * synthetic classes that are never defined. The first run of a code path is the costliest
 * because the JVM loads every class it needs (about a hundred ASM classes), which would
 * otherwise happen in the middle of defining a Lunar class. The agent therefore calls
 * {@link GameClassTransformer#warmUp()} in {@code premain}.
 */
final class TransformerWarmUp {

    private static final String TARGET = "dev/alloy/hooks/warmup/Target";
    private static final String LOADER = "com/moonsworth/lunar/genesis/AlloyWarmUpLoader";
    private static final String OBJECT = "java/lang/Object";
    private static final String OBJECT_TO_VOID = "(Ljava/lang/Object;)V";
    private static final String ANCHOR_CONSTANT = "warm-up";

    private TransformerWarmUp() {
        // Static-only class.
    }

    /**
     * Runs the rehearsal; nothing produced is kept.
     *
     * @return the rehearsal report: all its hooks must be applied
     * @throws IllegalStateException if the bridge could not be installed on the synthetic loader
     */
    static HookReport run(ClassPatcher patcher, GameLoaderBridgePatch bridgePatch) {
        if (bridgePatch.patch(TransformerWarmUp.loaderClass()).isEmpty()) {
            throw new IllegalStateException("The loader bridge does not recognise its own warm-up class");
        }
        List<Hook> hooks = TransformerWarmUp.hooks();
        List<String> hookIds = new ArrayList<>();
        for (Hook hook : hooks) {
            hookIds.add(hook.id());
        }
        HookReport report = new HookReport(hookIds);
        patcher.patch(TransformerWarmUp.targetClass(), hooks, report);
        // Loader keys are compared as soon as the first Lunar classes are defined, so exercise
        // storing and looking them up in a set once.
        Set<LoaderClassKey> keys = new HashSet<>();
        keys.add(LoaderClassKey.of(TransformerWarmUp.class));
        if (!keys.contains(LoaderClassKey.of(TransformerWarmUp.class))) {
            throw new IllegalStateException("Two keys of the same loader class are not equal");
        }
        return report;
    }

    /** One hook per strategy, each on a fitting synthetic method. */
    private static List<Hook> hooks() {
        String target = TransformerWarmUp.TARGET;
        String objectToVoid = TransformerWarmUp.OBJECT_TO_VOID;
        HookArgument self = HookArgument.self();
        HookArgument first = HookArgument.parameter(1);
        HookCall chooseSound = HookCall.to(
                "playSound", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", self, first);
        return List.of(
                Hook.inMethod("warmup.calls", target, "plain", objectToVoid,
                        new HeadCall(HookCall.to("clientTickStart", "()V")),
                        new ReturnCall(HookCall.to("clientTickEnd", "()V"), Occurrence.EVERY)),
                Hook.inMethod("warmup.cancel", target, "cancellable", objectToVoid,
                        new HeadCancel(HookCall.to("chatReceived", "(Ljava/lang/Object;)Z", first))),
                Hook.inMethod("warmup.open", target, "replaceable", objectToVoid,
                        new HeadCancelThenReplaceParameter(1,
                                HookCall.to("guiOpen", "(Ljava/lang/Object;)Z", first),
                                HookCall.to("guiOpenResult", "()Ljava/lang/Object;"))),
                Hook.inMethod("warmup.replace", target, "nullable", objectToVoid,
                        new HeadReplaceParameter(1, chooseSound, true)),
                Hook.inMethod("warmup.filter", target, "filtered", "()F",
                        new ReturnFilter(HookCall.to("fovUpdate", "(FLjava/lang/Object;)F", self))),
                Hook.inMethod("warmup.anchor", target, "anchored", "()V",
                        new AnchorCall(AnchorPosition.BEFORE, Anchor.constant(TransformerWarmUp.ANCHOR_CONSTANT),
                                Occurrence.FIRST, HookCall.to("modsInit", "()V"))),
                Hook.inAnyMethod("warmup.redirect", target,
                        new RedirectInvoke(Anchor.invoke(target, "next", "()Z"),
                                HookCall.to("mouseNext", "()Z"), false)));
    }

    /**
     * Builds the target class: {@code plain}, {@code cancellable}, {@code replaceable} and
     * {@code nullable} do nothing; {@code filtered} returns {@code 0}; {@code anchored} loads the
     * anchor constant; {@code polled} calls {@code next()}, the call to redirect.
     */
    private static byte[] targetClass() {
        ClassWriter writer = TransformerWarmUp.newClass(TransformerWarmUp.TARGET, TransformerWarmUp.OBJECT);
        String objectToVoid = TransformerWarmUp.OBJECT_TO_VOID;
        for (String name : List.of("plain", "cancellable", "replaceable", "nullable")) {
            MethodVisitor empty = TransformerWarmUp.newMethod(writer, Opcodes.ACC_PUBLIC, name, objectToVoid);
            TransformerWarmUp.end(empty, Opcodes.RETURN, 0, 2);
        }
        MethodVisitor filtered = TransformerWarmUp.newMethod(writer, Opcodes.ACC_PUBLIC, "filtered", "()F");
        filtered.visitInsn(Opcodes.FCONST_0);
        TransformerWarmUp.end(filtered, Opcodes.FRETURN, 1, 1);

        MethodVisitor anchored = TransformerWarmUp.newMethod(writer, Opcodes.ACC_PUBLIC, "anchored", "()V");
        anchored.visitLdcInsn(TransformerWarmUp.ANCHOR_CONSTANT);
        anchored.visitInsn(Opcodes.POP);
        TransformerWarmUp.end(anchored, Opcodes.RETURN, 1, 1);

        int publicStatic = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
        MethodVisitor next = TransformerWarmUp.newMethod(writer, publicStatic, "next", "()Z");
        next.visitInsn(Opcodes.ICONST_0);
        TransformerWarmUp.end(next, Opcodes.IRETURN, 1, 0);

        MethodVisitor polled = TransformerWarmUp.newMethod(writer, Opcodes.ACC_PUBLIC, "polled", "()V");
        polled.visitMethodInsn(Opcodes.INVOKESTATIC, TransformerWarmUp.TARGET, "next", "()Z", false);
        polled.visitInsn(Opcodes.POP);
        TransformerWarmUp.end(polled, Opcodes.RETURN, 1, 1);

        writer.visitEnd();
        return writer.toByteArray();
    }

    /** Builds a loader shaped like Lunar's, whose {@code loadClass} simply delegates to the parent. */
    private static byte[] loaderClass() {
        String descriptor = "(Ljava/lang/String;Z)Ljava/lang/Class;";
        ClassWriter writer = TransformerWarmUp.newClass(TransformerWarmUp.LOADER, "java/net/URLClassLoader");
        MethodVisitor loadClass = TransformerWarmUp.newMethod(writer, Opcodes.ACC_PUBLIC, "loadClass", descriptor);
        loadClass.visitVarInsn(Opcodes.ALOAD, 0);
        loadClass.visitVarInsn(Opcodes.ALOAD, 1);
        loadClass.visitVarInsn(Opcodes.ILOAD, 2);
        loadClass.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/net/URLClassLoader", "loadClass", descriptor, false);
        TransformerWarmUp.end(loadClass, Opcodes.ARETURN, 3, 3);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static ClassWriter newClass(String internalName, String superName) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, internalName, null, superName, null);
        return writer;
    }

    private static MethodVisitor newMethod(ClassWriter writer, int access, String name, String descriptor) {
        MethodVisitor code = writer.visitMethod(access, name, descriptor, null, null);
        code.visitCode();
        return code;
    }

    /** Ends a method with its return instruction and declares its stack and locals sizes. */
    private static void end(MethodVisitor code, int returnOpcode, int maxStack, int maxLocals) {
        code.visitInsn(returnOpcode);
        code.visitMaxs(maxStack, maxLocals);
        code.visitEnd();
    }
}
