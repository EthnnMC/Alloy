package dev.alloy.forge.loader;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraftforge.fml.common.DummyModContainer;
import net.minecraftforge.fml.common.LoadController;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ModMetadata;
import net.minecraftforge.fml.common.event.FMLEvent;

/**
 * What Forge knows about a loaded mod (id, jar, main object) and the way to pass it the startup
 * steps. Like Forge's {@code FMLModContainer}, it instantiates the {@code @Mod} class through its
 * no-argument constructor and indexes its {@code @Mod.EventHandler} methods by exact event type.
 */
public final class AlloyModContainer extends DummyModContainer {

    private final String className;
    private final File source;
    private final Map<String, Object> descriptor;
    private final List<String> ownedPackages;

    /** The mod's {@code @Mod.EventHandler} methods, by FML event class. */
    private final Map<Class<?>, Method> stateHandlers = new LinkedHashMap<>();

    /** The mod's main object; {@code null} until the mod is constructed. */
    private Object modInstance;

    /** Set once a step fails: the mod then receives nothing. */
    private boolean disabled;

    /**
     * Describes a mod found in a jar.
     *
     * @param metadata      metadata (from {@code mcmod.info} or the {@code @Mod} annotation)
     * @param className     name of the {@code @Mod} class
     * @param source        the mod jar (the rewritten copy that is actually loaded)
     * @param descriptor    values of the {@code @Mod} annotation
     * @param ownedPackages Java packages contained in the jar
     */
    public AlloyModContainer(
            ModMetadata metadata,
            String className,
            File source,
            Map<String, Object> descriptor,
            List<String> ownedPackages) {
        super(metadata);
        this.className = Objects.requireNonNull(className, "className");
        this.source = Objects.requireNonNull(source, "source");
        this.descriptor = Map.copyOf(descriptor);
        this.ownedPackages = List.copyOf(ownedPackages);
    }

    /**
     * Constructs the mod: loads its class, creates its main object and finds its
     * {@code @Mod.EventHandler} methods.
     *
     * @param loader the mod class loader
     * @throws ReflectiveOperationException if the class is missing or has no public no-argument constructor
     */
    public void construct(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> modClass = Class.forName(this.className, true, loader);
        this.modInstance = modClass.getDeclaredConstructor().newInstance();
        for (Method method : modClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Mod.EventHandler.class)
                    && method.getParameterCount() == 1
                    && FMLEvent.class.isAssignableFrom(method.getParameterTypes()[0])
                    && !Modifier.isStatic(method.getModifiers())) {
                method.setAccessible(true);
                this.stateHandlers.put(method.getParameterTypes()[0], method);
            }
        }
    }

    /**
     * Passes a startup step to the mod, if it has a method for that event type.
     *
     * @param event the FML event (pre-initialisation, initialisation, ...)
     * @throws ReflectiveOperationException if the mod's method cannot be called
     * @throws InvocationTargetException    if the mod throws (the cause is in {@code getCause()})
     */
    public void dispatch(FMLEvent event) throws ReflectiveOperationException {
        Method handler = this.stateHandlers.get(event.getClass());
        if (handler != null && this.modInstance != null) {
            handler.invoke(this.modInstance, event);
        }
    }

    /** Returns whether the mod was set aside after an error and must receive nothing more. */
    public boolean isDisabled() {
        return this.disabled;
    }

    /** Sets the mod aside for the rest of the session. */
    public void disable() {
        this.disabled = true;
    }

    /** Returns the dotted name of the {@code @Mod} class. */
    public String className() {
        return this.className;
    }

    @Override
    public File getSource() {
        return this.source;
    }

    @Override
    public Object getMod() {
        return this.modInstance;
    }

    @Override
    public boolean matches(Object mod) {
        return mod == this.modInstance;
    }

    @Override
    public boolean registerBus(com.google.common.eventbus.EventBus bus, LoadController controller) {
        // Forge registers the container on its internal bus here; Alloy calls the container directly.
        return true;
    }

    @Override
    public List<String> getOwnedPackages() {
        return this.ownedPackages;
    }

    @Override
    public Map<String, String> getSharedModDescriptor() {
        Map<String, String> shared = new LinkedHashMap<>();
        shared.put("modsystem", "FML");
        shared.put("id", this.getModId());
        shared.put("version", this.getDisplayVersion());
        shared.put("name", this.getName());
        return shared;
    }

    @Override
    public String getGuiClassName() {
        Object guiFactory = this.descriptor.get("guiFactory");
        return guiFactory instanceof String name && !name.isEmpty() ? name : null;
    }

    @Override
    public String toString() {
        return "AlloyMod:" + this.getModId() + "{" + this.getVersion() + "}";
    }
}
