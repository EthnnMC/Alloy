package dev.alloy.hooks.inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * A game method being modified, with what injection strategies need to know: whether it is
 * static, where its parameters live, and the local-variable state at entry. A stack map "frame"
 * describes the type of each local and stack value at a point in the code; the JVM verifier
 * requires one at every jump target in recent classes.
 */
public final class TargetMethod {

    private static final String CONSTRUCTOR_NAME = "<init>";

    /** The low 16 bits of the class version are the major version; the rest is the minor version. */
    private static final int MAJOR_VERSION_MASK = 0xFFFF;

    /** Internal name of the class declaring the method. */
    private final String ownerInternalName;

    /** Class file version, as ASM reports it. */
    private final int classVersion;

    private final MethodNode node;

    /** Parameter types, in declaration order. */
    private final Type[] parameterTypes;

    /**
     * Creates the descriptor of a method to modify.
     *
     * @param classVersion class file version ({@code ClassNode.version})
     * @param node         the method as an ASM tree
     */
    public TargetMethod(String ownerInternalName, int classVersion, MethodNode node) {
        this.ownerInternalName = Objects.requireNonNull(ownerInternalName, "ownerInternalName");
        this.classVersion = classVersion;
        this.node = Objects.requireNonNull(node, "node");
        this.parameterTypes = Type.getArgumentTypes(node.desc);
    }

    /** Returns the internal name of the declaring class, for example {@code net/minecraft/client/Minecraft}. */
    public String ownerInternalName() {
        return this.ownerInternalName;
    }

    /** Returns the method's ASM instruction list, which strategies modify in place. */
    public InsnList instructions() {
        return this.node.instructions;
    }

    /** Returns whether the method is {@code static} (no {@code this}). */
    public boolean isStatic() {
        return (this.node.access & Opcodes.ACC_STATIC) != 0;
    }

    /** Returns whether the method is a constructor ({@code <init>}). */
    public boolean isConstructor() {
        return TargetMethod.CONSTRUCTOR_NAME.equals(this.node.name);
    }

    /**
     * Rejects constructors. Until the super constructor is called, {@code this} is uninitialized
     * and the JVM verifier forbids passing it anywhere, so the only safe injection there is at
     * return instructions ({@link ReturnCall}).
     *
     * @param strategy name of the asking strategy, for the error message
     * @throws InjectionException if the method is a constructor
     */
    public void requireNotConstructor(String strategy) throws InjectionException {
        if (this.isConstructor()) {
            throw new InjectionException(
                    strategy + " is not allowed in constructor " + this.describe()
                            + ": only RETURN_CALL is, once 'this' is initialised");
        }
    }

    /** Returns whether the method has a body (abstract and native methods do not). */
    public boolean hasCode() {
        return this.node.instructions.size() > 0;
    }

    /** Returns the return type ({@link Type#VOID_TYPE} for {@code void}). */
    public Type returnType() {
        return Type.getReturnType(this.node.desc);
    }

    /** Returns the number of declared parameters, not counting {@code this}. */
    public int parameterCount() {
        return this.parameterTypes.length;
    }

    /**
     * Returns the type of a parameter.
     *
     * @param position parameter index, starting at 1 as in the catalog
     */
    public Type parameterType(int position) {
        return this.parameterTypes[position - 1];
    }

    /**
     * Returns the local variable slot holding a parameter. It is not the index: {@code this}
     * takes slot 0 of instance methods, and a {@code long} or {@code double} takes two slots.
     *
     * @param position parameter index, starting at 1
     */
    public int parameterSlot(int position) {
        int slot = this.isStatic() ? 0 : 1;
        for (int i = 0; i < position - 1; i++) {
            slot += this.parameterTypes[i].getSize();
        }
        return slot;
    }

    /**
     * Appends to head code its resume point: the label it jumps to when it does not leave the
     * method, followed by the frame a jump target needs in a recent class. This is the only frame
     * Alloy adds, and it needs no computation: the injected code leaves the stack empty and
     * changes no variable type, so the state equals the method-entry state, read from its
     * declaration ({@code this}, then the parameters).
     *
     * <p>No frame is added if the original code follows and its first instruction already has
     * one (the body starts with a loop): two frames at the same spot would make the class invalid.</p>
     *
     * @param code                the head code being built
     * @param originalCodeFollows {@code true} if the original code comes right after the label
     */
    public void appendResumePoint(InsnList code, LabelNode resumePoint, boolean originalCodeFollows) {
        code.add(resumePoint);
        boolean frameAlreadyThere = originalCodeFollows && this.startsWithFrame();
        if (this.usesStackMapFrames() && !frameAlreadyThere) {
            code.add(this.newInitialFrame());
        }
    }

    /** Describes the method for logs, for example {@code net/minecraft/client/Minecraft.runTick()V}. */
    public String describe() {
        return this.ownerInternalName + "." + this.node.name + this.node.desc;
    }

    /** Returns whether the JVM verifier reads frames for this class (Java 6+; mandatory from Java 7). */
    private boolean usesStackMapFrames() {
        return (this.classVersion & TargetMethod.MAJOR_VERSION_MASK) >= Opcodes.V1_6;
    }

    /** Returns whether a frame already precedes the method's first real instruction. */
    private boolean startsWithFrame() {
        // Labels, line numbers and frames are pseudo-instructions: their opcode is -1.
        AbstractInsnNode current = this.node.instructions.getFirst();
        while (current != null && current.getOpcode() < 0) {
            if (current instanceof FrameNode) {
                return true;
            }
            current = current.getNext();
        }
        return false;
    }

    /** Builds the method-entry frame in full format ({@code F_NEW}). */
    private FrameNode newInitialFrame() {
        List<Object> locals = new ArrayList<>();
        if (!this.isStatic()) {
            locals.add(this.isConstructor() ? Opcodes.UNINITIALIZED_THIS : this.ownerInternalName);
        }
        for (Type parameterType : this.parameterTypes) {
            locals.add(TargetMethod.frameElement(parameterType));
        }
        return new FrameNode(Opcodes.F_NEW, locals.size(), locals.toArray(), 0, new Object[0]);
    }

    /**
     * Translates a parameter type to frame vocabulary: the verifier does not distinguish
     * {@code boolean}, {@code byte}, {@code char}, {@code short} and {@code int}.
     */
    private static Object frameElement(Type type) {
        return switch (type.getSort()) {
            case Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT -> Opcodes.INTEGER;
            case Type.FLOAT -> Opcodes.FLOAT;
            case Type.LONG -> Opcodes.LONG;
            case Type.DOUBLE -> Opcodes.DOUBLE;
            // Objects and arrays: for an array, ASM returns its descriptor here, as a frame requires.
            default -> type.getInternalName();
        };
    }
}
