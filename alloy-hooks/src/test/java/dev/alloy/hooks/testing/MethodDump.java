package dev.alloy.hooks.testing;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

/**
 * Dumps a class as text, method by method, to compare two versions of its bytecode (instructions,
 * labels, frames, try/catch blocks, local variables and line numbers).
 *
 * <p>Proves that a rewrite touched only the intended methods: all the others must give exactly
 * the same text before and after.</p>
 */
public final class MethodDump {

    /** Marks a hook call in a method's text. */
    public static final String HOOK_CALL_MARK = "dev/alloy/bridge/GameHooks.";

    private MethodDump() {
    }

    /** Returns the text of each method, by {@code name + descriptor}, in class order. */
    public static Map<String, String> ofMethods(byte[] classBytes) {
        Map<String, String> dumps = new LinkedHashMap<>();
        for (MethodNode method : MethodDump.read(classBytes).methods) {
            Textifier printer = new Textifier();
            method.accept(new TraceMethodVisitor(printer));
            StringWriter text = new StringWriter();
            printer.print(new PrintWriter(text));
            dumps.put(method.name + method.desc, MethodDump.withoutComputedSizes(text.toString()));
        }
        return dumps;
    }

    /** Lists the fields of a class (access, name and descriptor), in class order. */
    public static List<String> ofFields(byte[] classBytes) {
        return MethodDump.read(classBytes).fields.stream().map(MethodDump::describe).toList();
    }

    private static ClassNode read(byte[] classBytes) {
        ClassNode classNode = new ClassNode();
        // Expanded frames: two equivalent writings of the same frame give the same text.
        new ClassReader(classBytes).accept(classNode, ClassReader.EXPAND_FRAMES);
        return classNode;
    }

    private static String describe(FieldNode field) {
        return field.access + " " + field.name + ":" + field.desc;
    }

    /**
     * Removes the stack and local variable sizes: ASM recomputes them on write and may find a
     * different, equally valid value from the original compiler's.
     */
    private static String withoutComputedSizes(String methodText) {
        return methodText.lines()
                .filter(line -> !line.trim().startsWith("MAXSTACK") && !line.trim().startsWith("MAXLOCALS"))
                .collect(Collectors.joining("\n"));
    }
}
