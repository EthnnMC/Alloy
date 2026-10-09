package dev.alloy.hooks.inject;

/**
 * Thrown when an injection cannot be applied to the target method (for example "this" requested
 * in a static method, or types that do not match the hook). The hook is marked failed without
 * affecting the others. A missing anchor is not an error: the strategy returns no sites.
 */
public final class InjectionException extends Exception {

    public InjectionException(String message) {
        super(message);
    }
}
