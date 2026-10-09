package dev.alloy.hooks.inject;

/** Which side of the anchor instruction the code is inserted on. */
public enum AnchorPosition {

    /** Just before the anchor. */
    BEFORE,

    /** Just after the anchor, once it has completed normally. */
    AFTER
}
