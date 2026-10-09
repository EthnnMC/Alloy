package dev.alloy.remap.transform;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

/**
 * Removes server-only elements: classes, fields and methods marked {@code @SideOnly(Side.SERVER)}
 * (what Forge's {@code SideTransformer} does at load time). {@code Side.CLIENT} elements are kept,
 * since Alloy only runs on the client.
 *
 * <p>Unlike Forge, which throws when loading a class reserved to the other side, the class is
 * left out of the jar with a warning, so mod preparation is not interrupted.</p>
 */
public final class SidePass implements ClassPass {

    /** Name, in Forge's {@code Side} enum, of the side Alloy runs on. */
    private static final String RUNNING_SIDE = "CLIENT";

    private static final String VALUE_ELEMENT = "value";

    private final Consumer<String> warnings;

    /**
     * @param warnings receives one message per dropped class
     */
    public SidePass(Consumer<String> warnings) {
        this.warnings = Objects.requireNonNull(warnings, "warnings");
    }

    @Override
    public Verdict apply(ClassNode classNode) {
        if (SidePass.isForOtherSide(classNode.visibleAnnotations)) {
            this.warnings.accept("Class " + classNode.name + " is @SideOnly for another side than "
                    + SidePass.RUNNING_SIDE + ": left out of the jar");
            return Verdict.DROP;
        }
        classNode.fields.removeIf(field -> SidePass.isForOtherSide(field.visibleAnnotations));
        classNode.methods.removeIf(method -> SidePass.isForOtherSide(method.visibleAnnotations));
        return Verdict.KEEP;
    }

    /**
     * Tells whether an element's annotations reserve it to a side other than the client.
     *
     * @param annotations visible annotations of the element, possibly {@code null}
     */
    private static boolean isForOtherSide(List<AnnotationNode> annotations) {
        Optional<AnnotationNode> sideOnly = Annotations.find(annotations, ForgeNames.SIDE_ONLY_ANNOTATION);
        if (sideOnly.isEmpty() || sideOnly.get().values == null) {
            return false;
        }
        // ASM stores annotation elements flat: name, value, name, value...
        List<Object> values = sideOnly.get().values;
        for (int i = 0; i + 1 < values.size(); i += 2) {
            // An enum constant is an array { enum descriptor, constant name }.
            if (SidePass.VALUE_ELEMENT.equals(values.get(i)) && values.get(i + 1) instanceof String[] constant) {
                return !SidePass.RUNNING_SIDE.equals(constant[1]);
            }
        }
        return false;
    }
}
