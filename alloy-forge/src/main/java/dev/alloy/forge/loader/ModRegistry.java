package dev.alloy.forge.loader;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraftforge.fml.common.DummyModContainer;
import net.minecraftforge.fml.common.LoaderState;
import net.minecraftforge.fml.common.MinecraftDummyContainer;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.ModMetadata;

/**
 * Registry of the loaded mods, answering what mods ask Forge ("is this mod present?", "which mod
 * is running?"). Forge's replaced {@code Loader} delegates to it. The dummy containers
 * {@code mcp}, {@code FML} and {@code Forge} are always present, as in Forge.
 * It is filled once on the game thread before mods run; only the active mod changes afterwards,
 * hence its {@code volatile} field.
 */
public final class ModRegistry {

    private static final String FML_VERSION = "8.0.99.99";
    private static final String FORGE_VERSION = "11.15.1.2318";
    private static final String MCP_VERSION = "9.19";

    /** All containers by id, in registration (load) order. */
    private final Map<String, ModContainer> byId = new LinkedHashMap<>();

    /** The real mods (no dummy containers), in load order. */
    private final List<AlloyModContainer> mods = new ArrayList<>();

    /** Java package to owning mod, to find who made a call. */
    private final Map<String, ModContainer> packageOwners = new LinkedHashMap<>();

    private final MinecraftDummyContainer minecraft;
    private final File configDirectory;

    /** The mod currently receiving a startup step; {@code null} otherwise. */
    private volatile ModContainer active;

    private volatile LoaderState state = LoaderState.NOINIT;

    /**
     * Creates the registry with its dummy containers.
     *
     * @param minecraftVersion game version
     * @param configDirectory  the game's {@code config} folder
     */
    public ModRegistry(String minecraftVersion, File configDirectory) {
        this.configDirectory = Objects.requireNonNull(configDirectory, "configDirectory");
        this.minecraft = new MinecraftDummyContainer(minecraftVersion);
        this.registerDummy("mcp", "Minecraft Coder Pack", ModRegistry.MCP_VERSION);
        this.registerDummy("FML", "Forge Mod Loader", ModRegistry.FML_VERSION);
        this.registerDummy("Forge", "Minecraft Forge", ModRegistry.FORGE_VERSION);
    }

    /**
     * Registers a mod. A second mod with an id already taken is refused.
     *
     * @return {@code true} if the mod is registered
     */
    public boolean register(AlloyModContainer container) {
        if (this.byId.containsKey(container.getModId())) {
            return false;
        }
        this.byId.put(container.getModId(), container);
        this.mods.add(container);
        for (String ownedPackage : container.getOwnedPackages()) {
            this.packageOwners.putIfAbsent(ownedPackage, container);
        }
        return true;
    }

    /** Returns an unmodifiable copy of all containers, dummies included, in load order. */
    public List<ModContainer> all() {
        return List.copyOf(this.byId.values());
    }

    /** Returns an unmodifiable copy of the mods loaded by Alloy, in load order. */
    public List<AlloyModContainer> mods() {
        return List.copyOf(this.mods);
    }

    /** Finds a container by mod id. */
    public Optional<ModContainer> find(String modId) {
        return Optional.ofNullable(this.byId.get(modId));
    }

    /** Returns an unmodifiable copy of all containers by id. */
    public Map<String, ModContainer> indexed() {
        return Map.copyOf(this.byId);
    }

    /**
     * Returns the "active" mod: the one receiving a startup step, otherwise the one with a class on
     * the call stack (as Forge does to know which mod registers an event listener).
     *
     * @return the container, or {@code null} if no mod is involved (Forge's convention)
     */
    public ModContainer activeContainer() {
        ModContainer current = this.active;
        return current != null ? current : this.ownerFromCallStack().orElse(null);
    }

    /**
     * Sets the running mod.
     *
     * @param container the mod, or {@code null} when it is done
     */
    public void setActive(ModContainer container) {
        this.active = container;
    }

    /** Returns the dummy container that represents Minecraft itself. */
    public MinecraftDummyContainer minecraftContainer() {
        return this.minecraft;
    }

    /** Returns the game's {@code config} folder. */
    public File configDirectory() {
        return this.configDirectory;
    }

    /** Returns the startup step reached, in Forge's terms. */
    public LoaderState state() {
        return this.state;
    }

    /** Records the startup step reached. */
    public void setState(LoaderState newState) {
        this.state = Objects.requireNonNull(newState, "newState");
    }

    private void registerDummy(String modId, String name, String version) {
        ModMetadata metadata = new ModMetadata();
        metadata.modId = modId;
        metadata.name = name;
        metadata.version = version;
        this.byId.put(modId, new DummyModContainer(metadata));
    }

    /** Walks up the call stack to the first class belonging to a mod. */
    private Optional<ModContainer> ownerFromCallStack() {
        if (this.packageOwners.isEmpty()) {
            return Optional.empty();
        }
        return StackWalker.getInstance().walk(frames -> frames
                .map(frame -> this.packageOwners.get(ModRegistry.packageOf(frame.getClassName())))
                .filter(Objects::nonNull)
                .findFirst());
    }

    static String packageOf(String className) {
        int lastDot = className.lastIndexOf('.');
        return lastDot < 0 ? "" : className.substring(0, lastDot);
    }
}
