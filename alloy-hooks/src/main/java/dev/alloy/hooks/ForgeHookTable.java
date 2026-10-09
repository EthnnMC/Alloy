package dev.alloy.hooks;

import dev.alloy.hooks.inject.Anchor;
import dev.alloy.hooks.inject.AnchorCall;
import dev.alloy.hooks.inject.AnchorPosition;
import dev.alloy.hooks.inject.HeadCall;
import dev.alloy.hooks.inject.HeadCancel;
import dev.alloy.hooks.inject.HeadCancelThenReplaceParameter;
import dev.alloy.hooks.inject.HeadReplaceParameter;
import dev.alloy.hooks.inject.HookArgument;
import dev.alloy.hooks.inject.HookCall;
import dev.alloy.hooks.inject.InvokeAnchor;
import dev.alloy.hooks.inject.Occurrence;
import dev.alloy.hooks.inject.RedirectInvoke;
import dev.alloy.hooks.inject.ReturnCall;
import dev.alloy.hooks.inject.ReturnFilter;
import java.util.ArrayList;
import java.util.List;

/**
 * The Forge 1.8.9 hook table: one entry per game event Alloy relays to mods, built into the
 * catalog by {@link HookCatalog#forgeDefaults()}. Each entry was checked against real Lunar 1.8.9
 * classes; comments say what Lunar's mixins did to the target method when that explains the
 * choice. An entry reads: id, class, method and descriptor, then the injections; in
 * {@code HookCall.to(name, descriptor, arguments...)} the name and descriptor are those of a
 * {@code dev.alloy.bridge.GameHooks} method.
 */
final class ForgeHookTable {

    private static final String VOID = "()V";
    private static final String OBJECT_TO_VOID = "(Ljava/lang/Object;)V";
    private static final String OBJECT_TO_BOOLEAN = "(Ljava/lang/Object;)Z";
    private static final String TWO_OBJECTS_TO_BOOLEAN = "(Ljava/lang/Object;Ljava/lang/Object;)Z";
    private static final String STRING_TO_VOID = "(Ljava/lang/String;)V";
    private static final String STRING_TO_BOOLEAN = "(Ljava/lang/String;)Z";

    private ForgeHookTable() {
        // Static-only class.
    }

    /** Builds the full table, grouped by domain. */
    static List<Hook> hooks() {
        List<Hook> hooks = new ArrayList<>();
        ForgeHookTable.addLifecycle(hooks);
        ForgeHookTable.addTicks(hooks);
        ForgeHookTable.addInput(hooks);
        ForgeHookTable.addScreens(hooks);
        ForgeHookTable.addChatAndNetwork(hooks);
        ForgeHookTable.addWorldAndEntities(hooks);
        ForgeHookTable.addOverlay(hooks);
        ForgeHookTable.addRendering(hooks);
        return hooks;
    }

    /** Game startup and mod lifecycle (construction, preInit, init). */
    private static void addLifecycle(List<Hook> hooks) {
        // The only hook installed before the Forge runtime exists: it starts the runtime.
        hooks.add(Hook.inMethod("boot.main", GameClasses.MAIN, "main", "([Ljava/lang/String;)V",
                new HeadCall(HookCall.to("gameMain", "([Ljava/lang/String;)V", HookArgument.parameter(1))))
                .activeFromTheStart());

        // startGame keeps Minecraft's order: ... refreshResources() ... new GuiIngame(this) ...
        InvokeAnchor firstResourceLoad = Anchor.invoke(GameClasses.MINECRAFT, "refreshResources", ForgeHookTable.VOID);
        hooks.add(Hook.inMethod("life.construct", GameClasses.MINECRAFT, "startGame", ForgeHookTable.VOID,
                new AnchorCall(AnchorPosition.BEFORE, firstResourceLoad, Occurrence.FIRST,
                        HookCall.to("modsConstruct", ForgeHookTable.VOID))));
        hooks.add(Hook.inMethod("life.preinit", GameClasses.MINECRAFT, "startGame", ForgeHookTable.VOID,
                new AnchorCall(AnchorPosition.AFTER, firstResourceLoad, Occurrence.FIRST,
                        HookCall.to("modsPreInit", ForgeHookTable.VOID))));
        hooks.add(Hook.inMethod("life.init", GameClasses.MINECRAFT, "startGame", ForgeHookTable.VOID,
                new AnchorCall(AnchorPosition.BEFORE, Anchor.newInstance(GameClasses.GUI_INGAME), Occurrence.FIRST,
                        HookCall.to("modsInit", ForgeHookTable.VOID))));
    }

    /** Client, render and player ticks. */
    private static void addTicks(List<Hook> hooks) {
        HookArgument self = HookArgument.self();

        hooks.add(Hook.inMethod("tick.client", GameClasses.MINECRAFT, "runTick", ForgeHookTable.VOID,
                new HeadCall(HookCall.to("clientTickStart", ForgeHookTable.VOID)),
                new ReturnCall(HookCall.to("clientTickEnd", ForgeHookTable.VOID), Occurrence.EVERY)));

        // Lunar already wraps this call in its own handlers; the call itself stayed direct.
        InvokeAnchor frameRender = Anchor.invoke(GameClasses.ENTITY_RENDERER, "updateCameraAndRender", "(FJ)V");
        hooks.add(Hook.inMethod("tick.render", GameClasses.MINECRAFT, "runGameLoop", ForgeHookTable.VOID,
                new AnchorCall(AnchorPosition.BEFORE, frameRender, Occurrence.FIRST,
                        HookCall.to("renderTickStart", ForgeHookTable.VOID)),
                new AnchorCall(AnchorPosition.AFTER, frameRender, Occurrence.FIRST,
                        HookCall.to("renderTickEnd", ForgeHookTable.VOID))));

        hooks.add(Hook.inMethod("tick.player", GameClasses.ENTITY_PLAYER, "onUpdate", ForgeHookTable.VOID,
                new HeadCall(HookCall.to("playerTickStart", ForgeHookTable.OBJECT_TO_VOID, self)),
                new ReturnCall(HookCall.to("playerTickEnd", ForgeHookTable.OBJECT_TO_VOID, self), Occurrence.EVERY)));
    }

    /**
     * Keyboard and mouse. Lunar moved the screen calls out of these methods, but the
     * {@code while (Mouse.next())} and {@code while (Keyboard.next())} loop heads are intact, so
     * control is taken there on every event.
     */
    private static void addInput(List<Hook> hooks) {
        InvokeAnchor mouseNext = Anchor.invoke(GameClasses.LWJGL_MOUSE, "next", "()Z");
        InvokeAnchor keyboardNext = Anchor.invoke(GameClasses.LWJGL_KEYBOARD, "next", "()Z");

        hooks.add(Hook.inMethod("input.mouse", GameClasses.MINECRAFT, "runTick", ForgeHookTable.VOID,
                new RedirectInvoke(mouseNext, HookCall.to("mouseNext", "()Z"), false)));
        hooks.add(Hook.inMethod("input.keyboard", GameClasses.MINECRAFT, "runTick", ForgeHookTable.VOID,
                new RedirectInvoke(keyboardNext, HookCall.to("keyboardNext", "()Z"), false)));
        // Inside a screen, the hook also receives the screen itself.
        HookCall guiMouseNext = HookCall.to("guiMouseNext", ForgeHookTable.OBJECT_TO_BOOLEAN);
        HookCall guiKeyboardNext = HookCall.to("guiKeyboardNext", ForgeHookTable.OBJECT_TO_BOOLEAN);
        hooks.add(Hook.inMethod("gui.input.mouse", GameClasses.GUI_SCREEN, "handleInput", ForgeHookTable.VOID,
                new RedirectInvoke(mouseNext, guiMouseNext, true)));
        hooks.add(Hook.inMethod("gui.input.keyboard", GameClasses.GUI_SCREEN, "handleInput", ForgeHookTable.VOID,
                new RedirectInvoke(keyboardNext, guiKeyboardNext, true)));
    }

    /** Screen opening, initialization, drawing and buttons. */
    private static void addScreens(List<Hook> hooks) {
        HookArgument self = HookArgument.self();

        // Installed at the head: it runs before Lunar's handler, which can cancel the opening.
        hooks.add(Hook.inMethod("gui.open", GameClasses.MINECRAFT, "displayGuiScreen",
                "(L" + GameClasses.GUI_SCREEN + ";)V",
                new HeadCancelThenReplaceParameter(1,
                        HookCall.to("guiOpen", ForgeHookTable.OBJECT_TO_BOOLEAN, HookArgument.parameter(1)),
                        HookCall.to("guiOpenResult", "()Ljava/lang/Object;"))));

        // Public method that became a mere Lunar facade: wrapping it wraps everything it delegates to.
        hooks.add(Hook.inMethod("gui.init", GameClasses.GUI_SCREEN, "setWorldAndResolution",
                "(L" + GameClasses.MINECRAFT + ";II)V",
                new HeadCall(HookCall.to("guiInitPre", ForgeHookTable.OBJECT_TO_VOID, self)),
                new ReturnCall(HookCall.to("guiInitPost", ForgeHookTable.OBJECT_TO_VOID, self), Occurrence.LAST)));

        // Lunar moved these two calls into synthetic methods, so they are searched everywhere.
        hooks.add(Hook.inAnyMethod("gui.draw", GameClasses.ENTITY_RENDERER,
                new RedirectInvoke(Anchor.invoke(GameClasses.GUI_SCREEN, "drawScreen", "(IIF)V"),
                        HookCall.to("drawScreen", "(Ljava/lang/Object;IIF)V"), false)));
        hooks.add(Hook.inAnyMethod("gui.action", GameClasses.GUI_SCREEN,
                new RedirectInvoke(
                        Anchor.invoke(GameClasses.GUI_SCREEN, "actionPerformed", "(L" + GameClasses.GUI_BUTTON + ";)V"),
                        HookCall.to("actionPerformed", "(Ljava/lang/Object;Ljava/lang/Object;)V"), false)));

        hooks.add(Hook.inMethod("gui.background", GameClasses.GUI_SCREEN, "drawBackground", "(I)V",
                new ReturnCall(HookCall.to("backgroundDrawn", ForgeHookTable.OBJECT_TO_VOID, self), Occurrence.EVERY)));
        // The last RETURN is the normal exit; the first one is a cancellation decided by Lunar.
        hooks.add(Hook.inMethod("gui.background.world", GameClasses.GUI_SCREEN, "drawWorldBackground", "(I)V",
                new ReturnCall(HookCall.to("worldBackgroundDrawn", ForgeHookTable.OBJECT_TO_VOID, self),
                        Occurrence.LAST)));
    }

    /** Client commands, completion, received messages and joining a server. */
    private static void addChatAndNetwork(List<Hook> hooks) {
        HookArgument first = HookArgument.parameter(1);
        HookArgument second = HookArgument.parameter(2);

        hooks.add(Hook.inMethod("chat.command", GameClasses.GUI_SCREEN, "sendChatMessage", "(Ljava/lang/String;Z)V",
                new HeadCancel(HookCall.to("clientCommand", "(Ljava/lang/String;Z)Z", first, second))));
        hooks.add(Hook.inMethod("chat.complete.request", GameClasses.GUI_CHAT, "sendAutocompleteRequest",
                "(Ljava/lang/String;Ljava/lang/String;)V",
                new HeadCall(HookCall.to("autoComplete", "(Ljava/lang/String;Ljava/lang/String;)V", first, second))));
        HookCall mergeCompletions = HookCall.to(
                "autoCompleteResponse", "([Ljava/lang/String;)[Ljava/lang/String;", first);
        hooks.add(Hook.inMethod("chat.complete.response", GameClasses.GUI_CHAT, "onAutocompleteResponse",
                "([Ljava/lang/String;)V", new HeadReplaceParameter(1, mergeCompletions, false)));

        // The method runs first on the network thread, then on the client thread: the runtime sorts it out.
        hooks.add(Hook.inMethod("chat.received", GameClasses.NET_HANDLER_PLAY_CLIENT, "handleChat",
                "(Lnet/minecraft/network/play/server/S02PacketChat;)V",
                new HeadCancel(HookCall.to("chatReceived", ForgeHookTable.OBJECT_TO_BOOLEAN, first))));
        hooks.add(Hook.inMethod("net.join", GameClasses.NET_HANDLER_PLAY_CLIENT, "handleJoinGame",
                "(Lnet/minecraft/network/play/server/S01PacketJoinGame;)V",
                new HeadCall(HookCall.to("joinGame", ForgeHookTable.OBJECT_TO_VOID, HookArgument.self()))));
    }

    /** World loading, entity arrival and updates, attacks. */
    private static void addWorldAndEntities(List<Hook> hooks) {
        HookArgument self = HookArgument.self();
        HookArgument first = HookArgument.parameter(1);
        HookCall entityJoinWorld = HookCall.to("entityJoinWorld", ForgeHookTable.TWO_OBJECTS_TO_BOOLEAN, self, first);

        hooks.add(Hook.inMethod("world.unload", GameClasses.MINECRAFT, "loadWorld",
                "(L" + GameClasses.WORLD_CLIENT + ";Ljava/lang/String;)V",
                new HeadCall(HookCall.to("worldUnload", ForgeHookTable.VOID))));

        // In a constructor, only the return instructions can be hooked.
        hooks.add(Hook.inMethod("world.load", GameClasses.WORLD_CLIENT, "<init>",
                "(L" + GameClasses.NET_HANDLER_PLAY_CLIENT + ";Lnet/minecraft/world/WorldSettings;"
                        + "ILnet/minecraft/world/EnumDifficulty;Lnet/minecraft/profiler/Profiler;)V",
                new ReturnCall(HookCall.to("worldLoad", ForgeHookTable.OBJECT_TO_VOID, self), Occurrence.EVERY)));

        hooks.add(Hook.inMethod("entity.join.spawn", GameClasses.WORLD, "spawnEntityInWorld",
                "(Lnet/minecraft/entity/Entity;)Z", new HeadCancel(entityJoinWorld)));
        hooks.add(Hook.inMethod("entity.join.surroundings", GameClasses.WORLD, "joinEntityInSurroundings",
                "(Lnet/minecraft/entity/Entity;)V", new HeadCancel(entityJoinWorld)));
        hooks.add(Hook.inMethod("entity.living.update", GameClasses.ENTITY_LIVING_BASE, "onUpdate", ForgeHookTable.VOID,
                new HeadCancel(HookCall.to("livingUpdate", ForgeHookTable.OBJECT_TO_BOOLEAN, self))));
        hooks.add(Hook.inMethod("entity.attack", GameClasses.ENTITY_PLAYER, "attackTargetEntityWithCurrentItem",
                "(Lnet/minecraft/entity/Entity;)V",
                new HeadCancel(HookCall.to("attackEntity", ForgeHookTable.TWO_OBJECTS_TO_BOOLEAN, self, first))));
        hooks.add(Hook.inMethod("entity.interact", GameClasses.ENTITY_PLAYER, "interactWith",
                "(L" + GameClasses.ENTITY + ";)Z",
                new HeadCancel(HookCall.to("entityInteract", ForgeHookTable.TWO_OBJECTS_TO_BOOLEAN, self, first))));

        // Lunar moved this call into a synthetic method, so it is searched everywhere.
        hooks.add(Hook.inAnyMethod("player.use.item", GameClasses.MINECRAFT,
                new RedirectInvoke(
                        Anchor.invoke(GameClasses.PLAYER_CONTROLLER_MP, "sendUseItem", "(L" + GameClasses.ENTITY_PLAYER
                                + ";L" + GameClasses.WORLD + ";L" + GameClasses.ITEM_STACK + ";)Z"),
                        HookCall.to("useItem",
                                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z"),
                        false)));

        ForgeHookTable.addEntitySounds(hooks);

        hooks.add(Hook.inMethod("entity.construct", GameClasses.ENTITY, "<init>", "(L" + GameClasses.WORLD + ";)V",
                new ReturnCall(HookCall.to("entityConstructing", ForgeHookTable.OBJECT_TO_VOID, self),
                        Occurrence.EVERY)));
        hooks.add(Hook.inMethod("entity.living.jump", GameClasses.ENTITY_LIVING_BASE, "jump", ForgeHookTable.VOID,
                new ReturnCall(HookCall.to("livingJump", ForgeHookTable.OBJECT_TO_VOID, self), Occurrence.EVERY)));

        // The client never calls Chunk.onChunkLoad: its chunks are reported where they are created.
        hooks.add(Hook.inMethod("chunk.load.client", GameClasses.CHUNK_PROVIDER_CLIENT, "loadChunk",
                "(II)L" + GameClasses.CHUNK + ";",
                new ReturnFilter(HookCall.to("chunkLoaded", "(Ljava/lang/Object;)Ljava/lang/Object;"))));
        hooks.add(Hook.inMethod("chunk.load", GameClasses.CHUNK, "onChunkLoad", ForgeHookTable.VOID,
                new ReturnCall(HookCall.to("chunkLoad", ForgeHookTable.OBJECT_TO_VOID, self), Occurrence.EVERY)));
        hooks.add(Hook.inMethod("chunk.unload", GameClasses.CHUNK, "onChunkUnload", ForgeHookTable.VOID,
                new ReturnCall(HookCall.to("chunkUnload", ForgeHookTable.OBJECT_TO_VOID, self), Occurrence.EVERY)));
    }

    /** The three methods through which an entity plays a sound. */
    private static void addEntitySounds(List<Hook> hooks) {
        HookArgument self = HookArgument.self();
        HookArgument first = HookArgument.parameter(1);
        HookArgument second = HookArgument.parameter(2);
        HookArgument third = HookArgument.parameter(3);
        HookArgument fourth = HookArgument.parameter(4);
        String descriptor = "(Ljava/lang/Object;Ljava/lang/String;FF)Z";

        hooks.add(Hook.inMethod("sound.entity", GameClasses.WORLD, "playSoundAtEntity",
                "(L" + GameClasses.ENTITY + ";Ljava/lang/String;FF)V",
                new HeadCancel(HookCall.to("entitySound", descriptor, first, second, third, fourth))));
        hooks.add(Hook.inMethod("sound.entity.near", GameClasses.WORLD, "playSoundToNearExcept",
                "(L" + GameClasses.ENTITY_PLAYER + ";Ljava/lang/String;FF)V",
                new HeadCancel(HookCall.to("entitySound", descriptor, first, second, third, fourth))));
        hooks.add(Hook.inMethod("sound.entity.self", GameClasses.ENTITY_PLAYER_SP, "playSound",
                "(Ljava/lang/String;FF)V",
                new HeadCancel(HookCall.to("entitySound", descriptor, self, first, second, third))));
    }

    /**
     * In-game overlay. {@code GuiIngame} is not replaced by Forge's version, which would make
     * Lunar's overlay disappear: events fire from the original class.
     */
    private static void addOverlay(List<Hook> hooks) {
        String resolution = "Lnet/minecraft/client/gui/ScaledResolution;";

        // The last RETURN is the normal exit; the others are cancellations decided by Lunar.
        hooks.add(Hook.inMethod("overlay.all", GameClasses.GUI_INGAME, "renderGameOverlay", "(F)V",
                new HeadCancel(HookCall.to("overlayPre", "(F)Z", HookArgument.parameter(1))),
                new ReturnCall(HookCall.to("overlayPost", ForgeHookTable.VOID), Occurrence.LAST)));

        hooks.add(ForgeHookTable.overlayElement("overlay.chat", "CHAT", GameClasses.GUI_NEW_CHAT, "drawChat", "(I)V"));
        hooks.add(ForgeHookTable.overlayElement("overlay.hotbar", "HOTBAR",
                GameClasses.GUI_INGAME, "renderTooltip", "(" + resolution + "F)V"));
        hooks.add(ForgeHookTable.overlayElement("overlay.experience", "EXPERIENCE",
                GameClasses.GUI_INGAME, "renderExpBar", "(" + resolution + "I)V"));
        hooks.add(ForgeHookTable.overlayElement("overlay.jumpbar", "JUMPBAR",
                GameClasses.GUI_INGAME, "renderHorseJumpBar", "(" + resolution + "I)V"));
        hooks.add(ForgeHookTable.overlayElement("overlay.bosshealth", "BOSSHEALTH",
                GameClasses.GUI_INGAME, "renderBossHealth", ForgeHookTable.VOID));
        hooks.add(ForgeHookTable.overlayElement("overlay.helmet", "HELMET",
                GameClasses.GUI_INGAME, "renderPumpkinOverlay", "(" + resolution + ")V"));
        hooks.add(ForgeHookTable.overlayElement("overlay.portal", "PORTAL",
                GameClasses.GUI_INGAME, "renderPortal", "(F" + resolution + ")V"));
        hooks.add(ForgeHookTable.overlayElement("overlay.playerlist", "PLAYER_LIST",
                GameClasses.GUI_PLAYER_TAB_OVERLAY, "renderPlayerlist",
                "(ILnet/minecraft/scoreboard/Scoreboard;Lnet/minecraft/scoreboard/ScoreObjective;)V"));

        hooks.add(ForgeHookTable.overlayElement("overlay.debug", "DEBUG",
                GameClasses.GUI_OVERLAY_DEBUG, "renderDebugInfo", "(" + resolution + ")V"));

        // One method draws health, armor, food and air: the runtime publishes the four events around it.
        HookArgument stats = HookArgument.constant("PLAYER_STATS");
        hooks.add(Hook.inMethod("overlay.stats", GameClasses.GUI_INGAME, "renderPlayerStats", "(" + resolution + ")V",
                new HeadCancel(HookCall.to("overlayElementPre", ForgeHookTable.STRING_TO_BOOLEAN, stats)),
                new ReturnCall(HookCall.to("overlayElementPost", ForgeHookTable.STRING_TO_VOID, stats),
                        Occurrence.LAST)));

        // showCrosshair() answers a question: nothing to report afterwards.
        hooks.add(Hook.inMethod("overlay.crosshairs", GameClasses.GUI_INGAME, "showCrosshair", "()Z",
                new HeadCancel(HookCall.to("overlayElementPre", ForgeHookTable.STRING_TO_BOOLEAN,
                        HookArgument.constant("CROSSHAIRS")))));
    }

    /**
     * Builds the entry of an overlay element: cancellable at the start of the method that draws
     * it, reported at each of its exits.
     *
     * @param element name of the {@code RenderGameOverlayEvent.ElementType} constant
     */
    private static Hook overlayElement(
            String id, String element, String className, String methodName, String descriptor) {
        HookArgument name = HookArgument.constant(element);
        HookCall before = HookCall.to("overlayElementPre", ForgeHookTable.STRING_TO_BOOLEAN, name);
        HookCall after = HookCall.to("overlayElementPost", ForgeHookTable.STRING_TO_VOID, name);
        return Hook.inMethod(id, className, methodName, descriptor,
                new HeadCancel(before), new ReturnCall(after, Occurrence.EVERY));
    }

    /** Entity, world and texture rendering, and sound. */
    private static void addRendering(List<Hook> hooks) {
        HookArgument self = HookArgument.self();
        HookArgument first = HookArgument.parameter(1);
        HookArgument second = HookArgument.parameter(2);
        HookArgument third = HookArgument.parameter(3);
        HookArgument fourth = HookArgument.parameter(4);

        // (entity, x, y, z, ...): the three doubles after the entity are its on-screen position.
        String livingPre = "(Ljava/lang/Object;Ljava/lang/Object;DDD)Z";
        String livingPost = "(Ljava/lang/Object;Ljava/lang/Object;DDD)V";
        hooks.add(Hook.inMethod("render.living", GameClasses.RENDERER_LIVING_ENTITY, "doRender",
                "(L" + GameClasses.ENTITY_LIVING_BASE + ";DDDFF)V",
                new HeadCancel(HookCall.to("renderLivingPre", livingPre, self, first, second, third, fourth)),
                new ReturnCall(HookCall.to("renderLivingPost", livingPost, self, first, second, third, fourth),
                        Occurrence.EVERY)));

        // Public method that became a facade: the original body, moved by Lunar, is never called any more.
        hooks.add(Hook.inMethod("render.living.specials", GameClasses.RENDERER_LIVING_ENTITY, "renderName",
                "(L" + GameClasses.ENTITY_LIVING_BASE + ";DDD)V",
                new HeadCancel(HookCall.to("renderLivingSpecialsPre", livingPre, self, first, second, third, fourth)),
                new ReturnCall(HookCall.to("renderLivingSpecialsPost", livingPost, self, first, second, third, fourth),
                        Occurrence.EVERY)));

        // (player, x, y, z, yaw, partial ticks): the partial ticks are the sixth parameter.
        HookArgument partialTicks = HookArgument.parameter(6);
        hooks.add(Hook.inMethod("render.player", GameClasses.RENDER_PLAYER, "doRender",
                "(L" + GameClasses.ABSTRACT_CLIENT_PLAYER + ";DDDFF)V",
                new HeadCancel(HookCall.to("renderPlayerPre", "(Ljava/lang/Object;Ljava/lang/Object;DDDF)Z",
                        self, first, second, third, fourth, partialTicks)),
                new ReturnCall(HookCall.to("renderPlayerPost", "(Ljava/lang/Object;Ljava/lang/Object;DDDF)V",
                        self, first, second, third, fourth, partialTicks), Occurrence.EVERY)));

        // "hand" opens the profiler section that draws the hand: just before it, the world is fully drawn.
        hooks.add(Hook.inMethod("render.world.last", GameClasses.ENTITY_RENDERER, "renderWorldPass", "(IFJ)V",
                new AnchorCall(AnchorPosition.BEFORE, Anchor.constant("hand"), Occurrence.FIRST,
                        HookCall.to("renderWorldLast", "(F)V", second))));

        HookCall stitchPost = HookCall.to("textureStitchPost", ForgeHookTable.OBJECT_TO_VOID, self);
        hooks.add(Hook.inMethod("render.texture.stitch", GameClasses.TEXTURE_MAP, "loadTextureAtlas",
                "(Lnet/minecraft/client/resources/IResourceManager;)V",
                new HeadCall(HookCall.to("textureStitchPre", ForgeHookTable.OBJECT_TO_VOID, self)),
                new ReturnCall(stitchPost, Occurrence.LAST)));

        hooks.add(Hook.inMethod("render.fov", GameClasses.ABSTRACT_CLIENT_PLAYER, "getFovModifier", "()F",
                new ReturnFilter(HookCall.to("fovUpdate", "(FLjava/lang/Object;)F", self))));
        hooks.add(Hook.inMethod("render.tooltip", GameClasses.ITEM_STACK, "getTooltip",
                "(L" + GameClasses.ENTITY_PLAYER + ";Z)Ljava/util/List;",
                new ReturnFilter(HookCall.to("itemTooltip",
                        "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Z)Ljava/lang/Object;",
                        self, first, second))));

        hooks.add(Hook.inMethod("render.highlight", GameClasses.RENDER_GLOBAL, "drawSelectionBox",
                "(L" + GameClasses.ENTITY_PLAYER + ";Lnet/minecraft/util/MovingObjectPosition;IF)V",
                new HeadCancel(HookCall.to("drawBlockHighlight",
                        "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;IF)Z",
                        self, first, second, third, fourth))));

        // A null result means "play nothing": the method ends without running its body.
        HookCall chooseSound = HookCall.to(
                "playSound", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", self, first);
        hooks.add(Hook.inMethod("sound.play", GameClasses.SOUND_MANAGER, "playSound",
                "(Lnet/minecraft/client/audio/ISound;)V", new HeadReplaceParameter(1, chooseSound, true)));
    }
}
