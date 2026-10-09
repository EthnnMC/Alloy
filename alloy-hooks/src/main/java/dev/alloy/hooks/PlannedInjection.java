package dev.alloy.hooks;

import dev.alloy.hooks.inject.InjectionStrategy;
import dev.alloy.hooks.inject.TargetMethod;
import java.util.List;
import org.objectweb.asm.tree.AbstractInsnNode;

/**
 * An injection ready to be made: the strategy, the method, and the sites it located there.
 * Nothing is modified until {@link #apply()} is called.
 *
 * @param sites the located injection sites (at least one)
 */
record PlannedInjection(InjectionStrategy injection, TargetMethod method, List<AbstractInsnNode> sites) {

    void apply() {
        this.injection.inject(this.method, this.sites);
    }

    /**
     * Describes the injection for the report, for example
     * {@code RETURN_CALL(EVERY) clientTickEnd x1 in net/minecraft/client/Minecraft.runTick()V}.
     */
    String describe() {
        return this.injection.describe() + " x" + this.sites.size() + " in " + this.method.describe();
    }
}
