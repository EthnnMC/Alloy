package dev.alloy.remap.transform;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InnerClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Makes everything public and non-final, as Lunar does in the real game. Only used to build the
 * Minecraft jar that code is compiled against.
 *
 * <p>Left untouched:</p>
 * <ul>
 *   <li>interface members: the JVM imposes their flags;</li>
 *   <li>synthetic members and static initializers: no source code can name them;</li>
 *   <li>{@code final} on compile-time constants: while present, the compiler inlines the value in
 *       callers, as Mojang's compiler did; removing it would make callers read the field at run time.</li>
 * </ul>
 */
public final class AccessWideningPass implements ClassPass {

    private static final String STATIC_INITIALIZER = "<clinit>";

    public AccessWideningPass() {
        // No settings: the rule is the same for every class.
    }

    @Override
    public Verdict apply(ClassNode classNode) {
        classNode.access = AccessWideningPass.widen(classNode.access);
        for (InnerClassNode inner : classNode.innerClasses) {
            // For an inner class the compiler reads access from this table, not the class header:
            // widen the entries describing the class and its member classes.
            if (inner.name.equals(classNode.name) || classNode.name.equals(inner.outerName)) {
                inner.access = AccessWideningPass.widen(inner.access);
            }
        }
        if ((classNode.access & Opcodes.ACC_INTERFACE) != 0) {
            return Verdict.KEEP;
        }
        for (FieldNode field : classNode.fields) {
            if ((field.access & Opcodes.ACC_SYNTHETIC) == 0) {
                boolean compileTimeConstant = field.value != null;
                field.access = compileTimeConstant
                        ? AccessWideningPass.toPublic(field.access)
                        : AccessWideningPass.widen(field.access);
            }
        }
        for (MethodNode method : classNode.methods) {
            if ((method.access & Opcodes.ACC_SYNTHETIC) == 0
                    && !AccessWideningPass.STATIC_INITIALIZER.equals(method.name)) {
                method.access = AccessWideningPass.widen(method.access);
            }
        }
        return Verdict.KEEP;
    }

    /** Public and non-final. */
    private static int widen(int access) {
        return AccessWideningPass.toPublic(access) & ~Opcodes.ACC_FINAL;
    }

    private static int toPublic(int access) {
        return access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED) | Opcodes.ACC_PUBLIC;
    }
}
