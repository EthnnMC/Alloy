package dev.alloy.remap.jar;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Recognizes the Forge classes that Alloy replaces with its own versions ("overlays").
 *
 * <p>Some Forge classes ({@code Loader}, {@code ModClassLoader}...) cannot work outside Forge's own
 * launcher, so Alloy's runtime provides same-named rewrites. The originals are removed from the
 * Forge jar <b>together with their inner classes</b>, so two versions of a class never mix.</p>
 */
public final class OverlayFilter implements Predicate<String> {

    private static final char INNER_SEPARATOR = '$';

    private final Set<String> overlaidClasses;

    /**
     * @param overlaidClasses internal names of the replaced classes
     *                        ({@code net/minecraftforge/fml/common/Loader})
     */
    public OverlayFilter(Set<String> overlaidClasses) {
        this.overlaidClasses = Set.copyOf(overlaidClasses);
    }

    /**
     * Tells whether a class must be removed.
     *
     * @return {@code true} if it is a replaced class or an inner class of one
     */
    @Override
    public boolean test(String internalName) {
        if (this.overlaidClasses.contains(internalName)) {
            return true;
        }
        // "A$B$C" is inner to "A$B" and to "A": try each cut at a "$".
        int separator = internalName.indexOf(OverlayFilter.INNER_SEPARATOR);
        while (separator >= 0) {
            if (this.overlaidClasses.contains(internalName.substring(0, separator))) {
                return true;
            }
            separator = internalName.indexOf(OverlayFilter.INNER_SEPARATOR, separator + 1);
        }
        return false;
    }
}
