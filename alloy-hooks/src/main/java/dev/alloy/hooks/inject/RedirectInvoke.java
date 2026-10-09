package dev.alloy.hooks.inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * {@code REDIRECT_INVOKE}: replaces a game method call with a call to a hook that consumes the
 * same stack values and returns the same type, so the hook decides whether and when to run the
 * original call. All occurrences are replaced. The stack keeps its shape, so no frame is needed.
 *
 * <pre>
 *   while (Mouse.next()) { ... }                   // before
 *   while (GameHooks.guiMouseNext(this)) { ... }   // after, with the "this" option
 *
 *   screen.drawScreen(x, y, ticks);                // before
 *   GameHooks.drawScreen(screen, x, y, ticks);     // after: the receiver becomes the first argument
 * </pre>
 *
 * <p>With the "this" option, {@code this} is pushed just before the call, so it is the
 * <em>last</em> hook argument. Searched in every method of a class, this strategy also finds a
 * call that Lunar's mixins moved into a synthetic method with an unpredictable name.</p>
 */
public final class RedirectInvoke implements InjectionStrategy {

    private static final String CONSTRUCTOR_NAME = "<init>";
    private static final int THIS_SLOT = 0;

    private final InvokeAnchor target;
    private final HookCall hook;
    private final boolean pushThis;

    /**
     * Creates the strategy.
     *
     * @param target   the game call to replace
     * @param hook     the replacement hook, with no loaded argument: it receives what the original call
     *                 found on the stack
     * @param pushThis {@code true} to also pass {@code this}, as the last argument
     * @throws IllegalArgumentException if the catalog entry is inconsistent
     */
    public RedirectInvoke(InvokeAnchor target, HookCall hook, boolean pushThis) {
        this.target = Objects.requireNonNull(target, "target");
        this.hook = Objects.requireNonNull(hook, "hook");
        this.pushThis = pushThis;
        if (!hook.arguments().isEmpty()) {
            throw new IllegalArgumentException(
                    "A redirect hook takes its operands from the stack: " + hook.signature());
        }
        if (RedirectInvoke.CONSTRUCTOR_NAME.equals(target.name())) {
            throw new IllegalArgumentException("A constructor call cannot be redirected: " + target.describe());
        }
    }

    @Override
    public List<AbstractInsnNode> locate(TargetMethod method) throws InjectionException {
        List<AbstractInsnNode> sites = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions()) {
            if (this.target.matches(instruction)) {
                this.requireSameStackEffect(method, (MethodInsnNode) instruction);
                sites.add(instruction);
            }
        }
        return sites;
    }

    @Override
    public void inject(TargetMethod method, List<AbstractInsnNode> sites) {
        for (AbstractInsnNode call : sites) {
            if (this.pushThis) {
                method.instructions().insertBefore(call, new VarInsnNode(Opcodes.ALOAD, RedirectInvoke.THIS_SLOT));
            }
            // Replaced in place: labels and frames preceding the call stay in front of it.
            method.instructions().set(call, this.hook.newInvokeInstruction());
        }
    }

    @Override
    public List<HookCall> hookCalls() {
        return List.of(this.hook);
    }

    @Override
    public String describe() {
        return "REDIRECT_INVOKE(" + this.target.describe() + ") " + this.hook.name();
    }

    /**
     * Checks that the hook consumes what the original call did (its receiver if not static, then
     * its arguments, then {@code this} with the option) and returns the same type.
     */
    private void requireSameStackEffect(TargetMethod method, MethodInsnNode call) throws InjectionException {
        method.requireNotConstructor("a redirection");
        List<Type> operands = new ArrayList<>();
        if (call.getOpcode() != Opcodes.INVOKESTATIC) {
            operands.add(Type.getObjectType(call.owner));
        }
        operands.addAll(List.of(Type.getArgumentTypes(call.desc)));
        if (this.pushThis) {
            operands.add(HookArgument.self().typeIn(method));
        }
        this.hook.requireAccepts(operands);
        this.hook.requireReturns(Type.getReturnType(call.desc));
    }
}
