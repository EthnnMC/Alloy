package dev.alloy.hooks.inject;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Tests what {@link TargetMethod} deduces from a method declaration: the position of the
 * parameters in the local variables and the frame of the resume point.
 */
class TargetMethodTest {

    private static final String OWNER = "net/minecraft/client/renderer/entity/RenderPlayer";
    private static final String DO_RENDER = "(Lnet/minecraft/client/entity/AbstractClientPlayer;DDDFF)V";

    @Test
    void instanceMethodKeepsSlotZeroForThis() {
        TargetMethod method = TargetMethodTest.method(Opcodes.ACC_PUBLIC, "doRender", TargetMethodTest.DO_RENDER, 61);

        assertEquals(1, method.parameterSlot(1));
    }

    @Test
    void wideParametersTakeTwoSlots() {
        TargetMethod method = TargetMethodTest.method(Opcodes.ACC_PUBLIC, "doRender", TargetMethodTest.DO_RENDER, 61);

        // this, the entity, then three doubles of two slots each: the first float is in slot 8.
        assertEquals(List.of(2, 4, 6, 8, 9), List.of(method.parameterSlot(2), method.parameterSlot(3),
                method.parameterSlot(4), method.parameterSlot(5), method.parameterSlot(6)));
    }

    @Test
    void staticMethodStartsAtSlotZero() {
        TargetMethod method = TargetMethodTest.method(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "main", "([Ljava/lang/String;)V", 61);

        assertEquals(0, method.parameterSlot(1));
    }

    @Test
    void parameterTypesAreReadFromTheDescriptor() {
        TargetMethod method = TargetMethodTest.method(Opcodes.ACC_PUBLIC, "doRender", TargetMethodTest.DO_RENDER, 61);

        assertEquals(6, method.parameterCount());
        assertEquals(Type.FLOAT_TYPE, method.parameterType(6));
    }

    @Test
    void resumePointCarriesTheEntryFrameOfTheMethod() {
        TargetMethod method = TargetMethodTest.method(Opcodes.ACC_PUBLIC, "doRender", TargetMethodTest.DO_RENDER, 61);
        InsnList code = new InsnList();

        method.appendResumePoint(code, new LabelNode(), true);

        FrameNode frame = (FrameNode) code.getLast();
        assertEquals(Opcodes.F_NEW, frame.type);
        assertEquals(
                List.of(TargetMethodTest.OWNER, "net/minecraft/client/entity/AbstractClientPlayer",
                        Opcodes.DOUBLE, Opcodes.DOUBLE, Opcodes.DOUBLE, Opcodes.FLOAT, Opcodes.FLOAT),
                frame.local);
        assertEquals(List.of(), frame.stack);
    }

    @Test
    void entryFrameOfAStaticMethodHasNoThis() {
        TargetMethod method = TargetMethodTest.method(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "setKeyBindState", "(IZ)V", 61);
        InsnList code = new InsnList();

        method.appendResumePoint(code, new LabelNode(), true);

        // No leading this; boolean and int are the same type for the verifier.
        assertEquals(List.of(Opcodes.INTEGER, Opcodes.INTEGER), ((FrameNode) code.getLast()).local);
    }

    @Test
    void entryFrameDescribesAnArrayByItsDescriptor() {
        TargetMethod method = TargetMethodTest.method(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "main", "([Ljava/lang/String;)V", 61);
        InsnList code = new InsnList();

        method.appendResumePoint(code, new LabelNode(), true);

        assertEquals(List.of("[Ljava/lang/String;"), ((FrameNode) code.getLast()).local);
    }

    @Test
    void noFrameIsAddedWhenTheOriginalCodeAlreadyStartsWithOne() {
        TargetMethod method = TargetMethodTest.method(Opcodes.ACC_PUBLIC, "spin", "(F)V", 61);
        // Body starting with a jump target: label, frame, then the first instruction.
        method.instructions().insert(new FrameNode(Opcodes.F_NEW, 0, new Object[0], 0, new Object[0]));
        method.instructions().insert(new LabelNode());
        InsnList code = new InsnList();
        LabelNode resumePoint = new LabelNode();

        method.appendResumePoint(code, resumePoint, true);

        assertEquals(1, code.size());
        assertEquals(resumePoint, code.getLast());
    }

    @Test
    void frameIsAddedAnywayWhenInjectedCodeFollowsTheResumePoint() {
        TargetMethod method = TargetMethodTest.method(Opcodes.ACC_PUBLIC, "spin", "(F)V", 61);
        method.instructions().insert(new FrameNode(Opcodes.F_NEW, 0, new Object[0], 0, new Object[0]));
        method.instructions().insert(new LabelNode());
        InsnList code = new InsnList();

        method.appendResumePoint(code, new LabelNode(), false);

        assertTrue(code.getLast() instanceof FrameNode);
    }

    @Test
    void noFrameIsAddedToAClassOlderThanJavaSix() {
        TargetMethod method = TargetMethodTest.method(Opcodes.ACC_PUBLIC, "runTick", "()V", Opcodes.V1_5);
        InsnList code = new InsnList();

        method.appendResumePoint(code, new LabelNode(), true);

        assertFalse(code.getLast() instanceof FrameNode);
    }

    @Test
    void constructorIsRefusedByTheStrategiesThatNeedThis() {
        TargetMethod constructor = TargetMethodTest.method(Opcodes.ACC_PUBLIC, "<init>", "()V", 61);

        assertThrows(InjectionException.class, () -> constructor.requireNotConstructor("a head injection"));
    }

    @Test
    void ordinaryMethodIsNotMistakenForAConstructor() {
        TargetMethod method = TargetMethodTest.method(Opcodes.ACC_PUBLIC, "runTick", "()V", 61);

        assertDoesNotThrow(() -> method.requireNotConstructor("a head injection"));
    }

    @Test
    void methodWithoutInstructionsHasNoCode() {
        int publicAbstract = Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT;
        MethodNode abstractMethod = new MethodNode(publicAbstract, "render", "()V", null, null);

        assertFalse(new TargetMethod(TargetMethodTest.OWNER, 61, abstractMethod).hasCode());
    }

    /** Builds a method whose body is just {@code return;}. */
    private static TargetMethod method(int access, String name, String descriptor, int classVersion) {
        MethodNode node = new MethodNode(access, name, descriptor, null, null);
        AbstractInsnNode onlyInstruction = new InsnNode(Opcodes.RETURN);
        node.instructions.add(onlyInstruction);
        return new TargetMethod(TargetMethodTest.OWNER, classVersion, node);
    }
}
