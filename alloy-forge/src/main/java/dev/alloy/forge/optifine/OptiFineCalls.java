package dev.alloy.forge.optifine;

import dev.alloy.bridge.BridgeLogger;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockPos;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.RenderBlockOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.common.eventhandler.Event;

/**
 * What OptiFine calls once {@link OptiFineBridge} has connected it: the Forge hook of the same
 * name, behind a guard. OptiFine rethrows whatever a hook throws, which would crash the game on a
 * failing mod listener; here the failure is logged and the game carries on as if no mod listened.
 * Called by reflection only.
 */
public final class OptiFineCalls {

    /** Stand-in for {@code MinecraftForge.EVENT_BUS} given to OptiFine. */
    public static final SafeBus EVENT_BUS = new SafeBus();

    /** Value Forge returns from {@code getFogDensity} when no mod sets the density. */
    private static final float FOG_DENSITY_UNCHANGED = -1.0F;
    private static final int MAX_REPORTS_PER_CALL = 3;
    private static final ConcurrentHashMap<String, Integer> REPORT_COUNTS = new ConcurrentHashMap<>();

    private static volatile BridgeLogger logger;

    private OptiFineCalls() {
    }

    static void setLogger(BridgeLogger bridgeLogger) {
        OptiFineCalls.logger = bridgeLogger;
    }

    /** {@code EntityViewRenderEvent.FOVModifier}. */
    public static float getFOVModifier(
            EntityRenderer renderer, Entity entity, Block block, double partialTicks, float fov) {
        try {
            return ForgeHooksClient.getFOVModifier(renderer, entity, block, partialTicks, fov);
        } catch (Throwable t) {
            OptiFineCalls.report("getFOVModifier", t);
            return fov;
        }
    }

    /** {@code EntityViewRenderEvent.FogDensity}. */
    public static float getFogDensity(
            EntityRenderer renderer, Entity entity, Block block, float partialTicks, float density) {
        try {
            return ForgeHooksClient.getFogDensity(renderer, entity, block, partialTicks, density);
        } catch (Throwable t) {
            OptiFineCalls.report("getFogDensity", t);
            return OptiFineCalls.FOG_DENSITY_UNCHANGED;
        }
    }

    /** {@code EntityViewRenderEvent.RenderFogEvent}. */
    public static void onFogRender(
            EntityRenderer renderer, Entity entity, Block block, float partialTicks, int mode, float distance) {
        try {
            ForgeHooksClient.onFogRender(renderer, entity, block, partialTicks, mode, distance);
        } catch (Throwable t) {
            OptiFineCalls.report("onFogRender", t);
        }
    }

    /** {@code RenderHandEvent}; {@code true} cancels the hand. */
    public static boolean renderFirstPersonHand(RenderGlobal context, float partialTicks, int renderPass) {
        try {
            return ForgeHooksClient.renderFirstPersonHand(context, partialTicks, renderPass);
        } catch (Throwable t) {
            OptiFineCalls.report("renderFirstPersonHand", t);
            return false;
        }
    }

    /** {@code RenderBlockOverlayEvent}; {@code true} cancels the overlay. */
    public static boolean renderBlockOverlay(
            EntityPlayer player, float partialTicks, RenderBlockOverlayEvent.OverlayType type,
            IBlockState block, BlockPos position) {
        try {
            return ForgeEventFactory.renderBlockOverlay(player, partialTicks, type, block, position);
        } catch (Throwable t) {
            OptiFineCalls.report("renderBlockOverlay", t);
            return false;
        }
    }

    /** {@code RenderBlockOverlayEvent} for the fire overlay. */
    public static boolean renderFireOverlay(EntityPlayer player, float partialTicks) {
        try {
            return ForgeEventFactory.renderFireOverlay(player, partialTicks);
        } catch (Throwable t) {
            OptiFineCalls.report("renderFireOverlay", t);
            return false;
        }
    }

    /** {@code RenderBlockOverlayEvent} for the water overlay. */
    public static boolean renderWaterOverlay(EntityPlayer player, float partialTicks) {
        try {
            return ForgeEventFactory.renderWaterOverlay(player, partialTicks);
        } catch (Throwable t) {
            OptiFineCalls.report("renderWaterOverlay", t);
            return false;
        }
    }

    /** Logs a failure, at most {@value #MAX_REPORTS_PER_CALL} times per call so a per-frame error stays readable. */
    private static void report(String call, Throwable error) {
        BridgeLogger current = OptiFineCalls.logger;
        if (current != null && OptiFineCalls.REPORT_COUNTS.merge(call, 1, Integer::sum) <= OptiFineCalls.MAX_REPORTS_PER_CALL) {
            current.error("Forge hook '" + call + "' called by OptiFine failed", error);
        }
    }

    /** The event bus as OptiFine uses it: one {@code post} method. */
    public static final class SafeBus {

        private SafeBus() {
        }

        /** Publishes an event OptiFine built; {@code true} if a mod cancelled it. */
        public boolean post(Object event) {
            try {
                return MinecraftForge.EVENT_BUS.post((Event) event);
            } catch (Throwable t) {
                OptiFineCalls.report("post " + event.getClass().getSimpleName(), t);
                return false;
            }
        }
    }
}
