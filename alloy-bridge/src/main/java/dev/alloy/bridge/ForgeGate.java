package dev.alloy.bridge;

/**
 * Says when game code may be given Forge classes. OptiFine and Lunar look for Forge by class name
 * and change behaviour if they find it, so Forge stays invisible to the game's class loader until
 * the runtime has made OptiFine settle on "Forge is absent" ({@code OptiFineBridge}). From then on,
 * code that mixins put in game classes can use Forge.
 */
public final class ForgeGate {

    private static volatile boolean open;

    private ForgeGate() {
    }

    /** Tells whether Forge classes may now be handed to game code. */
    public static boolean isOpen() {
        return ForgeGate.open;
    }

    /** Opens the gate; called once by the runtime. */
    public static void open() {
        ForgeGate.open = true;
    }
}
