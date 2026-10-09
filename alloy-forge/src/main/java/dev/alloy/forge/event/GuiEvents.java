package dev.alloy.forge.event;

import com.google.common.collect.ObjectArrays;
import dev.alloy.bridge.GuiOpenDecision;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;

/** Screen (menu, chat) and client-command events. */
final class GuiEvents {

    /** See {@code GameEventSink.onGuiOpen}. */
    GuiOpenDecision onGuiOpen(GuiScreen requested) {
        Minecraft minecraft = Minecraft.getMinecraft();
        // Forge posts the event after "no screen" became the main menu or death screen: redo that so mods see the same screen.
        GuiScreen shown = requested;
        if (requested == null && minecraft.theWorld == null) {
            shown = new GuiMainMenu();
        } else if (requested == null && minecraft.thePlayer != null && minecraft.thePlayer.getHealth() <= 0.0F) {
            shown = new GuiGameOver();
        }
        GuiOpenEvent event = new GuiOpenEvent(shown);
        if (MinecraftForge.EVENT_BUS.post(event)) {
            return GuiOpenDecision.cancel();
        }
        // Unchanged by mods: return the original request so the game (and Lunar) do their own replacement.
        return GuiOpenDecision.show(event.gui == shown ? requested : event.gui);
    }

    /** See {@code GameEventSink.onGuiInitPre}. Cancelling this event is not supported. */
    void onGuiInitPre(GuiScreen screen) {
        MinecraftForge.EVENT_BUS.post(new GuiScreenEvent.InitGuiEvent.Pre(screen, screen.buttonList));
    }

    /** See {@code GameEventSink.onGuiInitPost}. */
    void onGuiInitPost(GuiScreen screen) {
        MinecraftForge.EVENT_BUS.post(new GuiScreenEvent.InitGuiEvent.Post(screen, screen.buttonList));
    }

    /** See {@code GameEventSink.drawScreen}. */
    void drawScreen(GuiScreen screen, int mouseX, int mouseY, float partialTicks) {
        if (!MinecraftForge.EVENT_BUS.post(new GuiScreenEvent.DrawScreenEvent.Pre(screen, mouseX, mouseY, partialTicks))) {
            screen.drawScreen(mouseX, mouseY, partialTicks);
        }
        MinecraftForge.EVENT_BUS.post(new GuiScreenEvent.DrawScreenEvent.Post(screen, mouseX, mouseY, partialTicks));
    }

    /** See {@code GameEventSink.actionPerformed}. */
    void actionPerformed(GuiScreen screen, GuiButton button) {
        GuiScreenEvent.ActionPerformedEvent.Pre before =
                new GuiScreenEvent.ActionPerformedEvent.Pre(screen, button, screen.buttonList);
        if (MinecraftForge.EVENT_BUS.post(before)) {
            return;
        }
        // A mod may have replaced the button in the event.
        screen.actionPerformed(before.button);
        if (screen.equals(Minecraft.getMinecraft().currentScreen)) {
            MinecraftForge.EVENT_BUS.post(
                    new GuiScreenEvent.ActionPerformedEvent.Post(screen, before.button, screen.buttonList));
        }
    }

    /** See {@code GameEventSink.onClientCommand}. */
    boolean onClientCommand(String message, boolean addToChat) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer == null
                || ClientCommandHandler.instance.executeCommand(minecraft.thePlayer, message) == 0) {
            return false;
        }
        if (addToChat) {
            // The game would have done this before sending the message: the up arrow must recall the command.
            minecraft.ingameGUI.getChatGUI().addToSentMessages(message);
        }
        return true;
    }

    /** See {@code GameEventSink.onAutoCompleteRequest}. */
    void onAutoCompleteRequest(String leftOfCursor, String full) {
        ClientCommandHandler.instance.autoComplete(leftOfCursor, full);
    }

    /** See {@code GameEventSink.onAutoCompleteResponse}. */
    String[] onAutoCompleteResponse(String[] serverCompletions) {
        String[] clientCompletions = ClientCommandHandler.instance.latestAutoComplete;
        if (clientCompletions == null) {
            return serverCompletions;
        }
        return ObjectArrays.concat(clientCompletions, serverCompletions, String.class);
    }
}
