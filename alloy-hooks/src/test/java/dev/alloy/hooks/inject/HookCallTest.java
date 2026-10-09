package dev.alloy.hooks.inject;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

/** Tests the compatibility rules between a hook call and the values passed to it. */
class HookCallTest {

    private static final Type GUI_SCREEN = Type.getObjectType("net/minecraft/client/gui/GuiScreen");
    private static final Type STRING = Type.getType(String.class);

    @Test
    void objectParameterAcceptsAnyReference() {
        HookCall hook = HookCall.to("guiOpen", "(Ljava/lang/Object;)Z");

        assertDoesNotThrow(() -> hook.requireAccepts(List.of(HookCallTest.GUI_SCREEN)));
    }

    @Test
    void objectParameterAcceptsAnArray() {
        HookCall hook = HookCall.to("guiOpen", "(Ljava/lang/Object;)Z");

        assertDoesNotThrow(() -> hook.requireAccepts(List.of(Type.getType("[Ljava/lang/String;"))));
    }

    @Test
    void objectParameterRefusesAPrimitive() {
        HookCall hook = HookCall.to("guiOpen", "(Ljava/lang/Object;)Z");

        assertThrows(InjectionException.class, () -> hook.requireAccepts(List.of(Type.FLOAT_TYPE)));
    }

    @Test
    void typedParameterRefusesAnotherReferenceType() {
        HookCall hook = HookCall.to("overlayElementPre", "(Ljava/lang/String;)Z");

        assertThrows(InjectionException.class, () -> hook.requireAccepts(List.of(HookCallTest.GUI_SCREEN)));
    }

    @Test
    void primitiveParameterNeedsTheExactType() {
        HookCall hook = HookCall.to("overlayPre", "(F)Z");

        assertThrows(InjectionException.class, () -> hook.requireAccepts(List.of(Type.DOUBLE_TYPE)));
    }

    @Test
    void operandCountMustMatch() {
        HookCall hook = HookCall.to("entityJoinWorld", "(Ljava/lang/Object;Ljava/lang/Object;)Z");

        assertThrows(InjectionException.class, () -> hook.requireAccepts(List.of(HookCallTest.GUI_SCREEN)));
    }

    @Test
    void returnTypeMustBeTheExpectedOne() {
        HookCall hook = HookCall.to("clientTickStart", "()V");

        assertThrows(InjectionException.class, () -> hook.requireReturns(Type.BOOLEAN_TYPE));
    }

    @Test
    void referenceResultCanReplaceAnotherReferenceType() {
        HookCall hook = HookCall.to("guiOpenResult", "()Ljava/lang/Object;");

        assertDoesNotThrow(() -> hook.requireReturnsValueFor(HookCallTest.GUI_SCREEN));
    }

    @Test
    void referenceResultCannotReplaceAPrimitive() {
        HookCall hook = HookCall.to("guiOpenResult", "()Ljava/lang/Object;");

        assertThrows(InjectionException.class, () -> hook.requireReturnsValueFor(Type.INT_TYPE));
    }

    @Test
    void castIsAddedWhenTheHookReturnsALooserType() {
        HookCall hook = HookCall.to("guiOpenResult", "()Ljava/lang/Object;");
        InsnList code = new InsnList();

        hook.appendCastTo(HookCallTest.GUI_SCREEN, code);

        TypeInsnNode cast = (TypeInsnNode) code.getFirst();
        assertEquals(Opcodes.CHECKCAST, cast.getOpcode());
        assertEquals("net/minecraft/client/gui/GuiScreen", cast.desc);
    }

    @Test
    void noCastIsAddedWhenTheTypesAreIdentical() {
        HookCall hook = HookCall.to("overlayElementPost", "()Ljava/lang/String;");
        InsnList code = new InsnList();

        hook.appendCastTo(HookCallTest.STRING, code);

        assertEquals(0, code.size());
    }

    @Test
    void invokeInstructionTargetsGameHooks() {
        MethodInsnNode call = HookCall.to("clientTickStart", "()V").newInvokeInstruction();

        assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
        assertEquals("dev/alloy/bridge/GameHooks.clientTickStart()V", call.owner + "." + call.name + call.desc);
    }
}
