package dev.alloy.hooks.inject;

import java.util.List;
import java.util.Objects;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;

/**
 * {@code HEAD_CANCEL}: at the start of the method, asks a {@code boolean} hook whether to cancel;
 * if so the method returns at once with its type's neutral value. It adds a jump, hence a frame
 * at the resume point (see {@link TargetMethod#appendResumePoint}).
 *
 * <pre>
 *   if (GameHooks.chatReceived(packet)) {   // injected
 *       return;                             // injected
 *   }
 *   ... original body ...                   // resume label + frame
 * </pre>
 */
public final class HeadCancel implements InjectionStrategy {

    private final HookCall hook;

    /**
     * Creates the strategy.
     *
     * @param hook the hook to ask; it returns {@code true} to cancel
     */
    public HeadCancel(HookCall hook) {
        this.hook = Objects.requireNonNull(hook, "hook");
    }

    @Override
    public List<AbstractInsnNode> locate(TargetMethod method) throws InjectionException {
        List<AbstractInsnNode> sites = HeadCode.site(method);
        if (!sites.isEmpty()) {
            this.hook.requireAccepts(this.hook.loadedTypes(method));
            this.hook.requireReturns(Type.BOOLEAN_TYPE);
        }
        return sites;
    }

    @Override
    public void inject(TargetMethod method, List<AbstractInsnNode> sites) {
        LabelNode notCancelled = new LabelNode();
        InsnList code = this.hook.newInstructions(method);
        // IFEQ jumps when the boolean is 0 (not cancelled) and the normal flow resumes.
        code.add(new JumpInsnNode(Opcodes.IFEQ, notCancelled));
        HeadCode.appendDefaultReturn(method, code);
        method.appendResumePoint(code, notCancelled, true);
        method.instructions().insert(code);
    }

    @Override
    public List<HookCall> hookCalls() {
        return List.of(this.hook);
    }

    @Override
    public String describe() {
        return "HEAD_CANCEL " + this.hook.name();
    }
}
