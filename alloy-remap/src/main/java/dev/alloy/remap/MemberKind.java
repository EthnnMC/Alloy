package dev.alloy.remap;

/**
 * Kind of member a {@link MemberShim} replaces.
 */
public enum MemberKind {

    /** A method: keyed by name and method descriptor, e.g. {@code (I)V}. */
    METHOD,

    /** A field: keyed by name and type descriptor, e.g. {@code I}. */
    FIELD
}
