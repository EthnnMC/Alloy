package dev.alloy.hooks.inject;

import java.util.List;
import org.objectweb.asm.tree.AbstractInsnNode;

/**
 * A way to insert a {@code GameHooks} call into a game method. Strategy.
 *
 * <p>Applied in two steps so a class is never left half modified: {@link #locate} first,
 * touching nothing, then {@link #inject} only if everything was found. Modification is in
 * place (no field or method added, so it can be replayed by {@code retransformClasses}) and
 * injected code leaves the stack as it found it.</p>
 */
public interface InjectionStrategy {

    /**
     * Finds the instructions next to which code will be inserted, without modifying the method.
     *
     * @return the injection sites; an empty list means the anchor was not found
     * @throws InjectionException if a site exists but the injection cannot apply there
     */
    List<AbstractInsnNode> locate(TargetMethod method) throws InjectionException;

    /** Inserts the code at the sites returned by {@link #locate(TargetMethod)}. */
    void inject(TargetMethod method, List<AbstractInsnNode> sites);

    /** Returns the {@code GameHooks} calls this strategy inserts, in execution order. */
    List<HookCall> hookCalls();

    /** Describes the strategy for logs, for example {@code HEAD_CANCEL chatReceived}. */
    String describe();
}
