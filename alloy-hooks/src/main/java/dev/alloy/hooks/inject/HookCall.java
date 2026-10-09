package dev.alloy.hooks.inject;

import dev.alloy.bridge.GameHooks;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * A call to a static {@link GameHooks} method as the injected code writes it: load the
 * arguments, then {@code INVOKESTATIC}. Hooks declare {@code Object} for game types, so their
 * results need a {@code CHECKCAST} before going into a typed variable.
 *
 * @param name       name of the {@code GameHooks} method
 * @param descriptor its descriptor, for example {@code (Ljava/lang/Object;)Z}
 * @param arguments  values the injected code loads itself, in parameter order
 */
public record HookCall(String name, String descriptor, List<HookArgument> arguments) {

    private static final Type OBJECT_TYPE = Type.getType(Object.class);

    public HookCall {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(descriptor, "descriptor");
        arguments = List.copyOf(arguments);
    }

    /**
     * Creates a call, for example
     * {@code HookCall.to("playerTickStart", "(Ljava/lang/Object;)V", HookArgument.self())}.
     */
    public static HookCall to(String name, String descriptor, HookArgument... arguments) {
        return new HookCall(name, descriptor, List.of(arguments));
    }

    /** Returns the hook's return type. */
    public Type returnType() {
        return Type.getReturnType(this.descriptor);
    }

    /**
     * Returns, in order, the types the arguments push onto the stack.
     *
     * @throws InjectionException if the method cannot supply one of the arguments
     */
    public List<Type> loadedTypes(TargetMethod method) throws InjectionException {
        List<Type> types = new ArrayList<>();
        for (HookArgument argument : this.arguments) {
            types.add(argument.typeIn(method));
        }
        return types;
    }

    /**
     * Checks that the hook accepts, in this order, the values on the stack at call time.
     *
     * @param operands types of the values on the stack
     * @throws InjectionException if their count or types do not fit
     */
    public void requireAccepts(List<Type> operands) throws InjectionException {
        Type[] parameters = Type.getArgumentTypes(this.descriptor);
        boolean fits = parameters.length == operands.size();
        for (int i = 0; fits && i < parameters.length; i++) {
            fits = HookCall.accepts(parameters[i], operands.get(i));
        }
        if (!fits) {
            throw new InjectionException("hook " + this.signature() + " cannot receive the operands " + operands);
        }
    }

    /**
     * Checks that the hook returns exactly the expected type.
     *
     * @throws InjectionException if the return type differs
     */
    public void requireReturns(Type expected) throws InjectionException {
        if (!this.returnType().equals(expected)) {
            throw new InjectionException("hook " + this.signature() + " must return " + expected);
        }
    }

    /**
     * Checks that the hook's result fits a slot of the given type: either the exact type, or two
     * references bridged by a {@code CHECKCAST} (see {@link #appendCastTo(Type, InsnList)}).
     *
     * @throws InjectionException if no conversion is possible
     */
    public void requireReturnsValueFor(Type wanted) throws InjectionException {
        Type produced = this.returnType();
        boolean castable = HookCall.isReference(produced) && HookCall.isReference(wanted);
        if (!produced.equals(wanted) && !castable) {
            throw new InjectionException("hook " + this.signature() + " cannot produce a value of type " + wanted);
        }
    }

    /** Appends a {@code CHECKCAST} to {@code wanted} when the hook's return type differs. */
    public void appendCastTo(Type wanted, InsnList code) {
        if (!this.returnType().equals(wanted)) {
            // For an array, ASM returns its descriptor here, which is what CHECKCAST expects.
            code.add(new TypeInsnNode(Opcodes.CHECKCAST, wanted.getInternalName()));
        }
    }

    /**
     * Builds the call: argument loads, then {@code INVOKESTATIC}. Returns a fresh list each time
     * because ASM empties a list when inserting it elsewhere.
     */
    public InsnList newInstructions(TargetMethod method) {
        InsnList code = new InsnList();
        for (HookArgument argument : this.arguments) {
            argument.load(method, code);
        }
        code.add(this.newInvokeInstruction());
        return code;
    }

    /** Creates the {@code INVOKESTATIC} instruction alone, without argument loads. */
    public MethodInsnNode newInvokeInstruction() {
        return new MethodInsnNode(Opcodes.INVOKESTATIC, GameHooks.INTERNAL_NAME, this.name, this.descriptor, false);
    }

    /** Describes the hook for logs, for example {@code GameHooks.guiOpen(Ljava/lang/Object;)Z}. */
    public String signature() {
        return "GameHooks." + this.name + this.descriptor;
    }

    /** Returns whether the type is an object or array (not a primitive or {@code void}). */
    public static boolean isReference(Type type) {
        return type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY;
    }

    /** A parameter accepts its exact type; {@code Object} accepts any reference. */
    private static boolean accepts(Type parameter, Type operand) {
        return parameter.equals(operand) || (HookCall.OBJECT_TYPE.equals(parameter) && HookCall.isReference(operand));
    }
}
