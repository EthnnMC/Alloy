package dev.alloy.agent.loader;

import dev.alloy.bridge.BridgeLogger;
import dev.alloy.bridge.ClassRewriter;
import dev.alloy.bridge.ForgeGate;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * What the game's class loader asks before looking itself (the agent stores it in the field
 * {@code GameHooks.CLASS_SOURCE_FIELD} that {@code GameLoaderBridgePatch} adds to that loader): it
 * hands over the classes of the mods and of Forge, which the game cannot see on its own. Code that
 * a mixin puts in a game class calls its mod as if both were loaded together; this makes it true.
 *
 * <p>Forge classes are held back while {@link ForgeGate} is closed, and always from
 * {@code Class.forName}: OptiFine and Lunar probe for Forge that way and must keep finding nothing.
 * It also defines, in the game loader, the classes Mixin invents at run time.</p>
 */
public final class GameClassSource implements Function<String, Class<?>> {

    private static final Set<String> FORGE_PREFIXES = Set.of("net.minecraftforge.", "cpw.mods.");
    private static final String REFLECTION_CLASS = "java.lang.Class";
    private static final String LOADER_BASE_CLASS = "java.lang.ClassLoader";

    private final AlloyClassLoader mods;
    private final ClassLoader gameLoader;
    private final BridgeLogger logger;

    /** {@code ClassLoader.defineClass(String, byte[], int, int)}, opened by the agent. */
    private final Method defineClass;

    /** Generates Mixin's run-time classes; {@code null} until mixins are started. */
    private volatile ClassRewriter generator;

    /** Names already reported, so a failure repeated at every lookup is logged once. */
    private final Set<String> reported = ConcurrentHashMap.newKeySet();

    /**
     * @param mods        the class loader holding Forge and the mods
     * @param gameLoader  the game's class loader
     * @param defineClass {@code ClassLoader.defineClass(String, byte[], int, int)}, made accessible
     * @param logger      Alloy's log
     */
    public GameClassSource(AlloyClassLoader mods, ClassLoader gameLoader, Method defineClass, BridgeLogger logger) {
        this.mods = Objects.requireNonNull(mods, "mods");
        this.gameLoader = Objects.requireNonNull(gameLoader, "gameLoader");
        this.defineClass = Objects.requireNonNull(defineClass, "defineClass");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** Lets this source define the classes the Mixin host generates. */
    public void useGenerator(ClassRewriter rewriter) {
        this.generator = Objects.requireNonNull(rewriter, "rewriter");
    }

    /**
     * Called for every class the game's loader is asked for.
     *
     * @param name dotted class name
     * @return the class, or {@code null} to let the game's loader go on by itself
     */
    @Override
    public Class<?> apply(String name) {
        try {
            if (this.mods.definesItself(name)) {
                return this.mayGive(name) ? this.mods.loadClass(name) : null;
            }
            // Mixin's generated classes are all nested ones.
            ClassRewriter rewriter = this.generator;
            if (rewriter != null && name.indexOf('$') >= 0) {
                byte[] generated = rewriter.rewrite(name, null);
                if (generated != null) {
                    return this.define(name, generated);
                }
            }
        } catch (Throwable error) {
            // Runs inside the game's class loading: nothing may escape, the game then reports "class not found".
            if (this.reported.add(name)) {
                this.logger.error("Cannot give the class " + name + " to the game", error);
            }
        }
        return null;
    }

    private boolean mayGive(String name) {
        for (String prefix : GameClassSource.FORGE_PREFIXES) {
            if (name.startsWith(prefix)) {
                return ForgeGate.isOpen() && !this.isReflectiveLookup();
            }
        }
        return true;
    }

    /**
     * Tells whether the pending request comes from {@code Class.forName}: on the stack, right below
     * the class loader frames, sits either {@code java.lang.Class} (a lookup by name) or the code
     * whose linking needs the class.
     */
    private boolean isReflectiveLookup() {
        String gameLoaderClass = this.gameLoader.getClass().getName();
        return StackWalker.getInstance().walk(frames -> frames
                .map(StackWalker.StackFrame::getClassName)
                .dropWhile(frame -> !frame.equals(gameLoaderClass))
                .dropWhile(frame -> frame.equals(gameLoaderClass) || frame.equals(GameClassSource.LOADER_BASE_CLASS))
                .findFirst()
                .map(GameClassSource.REFLECTION_CLASS::equals)
                .orElse(false));
    }

    private Class<?> define(String name, byte[] classBytes) throws ReflectiveOperationException {
        try {
            return (Class<?>) this.defineClass.invoke(this.gameLoader, name, classBytes, 0, classBytes.length);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof LinkageError) {
                // Another thread defined it first: the game loader now finds it by itself.
                return null;
            }
            throw e;
        }
    }
}
