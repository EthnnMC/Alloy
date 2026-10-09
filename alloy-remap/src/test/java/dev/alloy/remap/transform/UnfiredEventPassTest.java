package dev.alloy.remap.transform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.alloy.remap.hierarchy.ClassHeader;
import dev.alloy.remap.hierarchy.ClassHierarchy;
import dev.alloy.remap.hierarchy.HeaderTable;
import dev.alloy.remap.testkit.ClassBytes;
import dev.alloy.remap.testkit.ForgeStubs;
import dev.alloy.remap.testkit.SourceCompiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class UnfiredEventPassTest {

    private static final String CLIENT_TICK = "net/minecraftforge/fml/common/gameevent/TickEvent$ClientTickEvent";

    private static Map<String, byte[]> compiled;

    @BeforeAll
    static void compile() {
        UnfiredEventPassTest.compiled = SourceCompiler.compile(8,
                ForgeStubs.SUBSCRIBE_EVENT,
                """
                package net.minecraftforge.fml.common.gameevent;
                public class TickEvent {
                    public static class ClientTickEvent extends TickEvent { }
                    public static class ServerTickEvent extends TickEvent { }
                }
                """,
                """
                package example.mod;
                import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
                import net.minecraftforge.fml.common.gameevent.TickEvent;
                class Handler {
                    @SubscribeEvent void published(TickEvent.ClientTickEvent event) { }
                    @SubscribeEvent void parentOfPublished(TickEvent event) { }
                    @SubscribeEvent void neverPublished(TickEvent.ServerTickEvent event) { }
                    @SubscribeEvent void sameEventAgain(TickEvent.ServerTickEvent event) { }
                    @SubscribeEvent void ownEvent(OwnEvent event) { }
                    void notAListener(TickEvent.ServerTickEvent event) { }
                }
                """,
                """
                package example.mod;
                class OwnEvent { }
                """);
    }

    private static List<String> warningsWith(Set<String> firedEvents) {
        List<ClassHeader> headers = new ArrayList<>();
        UnfiredEventPassTest.compiled.values().forEach(classFile -> headers.add(ClassHeader.read(classFile)));
        List<String> warnings = new ArrayList<>();
        UnfiredEventPass pass =
                new UnfiredEventPass(firedEvents, new ClassHierarchy(HeaderTable.of(headers)), warnings::add);
        ClassBytes.applyToAll(Map.of("example/mod/Handler", UnfiredEventPassTest.compiled.get("example/mod/Handler")), pass);
        return warnings;
    }

    @Test
    void forgeEventThatIsNeverPublishedIsReportedOnce() {
        List<String> warnings = UnfiredEventPassTest.warningsWith(Set.of(UnfiredEventPassTest.CLIENT_TICK));

        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("TickEvent.ServerTickEvent"), warnings.get(0));
        assertTrue(warnings.get(0).contains("example/mod/Handler.neverPublished"), warnings.get(0));
    }

    @Test
    void eventDefinedByTheModIsNeverReported() {
        List<String> warnings = UnfiredEventPassTest.warningsWith(Set.of());

        // Nothing is published here: the three Forge events are reported, the mod's own is not.
        assertEquals(3, warnings.size(), warnings.toString());
        assertTrue(warnings.stream().noneMatch(warning -> warning.contains("OwnEvent")), warnings.toString());
    }
}
