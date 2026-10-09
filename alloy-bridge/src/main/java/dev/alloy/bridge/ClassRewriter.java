package dev.alloy.bridge;

/**
 * Rewrites game classes for the mods before the JVM defines them. Implemented by the Mixin host,
 * which lives in a class loader of its own; the agent only knows this interface.
 */
public interface ClassRewriter {

    /**
     * Rewrites a class, or produces one the game does not have.
     *
     * @param className  dotted class name
     * @param classBytes the class as the game is about to define it, or {@code null} when the
     *                   game has no such class (the rewriter may then generate one)
     * @return the bytes to define instead, or {@code null} to leave things as they are
     */
    byte[] rewrite(String className, byte[] classBytes);
}
