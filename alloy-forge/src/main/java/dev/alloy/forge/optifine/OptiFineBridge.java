package dev.alloy.forge.optifine;

import dev.alloy.bridge.BridgeLogger;
import dev.alloy.bridge.ForgeGate;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Connects OptiFine's dormant Forge call sites to Alloy's Forge. OptiFine keeps Forge's hooks in
 * the classes it rewrites, each guarded by a {@code Reflector} entry that only resolves when the
 * game loader can see Forge, which never happens here. Pointing chosen entries at Forge members
 * (through the guards of {@link OptiFineCalls} for anything that runs mod code) makes OptiFine fire
 * those events itself; every other entry stays off, so OptiFine still believes Forge is absent.
 */
public final class OptiFineBridge {

    private static final String REFLECTOR_CLASS = "net.optifine.reflect.Reflector";
    private static final String CHECKED_FIELD = "checked";
    private static final String CLASS_ENTRY = "ReflectorClass";
    private static final List<String> FORGE_PREFIXES = List.of("net.minecraftforge.", "cpw.mods.");

    private static final String EVENT_PACKAGE = "net.minecraftforge.client.event.";
    private static final String CALLS = OptiFineCalls.class.getName();
    private static final String FOG_COLORS = OptiFineBridge.EVENT_PACKAGE + "EntityViewRenderEvent$FogColors";
    private static final String CAMERA_SETUP = OptiFineBridge.EVENT_PACKAGE + "EntityViewRenderEvent$CameraSetup";

    /** Reflector entries to switch on. Events Alloy already fires from its own hooks are left out. */
    private static final List<Entry> ENTRIES = List.of(
            // What Reflector.postForgeBusEvent needs to reach the event bus.
            Entry.field("MinecraftForge_EVENT_BUS", OptiFineBridge.CALLS, "EVENT_BUS"),
            Entry.method("EventBus_post", OptiFineCalls.SafeBus.class.getName(), "post"),
            // EntityViewRenderEvent: field of view, fog, camera.
            Entry.method("ForgeHooksClient_getFOVModifier", OptiFineBridge.CALLS, "getFOVModifier"),
            Entry.method("ForgeHooksClient_getFogDensity", OptiFineBridge.CALLS, "getFogDensity"),
            Entry.method("ForgeHooksClient_onFogRender", OptiFineBridge.CALLS, "onFogRender"),
            Entry.constructor("EntityViewRenderEvent_FogColors_Constructor", OptiFineBridge.FOG_COLORS),
            Entry.field("EntityViewRenderEvent_FogColors_red", OptiFineBridge.FOG_COLORS, "red"),
            Entry.field("EntityViewRenderEvent_FogColors_green", OptiFineBridge.FOG_COLORS, "green"),
            Entry.field("EntityViewRenderEvent_FogColors_blue", OptiFineBridge.FOG_COLORS, "blue"),
            Entry.constructor("EntityViewRenderEvent_CameraSetup_Constructor", OptiFineBridge.CAMERA_SETUP),
            Entry.field("EntityViewRenderEvent_CameraSetup_yaw", OptiFineBridge.CAMERA_SETUP, "yaw"),
            Entry.field("EntityViewRenderEvent_CameraSetup_pitch", OptiFineBridge.CAMERA_SETUP, "pitch"),
            Entry.field("EntityViewRenderEvent_CameraSetup_roll", OptiFineBridge.CAMERA_SETUP, "roll"),
            // RenderHandEvent.
            Entry.method("ForgeHooksClient_renderFirstPersonHand", OptiFineBridge.CALLS, "renderFirstPersonHand"),
            // RenderBlockOverlayEvent: fire, water and block-in-head overlays.
            Entry.method("ForgeEventFactory_renderBlockOverlay", OptiFineBridge.CALLS, "renderBlockOverlay"),
            Entry.method("ForgeEventFactory_renderFireOverlay", OptiFineBridge.CALLS, "renderFireOverlay"),
            Entry.method("ForgeEventFactory_renderWaterOverlay", OptiFineBridge.CALLS, "renderWaterOverlay"),
            Entry.field("RenderBlockOverlayEvent_OverlayType_BLOCK",
                    OptiFineBridge.EVENT_PACKAGE + "RenderBlockOverlayEvent$OverlayType", "BLOCK"),
            // RenderItemInFrameEvent.
            Entry.constructor("RenderItemInFrameEvent_Constructor",
                    OptiFineBridge.EVENT_PACKAGE + "RenderItemInFrameEvent"));

    private OptiFineBridge() {
    }

    /**
     * Makes OptiFine settle on "Forge is absent", switches the chosen entries on, then opens
     * {@link ForgeGate}. Without OptiFine only the gate is opened; an entry that does not fit this
     * OptiFine build is skipped, which only leaves its event unpublished.
     *
     * @param gameLoader the loader that holds OptiFine
     * @param logger     Alloy's log
     */
    public static void connect(ClassLoader gameLoader, BridgeLogger logger) {
        Class<?> reflector;
        try {
            reflector = Class.forName(OptiFineBridge.REFLECTOR_CLASS, true, gameLoader);
        } catch (ClassNotFoundException | LinkageError e) {
            logger.info("OptiFine not found: its Forge events stay off");
            ForgeGate.open();
            return;
        }
        try {
            OptiFineBridge.settleForgeLookups(reflector);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            // Unknown OptiFine build: Forge classes stay hidden from game code rather than risk OptiFine finding them.
            logger.error("OptiFine bridge: cannot settle OptiFine's Forge lookups, so mixin code cannot use Forge classes", e);
            return;
        }
        ForgeGate.open();
        OptiFineCalls.setLogger(logger);
        ClassLoader forgeLoader = OptiFineBridge.class.getClassLoader();
        List<String> skipped = new ArrayList<>();
        for (Entry entry : OptiFineBridge.ENTRIES) {
            try {
                entry.connect(reflector, forgeLoader);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                skipped.add(entry.reflectorField() + " (" + e + ")");
            }
        }
        int connected = OptiFineBridge.ENTRIES.size() - skipped.size();
        logger.info("OptiFine bridge: " + connected + " of " + OptiFineBridge.ENTRIES.size() + " Forge entries connected");
        skipped.forEach(reason -> logger.warn("OptiFine bridge: skipped " + reason));
    }

    /**
     * Makes OptiFine look for every Forge class now, while the game's loader still hides them.
     * OptiFine remembers each answer, so it will not notice Forge once the gate opens.
     */
    private static void settleForgeLookups(Class<?> reflector) throws ReflectiveOperationException {
        for (Field entry : reflector.getFields()) {
            if (!Modifier.isStatic(entry.getModifiers()) || !entry.getType().getSimpleName().equals(OptiFineBridge.CLASS_ENTRY)) {
                continue;
            }
            Object holder = entry.get(null);
            if (holder == null) {
                continue;
            }
            Object targetName = holder.getClass().getField("targetClassName").get(holder);
            if (targetName instanceof String name && OptiFineBridge.FORGE_PREFIXES.stream().anyMatch(name::startsWith)) {
                holder.getClass().getMethod("getTargetClass").invoke(holder);
            }
        }
    }

    /** What a Reflector entry wraps. */
    private enum Kind {
        METHOD("targetMethod"),
        CONSTRUCTOR("targetConstructor"),
        FIELD("targetField");

        private final String targetField;

        Kind(String targetField) {
            this.targetField = targetField;
        }
    }

    /**
     * One Reflector entry and the Forge member it must point at.
     *
     * @param reflectorField name of the static field of {@code Reflector} holding the entry
     * @param kind           what the entry wraps
     * @param forgeClass     dotted name of the class holding the member (Forge's, or {@link OptiFineCalls})
     * @param member         method or field name; unused for a constructor
     */
    private record Entry(String reflectorField, Kind kind, String forgeClass, String member) {

        static Entry method(String reflectorField, String forgeClass, String name) {
            return new Entry(reflectorField, Kind.METHOD, forgeClass, name);
        }

        static Entry constructor(String reflectorField, String forgeClass) {
            return new Entry(reflectorField, Kind.CONSTRUCTOR, forgeClass, "");
        }

        static Entry field(String reflectorField, String forgeClass, String name) {
            return new Entry(reflectorField, Kind.FIELD, forgeClass, name);
        }

        void connect(Class<?> reflector, ClassLoader forgeLoader) throws ReflectiveOperationException {
            Object holder = reflector.getField(this.reflectorField).get(null);
            if (holder == null) {
                throw new NoSuchFieldException("Reflector." + this.reflectorField + " is null");
            }
            // initialize = false: the Forge class only runs its static code when OptiFine first calls it.
            Class<?> target = Class.forName(this.forgeClass, false, forgeLoader);
            AccessibleObject member = switch (this.kind) {
                case METHOD -> Entry.singleMethod(target, this.member);
                case CONSTRUCTOR -> Entry.constructorFor(holder, target);
                case FIELD -> target.getField(this.member);
            };
            member.setAccessible(true);
            Class<?> holderClass = holder.getClass();
            holderClass.getField(this.kind.targetField).set(holder, member);
            holderClass.getField(OptiFineBridge.CHECKED_FIELD).setBoolean(holder, true);
        }

        /** OptiFine names these methods without their parameters, so the name must be unambiguous. */
        private static Method singleMethod(Class<?> target, String name) throws NoSuchMethodException {
            Method found = null;
            for (Method method : target.getMethods()) {
                if (!method.getName().equals(name)) {
                    continue;
                }
                if (found != null) {
                    throw new NoSuchMethodException(target.getName() + "." + name + " is overloaded");
                }
                found = method;
            }
            if (found == null) {
                throw new NoSuchMethodException(target.getName() + "." + name);
            }
            return found;
        }

        /** The entry itself lists the parameter types OptiFine will pass. */
        private static Constructor<?> constructorFor(Object holder, Class<?> target) throws ReflectiveOperationException {
            Field parameterTypes = holder.getClass().getField("parameterTypes");
            return target.getConstructor((Class<?>[]) parameterTypes.get(holder));
        }
    }
}
