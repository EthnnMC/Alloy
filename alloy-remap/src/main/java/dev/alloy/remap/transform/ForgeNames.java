package dev.alloy.remap.transform;

/**
 * Forge class and annotation names recognized in bytecode. Plain strings, so this module does not
 * depend on Forge at compile time: class names are internal names, annotations are descriptors.
 */
public final class ForgeNames {

    /** Base class of all Forge events. */
    public static final String EVENT = "net/minecraftforge/fml/common/eventhandler/Event";

    /** Listener list of an event class. */
    public static final String LISTENER_LIST = "net/minecraftforge/fml/common/eventhandler/ListenerList";

    /** {@code @Cancelable}: the event can be cancelled. */
    public static final String CANCELABLE_ANNOTATION = "Lnet/minecraftforge/fml/common/eventhandler/Cancelable;";

    /** {@code @Event.HasResult}: the event carries a result. */
    public static final String HAS_RESULT_ANNOTATION = "Lnet/minecraftforge/fml/common/eventhandler/Event$HasResult;";

    /** {@code @SubscribeEvent}: the method listens to an event. */
    public static final String SUBSCRIBE_EVENT_ANNOTATION =
            "Lnet/minecraftforge/fml/common/eventhandler/SubscribeEvent;";

    /** {@code @SideOnly}: the class or member exists only on the client or only on the server. */
    public static final String SIDE_ONLY_ANNOTATION = "Lnet/minecraftforge/fml/relauncher/SideOnly;";

    /** {@code @Mod}: the main class of a mod. */
    public static final String MOD_ANNOTATION = "Lnet/minecraftforge/fml/common/Mod;";

    /** Forge package prefix. */
    public static final String FORGE_PACKAGE = "net/minecraftforge/";

    /** Minecraft package prefix (note: not a prefix of {@link #FORGE_PACKAGE}). */
    public static final String MINECRAFT_PACKAGE = "net/minecraft/";

    private ForgeNames() {
        // Constants class: no instances.
    }
}
