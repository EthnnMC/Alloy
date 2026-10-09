package dev.alloy.forge.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Keyboard and mouse events. Alloy can only replace the {@code next()} call of the game's input
 * loops, so it remembers that an event was handed to the game and publishes the "after" event on
 * the next call; a cancelled "before" event is simply skipped. Called from the game thread only.
 */
final class InputEvents {

    private boolean mouseEventPending;
    private boolean keyEventPending;
    private boolean guiMouseEventPending;
    private boolean guiKeyEventPending;

    /** See {@code GameEventSink.nextMouseEvent}. */
    boolean nextMouseEvent() {
        if (this.mouseEventPending) {
            this.mouseEventPending = false;
            FMLCommonHandler.instance().fireMouseInput();
        }
        while (Mouse.next()) {
            if (!MinecraftForge.EVENT_BUS.post(new MouseEvent())) {
                this.mouseEventPending = true;
                return true;
            }
        }
        return false;
    }

    /** See {@code GameEventSink.nextKeyboardEvent}. */
    boolean nextKeyboardEvent() {
        if (this.keyEventPending) {
            this.keyEventPending = false;
            FMLCommonHandler.instance().fireKeyInput();
        }
        this.keyEventPending = Keyboard.next();
        return this.keyEventPending;
    }

    /** See {@code GameEventSink.nextGuiMouseEvent}. */
    boolean nextGuiMouseEvent(GuiScreen screen) {
        if (this.guiMouseEventPending) {
            this.guiMouseEventPending = false;
            if (screen.equals(Minecraft.getMinecraft().currentScreen)) {
                MinecraftForge.EVENT_BUS.post(new GuiScreenEvent.MouseInputEvent.Post(screen));
            }
        }
        while (Mouse.next()) {
            if (!MinecraftForge.EVENT_BUS.post(new GuiScreenEvent.MouseInputEvent.Pre(screen))) {
                this.guiMouseEventPending = true;
                return true;
            }
        }
        return false;
    }

    /** See {@code GameEventSink.nextGuiKeyboardEvent}. */
    boolean nextGuiKeyboardEvent(GuiScreen screen) {
        if (this.guiKeyEventPending) {
            this.guiKeyEventPending = false;
            if (screen.equals(Minecraft.getMinecraft().currentScreen)) {
                MinecraftForge.EVENT_BUS.post(new GuiScreenEvent.KeyboardInputEvent.Post(screen));
            }
        }
        while (Keyboard.next()) {
            if (!MinecraftForge.EVENT_BUS.post(new GuiScreenEvent.KeyboardInputEvent.Pre(screen))) {
                this.guiKeyEventPending = true;
                return true;
            }
        }
        return false;
    }
}
