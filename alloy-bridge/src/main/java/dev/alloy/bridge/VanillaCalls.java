package dev.alloy.bridge;

import java.lang.reflect.Method;

/**
 * Replays by reflection the original game call that a hook replaced, for when the Forge runtime
 * is not installed (the game would otherwise lose its input or display). Not used in normal operation.
 */
final class VanillaCalls {

    private static final String MOUSE_CLASS = "org.lwjgl.input.Mouse";
    private static final String KEYBOARD_CLASS = "org.lwjgl.input.Keyboard";

    private VanillaCalls() {
    }

    /** Equivalent of {@code org.lwjgl.input.Mouse.next()}. */
    static boolean mouseNext() {
        return VanillaCalls.invokeStaticNext(VanillaCalls.MOUSE_CLASS);
    }

    /** Equivalent of {@code org.lwjgl.input.Keyboard.next()}. */
    static boolean keyboardNext() {
        return VanillaCalls.invokeStaticNext(VanillaCalls.KEYBOARD_CLASS);
    }

    /** Equivalent of {@code screen.drawScreen(mouseX, mouseY, partialTicks)} on a {@code GuiScreen}. */
    static void drawScreen(Object screen, int mouseX, int mouseY, float partialTicks) {
        try {
            Method method = screen.getClass().getMethod("drawScreen", int.class, int.class, float.class);
            method.invoke(screen, mouseX, mouseY, partialTicks);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot replay GuiScreen.drawScreen", e);
        }
    }

    /** Equivalent of {@code screen.actionPerformed(button)} on a {@code GuiScreen}. */
    static void actionPerformed(Object screen, Object button) {
        try {
            Method method = VanillaCalls.findSingleParameterMethod(screen.getClass(), "actionPerformed");
            method.setAccessible(true);
            method.invoke(screen, button);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot replay GuiScreen.actionPerformed", e);
        }
    }

    private static boolean invokeStaticNext(String className) {
        try {
            // On the game thread the context class loader is Lunar's, which can see LWJGL.
            ClassLoader gameLoader = Thread.currentThread().getContextClassLoader();
            Class<?> inputClass = Class.forName(className, true, gameLoader);
            return (Boolean) inputClass.getMethod("next").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot replay " + className + ".next()", e);
        }
    }

    private static Method findSingleParameterMethod(Class<?> start, String name) throws NoSuchMethodException {
        // The method is protected in the original game, so walk up the hierarchy by hand.
        for (Class<?> type = start; type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == 1) {
                    return method;
                }
            }
        }
        throw new NoSuchMethodException(start.getName() + "." + name);
    }
}
