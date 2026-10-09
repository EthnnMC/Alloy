package dev.alloy.hooks.inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;

/**
 * {@code RETURN_FILTER}: passes the return value through a hook that may replace it. At each
 * return the value is already on the stack; the catalog's arguments are loaded after it, so the
 * hook's first parameter is always the return value. No jump is added, so no frame is needed.
 *
 * <pre>
 *   return (List) GameHooks.itemTooltip(value, this, player, advanced);   // around the original return
 * </pre>
 */
public final class ReturnFilter implements InjectionStrategy {

    private final HookCall hook;

    /**
     * Creates the strategy.
     *
     * @param hook the filtering hook; its arguments are the ones passed <em>after</em> the return value
     */
    public ReturnFilter(HookCall hook) {
        this.hook = Objects.requireNonNull(hook, "hook");
    }

    @Override
    public List<AbstractInsnNode> locate(TargetMethod method) throws InjectionException {
        List<AbstractInsnNode> sites = ReturnSites.in(method);
        if (!sites.isEmpty()) {
            Type returned = method.returnType();
            if (returned.equals(Type.VOID_TYPE)) {
                throw new InjectionException(method.describe() + " returns nothing: there is no value to filter");
            }
            List<Type> operands = new ArrayList<>();
            operands.add(returned);
            operands.addAll(this.hook.loadedTypes(method));
            this.hook.requireAccepts(operands);
            this.hook.requireReturnsValueFor(returned);
        }
        return sites;
    }

    @Override
    public void inject(TargetMethod method, List<AbstractInsnNode> sites) {
        for (AbstractInsnNode returnInstruction : sites) {
            InsnList code = this.hook.newInstructions(method);
            this.hook.appendCastTo(method.returnType(), code);
            method.instructions().insertBefore(returnInstruction, code);
        }
    }

    @Override
    public List<HookCall> hookCalls() {
        return List.of(this.hook);
    }

    @Override
    public String describe() {
        return "RETURN_FILTER " + this.hook.name();
    }
}
