package dev.alloy.hooks.inject;

import java.util.List;
import java.util.Objects;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;

/**
 * {@code HEAD_CALL}: calls a {@code void} hook at the very start of the method, then lets the
 * original code run. No jump is added, so no frame is needed.
 *
 * <pre>
 *   GameHooks.clientTickStart();        // injected
 *   ... original body ...
 * </pre>
 */
public final class HeadCall implements InjectionStrategy {

    private final HookCall hook;

    public HeadCall(HookCall hook) {
        this.hook = Objects.requireNonNull(hook, "hook");
    }

    @Override
    public List<AbstractInsnNode> locate(TargetMethod method) throws InjectionException {
        List<AbstractInsnNode> sites = HeadCode.site(method);
        if (!sites.isEmpty()) {
            this.hook.requireAccepts(this.hook.loadedTypes(method));
            this.hook.requireReturns(Type.VOID_TYPE);
        }
        return sites;
    }

    @Override
    public void inject(TargetMethod method, List<AbstractInsnNode> sites) {
        method.instructions().insert(this.hook.newInstructions(method));
    }

    @Override
    public List<HookCall> hookCalls() {
        return List.of(this.hook);
    }

    @Override
    public String describe() {
        return "HEAD_CALL " + this.hook.name();
    }
}
