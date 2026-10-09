package dev.alloy.forge;

import dev.alloy.bridge.BridgeLogger;
import dev.alloy.bridge.GameEventSink;
import dev.alloy.bridge.RuntimeContext;
import dev.alloy.forge.env.ForgeEnvironment;
import dev.alloy.forge.event.ForgeEventSink;
import dev.alloy.forge.loader.AlloyModContainer;
import dev.alloy.forge.loader.ModLifecycle;
import dev.alloy.forge.loader.ModRegistry;
import dev.alloy.forge.loader.ModScanner;
import java.io.File;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.discovery.ASMDataTable;

/**
 * Entry point of Alloy's Forge runtime, called by the agent when the game starts. It lives in the
 * mod class loader (the first Alloy code that sees both Minecraft and Forge); the agent calls it
 * by reflection and keeps only the {@link GameEventSink} interface. Facade.
 */
public final class ForgeRuntime {

    private static final String CONFIG_DIRECTORY = "config";

    private ForgeRuntime() {
    }

    /**
     * Prepares Forge and reads the mods without running any; the startup steps come later from the
     * hooks installed in {@code Minecraft.startGame}.
     *
     * @param context what the agent passes: folders, prepared mods, settings, log
     * @return the sink to plug into the game hooks
     */
    public static GameEventSink start(RuntimeContext context) {
        BridgeLogger logger = context.logger();
        ForgeEnvironment.prepare(context.minecraftVersion(), context.gameDirectory());

        File configDirectory = context.gameDirectory().resolve(ForgeRuntime.CONFIG_DIRECTORY).toFile();
        ModRegistry registry = new ModRegistry(context.minecraftVersion(), configDirectory);
        Loader.alloyInstall(registry);

        ASMDataTable table = new ASMDataTable();
        for (AlloyModContainer mod : new ModScanner(logger).scan(context.mods(), table)) {
            if (registry.register(mod)) {
                logger.info("Forge mod found: " + mod.getName() + " " + mod.getVersion() + " (" + mod.getModId() + ")");
            } else {
                logger.warn("Duplicate mod id '" + mod.getModId() + "' in " + mod.getSource().getName() + ": ignored");
            }
        }

        ClassLoader modLoader = ForgeRuntime.class.getClassLoader();
        ModLifecycle lifecycle = new ModLifecycle(registry, table, modLoader, logger);
        return new ForgeEventSink(context, lifecycle);
    }
}
