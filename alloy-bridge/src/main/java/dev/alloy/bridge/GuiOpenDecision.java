package dev.alloy.bridge;

/**
 * The mods' answer to a screen opening (Forge's {@code GuiOpenEvent}).
 *
 * @param cancelled {@code true} if a mod cancelled the opening: the current screen stays
 * @param screen    the screen to show when not cancelled (a {@code GuiScreen}, seen as an
 *                  {@code Object}); {@code null} closes the current screen
 */
public record GuiOpenDecision(boolean cancelled, Object screen) {

    /** Opening goes on with the given screen (the original or a replacement). */
    public static GuiOpenDecision show(Object screen) {
        return new GuiOpenDecision(false, screen);
    }

    /** Opening is cancelled. */
    public static GuiOpenDecision cancel() {
        return new GuiOpenDecision(true, null);
    }
}
