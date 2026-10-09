package dev.alloy.forge.event;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.common.MinecraftForge;

/**
 * In-game overlay events ({@code RenderGameOverlayEvent}). Alloy keeps the game's own UI (where
 * Lunar draws its overlay) and publishes the events from hooks in the methods that draw each
 * element; all events of a frame share a parent event carrying the screen resolution.
 * The game draws health, armor, food and air in one method: their four events fire together around
 * it, and the bars are only hidden when all four are cancelled. {@code HEALTHMOUNT} is not supported.
 */
final class OverlayEvents {

    /** Distance between the bottom of the screen and the chat, as in the game. */
    private static final int CHAT_BOTTOM_MARGIN = 48;
    private static final int TEXT_MARGIN = 2;
    private static final int TEXT_COLOR = 0xE0E0E0;
    private static final int TEXT_BACKGROUND = 0x90505050;

    /** Pseudo-element sent by the hook around {@code GuiIngame.renderPlayerStats}. */
    private static final String PLAYER_STATS = "PLAYER_STATS";
    private static final List<ElementType> PLAYER_STATS_TYPES =
            List.of(ElementType.HEALTH, ElementType.ARMOR, ElementType.FOOD, ElementType.AIR);

    /** Parent event of the current frame; {@code null} outside UI drawing. */
    private RenderGameOverlayEvent frame;

    /** See {@code GameEventSink.onOverlayPre}. */
    boolean onOverlayPre(float partialTicks) {
        this.frame = new RenderGameOverlayEvent(partialTicks, new ScaledResolution(Minecraft.getMinecraft()));
        return this.pre(ElementType.ALL);
    }

    /** See {@code GameEventSink.onOverlayPost}. */
    void onOverlayPost() {
        this.renderText();
        // Forge resets the graphics state before letting mods draw.
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableLighting();
        GlStateManager.enableAlpha();
        this.post(ElementType.ALL);
        this.frame = null;
    }

    /** See {@code GameEventSink.onOverlayElementPre}. */
    boolean onOverlayElementPre(String element) {
        if (OverlayEvents.PLAYER_STATS.equals(element)) {
            boolean allCancelled = true;
            for (ElementType part : OverlayEvents.PLAYER_STATS_TYPES) {
                // Not short-circuited: every event must be published.
                allCancelled &= this.pre(part);
            }
            return allCancelled;
        }
        ElementType type = ElementType.valueOf(element);
        if (type == ElementType.CHAT) {
            RenderGameOverlayEvent parent = this.currentFrame();
            int chatY = parent.resolution.getScaledHeight() - OverlayEvents.CHAT_BOTTOM_MARGIN;
            return MinecraftForge.EVENT_BUS.post(new RenderGameOverlayEvent.Chat(parent, 0, chatY));
        }
        return this.pre(type);
    }

    /** See {@code GameEventSink.onOverlayElementPost}. */
    void onOverlayElementPost(String element) {
        if (OverlayEvents.PLAYER_STATS.equals(element)) {
            OverlayEvents.PLAYER_STATS_TYPES.forEach(this::post);
            return;
        }
        this.post(ElementType.valueOf(element));
    }

    private boolean pre(ElementType type) {
        return MinecraftForge.EVENT_BUS.post(new RenderGameOverlayEvent.Pre(this.currentFrame(), type));
    }

    private void post(ElementType type) {
        MinecraftForge.EVENT_BUS.post(new RenderGameOverlayEvent.Post(this.currentFrame(), type));
    }

    /**
     * Returns the frame's parent event. Lunar sometimes draws an element (chat, hotbar) outside the
     * main method, in which case a parent is made on demand.
     */
    private RenderGameOverlayEvent currentFrame() {
        if (this.frame != null) {
            return this.frame;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        return new RenderGameOverlayEvent(minecraft.timer.renderPartialTicks, new ScaledResolution(minecraft));
    }

    /**
     * Publishes the {@code Text} event and draws the lines mods add on the left and right of the
     * screen, as {@code GuiIngameForge} does.
     */
    private void renderText() {
        RenderGameOverlayEvent parent = this.currentFrame();
        ArrayList<String> left = new ArrayList<>();
        ArrayList<String> right = new ArrayList<>();
        if (!MinecraftForge.EVENT_BUS.post(new RenderGameOverlayEvent.Text(parent, left, right))) {
            FontRenderer font = Minecraft.getMinecraft().fontRendererObj;
            OverlayEvents.drawLines(font, left, line -> OverlayEvents.TEXT_MARGIN);
            int screenWidth = parent.resolution.getScaledWidth();
            OverlayEvents.drawLines(font, right, line -> screenWidth - OverlayEvents.TEXT_MARGIN - font.getStringWidth(line));
        }
        this.post(ElementType.TEXT);
    }

    private static void drawLines(FontRenderer font, List<String> lines, LineStart startOf) {
        int top = OverlayEvents.TEXT_MARGIN;
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            int left = startOf.of(line);
            Gui.drawRect(left - 1, top - 1, left + font.getStringWidth(line) + 1, top + font.FONT_HEIGHT - 1,
                    OverlayEvents.TEXT_BACKGROUND);
            font.drawString(line, left, top, OverlayEvents.TEXT_COLOR);
            top += font.FONT_HEIGHT;
        }
    }

    /** Computes the x position where a line of text starts. */
    @FunctionalInterface
    private interface LineStart {
        int of(String line);
    }
}
