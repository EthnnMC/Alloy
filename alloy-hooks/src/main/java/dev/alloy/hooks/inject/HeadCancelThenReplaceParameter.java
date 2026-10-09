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
 * {@code HEAD_CANCEL_THEN_REPLACE_PARAMETER}: two-call protocol for opening a screen. The first
 * hook may cancel; otherwise the second returns the object that replaces the parameter. Two
 * calls because a Java method returns a single value and two are needed ("cancelled?" and
 * "which screen?").
 *
 * <pre>
 *   if (GameHooks.guiOpen(screen)) {                    // injected
 *       return;                                         // injected
 *   }
 *   screen = (GuiScreen) GameHooks.guiOpenResult();     // injected: resume label + frame
 *   ... original body ...
 * </pre>
 */
public final class HeadCancelThenReplaceParameter implements InjectionStrategy {

    private final int position;
    private final HookCall cancelHook;
    private final HookCall resultHook;

    /**
     * Creates the strategy.
     *
     * @param position   index, starting at 1, of the parameter to replace
     * @param cancelHook {@code boolean} hook: {@code true} cancels the method
     * @param resultHook no-argument hook returning the parameter's new value
     */
    public HeadCancelThenReplaceParameter(int position, HookCall cancelHook, HookCall resultHook) {
        this.position = position;
        this.cancelHook = Objects.requireNonNull(cancelHook, "cancelHook");
        this.resultHook = Objects.requireNonNull(resultHook, "resultHook");
    }

    @Override
    public List<AbstractInsnNode> locate(TargetMethod method) throws InjectionException {
        List<AbstractInsnNode> sites = HeadCode.site(method);
        if (!sites.isEmpty()) {
            Type replaced = HookArgument.parameter(this.position).typeIn(method);
            this.cancelHook.requireAccepts(this.cancelHook.loadedTypes(method));
            this.cancelHook.requireReturns(Type.BOOLEAN_TYPE);
            this.resultHook.requireAccepts(this.resultHook.loadedTypes(method));
            this.resultHook.requireReturnsValueFor(replaced);
        }
        return sites;
    }

    @Override
    public void inject(TargetMethod method, List<AbstractInsnNode> sites) {
        LabelNode notCancelled = new LabelNode();
        InsnList code = this.cancelHook.newInstructions(method);
        code.add(new JumpInsnNode(Opcodes.IFEQ, notCancelled));
        HeadCode.appendDefaultReturn(method, code);
        // Injected code follows the label, so the resume frame is always ours.
        method.appendResumePoint(code, notCancelled, false);
        code.add(this.resultHook.newInstructions(method));
        HeadCode.appendStoreIntoParameter(method, this.position, this.resultHook, code);
        method.instructions().insert(code);
    }

    @Override
    public List<HookCall> hookCalls() {
        return List.of(this.cancelHook, this.resultHook);
    }

    @Override
    public String describe() {
        return "HEAD_CANCEL_THEN_REPLACE_PARAMETER " + this.cancelHook.name() + "/" + this.resultHook.name();
    }
}
