package dev.alloy.bridge;

/**
 * Notified once, at the very start of {@code net.minecraft.client.main.Main.main}, so the agent
 * can prepare the mods and start the Forge runtime.
 */
@FunctionalInterface
public interface GameStartListener {

    /**
     * The game is starting.
     *
     * @param arguments Minecraft's launch arguments (never {@code null})
     */
    void onGameStart(String[] arguments);
}
