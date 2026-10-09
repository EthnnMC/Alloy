package dev.alloy.hooks;

import java.util.Objects;

/**
 * Result of installing a hook: its status and a readable explanation.
 *
 * @param status the status
 * @param detail for {@link HookStatus#APPLIED}, what was injected; otherwise the reason
 */
public record HookOutcome(HookStatus status, String detail) {

    private static final HookOutcome PENDING = new HookOutcome(HookStatus.PENDING, "class not transformed yet");

    public HookOutcome {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(detail, "detail");
    }

    /** The target class has not been transformed yet. */
    public static HookOutcome pending() {
        return HookOutcome.PENDING;
    }

    /** All injections are in place. */
    public static HookOutcome applied(String detail) {
        return new HookOutcome(HookStatus.APPLIED, detail);
    }

    /** Method or anchor not found. */
    public static HookOutcome skipped(String reason) {
        return new HookOutcome(HookStatus.SKIPPED, reason);
    }

    /** Injection rejected or failed. */
    public static HookOutcome failed(String reason) {
        return new HookOutcome(HookStatus.FAILED, reason);
    }
}
