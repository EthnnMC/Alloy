package dev.alloy.hooks.testing;

import java.util.function.Consumer;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Small builder of synthetic test classes: a name, a class file version, and methods whose
 * bodies are written instruction by instruction.
 *
 * <p>ASM computes the frames here ({@code COMPUTE_FRAMES}) because these classes only use JDK
 * types; Alloy's own code never does.</p>
 */
public final class SyntheticClass {

    private final String internalName;
    private final ClassWriter writer;

    /** Starts a public class extending {@code Object}; version 50 is Java 6, 61 is Java 17. */
    public SyntheticClass(String internalName, int classVersion) {
        this(internalName, classVersion, "java/lang/Object");
    }

    /** Starts a public class with the given class file version and superclass internal name. */
    public SyntheticClass(String internalName, int classVersion, String superName) {
        this.internalName = internalName;
        this.writer = new FrameComputingWriter();
        this.writer.visit(classVersion, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, internalName, null, superName, null);
    }

    /** Returns the internal name of the class being built. */
    public String internalName() {
        return this.internalName;
    }

    /** Adds a public field. */
    public SyntheticClass field(String name, String descriptor) {
        this.writer.visitField(Opcodes.ACC_PUBLIC, name, descriptor, null, null).visitEnd();
        return this;
    }

    /** Adds the public no-argument constructor, which only calls {@code Object}'s. */
    public SyntheticClass defaultConstructor() {
        return this.method(Opcodes.ACC_PUBLIC, "<init>", "()V", code -> {
            code.visitVarInsn(Opcodes.ALOAD, 0);
            code.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            code.visitInsn(Opcodes.RETURN);
        });
    }

    /** Adds a method; {@code body} writes its instructions, return included. */
    public SyntheticClass method(int access, String name, String descriptor, Consumer<MethodVisitor> body) {
        MethodVisitor code = this.writer.visitMethod(access, name, descriptor, null, null);
        code.visitCode();
        body.accept(code);
        // With COMPUTE_FRAMES, ASM ignores these two values and computes the real ones.
        code.visitMaxs(0, 0);
        code.visitEnd();
        return this;
    }

    /** Finishes the class and returns its bytecode. */
    public byte[] toByteArray() {
        this.writer.visitEnd();
        return this.writer.toByteArray();
    }

    /** Writes the {@code GameTrace.record(event)} call: the mark the original code leaves in the test log. */
    public static void record(MethodVisitor code, String event) {
        code.visitLdcInsn(event);
        code.visitMethodInsn(Opcodes.INVOKESTATIC, SyntheticClass.internalNameOf(GameTrace.class),
                "record", "(Ljava/lang/String;)V", false);
    }

    /** Returns the slash-separated internal name of a loaded class. */
    public static String internalNameOf(Class<?> type) {
        return type.getName().replace('.', '/');
    }

    /** Writer that computes frames without ever loading the synthetic class itself. */
    private static final class FrameComputingWriter extends ClassWriter {

        FrameComputingWriter() {
            super(ClassWriter.COMPUTE_FRAMES);
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            // Test classes never merge two different object types at a join point.
            return "java/lang/Object";
        }
    }
}
