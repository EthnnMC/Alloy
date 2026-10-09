package dev.alloy.remap.transform;

import java.util.Objects;
import java.util.function.Consumer;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Makes classes listening to Forge events, and their {@code @SubscribeEvent} methods, public
 * (what Forge's {@code EventSubscriberTransformer} does at load time). Forge's event bus generates
 * a class in another package that calls the method directly, which requires public access.
 *
 * <p>Unlike Forge, which throws on a <b>private</b> {@code @SubscribeEvent} method, this logs a
 * warning, leaves the method private (the bus will not see it) and processes the rest of the class.</p>
 */
public final class SubscriberPass implements ClassPass {

    private final Consumer<String> warnings;

    /**
     * @param warnings receives one message per private {@code @SubscribeEvent} method
     */
    public SubscriberPass(Consumer<String> warnings) {
        this.warnings = Objects.requireNonNull(warnings, "warnings");
    }

    @Override
    public Verdict apply(ClassNode classNode) {
        boolean subscriber = false;
        for (MethodNode method : classNode.methods) {
            if (!Annotations.isPresent(method.visibleAnnotations, ForgeNames.SUBSCRIBE_EVENT_ANNOTATION)) {
                continue;
            }
            subscriber = true;
            if ((method.access & Opcodes.ACC_PRIVATE) != 0) {
                this.warnings.accept("Cannot apply @SubscribeEvent to private method " + classNode.name + "/"
                        + method.name + method.desc + ": this handler will never be called");
            } else {
                method.access = SubscriberPass.toPublic(method.access);
            }
        }
        if (subscriber) {
            classNode.access = SubscriberPass.toPublic(classNode.access);
        }
        return Verdict.KEEP;
    }

    private static int toPublic(int access) {
        return access & ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED) | Opcodes.ACC_PUBLIC;
    }
}
