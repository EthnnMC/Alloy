package dev.alloy.hooks.inject;

import java.util.List;
import java.util.Objects;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * {@code HEAD_REPLACE_PARAMETER}: at the start of the method, replaces a parameter with a hook's
 * result. Optionally a {@code null} result cancels the method; without that option no jump is
 * added.
 *
 * <pre>
 *   sound = (ISound) GameHooks.playSound(this, sound);   // injected
 *   if (sound == null) {                                 // injected (null-cancels option)
 *       return;                                          // injected
 *   }
 *   ... original body ...                                // resume label + frame
 * </pre>
 */
public final class HeadReplaceParameter implements InjectionStrategy {

    private final int position;
    private final HookCall hook;
    private final boolean nullCancels;

    /**
     * Creates the strategy.
     *
     * @param position    index, starting at 1, of the parameter to replace
     * @param hook        hook returning the new value
     * @param nullCancels {@code true} if a {@code null} result must end the method
     */
    public HeadReplaceParameter(int position, HookCall hook, boolean nullCancels) {
        this.position = position;
        this.hook = Objects.requireNonNull(hook, "hook");
        this.nullCancels = nullCancels;
    }

    @Override
    public List<AbstractInsnNode> locate(TargetMethod method) throws InjectionException {
        List<AbstractInsnNode> sites = HeadCode.site(method);
        if (!sites.isEmpty()) {
            Type replaced = HookArgument.parameter(this.position).typeIn(method);
            this.hook.requireAccepts(this.hook.loadedTypes(method));
            this.hook.requireReturnsValueFor(replaced);
            if (this.nullCancels && !HookCall.isReference(replaced)) {
                throw new InjectionException(
                        "parameter " + this.position + " of " + method.describe() + " is primitive: it cannot be null");
            }
        }
        return sites;
    }

    @Override
    public void inject(TargetMethod method, List<AbstractInsnNode> sites) {
        InsnList code = this.hook.newInstructions(method);
        HeadCode.appendStoreIntoParameter(method, this.position, this.hook, code);
        if (this.nullCancels) {
            LabelNode notNull = new LabelNode();
            code.add(new VarInsnNode(Opcodes.ALOAD, method.parameterSlot(this.position)));
            code.add(new JumpInsnNode(Opcodes.IFNONNULL, notNull));
            HeadCode.appendDefaultReturn(method, code);
            method.appendResumePoint(code, notNull, true);
        }
        method.instructions().insert(code);
    }

    @Override
    public List<HookCall> hookCalls() {
        return List.of(this.hook);
    }

    @Override
    public String describe() {
        return "HEAD_REPLACE_PARAMETER " + this.hook.name();
    }
}
