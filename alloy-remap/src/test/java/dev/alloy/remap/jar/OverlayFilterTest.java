package dev.alloy.remap.jar;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

class OverlayFilterTest {

    private static final OverlayFilter FILTER = new OverlayFilter(Set.of(
            "net/minecraftforge/fml/common/Loader",
            "net/minecraftforge/fml/relauncher/CoreModManager$FMLPluginWrapper"));

    @Test
    void matchesAnOverlaidClass() {
        assertTrue(OverlayFilterTest.FILTER.test("net/minecraftforge/fml/common/Loader"));
    }

    @Test
    void matchesInnerClassesAtAnyDepth() {
        assertTrue(OverlayFilterTest.FILTER.test("net/minecraftforge/fml/common/Loader$1"));
        assertTrue(OverlayFilterTest.FILTER.test("net/minecraftforge/fml/common/Loader$ModIdComparator$1"));
        assertTrue(OverlayFilterTest.FILTER.test("net/minecraftforge/fml/relauncher/CoreModManager$FMLPluginWrapper$1"));
    }

    @Test
    void doesNotMatchClassesThatOnlyShareAPrefix() {
        assertFalse(OverlayFilterTest.FILTER.test("net/minecraftforge/fml/common/LoaderState"));
        assertFalse(OverlayFilterTest.FILTER.test("net/minecraftforge/fml/common/LoadController"));
        // The outer class of a replaced inner class is not replaced with it.
        assertFalse(OverlayFilterTest.FILTER.test("net/minecraftforge/fml/relauncher/CoreModManager"));
        assertFalse(OverlayFilterTest.FILTER.test("net/minecraftforge/fml/relauncher/CoreModManager$1"));
    }
}
