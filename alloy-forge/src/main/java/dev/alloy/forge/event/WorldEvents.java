package dev.alloy.forge.event;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.world.WorldEvent;

/** World and entity events. */
final class WorldEvents {

    /** See {@code GameEventSink.onWorldUnload}. */
    void onWorldUnload() {
        World current = Minecraft.getMinecraft().theWorld;
        if (current != null) {
            MinecraftForge.EVENT_BUS.post(new WorldEvent.Unload(current));
        }
    }

    /** See {@code GameEventSink.onWorldLoad}. */
    void onWorldLoad(World world) {
        MinecraftForge.EVENT_BUS.post(new WorldEvent.Load(world));
    }

    /** See {@code GameEventSink.onEntityJoinWorld}. */
    boolean onEntityJoinWorld(World world, Entity entity) {
        boolean cancelled = MinecraftForge.EVENT_BUS.post(new EntityJoinWorldEvent(entity, world));
        // Forge's rule: a player, or an entity whose spawn is forced, always joins.
        boolean alwaysJoins = entity.forceSpawn || entity instanceof EntityPlayer;
        return cancelled && !alwaysJoins;
    }

    /** See {@code GameEventSink.onLivingUpdate}. */
    boolean onLivingUpdate(EntityLivingBase entity) {
        return MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingUpdateEvent(entity));
    }

    /** See {@code GameEventSink.onAttackEntity}. */
    boolean onAttackEntity(EntityPlayer player, Entity target) {
        return MinecraftForge.EVENT_BUS.post(new AttackEntityEvent(player, target));
    }
}
