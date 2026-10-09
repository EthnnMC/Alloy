package dev.alloy.remap.transform;

import java.util.List;

import org.objectweb.asm.tree.ClassNode;

/**
 * An ordered sequence of passes, seen as a single pass.
 */
public final class ClassPipeline implements ClassPass {

    private final List<ClassPass> passes;

    /**
     * @param passes the passes, in the order they apply to each class
     */
    public ClassPipeline(List<ClassPass> passes) {
        this.passes = List.copyOf(passes);
    }

    /**
     * Applies the passes one after another; once a pass drops the class, the rest are skipped.
     */
    @Override
    public Verdict apply(ClassNode classNode) {
        for (ClassPass pass : this.passes) {
            if (pass.apply(classNode) == Verdict.DROP) {
                return Verdict.DROP;
            }
        }
        return Verdict.KEEP;
    }
}
