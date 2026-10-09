package dev.alloy.remap.transform;

import dev.alloy.remap.hierarchy.ClassHierarchy;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Completes Forge event classes, reproducing Forge's {@code EventSubscriptionTransformer}: every
 * subclass of {@code Event} gets the code below (methods already written by the class author are
 * never replaced).
 * <pre>
 *   private static ListenerList LISTENER_LIST;
 *   public Sub() { super(); }                               // only if absent
 *   protected void setup() {
 *       super.setup();
 *       if (LISTENER_LIST != null) { return; }
 *       LISTENER_LIST = new ListenerList(super.getListenerList());
 *   }
 *   public ListenerList getListenerList() { return LISTENER_LIST; }
 *   public boolean isCancelable() { return true; }          // if annotated @Cancelable
 *   public boolean hasResult()    { return true; }          // if annotated @Event.HasResult
 * </pre>
 *
 * <p>Differences from Forge: the {@code Event} ancestry comes from class headers
 * ({@link ClassHierarchy}) instead of loading the super-class, and the only needed stack map frame
 * (the {@code if}) is written by hand instead of recomputing all frames, which would load classes.</p>
 */
public final class EventClassPass implements ClassPass {

    private static final String LISTENER_FIELD = "LISTENER_LIST";
    private static final String CONSTRUCTOR = "<init>";
    private static final String SETUP = "setup";
    private static final String GET_LISTENER_LIST = "getListenerList";
    private static final String IS_CANCELABLE = "isCancelable";
    private static final String HAS_RESULT = "hasResult";

    private static final String NO_ARGS_VOID = "()V";
    private static final String NO_ARGS_BOOLEAN = "()Z";
    private static final String LIST_TYPE = "L" + ForgeNames.LISTENER_LIST + ";";
    private static final String LIST_GETTER = "()" + EventClassPass.LIST_TYPE;
    private static final String LIST_CONSTRUCTOR = "(" + EventClassPass.LIST_TYPE + ")V";

    /** Low 16 bits of ASM's {@code version} field: the major version. */
    private static final int MAJOR_VERSION_MASK = 0xFFFF;

    private static final int ANY_ACCESS = 0;

    private final ClassHierarchy hierarchy;
    private final Consumer<String> warnings;

    /**
     * @param hierarchy inheritance of Forge's classes and of the processed jar (a mod can declare
     *                  its own events, subclasses of a Forge event)
     * @param warnings  receives the anomalies found
     */
    public EventClassPass(ClassHierarchy hierarchy, Consumer<String> warnings) {
        this.hierarchy = Objects.requireNonNull(hierarchy, "hierarchy");
        this.warnings = Objects.requireNonNull(warnings, "warnings");
    }

    @Override
    public Verdict apply(ClassNode classNode) {
        if (this.isEventSubclass(classNode)) {
            this.complete(classNode);
        }
        return Verdict.KEEP;
    }

    /**
     * Applies Forge's exclusions (the {@code Event} class itself, Minecraft classes, classes without
     * a package), then looks for {@code Event} among the super-classes.
     */
    private boolean isEventSubclass(ClassNode classNode) {
        String name = classNode.name;
        if (name.equals(ForgeNames.EVENT) || name.startsWith(ForgeNames.MINECRAFT_PACKAGE) || name.indexOf('/') < 0
                || classNode.superName == null) {
            return false;
        }
        List<String> ancestors = this.hierarchy.superclassChain(classNode.superName);
        if (ancestors.contains(ForgeNames.EVENT)) {
            return true;
        }
        String oldestKnown = ancestors.get(ancestors.size() - 1);
        if (oldestKnown.startsWith(ForgeNames.FORGE_PACKAGE)) {
            this.warnings.accept("Cannot tell whether " + name + " is a Forge event: its ancestor " + oldestKnown
                    + " is not a known Forge class (was the Forge jar prepared or declared first?);"
                    + " class left unchanged");
        }
        return false;
    }

    private void complete(ClassNode classNode) {
        boolean hasSetup = EventClassPass.declares(classNode, EventClassPass.SETUP, EventClassPass.NO_ARGS_VOID,
                Opcodes.ACC_PROTECTED);
        boolean hasListGetter = EventClassPass.declares(classNode, EventClassPass.GET_LISTENER_LIST,
                EventClassPass.LIST_GETTER, Opcodes.ACC_PUBLIC);
        if (hasSetup && !hasListGetter) {
            // Forge throws here and catches it right away, keeping the original class.
            this.warnings.accept("Event class defines setup() but does not define getListenerList(): "
                    + classNode.name + " (left unchanged, as Forge does)");
            return;
        }
        EventClassPass.addAnnotationMethods(classNode);
        if (hasSetup) {
            // The class manages its own listener list: Forge adds nothing else.
            return;
        }
        classNode.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, EventClassPass.LISTENER_FIELD,
                EventClassPass.LIST_TYPE, null, null));
        if (!EventClassPass.declares(classNode, EventClassPass.CONSTRUCTOR, EventClassPass.NO_ARGS_VOID,
                EventClassPass.ANY_ACCESS)) {
            classNode.methods.add(EventClassPass.defaultConstructor(classNode));
        }
        classNode.methods.add(EventClassPass.setupMethod(classNode));
        if (hasListGetter) {
            // Forge would add a second identical method here, making the class invalid.
            this.warnings.accept("Event class " + classNode.name
                    + " already defines getListenerList() without setup(): its own method is kept");
        } else {
            classNode.methods.add(EventClassPass.listGetter(classNode));
        }
    }

    /** Adds {@code isCancelable()} and {@code hasResult()} from the annotations, unless already present. */
    private static void addAnnotationMethods(ClassNode classNode) {
        if (Annotations.isPresent(classNode.visibleAnnotations, ForgeNames.HAS_RESULT_ANNOTATION)
                && !EventClassPass.declares(classNode, EventClassPass.HAS_RESULT, EventClassPass.NO_ARGS_BOOLEAN,
                        Opcodes.ACC_PUBLIC)) {
            classNode.methods.add(EventClassPass.alwaysTrue(EventClassPass.HAS_RESULT));
        }
        if (Annotations.isPresent(classNode.visibleAnnotations, ForgeNames.CANCELABLE_ANNOTATION)
                && !EventClassPass.declares(classNode, EventClassPass.IS_CANCELABLE, EventClassPass.NO_ARGS_BOOLEAN,
                        Opcodes.ACC_PUBLIC)) {
            classNode.methods.add(EventClassPass.alwaysTrue(EventClassPass.IS_CANCELABLE));
        }
    }

    /**
     * Tells whether the class declares a method with this name and descriptor whose access
     * contains all the required flags.
     */
    private static boolean declares(ClassNode classNode, String name, String descriptor, int requiredAccess) {
        for (MethodNode method : classNode.methods) {
            if (method.name.equals(name) && method.desc.equals(descriptor)
                    && (method.access & requiredAccess) == requiredAccess) {
                return true;
            }
        }
        return false;
    }

    /** Builds {@code public boolean <name>() { return true; }}. */
    private static MethodNode alwaysTrue(String name) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, EventClassPass.NO_ARGS_BOOLEAN, null, null);
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.maxStack = 1;
        method.maxLocals = 1;
        return method;
    }

    /**
     * Builds {@code public <init>() { super(); }}: the event bus creates an instance of each event
     * class through it when a listener subscribes.
     */
    private static MethodNode defaultConstructor(ClassNode classNode) {
        MethodNode method = new MethodNode(
                Opcodes.ACC_PUBLIC, EventClassPass.CONSTRUCTOR, EventClassPass.NO_ARGS_VOID, null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, classNode.superName,
                EventClassPass.CONSTRUCTOR, EventClassPass.NO_ARGS_VOID, false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        method.maxStack = 1;
        method.maxLocals = 1;
        return method;
    }

    /** Builds the {@code setup()} method described in the class Javadoc. */
    private static MethodNode setupMethod(ClassNode classNode) {
        MethodNode method = new MethodNode(
                Opcodes.ACC_PROTECTED, EventClassPass.SETUP, EventClassPass.NO_ARGS_VOID, null, null);
        InsnList code = method.instructions;
        LabelNode createList = new LabelNode();
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, classNode.superName, EventClassPass.SETUP,
                EventClassPass.NO_ARGS_VOID, false));
        code.add(new FieldInsnNode(Opcodes.GETSTATIC, classNode.name, EventClassPass.LISTENER_FIELD,
                EventClassPass.LIST_TYPE));
        code.add(new JumpInsnNode(Opcodes.IFNULL, createList));
        code.add(new InsnNode(Opcodes.RETURN));
        code.add(createList);
        if (EventClassPass.needsStackMapFrames(classNode)) {
            // At this label the state is the method's start: same locals, empty stack.
            code.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        }
        code.add(new TypeInsnNode(Opcodes.NEW, ForgeNames.LISTENER_LIST));
        code.add(new InsnNode(Opcodes.DUP));
        code.add(new VarInsnNode(Opcodes.ALOAD, 0));
        code.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, classNode.superName, EventClassPass.GET_LISTENER_LIST,
                EventClassPass.LIST_GETTER, false));
        code.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, ForgeNames.LISTENER_LIST, EventClassPass.CONSTRUCTOR,
                EventClassPass.LIST_CONSTRUCTOR, false));
        code.add(new FieldInsnNode(Opcodes.PUTSTATIC, classNode.name, EventClassPass.LISTENER_FIELD,
                EventClassPass.LIST_TYPE));
        code.add(new InsnNode(Opcodes.RETURN));
        // At its peak the stack holds: the new list, its copy (DUP) and the super-class list.
        method.maxStack = 3;
        method.maxLocals = 1;
        return method;
    }

    /** Builds {@code public ListenerList getListenerList() { return LISTENER_LIST; }}. */
    private static MethodNode listGetter(ClassNode classNode) {
        MethodNode method = new MethodNode(
                Opcodes.ACC_PUBLIC, EventClassPass.GET_LISTENER_LIST, EventClassPass.LIST_GETTER, null, null);
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, classNode.name, EventClassPass.LISTENER_FIELD,
                EventClassPass.LIST_TYPE));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        method.maxStack = 1;
        method.maxLocals = 1;
        return method;
    }

    /**
     * Classes compiled for Java 6 or later are verified with stack map frames (mandatory from
     * Java 7); older classes have none.
     */
    private static boolean needsStackMapFrames(ClassNode classNode) {
        return (classNode.version & EventClassPass.MAJOR_VERSION_MASK) >= Opcodes.V1_6;
    }
}
