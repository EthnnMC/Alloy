package dev.alloy.remap.transform;

/**
 * What a {@link ClassPass} decides about a class.
 */
public enum Verdict {

    /** The class stays in the jar (modified or not). */
    KEEP,

    /** The class must be removed from the jar. */
    DROP
}
