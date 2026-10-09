package dev.alloy.remap.transform;

import java.util.List;
import java.util.Optional;

import org.objectweb.asm.tree.AnnotationNode;

/**
 * Looks up annotations in the list ASM attaches to a class, field or method. ASM leaves that list
 * {@code null} when there is no annotation; this is handled here.
 */
public final class Annotations {

    private Annotations() {
        // Utility class: no instances.
    }

    /**
     * Finds an annotation by type.
     *
     * @param annotations an element's annotation list, possibly {@code null}
     * @param descriptor  annotation type descriptor ({@code La/b/C;})
     * @return the annotation, or empty if the element does not carry it
     */
    public static Optional<AnnotationNode> find(List<AnnotationNode> annotations, String descriptor) {
        if (annotations == null) {
            return Optional.empty();
        }
        for (AnnotationNode annotation : annotations) {
            if (descriptor.equals(annotation.desc)) {
                return Optional.of(annotation);
            }
        }
        return Optional.empty();
    }

    /**
     * Tells whether an element carries an annotation.
     */
    public static boolean isPresent(List<AnnotationNode> annotations, String descriptor) {
        return Annotations.find(annotations, descriptor).isPresent();
    }
}
