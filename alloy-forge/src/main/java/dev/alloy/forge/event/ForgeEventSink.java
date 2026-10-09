package dev.alloy.forge.event;

import dev.alloy.bridge.GameEventSink;
import dev.alloy.bridge.GuiOpenDecision;
import dev.alloy.bridge.RuntimeContext;
import dev.alloy.forge.env.ForgeEnvironment;
import dev.alloy.forge.loader.ModLifecycle;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;

/**
 * Receives each {@code GameHooks} call and turns it into a Forge event. It restores the real
 * parameter types ({@link GameEventSink} only knows {@code Object}) and delegates to a small
 * collaborator per area: input, screens, network, world, overlay, rendering.
 */
public final class ForgeEventSink implements GameEventSink {

    /** Setting: reload resources after mod initialisation, as Forge does. */
    private static final String REFRESH_RESOURCES_OPTION = "forge.refreshResourcesAfterInit";

    private final RuntimeContext context;
    private final ModLifecycle lifecycle;
    private final InputEvents input = new InputEvents();
    private final GuiEvents gui = new GuiEvents();
    private final NetworkEvents network = new NetworkEvents();
    private final WorldEvents world = new WorldEvents();
    private final OverlayEvents overlay = new OverlayEvents();
    private final RenderEvents render = new RenderEvents();

    /**
     * Creates the sink.
     *
     * @param context   what the agent passed to the runtime
     * @param lifecycle the mod lifecycle
     */
    public ForgeEventSink(RuntimeContext context, ModLifecycle lifecycle) {
        this.context = Objects.requireNonNull(context, "context");
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onModsConstruct() {
        Minecraft minecraft = Minecraft.getMinecraft();
        ForgeEnvironment.attachClient(minecraft);
        this.lifecycle.addResourcePacks(minecraft);
        this.lifecycle.constructMods();
    }

    @Override
    public void onModsPreInit() {
        this.lifecycle.preInitialize();
    }

    @Override
    public void onModsInit() {
        this.lifecycle.initialize();
        Minecraft minecraft = Minecraft.getMinecraft();
        if (this.context.booleanOption(ForgeEventSink.REFRESH_RESOURCES_OPTION, true)) {
            minecraft.refreshResources();
        }
        // The mods have just added their keys: reread options.txt to give them the player's settings.
        minecraft.gameSettings.loadOptions();
        ForgeEnvironment.loadingFinished();
        this.context.logger().info("Forge mods initialised");
    }

    // ------------------------------------------------------------------ ticks

    @Override
    public void onClientTickStart() {
        FMLCommonHandler.instance().onPreClientTick();
    }

    @Override
    public void onClientTickEnd() {
        FMLCommonHandler.instance().onPostClientTick();
    }

    @Override
    public void onRenderTickStart() {
        FMLCommonHandler.instance().onRenderTickStart(Minecraft.getMinecraft().timer.renderPartialTicks);
    }

    @Override
    public void onRenderTickEnd() {
        FMLCommonHandler.instance().onRenderTickEnd(Minecraft.getMinecraft().timer.renderPartialTicks);
    }

    @Override
    public void onPlayerTickStart(Object player) {
        FMLCommonHandler.instance().onPlayerPreTick((EntityPlayer) player);
    }

    @Override
    public void onPlayerTickEnd(Object player) {
        FMLCommonHandler.instance().onPlayerPostTick((EntityPlayer) player);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean nextMouseEvent() {
        return this.input.nextMouseEvent();
    }

    @Override
    public boolean nextKeyboardEvent() {
        return this.input.nextKeyboardEvent();
    }

    @Override
    public boolean nextGuiMouseEvent(Object screen) {
        return this.input.nextGuiMouseEvent((GuiScreen) screen);
    }

    @Override
    public boolean nextGuiKeyboardEvent(Object screen) {
        return this.input.nextGuiKeyboardEvent((GuiScreen) screen);
    }

    // ------------------------------------------------------------------ screens

    @Override
    public GuiOpenDecision onGuiOpen(Object screen) {
        return this.gui.onGuiOpen((GuiScreen) screen);
    }

    @Override
    public void onGuiInitPre(Object screen) {
        this.gui.onGuiInitPre((GuiScreen) screen);
    }

    @Override
    public void onGuiInitPost(Object screen) {
        this.gui.onGuiInitPost((GuiScreen) screen);
    }

    @Override
    public void drawScreen(Object screen, int mouseX, int mouseY, float partialTicks) {
        this.gui.drawScreen((GuiScreen) screen, mouseX, mouseY, partialTicks);
    }

    @Override
    public void actionPerformed(Object screen, Object button) {
        this.gui.actionPerformed((GuiScreen) screen, (GuiButton) button);
    }

    @Override
    public boolean onClientCommand(String message, boolean addToChat) {
        return this.gui.onClientCommand(message, addToChat);
    }

    @Override
    public void onAutoCompleteRequest(String leftOfCursor, String full) {
        this.gui.onAutoCompleteRequest(leftOfCursor, full);
    }

    @Override
    public String[] onAutoCompleteResponse(String[] serverCompletions) {
        return this.gui.onAutoCompleteResponse(serverCompletions);
    }

    // ------------------------------------------------------------------ chat and network

    @Override
    public boolean onChatReceived(Object chatPacket) {
        return this.network.onChatReceived((S02PacketChat) chatPacket);
    }

    @Override
    public void onJoinGame(Object netHandler) {
        this.network.onJoinGame((NetHandlerPlayClient) netHandler);
    }

    // ------------------------------------------------------------------ world and entities

    @Override
    public void onWorldUnload() {
        this.world.onWorldUnload();
    }

    @Override
    public void onWorldLoad(Object world) {
        this.world.onWorldLoad((World) world);
    }

    @Override
    public boolean onEntityJoinWorld(Object world, Object entity) {
        return this.world.onEntityJoinWorld((World) world, (Entity) entity);
    }

    @Override
    public boolean onLivingUpdate(Object entity) {
        return this.world.onLivingUpdate((EntityLivingBase) entity);
    }

    @Override
    public boolean onAttackEntity(Object player, Object target) {
        return this.world.onAttackEntity((EntityPlayer) player, (Entity) target);
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public boolean onOverlayPre(float partialTicks) {
        return this.overlay.onOverlayPre(partialTicks);
    }

    @Override
    public void onOverlayPost() {
        this.overlay.onOverlayPost();
    }

    @Override
    public boolean onOverlayElementPre(String element) {
        return this.overlay.onOverlayElementPre(element);
    }

    @Override
    public void onOverlayElementPost(String element) {
        this.overlay.onOverlayElementPost(element);
    }

    @Override
    public boolean onRenderLivingPre(Object renderer, Object entity, double x, double y, double z) {
        return this.render.onRenderLivingPre((RendererLivingEntity<?>) renderer, (EntityLivingBase) entity, x, y, z);
    }

    @Override
    public void onRenderLivingPost(Object renderer, Object entity, double x, double y, double z) {
        this.render.onRenderLivingPost((RendererLivingEntity<?>) renderer, (EntityLivingBase) entity, x, y, z);
    }

    @Override
    public boolean onRenderLivingSpecialsPre(Object renderer, Object entity, double x, double y, double z) {
        return this.render.onRenderLivingSpecialsPre((RendererLivingEntity<?>) renderer, (EntityLivingBase) entity, x, y, z);
    }

    @Override
    public void onRenderLivingSpecialsPost(Object renderer, Object entity, double x, double y, double z) {
        this.render.onRenderLivingSpecialsPost((RendererLivingEntity<?>) renderer, (EntityLivingBase) entity, x, y, z);
    }

    @Override
    public boolean onRenderPlayerPre(Object renderer, Object player, double x, double y, double z, float partialTicks) {
        return this.render.onRenderPlayerPre((RenderPlayer) renderer, (EntityPlayer) player, x, y, z, partialTicks);
    }

    @Override
    public void onRenderPlayerPost(Object renderer, Object player, double x, double y, double z, float partialTicks) {
        this.render.onRenderPlayerPost((RenderPlayer) renderer, (EntityPlayer) player, x, y, z, partialTicks);
    }

    @Override
    public void onRenderWorldLast(float partialTicks) {
        this.render.onRenderWorldLast(partialTicks);
    }

    @Override
    public void onTextureStitchPre(Object textureMap) {
        this.render.onTextureStitchPre((TextureMap) textureMap);
    }

    @Override
    public void onTextureStitchPost(Object textureMap) {
        this.render.onTextureStitchPost((TextureMap) textureMap);
    }

    @Override
    public float onFovUpdate(float fov, Object player) {
        return this.render.onFovUpdate(fov, (EntityPlayer) player);
    }

    @Override
    @SuppressWarnings("unchecked") // the game does build a List<String> for a tooltip
    public Object onItemTooltip(Object tooltip, Object stack, Object player, boolean advanced) {
        return this.render.onItemTooltip((List<String>) tooltip, (ItemStack) stack, (EntityPlayer) player, advanced);
    }

    @Override
    public Object onPlaySound(Object soundManager, Object sound) {
        return this.render.onPlaySound((SoundManager) soundManager, (ISound) sound);
    }

    @Override
    public boolean onDrawBlockHighlight(Object renderGlobal, Object player, Object target, int subId, float partialTicks) {
        return this.render.onDrawBlockHighlight(
                (RenderGlobal) renderGlobal, (EntityPlayer) player, (MovingObjectPosition) target, subId, partialTicks);
    }
}
