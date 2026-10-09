package dev.alloy.bridge;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Static entry point called by the bytecode injected into Minecraft; each method forwards to the
 * installed {@linkplain GameEventSink sink}. No exception ever reaches the game: failures are
 * logged and the game carries on as if the hook did not exist.
 */
public final class GameHooks {

    /** Internal name of this class, as it appears in bytecode. */
    public static final String INTERNAL_NAME = "dev/alloy/bridge/GameHooks";

    /** Prefix of the classes the game's class loader must fetch from the system class loader. */
    public static final String BRIDGE_PACKAGE_PREFIX = "dev.alloy.bridge.";

    /**
     * Name of the static field Alloy adds to the game's class loader class. The agent stores there
     * the {@code Function<String, Class<?>>} that loader asks before looking itself.
     */
    public static final String CLASS_SOURCE_FIELD = "alloy$classSource";

    /** Maximum number of logged failures per hook. */
    private static final int MAX_REPORTS_PER_HOOK = 3;

    /** The game event sink; {@code null} until the Forge runtime has started. */
    private static volatile GameEventSink sink;

    /** The game start listener, set by the agent. */
    private static volatile GameStartListener startListener;

    /** The logger; standard error by default, replaced by the agent's. */
    private static volatile BridgeLogger logger = GameHooks::printToStandardError;

    /** Decision returned by the last {@link #guiOpen}, read back by {@link #guiOpenResult}. */
    private static volatile GuiOpenDecision lastGuiOpenDecision = GuiOpenDecision.cancel();

    /** Failures already logged, per hook. */
    private static final ConcurrentHashMap<String, Integer> REPORT_COUNTS = new ConcurrentHashMap<>();

    /** Hooks fired at least once (for diagnostics). */
    private static final Set<String> FIRED_HOOKS = ConcurrentHashMap.newKeySet();

    private GameHooks() {
    }

    // ================================================================== installation (agent side)

    /** Installs the listener notified when the game starts. */
    public static void setGameStartListener(GameStartListener listener) {
        GameHooks.startListener = Objects.requireNonNull(listener, "listener");
    }

    /** Installs Alloy's logger. */
    public static void setLogger(BridgeLogger bridgeLogger) {
        GameHooks.logger = Objects.requireNonNull(bridgeLogger, "bridgeLogger");
    }

    /** Installs the game event sink (the Forge runtime); hooks are active from this point on. */
    public static void install(GameEventSink eventSink) {
        GameHooks.sink = Objects.requireNonNull(eventSink, "eventSink");
    }

    /** Tells whether the Forge runtime is installed. */
    public static boolean isActive() {
        return GameHooks.sink != null;
    }

    /** Returns an unmodifiable copy of the names of the hooks fired at least once. */
    public static Set<String> firedHooks() {
        return Set.copyOf(GameHooks.FIRED_HOOKS);
    }

    // ================================================================== startup

    /** Injected at the start of {@code net.minecraft.client.main.Main.main}. */
    public static void gameMain(String[] arguments) {
        GameStartListener listener = GameHooks.startListener;
        if (listener == null) {
            return;
        }
        try {
            listener.onGameStart(arguments == null ? new String[0] : arguments);
        } catch (Throwable t) {
            GameHooks.report("gameMain", t);
        }
    }

    // ================================================================== mod lifecycle

    /** See {@link GameEventSink#onModsConstruct()}. */
    public static void modsConstruct() {
        GameEventSink s = GameHooks.enter("modsConstruct");
        if (s == null) {
            return;
        }
        try {
            s.onModsConstruct();
        } catch (Throwable t) {
            GameHooks.report("modsConstruct", t);
        }
    }

    /** See {@link GameEventSink#onModsPreInit()}. */
    public static void modsPreInit() {
        GameEventSink s = GameHooks.enter("modsPreInit");
        if (s == null) {
            return;
        }
        try {
            s.onModsPreInit();
        } catch (Throwable t) {
            GameHooks.report("modsPreInit", t);
        }
    }

    /** See {@link GameEventSink#onModsInit()}. */
    public static void modsInit() {
        GameEventSink s = GameHooks.enter("modsInit");
        if (s == null) {
            return;
        }
        try {
            s.onModsInit();
        } catch (Throwable t) {
            GameHooks.report("modsInit", t);
        }
    }

    // ================================================================== ticks

    /** See {@link GameEventSink#onClientTickStart()}. */
    public static void clientTickStart() {
        GameEventSink s = GameHooks.enter("clientTickStart");
        if (s == null) {
            return;
        }
        try {
            s.onClientTickStart();
        } catch (Throwable t) {
            GameHooks.report("clientTickStart", t);
        }
    }

    /** See {@link GameEventSink#onClientTickEnd()}. */
    public static void clientTickEnd() {
        GameEventSink s = GameHooks.enter("clientTickEnd");
        if (s == null) {
            return;
        }
        try {
            s.onClientTickEnd();
        } catch (Throwable t) {
            GameHooks.report("clientTickEnd", t);
        }
    }

    /** See {@link GameEventSink#onRenderTickStart()}. */
    public static void renderTickStart() {
        GameEventSink s = GameHooks.enter("renderTickStart");
        if (s == null) {
            return;
        }
        try {
            s.onRenderTickStart();
        } catch (Throwable t) {
            GameHooks.report("renderTickStart", t);
        }
    }

    /** See {@link GameEventSink#onRenderTickEnd()}. */
    public static void renderTickEnd() {
        GameEventSink s = GameHooks.enter("renderTickEnd");
        if (s == null) {
            return;
        }
        try {
            s.onRenderTickEnd();
        } catch (Throwable t) {
            GameHooks.report("renderTickEnd", t);
        }
    }

    /** See {@link GameEventSink#onPlayerTickStart(Object)}. */
    public static void playerTickStart(Object player) {
        GameEventSink s = GameHooks.enter("playerTickStart");
        if (s == null) {
            return;
        }
        try {
            s.onPlayerTickStart(player);
        } catch (Throwable t) {
            GameHooks.report("playerTickStart", t);
        }
    }

    /** See {@link GameEventSink#onPlayerTickEnd(Object)}. */
    public static void playerTickEnd(Object player) {
        GameEventSink s = GameHooks.enter("playerTickEnd");
        if (s == null) {
            return;
        }
        try {
            s.onPlayerTickEnd(player);
        } catch (Throwable t) {
            GameHooks.report("playerTickEnd", t);
        }
    }

    // ================================================================== keyboard / mouse input

    /** See {@link GameEventSink#nextMouseEvent()}. */
    public static boolean mouseNext() {
        GameEventSink s = GameHooks.enter("mouseNext");
        if (s == null) {
            return VanillaCalls.mouseNext();
        }
        try {
            return s.nextMouseEvent();
        } catch (Throwable t) {
            GameHooks.report("mouseNext", t);
            return false;
        }
    }

    /** See {@link GameEventSink#nextKeyboardEvent()}. */
    public static boolean keyboardNext() {
        GameEventSink s = GameHooks.enter("keyboardNext");
        if (s == null) {
            return VanillaCalls.keyboardNext();
        }
        try {
            return s.nextKeyboardEvent();
        } catch (Throwable t) {
            GameHooks.report("keyboardNext", t);
            return false;
        }
    }

    /** See {@link GameEventSink#nextGuiMouseEvent(Object)}. */
    public static boolean guiMouseNext(Object screen) {
        GameEventSink s = GameHooks.enter("guiMouseNext");
        if (s == null) {
            return VanillaCalls.mouseNext();
        }
        try {
            return s.nextGuiMouseEvent(screen);
        } catch (Throwable t) {
            GameHooks.report("guiMouseNext", t);
            return false;
        }
    }

    /** See {@link GameEventSink#nextGuiKeyboardEvent(Object)}. */
    public static boolean guiKeyboardNext(Object screen) {
        GameEventSink s = GameHooks.enter("guiKeyboardNext");
        if (s == null) {
            return VanillaCalls.keyboardNext();
        }
        try {
            return s.nextGuiKeyboardEvent(screen);
        } catch (Throwable t) {
            GameHooks.report("guiKeyboardNext", t);
            return false;
        }
    }

    // ================================================================== screens

    /**
     * Injected at the start of {@code Minecraft.displayGuiScreen}: returns {@code true} if opening
     * is cancelled, otherwise {@link #guiOpenResult()} gives the screen to show.
     */
    public static boolean guiOpen(Object screen) {
        GuiOpenDecision decision = GuiOpenDecision.show(screen);
        GameEventSink s = GameHooks.enter("guiOpen");
        if (s != null) {
            try {
                decision = Objects.requireNonNull(s.onGuiOpen(screen), "decision");
            } catch (Throwable t) {
                GameHooks.report("guiOpen", t);
                decision = GuiOpenDecision.show(screen);
            }
        }
        GameHooks.lastGuiOpenDecision = decision;
        return decision.cancelled();
    }

    /** Returns the screen chosen by the last {@link #guiOpen(Object)} call (may be {@code null}). */
    public static Object guiOpenResult() {
        return GameHooks.lastGuiOpenDecision.screen();
    }

    /** See {@link GameEventSink#onGuiInitPre(Object)}. */
    public static void guiInitPre(Object screen) {
        GameEventSink s = GameHooks.enter("guiInitPre");
        if (s == null) {
            return;
        }
        try {
            s.onGuiInitPre(screen);
        } catch (Throwable t) {
            GameHooks.report("guiInitPre", t);
        }
    }

    /** See {@link GameEventSink#onGuiInitPost(Object)}. */
    public static void guiInitPost(Object screen) {
        GameEventSink s = GameHooks.enter("guiInitPost");
        if (s == null) {
            return;
        }
        try {
            s.onGuiInitPost(screen);
        } catch (Throwable t) {
            GameHooks.report("guiInitPost", t);
        }
    }

    /** See {@link GameEventSink#drawScreen(Object, int, int, float)}. */
    public static void drawScreen(Object screen, int mouseX, int mouseY, float partialTicks) {
        GameEventSink s = GameHooks.enter("drawScreen");
        if (s == null) {
            VanillaCalls.drawScreen(screen, mouseX, mouseY, partialTicks);
            return;
        }
        try {
            s.drawScreen(screen, mouseX, mouseY, partialTicks);
        } catch (Throwable t) {
            GameHooks.report("drawScreen", t);
        }
    }

    /** See {@link GameEventSink#actionPerformed(Object, Object)}. */
    public static void actionPerformed(Object screen, Object button) {
        GameEventSink s = GameHooks.enter("actionPerformed");
        if (s == null) {
            VanillaCalls.actionPerformed(screen, button);
            return;
        }
        try {
            s.actionPerformed(screen, button);
        } catch (Throwable t) {
            GameHooks.report("actionPerformed", t);
        }
    }

    /** See {@link GameEventSink#onBackgroundDrawn(Object, boolean)}; called from {@code drawBackground}. */
    public static void backgroundDrawn(Object screen) {
        GameHooks.backgroundDrawn(screen, false);
    }

    /** See {@link GameEventSink#onBackgroundDrawn(Object, boolean)}; called from {@code drawWorldBackground}. */
    public static void worldBackgroundDrawn(Object screen) {
        GameHooks.backgroundDrawn(screen, true);
    }

    private static void backgroundDrawn(Object screen, boolean overWorld) {
        GameEventSink s = GameHooks.enter("backgroundDrawn");
        if (s == null) {
            return;
        }
        try {
            s.onBackgroundDrawn(screen, overWorld);
        } catch (Throwable t) {
            GameHooks.report("backgroundDrawn", t);
        }
    }

    /** See {@link GameEventSink#onClientCommand(String, boolean)}. */
    public static boolean clientCommand(String message, boolean addToChat) {
        GameEventSink s = GameHooks.enter("clientCommand");
        if (s == null) {
            return false;
        }
        try {
            return s.onClientCommand(message, addToChat);
        } catch (Throwable t) {
            GameHooks.report("clientCommand", t);
            return false;
        }
    }

    /** See {@link GameEventSink#onAutoCompleteRequest(String, String)}. */
    public static void autoComplete(String leftOfCursor, String full) {
        GameEventSink s = GameHooks.enter("autoComplete");
        if (s == null) {
            return;
        }
        try {
            s.onAutoCompleteRequest(leftOfCursor, full);
        } catch (Throwable t) {
            GameHooks.report("autoComplete", t);
        }
    }

    /** See {@link GameEventSink#onAutoCompleteResponse(String[])}. */
    public static String[] autoCompleteResponse(String[] serverCompletions) {
        GameEventSink s = GameHooks.enter("autoCompleteResponse");
        if (s == null) {
            return serverCompletions;
        }
        try {
            String[] merged = s.onAutoCompleteResponse(serverCompletions);
            return merged == null ? serverCompletions : merged;
        } catch (Throwable t) {
            GameHooks.report("autoCompleteResponse", t);
            return serverCompletions;
        }
    }

    // ================================================================== chat and network

    /** See {@link GameEventSink#onChatReceived(Object)}. */
    public static boolean chatReceived(Object chatPacket) {
        GameEventSink s = GameHooks.enter("chatReceived");
        if (s == null) {
            return false;
        }
        try {
            return s.onChatReceived(chatPacket);
        } catch (Throwable t) {
            GameHooks.report("chatReceived", t);
            return false;
        }
    }

    /** See {@link GameEventSink#onJoinGame(Object)}. */
    public static void joinGame(Object netHandler) {
        GameEventSink s = GameHooks.enter("joinGame");
        if (s == null) {
            return;
        }
        try {
            s.onJoinGame(netHandler);
        } catch (Throwable t) {
            GameHooks.report("joinGame", t);
        }
    }

    // ================================================================== world and entities

    /** See {@link GameEventSink#onWorldUnload()}. */
    public static void worldUnload() {
        GameEventSink s = GameHooks.enter("worldUnload");
        if (s == null) {
            return;
        }
        try {
            s.onWorldUnload();
        } catch (Throwable t) {
            GameHooks.report("worldUnload", t);
        }
    }

    /** See {@link GameEventSink#onWorldLoad(Object)}. */
    public static void worldLoad(Object world) {
        GameEventSink s = GameHooks.enter("worldLoad");
        if (s == null) {
            return;
        }
        try {
            s.onWorldLoad(world);
        } catch (Throwable t) {
            GameHooks.report("worldLoad", t);
        }
    }

    /** See {@link GameEventSink#onEntityJoinWorld(Object, Object)}. */
    public static boolean entityJoinWorld(Object world, Object entity) {
        GameEventSink s = GameHooks.enter("entityJoinWorld");
        if (s == null) {
            return false;
        }
        try {
            return s.onEntityJoinWorld(world, entity);
        } catch (Throwable t) {
            GameHooks.report("entityJoinWorld", t);
            return false;
        }
    }

    /** See {@link GameEventSink#onLivingUpdate(Object)}. */
    public static boolean livingUpdate(Object entity) {
        GameEventSink s = GameHooks.enter("livingUpdate");
        if (s == null) {
            return false;
        }
        try {
            return s.onLivingUpdate(entity);
        } catch (Throwable t) {
            GameHooks.report("livingUpdate", t);
            return false;
        }
    }

    /** See {@link GameEventSink#onAttackEntity(Object, Object)}. */
    public static boolean attackEntity(Object player, Object target) {
        GameEventSink s = GameHooks.enter("attackEntity");
        if (s == null) {
            return false;
        }
        try {
            return s.onAttackEntity(player, target);
        } catch (Throwable t) {
            GameHooks.report("attackEntity", t);
            return false;
        }
    }

    /** See {@link GameEventSink#onEntityInteract(Object, Object)}. */
    public static boolean entityInteract(Object player, Object target) {
        GameEventSink s = GameHooks.enter("entityInteract");
        if (s == null) {
            return false;
        }
        try {
            return s.onEntityInteract(player, target);
        } catch (Throwable t) {
            GameHooks.report("entityInteract", t);
            return false;
        }
    }

    /** See {@link GameEventSink#onUseItem(Object, Object, Object, Object)}. */
    public static boolean useItem(Object controller, Object player, Object world, Object stack) {
        GameEventSink s = GameHooks.enter("useItem");
        if (s == null) {
            return VanillaCalls.sendUseItem(controller, player, world, stack);
        }
        try {
            return s.onUseItem(controller, player, world, stack);
        } catch (Throwable t) {
            GameHooks.report("useItem", t);
            return false;
        }
    }

    /** See {@link GameEventSink#onPlaySoundAtEntity(Object, String, float, float)}. */
    public static boolean entitySound(Object entity, String name, float volume, float pitch) {
        GameEventSink s = GameHooks.enter("entitySound");
        if (s == null) {
            return false;
        }
        try {
            return s.onPlaySoundAtEntity(entity, name, volume, pitch);
        } catch (Throwable t) {
            GameHooks.report("entitySound", t);
            return false;
        }
    }

    /** See {@link GameEventSink#onEntityConstructing(Object)}. */
    public static void entityConstructing(Object entity) {
        GameEventSink s = GameHooks.enter("entityConstructing");
        if (s == null) {
            return;
        }
        try {
            s.onEntityConstructing(entity);
        } catch (Throwable t) {
            GameHooks.report("entityConstructing", t);
        }
    }

    /** See {@link GameEventSink#onLivingJump(Object)}. */
    public static void livingJump(Object entity) {
        GameEventSink s = GameHooks.enter("livingJump");
        if (s == null) {
            return;
        }
        try {
            s.onLivingJump(entity);
        } catch (Throwable t) {
            GameHooks.report("livingJump", t);
        }
    }

    /** See {@link GameEventSink#onChunkLoad(Object)}. */
    public static void chunkLoad(Object chunk) {
        GameEventSink s = GameHooks.enter("chunkLoad");
        if (s == null) {
            return;
        }
        try {
            s.onChunkLoad(chunk);
        } catch (Throwable t) {
            GameHooks.report("chunkLoad", t);
        }
    }

    /** Variant of {@link #chunkLoad(Object)} that filters a returned chunk. */
    public static Object chunkLoaded(Object chunk) {
        GameHooks.chunkLoad(chunk);
        return chunk;
    }

    /** See {@link GameEventSink#onChunkUnload(Object)}. */
    public static void chunkUnload(Object chunk) {
        GameEventSink s = GameHooks.enter("chunkUnload");
        if (s == null) {
            return;
        }
        try {
            s.onChunkUnload(chunk);
        } catch (Throwable t) {
            GameHooks.report("chunkUnload", t);
        }
    }

    // ================================================================== rendering

    /** See {@link GameEventSink#onOverlayPre(float)}. */
    public static boolean overlayPre(float partialTicks) {
        GameEventSink s = GameHooks.enter("overlayPre");
        if (s == null) {
            return false;
        }
        try {
            return s.onOverlayPre(partialTicks);
        } catch (Throwable t) {
            GameHooks.report("overlayPre", t);
            return false;
        }
    }

    /** See {@link GameEventSink#onOverlayPost()}. */
    public static void overlayPost() {
        GameEventSink s = GameHooks.enter("overlayPost");
        if (s == null) {
            return;
        }
        try {
            s.onOverlayPost();
        } catch (Throwable t) {
            GameHooks.report("overlayPost", t);
        }
    }

    /** See {@link GameEventSink#onOverlayElementPre(String)}. */
    public static boolean overlayElementPre(String element) {
        GameEventSink s = GameHooks.enter("overlayElementPre");
        if (s == null) {
            return false;
        }
        try {
            return s.onOverlayElementPre(element);
        } catch (Throwable t) {
            GameHooks.report("overlayElementPre", t);
            return false;
        }
    }

    /** See {@link GameEventSink#onOverlayElementPost(String)}. */
    public static void overlayElementPost(String element) {
        GameEventSink s = GameHooks.enter("overlayElementPost");
        if (s == null) {
            return;
        }
        try {
            s.onOverlayElementPost(element);
        } catch (Throwable t) {
            GameHooks.report("overlayElementPost", t);
        }
    }

    /** See {@link GameEventSink#onRenderLivingPre(Object, Object, double, double, double)}. */
    public static boolean renderLivingPre(Object renderer, Object entity, double x, double y, double z) {
        GameEventSink s = GameHooks.enter("renderLivingPre");
        if (s == null) {
            return false;
        }
        try {
            return s.onRenderLivingPre(renderer, entity, x, y, z);
        } catch (Throwable t) {
            GameHooks.report("renderLivingPre", t);
            return false;
        }
    }

    /** See {@link GameEventSink#onRenderLivingPost(Object, Object, double, double, double)}. */
    public static void renderLivingPost(Object renderer, Object entity, double x, double y, double z) {
        GameEventSink s = GameHooks.enter("renderLivingPost");
        if (s == null) {
            return;
        }
        try {
            s.onRenderLivingPost(renderer, entity, x, y, z);
        } catch (Throwable t) {
            GameHooks.report("renderLivingPost", t);
        }
    }

    /** See {@link GameEventSink#onRenderLivingSpecialsPre(Object, Object, double, double, double)}. */
    public static boolean renderLivingSpecialsPre(Object renderer, Object entity, double x, double y, double z) {
        GameEventSink s = GameHooks.enter("renderLivingSpecialsPre");
        if (s == null) {
            return false;
        }
        try {
            return s.onRenderLivingSpecialsPre(renderer, entity, x, y, z);
        } catch (Throwable t) {
            GameHooks.report("renderLivingSpecialsPre", t);
            return false;
        }
    }

    /** See {@link GameEventSink#onRenderLivingSpecialsPost(Object, Object, double, double, double)}. */
    public static void renderLivingSpecialsPost(Object renderer, Object entity, double x, double y, double z) {
        GameEventSink s = GameHooks.enter("renderLivingSpecialsPost");
        if (s == null) {
            return;
        }
        try {
            s.onRenderLivingSpecialsPost(renderer, entity, x, y, z);
        } catch (Throwable t) {
            GameHooks.report("renderLivingSpecialsPost", t);
        }
    }

    /** See {@link GameEventSink#onRenderPlayerPre(Object, Object, double, double, double, float)}. */
    public static boolean renderPlayerPre(
            Object renderer, Object player, double x, double y, double z, float partialTicks) {
        GameEventSink s = GameHooks.enter("renderPlayerPre");
        if (s == null) {
            return false;
        }
        try {
            return s.onRenderPlayerPre(renderer, player, x, y, z, partialTicks);
        } catch (Throwable t) {
            GameHooks.report("renderPlayerPre", t);
            return false;
        }
    }

    /** See {@link GameEventSink#onRenderPlayerPost(Object, Object, double, double, double, float)}. */
    public static void renderPlayerPost(
            Object renderer, Object player, double x, double y, double z, float partialTicks) {
        GameEventSink s = GameHooks.enter("renderPlayerPost");
        if (s == null) {
            return;
        }
        try {
            s.onRenderPlayerPost(renderer, player, x, y, z, partialTicks);
        } catch (Throwable t) {
            GameHooks.report("renderPlayerPost", t);
        }
    }

    /** See {@link GameEventSink#onRenderWorldLast(float)}. */
    public static void renderWorldLast(float partialTicks) {
        GameEventSink s = GameHooks.enter("renderWorldLast");
        if (s == null) {
            return;
        }
        try {
            s.onRenderWorldLast(partialTicks);
        } catch (Throwable t) {
            GameHooks.report("renderWorldLast", t);
        }
    }

    /** See {@link GameEventSink#onTextureStitchPre(Object)}. */
    public static void textureStitchPre(Object textureMap) {
        GameEventSink s = GameHooks.enter("textureStitchPre");
        if (s == null) {
            return;
        }
        try {
            s.onTextureStitchPre(textureMap);
        } catch (Throwable t) {
            GameHooks.report("textureStitchPre", t);
        }
    }

    /** See {@link GameEventSink#onTextureStitchPost(Object)}. */
    public static void textureStitchPost(Object textureMap) {
        GameEventSink s = GameHooks.enter("textureStitchPost");
        if (s == null) {
            return;
        }
        try {
            s.onTextureStitchPost(textureMap);
        } catch (Throwable t) {
            GameHooks.report("textureStitchPost", t);
        }
    }

    /** See {@link GameEventSink#onFovUpdate(float, Object)}. */
    public static float fovUpdate(float fov, Object player) {
        GameEventSink s = GameHooks.enter("fovUpdate");
        if (s == null) {
            return fov;
        }
        try {
            return s.onFovUpdate(fov, player);
        } catch (Throwable t) {
            GameHooks.report("fovUpdate", t);
            return fov;
        }
    }

    /** See {@link GameEventSink#onItemTooltip(Object, Object, Object, boolean)}. */
    public static Object itemTooltip(Object tooltip, Object stack, Object player, boolean advanced) {
        GameEventSink s = GameHooks.enter("itemTooltip");
        if (s == null) {
            return tooltip;
        }
        try {
            Object result = s.onItemTooltip(tooltip, stack, player, advanced);
            return result == null ? tooltip : result;
        } catch (Throwable t) {
            GameHooks.report("itemTooltip", t);
            return tooltip;
        }
    }

    /** See {@link GameEventSink#onPlaySound(Object, Object)}. */
    public static Object playSound(Object soundManager, Object sound) {
        GameEventSink s = GameHooks.enter("playSound");
        if (s == null) {
            return sound;
        }
        try {
            return s.onPlaySound(soundManager, sound);
        } catch (Throwable t) {
            GameHooks.report("playSound", t);
            return sound;
        }
    }

    /** See {@link GameEventSink#onDrawBlockHighlight(Object, Object, Object, int, float)}. */
    public static boolean drawBlockHighlight(
            Object renderGlobal, Object player, Object target, int subId, float partialTicks) {
        GameEventSink s = GameHooks.enter("drawBlockHighlight");
        if (s == null) {
            return false;
        }
        try {
            return s.onDrawBlockHighlight(renderGlobal, player, target, subId, partialTicks);
        } catch (Throwable t) {
            GameHooks.report("drawBlockHighlight", t);
            return false;
        }
    }

    // ================================================================== internals

    /** Resets the class to its initial state; tests only (the sink is never uninstalled otherwise). */
    static void resetForTests() {
        GameHooks.sink = null;
        GameHooks.startListener = null;
        GameHooks.logger = GameHooks::printToStandardError;
        GameHooks.lastGuiOpenDecision = GuiOpenDecision.cancel();
        GameHooks.REPORT_COUNTS.clear();
        GameHooks.FIRED_HOOKS.clear();
    }

    /** Records that the hook fired and returns the current sink, or {@code null} if no runtime is installed. */
    private static GameEventSink enter(String hookName) {
        GameEventSink current = GameHooks.sink;
        if (current != null) {
            GameHooks.FIRED_HOOKS.add(hookName);
        }
        return current;
    }

    /**
     * Logs a hook failure, at most {@value #MAX_REPORTS_PER_HOOK} times per hook so that an error
     * repeating every frame does not flood the log.
     */
    private static void report(String hookName, Throwable error) {
        int count = GameHooks.REPORT_COUNTS.merge(hookName, 1, Integer::sum);
        if (count > GameHooks.MAX_REPORTS_PER_HOOK) {
            return;
        }
        String suffix = count == GameHooks.MAX_REPORTS_PER_HOOK ? " (further failures of this hook are not logged)" : "";
        try {
            GameHooks.logger.log(LogLevel.ERROR, "Hook '" + hookName + "' failed" + suffix, error);
        } catch (Throwable loggingFailure) {
            GameHooks.printToStandardError(LogLevel.ERROR, "Hook '" + hookName + "' failed" + suffix, error);
        }
    }

    private static void printToStandardError(LogLevel level, String message, Throwable error) {
        System.err.println("[Alloy/" + level + "] " + message);
        if (error != null) {
            error.printStackTrace();
        }
    }
}
