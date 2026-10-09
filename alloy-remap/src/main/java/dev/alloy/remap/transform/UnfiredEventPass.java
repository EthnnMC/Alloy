package dev.alloy.remap.transform;

import dev.alloy.remap.hierarchy.ClassHierarchy;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Reports {@code @SubscribeEvent} methods that listen to a Forge event Alloy never publishes. Events
 * defined by mods are ignored (the mod publishes them itself). The class is left unchanged; each
 * event is reported once per jar.
 */
public final class UnfiredEventPass implements ClassPass {

    private final Set<String> firedEvents;
    private final ClassHierarchy forge;
    private final Consumer<String> warnings;
    private final Set<String> examined = new HashSet<>();

    /**
     * @param firedEvents internal names of the event classes Alloy publishes
     * @param forge       inheritance of the Forge classes, in game names
     * @param warnings    receives one message per event that is never published
     */
    public UnfiredEventPass(Set<String> firedEvents, ClassHierarchy forge, Consumer<String> warnings) {
        this.firedEvents = Set.copyOf(firedEvents);
        this.forge = Objects.requireNonNull(forge, "forge");
        this.warnings = Objects.requireNonNull(warnings, "warnings");
    }

    @Override
    public Verdict apply(ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            if (!Annotations.isPresent(method.visibleAnnotations, ForgeNames.SUBSCRIBE_EVENT_ANNOTATION)) {
                continue;
            }
            Type[] parameters = Type.getArgumentTypes(method.desc);
            if (parameters.length != 1 || parameters[0].getSort() != Type.OBJECT) {
                continue;
            }
            String event = parameters[0].getInternalName();
            if (event.startsWith(ForgeNames.FORGE_PACKAGE) && this.examined.add(event) && !this.isFired(event)) {
                this.warnings.accept("Listens to " + event.substring(event.lastIndexOf('/') + 1).replace('$', '.')
                        + ", a Forge event Alloy does not publish yet: " + classNode.name + "." + method.name
                        + " will never be called");
            }
        }
        return Verdict.KEEP;
    }

    /** A listener of a parent class also receives the events of its subclasses. */
    private boolean isFired(String event) {
        if (this.firedEvents.contains(event)) {
            return true;
        }
        for (String fired : this.firedEvents) {
            if (this.forge.isSameOrSubclassOf(fired, event)) {
                return true;
            }
        }
        return false;
    }
}
