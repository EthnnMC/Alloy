package dev.alloy.forge.loader;

import com.google.common.collect.ArrayListMultimap;
import dev.alloy.bridge.BridgeLogger;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.client.FMLFileResourcePack;
import net.minecraftforge.fml.common.ILanguageAdapter;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.LoaderState;
import net.minecraftforge.fml.common.ProxyInjector;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import net.minecraftforge.fml.common.event.FMLConstructionEvent;
import net.minecraftforge.fml.common.event.FMLEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Runs Forge's startup steps for the mods, in Forge's order: construction, pre-initialisation,
 * initialisation, post-initialisation, load complete. Each step goes to all mods before the next,
 * with the mod marked active in the {@link ModRegistry}. A mod that throws is logged and disabled
 * so the others and the game carry on (Forge itself stops everything).
 */
public final class ModLifecycle {

    private final ModRegistry registry;
    private final ASMDataTable table;
    private final ClassLoader loader;
    private final BridgeLogger logger;
    private final FieldInjector fieldInjector;

    /**
     * Creates the lifecycle.
     *
     * @param registry registry of the mods to start
     * @param table    annotation table read from the jars
     * @param loader   the mod class loader
     * @param logger   Alloy log
     */
    public ModLifecycle(ModRegistry registry, ASMDataTable table, ClassLoader loader, BridgeLogger logger) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.table = Objects.requireNonNull(table, "table");
        this.loader = Objects.requireNonNull(loader, "loader");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.fieldInjector = new FieldInjector(registry, table, loader);
    }

    /**
     * Adds each mod's jar to the game's resource packs, so its textures and translations
     * ({@code assets/<modid>/...}) are read at the next resource load.
     *
     * @param minecraft the game client
     */
    public void addResourcePacks(Minecraft minecraft) {
        for (AlloyModContainer mod : this.registry.mods()) {
            minecraft.defaultResourcePacks.add(new FMLFileResourcePack(mod));
        }
    }

    /** Construction step: creates each mod's main object and fills its annotated fields. */
    public void constructMods() {
        this.registry.setState(LoaderState.CONSTRUCTING);
        for (AlloyModContainer mod : this.registry.mods()) {
            this.run(mod, "construction", () -> mod.construct(this.loader));
        }
        // Fields are filled only once all mods are constructed: a mod may ask for another's object.
        for (AlloyModContainer mod : this.registry.mods()) {
            this.run(mod, "field injection", () -> {
                ProxyInjector.inject(mod, this.table, Side.CLIENT, new ILanguageAdapter.JavaAdapter());
                this.fieldInjector.inject(mod);
            });
        }
        this.dispatch("construction event", () -> new FMLConstructionEvent(
                Loader.instance().getModClassLoader(), this.table, ArrayListMultimap.<String, String>create()));
        this.logger.info("Forge mods constructed: " + this.registry.mods());
    }

    /** Pre-initialisation step: {@code FMLPreInitializationEvent}. */
    public void preInitialize() {
        this.registry.setState(LoaderState.PREINITIALIZATION);
        this.dispatch("pre-initialization",
                () -> new FMLPreInitializationEvent(this.table, this.registry.configDirectory()));
    }

    /** Initialisation, post-initialisation and load-complete steps. */
    public void initialize() {
        this.registry.setState(LoaderState.INITIALIZATION);
        this.dispatch("initialization", FMLInitializationEvent::new);
        this.registry.setState(LoaderState.POSTINITIALIZATION);
        this.dispatch("post-initialization", FMLPostInitializationEvent::new);
        this.registry.setState(LoaderState.AVAILABLE);
        this.dispatch("load-complete", FMLLoadCompleteEvent::new);
    }

    /** Passes a step to all still-valid mods, with a fresh event for the step. */
    private void dispatch(String stepName, Supplier<FMLEvent> eventFactory) {
        FMLEvent event = eventFactory.get();
        for (AlloyModContainer mod : this.registry.mods()) {
            this.run(mod, stepName, () -> {
                event.applyModContainer(mod);
                mod.dispatch(event);
            });
        }
    }

    /** Runs a step for a mod, marking it active and disabling it if it fails. */
    private void run(AlloyModContainer mod, String stepName, ModStep step) {
        if (mod.isDisabled()) {
            return;
        }
        this.registry.setActive(mod);
        try {
            step.run();
        } catch (InvocationTargetException e) {
            this.disable(mod, stepName, e.getCause());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            this.disable(mod, stepName, e);
        } finally {
            this.registry.setActive(null);
        }
    }

    private void disable(AlloyModContainer mod, String stepName, Throwable cause) {
        mod.disable();
        this.logger.error("Mod '" + mod.getModId() + "' failed during " + stepName + " and is disabled", cause);
    }

    /** One startup step of a mod; may fail by reflection. */
    @FunctionalInterface
    private interface ModStep {
        void run() throws ReflectiveOperationException;
    }
}
