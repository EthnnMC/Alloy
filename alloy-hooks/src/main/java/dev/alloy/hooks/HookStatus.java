package dev.alloy.hooks;

/** What happened to a catalog hook. */
public enum HookStatus {

    /** Its class is not loaded yet, or hooks are not yet enabled. */
    PENDING,

    /** All its injections are in place. */
    APPLIED,

    /**
     * Its method or anchor was not found in the real class (Lunar changed): nothing was
     * injected, so the matching Forge event will not be posted.
     */
    SKIPPED,

    /** The injection was rejected or failed: the catalog entry does not fit the real method. */
    FAILED
}
