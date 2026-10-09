package dev.alloy.hooks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alloy.bridge.GameHooks;
import dev.alloy.hooks.inject.HeadCall;
import dev.alloy.hooks.inject.HookCall;
import dev.alloy.hooks.inject.InjectionStrategy;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

/**
 * Checks the default catalog as data: it names only methods that exist in {@link GameHooks} and
 * holds exactly the expected hooks.
 */
class HookCatalogTest {

    private final HookCatalog catalog = HookCatalog.forgeDefaults();

    @Test
    void everyHookCallNamesAnExistingGameHooksMethod() {
        Set<String> available = HookCatalogTest.publicStaticMethodsOf(GameHooks.class);

        List<String> unknown = new ArrayList<>();
        for (Hook hook : this.catalog.hooks()) {
            for (InjectionStrategy injection : hook.injections()) {
                for (HookCall call : injection.hookCalls()) {
                    if (!available.contains(call.name() + call.descriptor())) {
                        unknown.add(hook.id() + " -> " + call.signature());
                    }
                }
            }
        }

        assertEquals(List.of(), unknown);
    }

    @Test
    void catalogHoldsTheHooksOfTheDesign() {
        List<String> expected = List.of(
                "boot.main", "life.construct", "life.preinit", "life.init",
                "tick.client", "tick.render", "tick.player",
                "input.mouse", "input.keyboard", "gui.input.mouse", "gui.input.keyboard",
                "gui.open", "gui.init", "gui.draw", "gui.action",
                "chat.command", "chat.complete.request", "chat.complete.response", "chat.received", "net.join",
                "world.unload", "world.load", "entity.join.spawn", "entity.join.surroundings",
                "entity.living.update", "entity.attack",
                "overlay.all", "overlay.chat", "overlay.hotbar", "overlay.experience", "overlay.jumpbar",
                "overlay.bosshealth", "overlay.helmet", "overlay.portal", "overlay.playerlist", "overlay.crosshairs",
                "render.living", "render.living.specials", "render.player", "render.world.last",
                "render.texture.stitch", "render.fov", "render.tooltip", "render.highlight", "sound.play");

        assertEquals(expected, this.catalog.hookIds());
    }

    @Test
    void onlyTheStartHookIsActiveBeforeTheRuntimeExists() {
        List<String> alwaysActive = this.catalog.hooks().stream().filter(Hook::alwaysActive).map(Hook::id).toList();

        assertEquals(List.of("boot.main"), alwaysActive);
    }

    @Test
    void targetClassesAreInternalNames() {
        for (String className : this.catalog.classNames()) {
            assertTrue(className.startsWith("net/minecraft/") && !className.contains("."), className);
        }
    }

    @Test
    void hooksAreFoundByTargetClass() {
        List<String> ids = this.catalog.hooksFor("net/minecraft/world/World").stream().map(Hook::id).toList();

        assertEquals(List.of("entity.join.spawn", "entity.join.surroundings"), ids);
    }

    @Test
    void classOutsideTheCatalogHasNoHook() {
        assertEquals(List.of(), this.catalog.hooksFor("java/lang/String"));
    }

    @Test
    void duplicateIdsAreRejected() {
        Hook hook = Hook.inMethod("same.id", GameClasses.MINECRAFT, "runTick", "()V",
                new HeadCall(HookCall.to("clientTickStart", "()V")));

        assertThrows(IllegalArgumentException.class, () -> HookCatalog.of(List.of(hook, hook)));
    }

    /** Lists the public static methods of a class as {@code name + descriptor}. */
    private static Set<String> publicStaticMethodsOf(Class<?> type) {
        Set<String> signatures = new HashSet<>();
        for (Method method : type.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers()) && Modifier.isStatic(method.getModifiers())) {
                signatures.add(method.getName() + Type.getMethodDescriptor(method));
            }
        }
        return signatures;
    }
}
