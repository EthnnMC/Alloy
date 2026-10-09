package dev.alloy.forge.event;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.PlaySoundAtEntityEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.ChunkEvent;
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

    /** See {@code GameEventSink.onEntityInteract}. */
    boolean onEntityInteract(EntityPlayer player, Entity target) {
        return MinecraftForge.EVENT_BUS.post(new EntityInteractEvent(player, target));
    }

    /** See {@code GameEventSink.onUseItem}. */
    boolean onUseItem(PlayerControllerMP controller, EntityPlayer player, World world, ItemStack stack) {
        PlayerInteractEvent event =
                new PlayerInteractEvent(player, PlayerInteractEvent.Action.RIGHT_CLICK_AIR, null, null, world);
        if (MinecraftForge.EVENT_BUS.post(event)) {
            return false;
        }
        return controller.sendUseItem(player, world, stack);
    }

    /** See {@code GameEventSink.onPlaySoundAtEntity}. */
    boolean onPlaySoundAtEntity(Entity entity, String name, float volume, float pitch) {
        PlaySoundAtEntityEvent event = new PlaySoundAtEntityEvent(entity, name, volume, pitch);
        // Same rule as Forge: a cancelled event or an erased sound name plays nothing.
        return MinecraftForge.EVENT_BUS.post(event) || event.name == null;
    }

    /** See {@code GameEventSink.onEntityConstructing}. */
    void onEntityConstructing(Entity entity) {
        MinecraftForge.EVENT_BUS.post(new EntityEvent.EntityConstructing(entity));
    }

    /** See {@code GameEventSink.onLivingJump}. */
    void onLivingJump(EntityLivingBase entity) {
        MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingJumpEvent(entity));
    }

    /** See {@code GameEventSink.onChunkLoad}. */
    void onChunkLoad(Chunk chunk) {
        MinecraftForge.EVENT_BUS.post(new ChunkEvent.Load(chunk));
    }

    /** See {@code GameEventSink.onChunkUnload}. */
    void onChunkUnload(Chunk chunk) {
        MinecraftForge.EVENT_BUS.post(new ChunkEvent.Unload(chunk));
    }
}
