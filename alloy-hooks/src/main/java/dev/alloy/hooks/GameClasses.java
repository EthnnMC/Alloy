package dev.alloy.hooks;

/**
 * Internal names (with slashes) of the Minecraft and library classes the hook catalog targets
 * or mentions, as Lunar names them at runtime (MCP names). They are plain text: Alloy never
 * loads these classes.
 */
final class GameClasses {

    static final String MAIN = "net/minecraft/client/main/Main";
    static final String MINECRAFT = "net/minecraft/client/Minecraft";
    static final String ENTITY_RENDERER = "net/minecraft/client/renderer/EntityRenderer";
    static final String RENDER_GLOBAL = "net/minecraft/client/renderer/RenderGlobal";
    static final String TEXTURE_MAP = "net/minecraft/client/renderer/texture/TextureMap";
    static final String RENDERER_LIVING_ENTITY = "net/minecraft/client/renderer/entity/RendererLivingEntity";
    static final String RENDER_PLAYER = "net/minecraft/client/renderer/entity/RenderPlayer";

    static final String GUI_SCREEN = "net/minecraft/client/gui/GuiScreen";
    static final String GUI_BUTTON = "net/minecraft/client/gui/GuiButton";
    static final String GUI_CHAT = "net/minecraft/client/gui/GuiChat";
    static final String GUI_INGAME = "net/minecraft/client/gui/GuiIngame";
    static final String GUI_NEW_CHAT = "net/minecraft/client/gui/GuiNewChat";
    static final String GUI_PLAYER_TAB_OVERLAY = "net/minecraft/client/gui/GuiPlayerTabOverlay";

    static final String NET_HANDLER_PLAY_CLIENT = "net/minecraft/client/network/NetHandlerPlayClient";
    static final String WORLD_CLIENT = "net/minecraft/client/multiplayer/WorldClient";
    static final String WORLD = "net/minecraft/world/World";
    static final String SOUND_MANAGER = "net/minecraft/client/audio/SoundManager";

    static final String ENTITY_LIVING_BASE = "net/minecraft/entity/EntityLivingBase";
    static final String ENTITY_PLAYER = "net/minecraft/entity/player/EntityPlayer";
    static final String ABSTRACT_CLIENT_PLAYER = "net/minecraft/client/entity/AbstractClientPlayer";
    static final String ITEM_STACK = "net/minecraft/item/ItemStack";

    /** LWJGL 2 classes that dispatch mouse and keyboard events. */
    static final String LWJGL_MOUSE = "org/lwjgl/input/Mouse";
    static final String LWJGL_KEYBOARD = "org/lwjgl/input/Keyboard";

    private GameClasses() {
        // Constants class.
    }
}
