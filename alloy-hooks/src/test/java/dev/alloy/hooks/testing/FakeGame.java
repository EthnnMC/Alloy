package dev.alloy.hooks.testing;

import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Builds the bytecode of a fake game: a synthetic class whose methods each imitate, in
 * miniature, the shape of a Minecraft method targeted by a hook.
 *
 * <p>Each body records its passage in {@link GameTrace}, so a test sees whether the original
 * code ran and when, relative to the hooks.</p>
 */
public final class FakeGame {

    /** Internal name of the generated class. */
    public static final String NAME = "fake/game/Game";

    private static final String TRACE = SyntheticClass.internalNameOf(GameTrace.class);
    private static final String STRING_BUILDER = "java/lang/StringBuilder";

    private FakeGame() {
    }

    /** Builds the class; {@code classVersion} is the class file version (50 or 61). */
    public static byte[] bytes(int classVersion) {
        SyntheticClass game = new SyntheticClass(FakeGame.NAME, classVersion)
                .field("shown", "Ljava/lang/String;")
                .field("completions", "[Ljava/lang/String;")
                .field("played", "Ljava/lang/CharSequence;")
                .defaultConstructor();
        FakeGame.addEntryPoints(game);
        FakeGame.addSimpleBodies(game);
        FakeGame.addBranchingBodies(game);
        FakeGame.addParameterUsers(game);
        FakeGame.addValueProducers(game);
        FakeGame.addAnchoredBodies(game);
        FakeGame.addInputLoops(game);
        FakeGame.addMovedCallSite(game);
        return game.toByteArray();
    }

    private static void addEntryPoints(SyntheticClass game) {
        game.method(Opcodes.ACC_PUBLIC, "<init>", "(Ljava/lang/Object;)V", code -> {
            code.visitVarInsn(Opcodes.ALOAD, 0);
            code.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            SyntheticClass.record(code, "constructed");
            code.visitInsn(Opcodes.RETURN);
        });
        game.method(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "main", "([Ljava/lang/String;)V",
                code -> FakeGame.recordAndReturn(code, "main"));
        game.method(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "command", "(Ljava/lang/String;Z)V",
                code -> FakeGame.recordAndReturn(code, "command"));
    }

    private static void addSimpleBodies(SyntheticClass game) {
        game.method(Opcodes.ACC_PUBLIC, "tick", "()V", code -> FakeGame.recordAndReturn(code, "tick"));
        game.method(Opcodes.ACC_PUBLIC, "chat", "(Ljava/lang/Object;)V",
                code -> FakeGame.recordAndReturn(code, "chat"));
        game.method(Opcodes.ACC_PUBLIC, "render", "(Ljava/lang/Object;DDDFF)V",
                code -> FakeGame.recordAndReturn(code, "render"));
        game.method(Opcodes.ACC_PUBLIC, "refresh", "()V", code -> FakeGame.recordAndReturn(code, "refresh"));
        game.method(Opcodes.ACC_PUBLIC, "draw", "(IIF)V", code -> FakeGame.recordAndReturn(code, "draw"));
        game.method(Opcodes.ACC_PUBLIC, "spawn", "(Ljava/lang/Object;)Z", code -> {
            SyntheticClass.record(code, "spawn");
            code.visitInsn(Opcodes.ICONST_1);
            code.visitInsn(Opcodes.IRETURN);
        });
        game.method(Opcodes.ACC_PUBLIC, "crosshair", "()Z", code -> {
            code.visitInsn(Opcodes.ICONST_1);
            code.visitInsn(Opcodes.IRETURN);
        });
    }

    private static void addBranchingBodies(SyntheticClass game) {
        game.method(Opcodes.ACC_PUBLIC, "update", "(Z)V", FakeGame::earlyOrLate);
        game.method(Opcodes.ACC_PUBLIC, "init", "(Z)V", FakeGame::earlyOrLate);
        // The loop starts at the very first instruction: it is a jump target and carries a frame.
        game.method(Opcodes.ACC_PUBLIC, "spin", "(F)V", code -> {
            Label loop = new Label();
            Label again = new Label();
            code.visitLabel(loop);
            code.visitVarInsn(Opcodes.FLOAD, 1);
            code.visitInsn(Opcodes.FCONST_0);
            code.visitInsn(Opcodes.FCMPG);
            code.visitJumpInsn(Opcodes.IFGT, again);
            SyntheticClass.record(code, "spun");
            code.visitInsn(Opcodes.RETURN);
            code.visitLabel(again);
            code.visitVarInsn(Opcodes.FLOAD, 1);
            code.visitInsn(Opcodes.FCONST_1);
            code.visitInsn(Opcodes.FSUB);
            code.visitVarInsn(Opcodes.FSTORE, 1);
            code.visitJumpInsn(Opcodes.GOTO, loop);
        });
    }

    private static void addParameterUsers(SyntheticClass game) {
        game.method(Opcodes.ACC_PUBLIC, "open", "(Ljava/lang/String;)V", code -> {
            FakeGame.storeFirstParameter(code, "shown", "Ljava/lang/String;");
            FakeGame.recordAndReturn(code, "open");
        });
        game.method(Opcodes.ACC_PUBLIC, "complete", "([Ljava/lang/String;)V", code -> {
            FakeGame.storeFirstParameter(code, "completions", "[Ljava/lang/String;");
            code.visitInsn(Opcodes.RETURN);
        });
        game.method(Opcodes.ACC_PUBLIC, "play", "(Ljava/lang/CharSequence;)V", code -> {
            FakeGame.storeFirstParameter(code, "played", "Ljava/lang/CharSequence;");
            FakeGame.recordAndReturn(code, "play");
        });
    }

    private static void addValueProducers(SyntheticClass game) {
        game.method(Opcodes.ACC_PUBLIC, "fov", "()F", code -> {
            code.visitInsn(Opcodes.FCONST_1);
            code.visitInsn(Opcodes.FRETURN);
        });
        game.method(Opcodes.ACC_PUBLIC, "tooltip", "(Ljava/lang/Object;Z)Ljava/util/List;", code -> {
            code.visitTypeInsn(Opcodes.NEW, "java/util/ArrayList");
            code.visitInsn(Opcodes.DUP);
            code.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/ArrayList", "<init>", "()V", false);
            code.visitInsn(Opcodes.ARETURN);
        });
    }

    private static void addAnchoredBodies(SyntheticClass game) {
        game.method(Opcodes.ACC_PUBLIC, "start", "()V", code -> {
            SyntheticClass.record(code, "before");
            code.visitVarInsn(Opcodes.ALOAD, 0);
            code.visitMethodInsn(Opcodes.INVOKEVIRTUAL, FakeGame.NAME, "refresh", "()V", false);
            SyntheticClass.record(code, "between");
            code.visitTypeInsn(Opcodes.NEW, FakeGame.STRING_BUILDER);
            code.visitInsn(Opcodes.DUP);
            code.visitMethodInsn(Opcodes.INVOKESPECIAL, FakeGame.STRING_BUILDER, "<init>", "()V", false);
            code.visitInsn(Opcodes.POP);
            FakeGame.recordAndReturn(code, "after");
        });
        game.method(Opcodes.ACC_PUBLIC, "world", "(IFJ)V", code -> {
            SyntheticClass.record(code, "sky");
            FakeGame.recordAndReturn(code, "hand");
        });
        // The choice flag ? "x" : "y" happens between the NEW and the constructor call: the frames
        // of both branches designate the not yet initialized object by the NEW label.
        game.method(Opcodes.ACC_PUBLIC, "risky", "(Z)V", code -> {
            Label otherwise = new Label();
            Label construct = new Label();
            code.visitTypeInsn(Opcodes.NEW, FakeGame.STRING_BUILDER);
            code.visitInsn(Opcodes.DUP);
            code.visitVarInsn(Opcodes.ILOAD, 1);
            code.visitJumpInsn(Opcodes.IFEQ, otherwise);
            code.visitLdcInsn("x");
            code.visitJumpInsn(Opcodes.GOTO, construct);
            code.visitLabel(otherwise);
            code.visitLdcInsn("y");
            code.visitLabel(construct);
            code.visitMethodInsn(
                    Opcodes.INVOKESPECIAL, FakeGame.STRING_BUILDER, "<init>", "(Ljava/lang/String;)V", false);
            code.visitInsn(Opcodes.POP);
            code.visitInsn(Opcodes.RETURN);
        });
    }

    private static void addInputLoops(SyntheticClass game) {
        game.method(Opcodes.ACC_PUBLIC, "poll", "()I", FakeGame::countInputs);
        game.method(Opcodes.ACC_PUBLIC, "pollGui", "()I", FakeGame::countInputs);
    }

    /** Imitates a call that Lunar's mixins moved into a static synthetic method. */
    private static void addMovedCallSite(SyntheticClass game) {
        String lambdaDescriptor = "(L" + FakeGame.NAME + ";IIF)V";
        game.method(Opcodes.ACC_PUBLIC, "frame", "(IIF)V", code -> {
            code.visitVarInsn(Opcodes.ALOAD, 0);
            FakeGame.loadDrawArguments(code, 1);
            code.visitMethodInsn(Opcodes.INVOKESTATIC, FakeGame.NAME, "lambda$frame$0", lambdaDescriptor, false);
            code.visitInsn(Opcodes.RETURN);
        });
        int privateStaticSynthetic = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC;
        game.method(privateStaticSynthetic, "lambda$frame$0", lambdaDescriptor, code -> {
            code.visitVarInsn(Opcodes.ALOAD, 0);
            FakeGame.loadDrawArguments(code, 1);
            code.visitMethodInsn(Opcodes.INVOKEVIRTUAL, FakeGame.NAME, "draw", "(IIF)V", false);
            code.visitInsn(Opcodes.RETURN);
        });
    }

    private static void recordAndReturn(MethodVisitor code, String event) {
        SyntheticClass.record(code, event);
        code.visitInsn(Opcodes.RETURN);
    }

    /** Two-exit body: {@code if (early) { record("early"); return; } record("late");}. */
    private static void earlyOrLate(MethodVisitor code) {
        Label late = new Label();
        code.visitVarInsn(Opcodes.ILOAD, 1);
        code.visitJumpInsn(Opcodes.IFEQ, late);
        FakeGame.recordAndReturn(code, "early");
        code.visitLabel(late);
        FakeGame.recordAndReturn(code, "late");
    }

    /**
     * Body {@code int n = 0; while (GameTrace.next()) { n++; } return n;}: the loop head is the
     * call itself, which therefore carries a frame.
     */
    private static void countInputs(MethodVisitor code) {
        Label loop = new Label();
        Label done = new Label();
        code.visitInsn(Opcodes.ICONST_0);
        code.visitVarInsn(Opcodes.ISTORE, 1);
        code.visitLabel(loop);
        code.visitMethodInsn(Opcodes.INVOKESTATIC, FakeGame.TRACE, "next", "()Z", false);
        code.visitJumpInsn(Opcodes.IFEQ, done);
        code.visitIincInsn(1, 1);
        code.visitJumpInsn(Opcodes.GOTO, loop);
        code.visitLabel(done);
        code.visitVarInsn(Opcodes.ILOAD, 1);
        code.visitInsn(Opcodes.IRETURN);
    }

    private static void storeFirstParameter(MethodVisitor code, String field, String descriptor) {
        code.visitVarInsn(Opcodes.ALOAD, 0);
        code.visitVarInsn(Opcodes.ALOAD, 1);
        code.visitFieldInsn(Opcodes.PUTFIELD, FakeGame.NAME, field, descriptor);
    }

    /** Loads the parameters {@code (int x, int y, float ticks)} stored from the given variable. */
    private static void loadDrawArguments(MethodVisitor code, int firstSlot) {
        code.visitVarInsn(Opcodes.ILOAD, firstSlot);
        code.visitVarInsn(Opcodes.ILOAD, firstSlot + 1);
        code.visitVarInsn(Opcodes.FLOAD, firstSlot + 2);
    }
}
