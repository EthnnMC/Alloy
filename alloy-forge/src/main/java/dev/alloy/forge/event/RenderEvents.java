package dev.alloy.forge.event;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundEventAccessorComposite;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

/** Events for world, entity, texture and sound rendering. */
final class RenderEvents {

    /** See {@code GameEventSink.onRenderLivingPre}. */
    @SuppressWarnings({"rawtypes", "unchecked"}) // Forge's events are generic, the game's renderer is not here
    boolean onRenderLivingPre(RendererLivingEntity renderer, EntityLivingBase entity, double x, double y, double z) {
        return MinecraftForge.EVENT_BUS.post(new RenderLivingEvent.Pre(entity, renderer, x, y, z));
    }

    /** See {@code GameEventSink.onRenderLivingPost}. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    void onRenderLivingPost(RendererLivingEntity renderer, EntityLivingBase entity, double x, double y, double z) {
        MinecraftForge.EVENT_BUS.post(new RenderLivingEvent.Post(entity, renderer, x, y, z));
    }

    /** See {@code GameEventSink.onRenderLivingSpecialsPre}. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    boolean onRenderLivingSpecialsPre(RendererLivingEntity renderer, EntityLivingBase entity, double x, double y, double z) {
        return MinecraftForge.EVENT_BUS.post(new RenderLivingEvent.Specials.Pre(entity, renderer, x, y, z));
    }

    /** See {@code GameEventSink.onRenderLivingSpecialsPost}. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    void onRenderLivingSpecialsPost(RendererLivingEntity renderer, EntityLivingBase entity, double x, double y, double z) {
        MinecraftForge.EVENT_BUS.post(new RenderLivingEvent.Specials.Post(entity, renderer, x, y, z));
    }

    /** See {@code GameEventSink.onRenderPlayerPre}. */
    boolean onRenderPlayerPre(RenderPlayer renderer, EntityPlayer player, double x, double y, double z, float partialTicks) {
        return MinecraftForge.EVENT_BUS.post(new RenderPlayerEvent.Pre(player, renderer, partialTicks, x, y, z));
    }

    /** See {@code GameEventSink.onRenderPlayerPost}. */
    void onRenderPlayerPost(RenderPlayer renderer, EntityPlayer player, double x, double y, double z, float partialTicks) {
        MinecraftForge.EVENT_BUS.post(new RenderPlayerEvent.Post(player, renderer, partialTicks, x, y, z));
    }

    /** See {@code GameEventSink.onRenderWorldLast}. */
    void onRenderWorldLast(float partialTicks) {
        RenderGlobal renderGlobal = Minecraft.getMinecraft().renderGlobal;
        MinecraftForge.EVENT_BUS.post(new RenderWorldLastEvent(renderGlobal, partialTicks));
    }

    /** See {@code GameEventSink.onTextureStitchPre}. */
    void onTextureStitchPre(TextureMap map) {
        MinecraftForge.EVENT_BUS.post(new TextureStitchEvent.Pre(map));
    }

    /** See {@code GameEventSink.onTextureStitchPost}. */
    void onTextureStitchPost(TextureMap map) {
        MinecraftForge.EVENT_BUS.post(new TextureStitchEvent.Post(map));
    }

    /** See {@code GameEventSink.onFovUpdate}. */
    float onFovUpdate(float fov, EntityPlayer player) {
        FOVUpdateEvent event = new FOVUpdateEvent(player, fov);
        MinecraftForge.EVENT_BUS.post(event);
        return event.newfov;
    }

    /** See {@code GameEventSink.onItemTooltip}. */
    List<String> onItemTooltip(List<String> tooltip, ItemStack stack, EntityPlayer player, boolean advanced) {
        MinecraftForge.EVENT_BUS.post(new ItemTooltipEvent(stack, player, tooltip, advanced));
        return tooltip;
    }

    /** See {@code GameEventSink.onPlaySound}. */
    ISound onPlaySound(SoundManager manager, ISound sound) {
        SoundEventAccessorComposite accessor = manager.sndHandler.getSound(sound.getSoundLocation());
        PlaySoundEvent event = new PlaySoundEvent(manager, sound, accessor == null ? null : accessor.getSoundCategory());
        MinecraftForge.EVENT_BUS.post(event);
        return event.result;
    }

    /** See {@code GameEventSink.onDrawBlockHighlight}. */
    boolean onDrawBlockHighlight(
            RenderGlobal renderGlobal, EntityPlayer player, MovingObjectPosition target, int subId, float partialTicks) {
        return MinecraftForge.EVENT_BUS.post(new DrawBlockHighlightEvent(
                renderGlobal, player, target, subId, player.getHeldItem(), partialTicks));
    }
}
