package dev.alloy.hooks.testing;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.util.CheckClassAdapter;

/**
 * Verifies bytecode without loading it: ASM's {@link CheckClassAdapter} (structure, stack sizes,
 * frames), then a data-flow analysis with the types of a {@link ClassHierarchy}, then a comparison
 * of every written frame with the state the analysis computed at the same place.
 *
 * <p>The result is a list of problems, empty when all is well. For a real class, compare with the
 * list from before modification: only a <em>new</em> problem is Alloy's doing.</p>
 */
public final class BytecodeChecks {

    private BytecodeChecks() {
    }

    /** Returns the problems found in a class, one text per problem; empty if the class is sound. */
    public static List<String> problems(byte[] classBytes, ClassHierarchy hierarchy) {
        List<String> problems = new ArrayList<>();
        BytecodeChecks.checkStructureAndFrames(classBytes, problems);

        ClassNode classNode = new ClassNode();
        new ClassReader(classBytes).accept(classNode, ClassReader.EXPAND_FRAMES);
        HierarchyVerifier verifier = new HierarchyVerifier(hierarchy);
        for (MethodNode method : classNode.methods) {
            String where = classNode.name + "." + method.name + method.desc;
            Analyzer<BasicValue> analyzer = new Analyzer<>(verifier);
            try {
                Frame<BasicValue>[] computed = analyzer.analyze(classNode.name, method);
                BytecodeChecks.compareFrames(where, method, computed, verifier, problems);
            } catch (AnalyzerException e) {
                problems.add(where + ": " + e.getMessage());
            }
        }
        return problems;
    }

    /** First pass: ASM's checker, plugged into a writer that computes nothing so that it checks everything. */
    private static void checkStructureAndFrames(byte[] classBytes, List<String> problems) {
        try {
            new ClassReader(classBytes).accept(new CheckClassAdapter(new ClassWriter(0), true), 0);
        } catch (RuntimeException e) {
            // The checker appends the whole method listing to its message: the first line is enough.
            problems.add("CheckClassAdapter: " + String.valueOf(e.getMessage()).lines().findFirst().orElse(""));
        }
    }

    /** Third pass: every written frame must be satisfied by the state computed at the same place. */
    private static void compareFrames(
            String where,
            MethodNode method,
            Frame<BasicValue>[] computed,
            HierarchyVerifier verifier,
            List<String> problems) {
        AbstractInsnNode[] instructions = method.instructions.toArray();
        for (int index = 0; index < instructions.length; index++) {
            Frame<BasicValue> actual = computed[index];
            // No computed state: the instruction is unreachable, nothing to compare.
            if (instructions[index] instanceof FrameNode declared && actual != null) {
                String mismatch = BytecodeChecks.mismatch(declared, actual, verifier);
                if (mismatch != null) {
                    problems.add(where + ": frame at instruction " + index + " " + mismatch);
                }
            }
        }
    }

    private static String mismatch(FrameNode declared, Frame<BasicValue> actual, HierarchyVerifier verifier) {
        int slot = 0;
        for (Object element : declared.local) {
            BasicValue value = slot < actual.getLocals() ? actual.getLocal(slot) : null;
            if (!BytecodeChecks.satisfies(value, element, verifier)) {
                return "declares local " + slot + " as " + element + " but the code provides " + value;
            }
            // A long or double takes two local variable slots but only one frame entry.
            slot += Opcodes.LONG.equals(element) || Opcodes.DOUBLE.equals(element) ? 2 : 1;
        }
        if (declared.stack.size() != actual.getStackSize()) {
            return "declares " + declared.stack.size() + " stack value(s) but the code provides "
                    + actual.getStackSize();
        }
        for (int depth = 0; depth < declared.stack.size(); depth++) {
            if (!BytecodeChecks.satisfies(actual.getStack(depth), declared.stack.get(depth), verifier)) {
                return "declares stack value " + depth + " as " + declared.stack.get(depth)
                        + " but the code provides " + actual.getStack(depth);
            }
        }
        return null;
    }

    /** Tells whether the computed value fits what a frame element declares. */
    private static boolean satisfies(BasicValue value, Object declared, HierarchyVerifier verifier) {
        if (Opcodes.TOP.equals(declared)) {
            return true;
        }
        if (value == null || value.getType() == null) {
            return false;
        }
        Type type = value.getType();
        if (Opcodes.INTEGER.equals(declared)) {
            return type.getSort() >= Type.BOOLEAN && type.getSort() <= Type.INT;
        }
        if (Opcodes.FLOAT.equals(declared)) {
            return type.getSort() == Type.FLOAT;
        }
        if (Opcodes.LONG.equals(declared)) {
            return type.getSort() == Type.LONG;
        }
        if (Opcodes.DOUBLE.equals(declared)) {
            return type.getSort() == Type.DOUBLE;
        }
        if (Opcodes.NULL.equals(declared)) {
            return type.equals(BasicInterpreter.NULL_TYPE);
        }
        if (declared instanceof String internalName) {
            return type.equals(BasicInterpreter.NULL_TYPE)
                    || (value.isReference() && verifier.accepts(Type.getObjectType(internalName), type));
        }
        // Object created but not yet initialized (UNINITIALIZED_THIS or a NEW label): ASM's
        // analysis does not track that state, so only check that it is a reference.
        return value.isReference();
    }
}
