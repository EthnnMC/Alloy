package dev.alloy.hooks.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Tests the checker itself, since a check that never reports anything proves nothing: each test
 * builds a class with one specific defect and expects it to be found.
 */
class BytecodeChecksTest {

    private static final String NAME = "fake/checks/Sample";

    @Test
    void healthyClassHasNoProblem() {
        Object[] entryFrame = {BytecodeChecksTest.NAME, "java/lang/String"};
        byte[] healthy = BytecodeChecksTest.classWithResumeFrame(entryFrame);

        assertEquals(List.of(), BytecodeChecksTest.problemsOf(healthy));
    }

    @Test
    void frameAnnouncingTheWrongKindOfValueIsReported() {
        // The parameter is a String, the frame claims it is an int.
        Object[] wrongFrame = {BytecodeChecksTest.NAME, Opcodes.INTEGER};
        byte[] broken = BytecodeChecksTest.classWithResumeFrame(wrongFrame);

        assertFalse(BytecodeChecksTest.problemsOf(broken).isEmpty());
    }

    @Test
    void frameAnnouncingAnUnrelatedClassIsReported() {
        // Both are references: only the analysis with the real types sees the difference.
        Object[] wrongFrame = {BytecodeChecksTest.NAME, "java/lang/StringBuilder"};
        byte[] broken = BytecodeChecksTest.classWithResumeFrame(wrongFrame);

        List<String> problems = BytecodeChecksTest.problemsOf(broken);

        assertTrue(problems.stream().anyMatch(problem -> problem.contains("declares local 1")), problems.toString());
    }

    @Test
    void missingFrameAtAJumpTargetIsReported() {
        byte[] broken = BytecodeChecksTest.classWithResumeFrame(null);

        assertFalse(BytecodeChecksTest.problemsOf(broken).isEmpty());
    }

    @Test
    void valueOfTheWrongTypeGivenToAMethodIsReported() {
        ClassWriter writer = BytecodeChecksTest.newClass();
        MethodVisitor code = writer.visitMethod(Opcodes.ACC_PUBLIC, "length", "(Ljava/lang/Object;)I", null, null);
        code.visitCode();
        // String.length() called on an Object: the JVM would reject it.
        code.visitVarInsn(Opcodes.ALOAD, 1);
        code.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
        code.visitInsn(Opcodes.IRETURN);
        code.visitMaxs(1, 2);
        code.visitEnd();
        writer.visitEnd();

        assertFalse(BytecodeChecksTest.problemsOf(writer.toByteArray()).isEmpty());
    }

    private static List<String> problemsOf(byte[] classBytes) {
        return BytecodeChecks.problems(classBytes, new ClassHierarchy().withClass(classBytes).withSystemResources());
    }

    /**
     * Builds a Java 17 class whose {@code check(String)} jumps over a {@code return} (the exact
     * shape of a head cancellation) with the given frame at the jump target, or none if
     * {@code frameLocals} is {@code null}.
     */
    private static byte[] classWithResumeFrame(Object[] frameLocals) {
        ClassWriter writer = BytecodeChecksTest.newClass();
        MethodVisitor code = writer.visitMethod(Opcodes.ACC_PUBLIC, "check", "(Ljava/lang/String;)V", null, null);
        Label resumePoint = new Label();
        code.visitCode();
        code.visitVarInsn(Opcodes.ALOAD, 1);
        code.visitJumpInsn(Opcodes.IFNONNULL, resumePoint);
        code.visitInsn(Opcodes.RETURN);
        code.visitLabel(resumePoint);
        if (frameLocals != null) {
            code.visitFrame(Opcodes.F_NEW, frameLocals.length, frameLocals, 0, new Object[0]);
        }
        code.visitVarInsn(Opcodes.ALOAD, 1);
        code.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
        code.visitInsn(Opcodes.POP);
        code.visitInsn(Opcodes.RETURN);
        code.visitMaxs(1, 2);
        code.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    /** Starts a class written with no automatic computation: what is wrong stays wrong. */
    private static ClassWriter newClass() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, BytecodeChecksTest.NAME, null,
                "java/lang/Object", null);
        return writer;
    }
}
