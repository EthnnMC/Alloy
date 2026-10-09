package dev.alloy.hooks.inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.LabelNode;

/**
 * {@code BEFORE_ANCHOR} and {@code AFTER_ANCHOR}: call a {@code void} hook just before or after
 * an anchor instruction. The stack may already hold the anchor's arguments; the call leaves them
 * alone. No jump is added, so no frame is needed.
 *
 * <pre>
 *   GameHooks.renderTickStart();                               // injected (BEFORE)
 *   this.entityRenderer.updateCameraAndRender(partialTicks, time);
 *   GameHooks.renderTickEnd();                                 // injected (AFTER)
 * </pre>
 */
public final class AnchorCall implements InjectionStrategy {

    private final AnchorPosition position;
    private final Anchor anchor;
    private final Occurrence occurrence;
    private final HookCall hook;

    public AnchorCall(AnchorPosition position, Anchor anchor, Occurrence occurrence, HookCall hook) {
        this.position = Objects.requireNonNull(position, "position");
        this.anchor = Objects.requireNonNull(anchor, "anchor");
        this.occurrence = Objects.requireNonNull(occurrence, "occurrence");
        this.hook = Objects.requireNonNull(hook, "hook");
    }

    @Override
    public List<AbstractInsnNode> locate(TargetMethod method) throws InjectionException {
        List<AbstractInsnNode> matches = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions()) {
            if (this.anchor.matches(instruction)) {
                matches.add(instruction);
            }
        }
        List<AbstractInsnNode> sites = this.occurrence.select(matches);
        if (!sites.isEmpty()) {
            method.requireNotConstructor("an anchor injection");
            this.hook.requireAccepts(this.hook.loadedTypes(method));
            this.hook.requireReturns(Type.VOID_TYPE);
            for (AbstractInsnNode site : sites) {
                this.requireInsertable(method, site);
            }
        }
        return sites;
    }

    @Override
    public void inject(TargetMethod method, List<AbstractInsnNode> sites) {
        for (AbstractInsnNode site : sites) {
            if (this.position == AnchorPosition.BEFORE) {
                method.instructions().insertBefore(site, this.hook.newInstructions(method));
            } else {
                method.instructions().insert(site, this.hook.newInstructions(method));
            }
        }
    }

    @Override
    public List<HookCall> hookCalls() {
        return List.of(this.hook);
    }

    @Override
    public String describe() {
        return this.position + "_ANCHOR(" + this.anchor.describe() + ") " + this.hook.name();
    }

    /**
     * Rejects the one case where inserting before an instruction would break the class: a stack
     * map frame between a {@code NEW} and its constructor call designates the uninitialized
     * object by the label just before the {@code NEW}. Code inserted there would make the frame
     * point at our call, and the JVM would reject the class.
     */
    private void requireInsertable(TargetMethod method, AbstractInsnNode site) throws InjectionException {
        boolean beforeNew = this.position == AnchorPosition.BEFORE && site.getOpcode() == Opcodes.NEW;
        if (beforeNew && AnchorCall.isDesignatedByFrame(method, site)) {
            throw new InjectionException(
                    "a stack map frame of " + method.describe() + " designates this " + this.anchor.describe()
                            + " by its label: no code can be inserted before it");
        }
    }

    /** Returns whether a frame designates this {@code NEW} by one of the labels preceding it. */
    private static boolean isDesignatedByFrame(TargetMethod method, AbstractInsnNode newInstruction) {
        Set<LabelNode> labels = new HashSet<>();
        AbstractInsnNode previous = newInstruction.getPrevious();
        while (previous != null && previous.getOpcode() < 0) {
            if (previous instanceof LabelNode label) {
                labels.add(label);
            }
            previous = previous.getPrevious();
        }
        for (AbstractInsnNode instruction : method.instructions()) {
            if (instruction instanceof FrameNode frame
                    && (AnchorCall.mentions(frame.local, labels) || AnchorCall.mentions(frame.stack, labels))) {
                return true;
            }
        }
        return false;
    }

    private static boolean mentions(List<Object> frameElements, Set<LabelNode> labels) {
        return frameElements != null && !Collections.disjoint(frameElements, labels);
    }
}
