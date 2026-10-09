package dev.alloy.remap.hierarchy;

import java.util.Optional;

/**
 * A place to look up a class header by name, e.g. {@link HeaderTable} or {@link LayeredHeaderSource}.
 */
@FunctionalInterface
public interface ClassHeaderSource {

    /**
     * Looks up a class header.
     *
     * @param internalName internal class name
     * @return the header, or empty if this source does not know the class (JDK, libraries...)
     */
    Optional<ClassHeader> find(String internalName);
}
