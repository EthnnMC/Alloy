package net.minecraftforge.fml.common;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import dev.alloy.forge.loader.AlloyModContainer;
import dev.alloy.forge.loader.ModRegistry;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraftforge.fml.common.versioning.ArtifactVersion;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Replaces Forge's {@code Loader}, whose original needs the class loader of Forge's launcher
 * (absent under Lunar). It has the same name and public methods because mods call
 * {@code Loader.instance()}; it is backed by {@link ModRegistry}, and the original is removed from
 * the Forge jar. Methods that drove mod loading in Forge (now {@code dev.alloy.forge.loader.ModLifecycle})
 * or concern a server throw or do nothing.
 */
public class Loader {

    /** Minecraft version this Forge is made for. */
    public static final String MC_VERSION = "1.8.9";

    private static final String FML_VERSION = "8.0.99.99";
    private static final String MCP_VERSION = "9.19";

    /** The single instance, as Forge's API requires. */
    private static final Loader INSTANCE = new Loader();

    /** Alloy's registry; {@code null} until the runtime has started. */
    private volatile ModRegistry registry;

    /** Wrapper of the mod class loader, created on first request. */
    private ModClassLoader modClassLoader;

    private Loader() {
    }

    /** Returns the single instance. */
    public static Loader instance() {
        return Loader.INSTANCE;
    }

    /**
     * Plugs in Alloy's registry. Called once by the runtime, before any mod.
     *
     * @param modRegistry the mod registry
     */
    public static void alloyInstall(ModRegistry modRegistry) {
        Loader.INSTANCE.registry = Objects.requireNonNull(modRegistry, "modRegistry");
    }

    /** No-op: in Forge this receives version numbers from the launcher; Alloy already knows them. */
    public static void injectData(Object... data) {
    }

    /**
     * Returns whether a mod is loaded.
     *
     * @param modname mod id
     * @return {@code true} if it is present and was not set aside after an error
     */
    public static boolean isModLoaded(String modname) {
        ModRegistry current = Loader.INSTANCE.registry;
        if (current == null) {
            return false;
        }
        return current.find(modname)
                .map(container -> !(container instanceof AlloyModContainer mod) || !mod.isDisabled())
                .orElse(false);
    }

    /** Returns an unmodifiable list of all mod containers, in load order. */
    public List<ModContainer> getModList() {
        return this.registry == null ? List.of() : this.registry.all();
    }

    /** Returns an unmodifiable list of the active mod containers. */
    public List<ModContainer> getActiveModList() {
        List<ModContainer> active = new ArrayList<>();
        for (ModContainer container : this.getModList()) {
            if (!(container instanceof AlloyModContainer mod) || !mod.isDisabled()) {
                active.add(container);
            }
        }
        return List.copyOf(active);
    }

    /** Returns an unmodifiable map of containers by mod id. */
    public Map<String, ModContainer> getIndexedModList() {
        return this.registry == null ? Map.of() : this.registry.indexed();
    }

    /** Returns a two-way map between each container and its mod's main object. */
    public BiMap<ModContainer, Object> getModObjectList() {
        BiMap<ModContainer, Object> objects = HashBiMap.create();
        for (ModContainer container : this.getActiveModList()) {
            if (container.getMod() != null) {
                objects.put(container, container.getMod());
            }
        }
        return objects;
    }

    /** Returns the inverse of {@link #getModObjectList()}. */
    public BiMap<Object, ModContainer> getReversedModObjectList() {
        return this.getModObjectList().inverse();
    }

    /**
     * Returns the mod currently running.
     *
     * @return its container, or {@code null} if no mod is involved
     */
    public ModContainer activeModContainer() {
        return this.registry == null ? null : this.registry.activeContainer();
    }

    /** Returns the dummy container that represents Minecraft. */
    public MinecraftDummyContainer getMinecraftModContainer() {
        return this.requireRegistry().minecraftContainer();
    }

    /** Returns the game's {@code config} folder. */
    public File getConfigDir() {
        return this.requireRegistry().configDirectory();
    }

    /** Returns a wrapper of the class loader that holds Forge and the mods. */
    public synchronized ModClassLoader getModClassLoader() {
        if (this.modClassLoader == null) {
            this.modClassLoader = new ModClassLoader(Loader.class.getClassLoader());
        }
        return this.modClassLoader;
    }

    /** Returns the text "Minecraft 1.8.9". */
    public String getMCVersionString() {
        return "Minecraft " + Loader.MC_VERSION;
    }

    /** Returns the text "MCP 9.19". */
    public String getMCPVersionString() {
        return "MCP " + Loader.MCP_VERSION;
    }

    /** Returns the Forge Mod Loader version number. */
    public String getFMLVersionString() {
        return Loader.FML_VERSION;
    }

    /** Returns an empty map: Alloy adds no mention on the title screen. */
    public Map<String, String> getFMLBrandingProperties() {
        return new HashMap<>();
    }

    /** Returns the loading step reached. */
    public LoaderState getLoaderState() {
        return this.registry == null ? LoaderState.NOINIT : this.registry.state();
    }

    /** Returns whether loading has reached or passed the given step. */
    public boolean hasReachedState(LoaderState state) {
        return this.getLoaderState().ordinal() >= state.ordinal();
    }

    /** Returns whether loading is exactly at the given step. */
    public boolean isInState(LoaderState state) {
        return this.getLoaderState() == state;
    }

    /**
     * Returns the state of a mod.
     *
     * @return {@code DISABLED} if it was set aside, otherwise {@code AVAILABLE}
     */
    public LoaderState.ModState getModState(ModContainer selectedMod) {
        boolean disabled = selectedMod instanceof AlloyModContainer mod && mod.isDisabled();
        return disabled ? LoaderState.ModState.DISABLED : LoaderState.ModState.AVAILABLE;
    }

    /** Returns the crash-report entry, labelled "FML", that lists the mods. */
    public ICrashCallable getCallableCrashInformation() {
        return new ICrashCallable() {
            @Override
            public String call() {
                return Loader.this.getCrashInformation();
            }

            @Override
            public String getLabel() {
                return "FML";
            }
        };
    }

    /** Returns the one-line mod list for a crash report. */
    public String getCrashInformation() {
        StringBuilder text = new StringBuilder("Alloy (Forge compatibility layer), mods: ");
        for (ModContainer container : this.getModList()) {
            text.append(container.getModId()).append('{').append(container.getVersion()).append("} ");
        }
        return text.toString().trim();
    }

    /**
     * Returns a mod's custom properties.
     *
     * @return its properties, or an empty map
     */
    public Map<String, String> getCustomModProperties(String modId) {
        ModContainer container = this.getIndexedModList().get(modId);
        return container == null ? Map.of() : container.getCustomModProperties();
    }

    /** No-op: Alloy does not disable mods on request. */
    public void runtimeDisableMod(String modId) {
    }

    // ------------------------------------------------------------------ Forge internal methods

    /** No-op: dependencies between mods are not computed. */
    public void computeDependencies(
            String dependencyString,
            Set<ArtifactVersion> requirements,
            List<ArtifactVersion> dependencies,
            List<ArtifactVersion> dependants) {
    }

    /** Returns an empty list. */
    public List<String> getInjectedAfter(String modId) {
        return List.of();
    }

    /** Returns an empty list. */
    public List<String> getInjectedBefore(String modId) {
        return List.of();
    }

    /** Always {@code true}: Alloy does not do Forge's server handshake. */
    public boolean checkRemoteModList(Map<String, String> modList, Side side) {
        return true;
    }

    /** Not supported: loading is done by Alloy; always throws. */
    public void loadMods() {
        throw Loader.unsupported("loadMods");
    }

    /** Not supported: loading is done by Alloy; always throws. */
    public void preinitializeMods() {
        throw Loader.unsupported("preinitializeMods");
    }

    /** Not supported: loading is done by Alloy; always throws. */
    public void initializeMods() {
        throw Loader.unsupported("initializeMods");
    }

    /** No-op: in Forge this frees loading structures. */
    public void loadingComplete() {
    }

    /** Forge server event, not passed to mods by Alloy; always {@code true}. */
    public boolean serverAboutToStart(Object server) {
        return true;
    }

    /** Forge server event, not passed to mods by Alloy; always {@code true}. */
    public boolean serverStarting(Object server) {
        return true;
    }

    /** Forge server event, not passed to mods by Alloy. */
    public void serverStarted() {
    }

    /** Forge server event, not passed to mods by Alloy. */
    public void serverStopping() {
    }

    /** Forge server event, not passed to mods by Alloy. */
    public void serverStopped() {
    }

    private ModRegistry requireRegistry() {
        ModRegistry current = this.registry;
        if (current == null) {
            throw new IllegalStateException("Alloy: the Forge runtime is not started yet");
        }
        return current;
    }

    private static UnsupportedOperationException unsupported(String method) {
        return new UnsupportedOperationException("Alloy: Loader." + method + " is not supported");
    }
}
