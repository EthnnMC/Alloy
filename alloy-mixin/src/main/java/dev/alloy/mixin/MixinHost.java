package dev.alloy.mixin;

import dev.alloy.bridge.BridgeLogger;
import dev.alloy.bridge.ClassRewriter;
import dev.alloy.bridge.MixinSetup;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;

/**
 * Entry point of the Mixin host: starts the Mixin library with the mods' configuration files, then
 * rewrites the game classes the agent submits. The agent creates it by reflection, in the class
 * loader that holds this module and Mixin, and uses it through {@link ClassRewriter}. Facade.
 */
public final class MixinHost implements ClassRewriter {

    private final IMixinTransformer transformer;
    private final BridgeLogger logger;

    private MixinHost(IMixinTransformer transformer, BridgeLogger logger) {
        this.transformer = transformer;
        this.logger = logger;
    }

    /**
     * Starts Mixin. Call it once, before the game loads the classes the mixins target.
     *
     * @param setup what the agent provides
     * @return the rewriter to give every targeted game class to
     * @throws ReflectiveOperationException if this Mixin version lacks the phase switch used here
     */
    public static ClassRewriter start(MixinSetup setup) throws ReflectiveOperationException {
        AlloyMixinService.install(setup);
        MixinBootstrap.init();
        Mixins.registerErrorHandlerClass(LenientErrorHandler.class.getName());
        setup.configs().forEach(Mixins::addConfiguration);

        // Mixin waits for its launcher to announce that the game is starting; there is no public call for it.
        Method gotoPhase = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
        gotoPhase.setAccessible(true);
        gotoPhase.invoke(null, MixinEnvironment.Phase.INIT);
        gotoPhase.invoke(null, MixinEnvironment.Phase.DEFAULT);

        IMixinTransformer transformer = AlloyMixinService.transformer();
        if (transformer == null) {
            throw new IllegalStateException("Mixin started without creating its transformer");
        }
        MixinHost host = new MixinHost(transformer, setup.logger());
        host.prepareNow();
        return host;
    }

    /**
     * Makes Mixin read the configurations and load the mods' mixin plugins now. It would otherwise
     * do so at the first class it is given, in the middle of the game defining that class, where
     * loading more game classes is not safe.
     */
    private void prepareNow() {
        String ownClass = MixinHost.class.getName();
        try (InputStream input = MixinHost.class.getResourceAsStream(MixinHost.class.getSimpleName() + ".class")) {
            if (input != null) {
                this.rewrite(ownClass, input.readAllBytes());
            }
        } catch (IOException e) {
            this.logger.warn("Mixin could not be prepared ahead of the first game class: " + e);
        }
    }

    @Override
    public byte[] rewrite(String className, byte[] classBytes) {
        try {
            if (classBytes == null) {
                return this.generate(className);
            }
            byte[] rewritten = this.transformer.transformClassBytes(className, className, classBytes);
            // Mixin returns its input when no mixin targets the class.
            return rewritten == classBytes ? null : rewritten;
        } catch (Throwable error) {
            // Throwable: Mixin reports a failed mixin with errors, and nothing may reach class loading.
            this.logger.error("Mixin could not process " + className + ": the class is left as the game has it", error);
            return null;
        }
    }

    /** Classes Mixin invents at run time (argument holders, copies of a mixin's inner classes). */
    private byte[] generate(String className) {
        if (this.transformer.getExtensions().getSyntheticClassRegistry().findSyntheticClass(className) == null) {
            return null;
        }
        return this.transformer.generateClass(MixinEnvironment.getCurrentEnvironment(), className);
    }
}
