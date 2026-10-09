package dev.alloy.hooks.inject;

import java.util.List;
import java.util.Objects;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;

/**
 * {@code RETURN_CALL}: calls a {@code void} hook just before a return, at every return or only
 * the last one. It is the only strategy allowed in a constructor, since {@code this} is
 * initialized at its returns. A returned value waits untouched on the stack. Exits by exception
 * are not covered. No jump is added, so no frame is needed.
 *
 * <pre>
 *   ... original body ...
 *   GameHooks.clientTickEnd();          // injected
 *   return;
 * </pre>
 */
public final class ReturnCall implements InjectionStrategy {

    private final HookCall hook;
    private final Occurrence occurrence;

    /**
     * Creates the strategy.
     *
     * @param occurrence which returns to equip ({@link Occurrence#EVERY} or {@link Occurrence#LAST})
     */
    public ReturnCall(HookCall hook, Occurrence occurrence) {
        this.hook = Objects.requireNonNull(hook, "hook");
        this.occurrence = Objects.requireNonNull(occurrence, "occurrence");
    }

    @Override
    public List<AbstractInsnNode> locate(TargetMethod method) throws InjectionException {
        List<AbstractInsnNode> sites = this.occurrence.select(ReturnSites.in(method));
        if (!sites.isEmpty()) {
            this.hook.requireAccepts(this.hook.loadedTypes(method));
            this.hook.requireReturns(Type.VOID_TYPE);
        }
        return sites;
    }

    @Override
    public void inject(TargetMethod method, List<AbstractInsnNode> sites) {
        for (AbstractInsnNode returnInstruction : sites) {
            method.instructions().insertBefore(returnInstruction, this.hook.newInstructions(method));
        }
    }

    @Override
    public List<HookCall> hookCalls() {
        return List.of(this.hook);
    }

    @Override
    public String describe() {
        return "RETURN_CALL(" + this.occurrence + ") " + this.hook.name();
    }
}
