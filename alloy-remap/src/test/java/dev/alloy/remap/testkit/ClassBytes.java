package dev.alloy.remap.testkit;

import dev.alloy.remap.transform.ClassPass;
import dev.alloy.remap.transform.Verdict;

import java.util.LinkedHashMap;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;

/**
 * Test helper: converts class bytes to an ASM tree and back, as the production code does
 * (no stack or frame recomputation).
 */
public final class ClassBytes {

    private ClassBytes() {
        // Utility class.
    }

    public static ClassNode read(byte[] classFile) {
        ClassNode classNode = new ClassNode();
        new ClassReader(classFile).accept(classNode, 0);
        return classNode;
    }

    /**
     * Reads a class and gives it another class file version (49 = Java 5, 50 = Java 6...).
     * Classes before Java 6 have no stack map frames, so frames are skipped for them.
     */
    public static ClassNode readAsVersion(byte[] classFile, int majorVersion) {
        ClassNode classNode = new ClassNode();
        boolean framesAllowed = majorVersion >= Opcodes.V1_6;
        new ClassReader(classFile).accept(classNode, framesAllowed ? 0 : ClassReader.SKIP_FRAMES);
        classNode.version = majorVersion;
        return classNode;
    }

    public static byte[] write(ClassNode classNode) {
        ClassWriter writer = new ClassWriter(0);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    /** Remaps a set of classes; keys of the result are the remapped internal names. */
    public static Map<String, byte[]> remapAll(Map<String, byte[]> classFiles, Remapper remapper) {
        Map<String, byte[]> remapped = new LinkedHashMap<>();
        for (byte[] classFile : classFiles.values()) {
            ClassNode classNode = new ClassNode();
            new ClassReader(classFile).accept(new ClassRemapper(classNode, remapper), 0);
            remapped.put(classNode.name, ClassBytes.write(classNode));
        }
        return remapped;
    }

    /** Applies a pass to every class; classes the pass drops are omitted from the result. */
    public static Map<String, byte[]> applyToAll(Map<String, byte[]> classFiles, ClassPass pass) {
        Map<String, byte[]> transformed = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> classFile : classFiles.entrySet()) {
            ClassNode classNode = ClassBytes.read(classFile.getValue());
            if (pass.apply(classNode) == Verdict.KEEP) {
                transformed.put(classFile.getKey(), ClassBytes.write(classNode));
            }
        }
        return transformed;
    }
}
