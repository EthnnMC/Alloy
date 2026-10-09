package dev.alloy.remap.transform;

import org.objectweb.asm.tree.ClassNode;

/**
 * A rewrite step applied to a class already translated to game names. A pass edits the ASM tree it
 * receives in place and must not load classes or touch the disk.
 */
public interface ClassPass {

    /**
     * Examines a class and modifies it if needed.
     *
     * @return {@link Verdict#KEEP} normally, {@link Verdict#DROP} if the class must leave the jar
     */
    Verdict apply(ClassNode classNode);
}
