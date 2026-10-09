package dev.alloy.bridge;

/**
 * Receives the game events; each method matches a hook injected into a Minecraft class.
 *
 * <p>Parameters are {@code Object} because this interface is loaded by the system class loader,
 * which cannot see Minecraft classes; the implementation casts them to the real types noted below.</p>
 *
 * @see GameHooks
 */
public interface GameEventSink {

    // ------------------------------------------------------------------ lifecycle

    /**
     * Just before the first resource load ({@code Minecraft.startGame}): adds the mod jars as
     * resource packs and constructs the mods.
     */
    void onModsConstruct();

    /** Just after the first resource load: {@code FMLPreInitializationEvent}. */
    void onModsPreInit();

    /**
     * Just before {@code new GuiIngame}: {@code FMLInitializationEvent},
     * {@code FMLPostInitializationEvent} and {@code FMLLoadCompleteEvent}.
     */
    void onModsInit();

    // ------------------------------------------------------------------ ticks

    /** Start of {@code Minecraft.runTick}: {@code TickEvent.ClientTickEvent(START)}. */
    void onClientTickStart();

    /** End of {@code Minecraft.runTick}: {@code TickEvent.ClientTickEvent(END)}. */
    void onClientTickEnd();

    /** Before a frame is rendered: {@code TickEvent.RenderTickEvent(START)}. */
    void onRenderTickStart();

    /** After a frame is rendered: {@code TickEvent.RenderTickEvent(END)}. */
    void onRenderTickEnd();

    /**
     * Start of {@code EntityPlayer.onUpdate}: {@code TickEvent.PlayerTickEvent(START)}.
     *
     * @param player the player ({@code EntityPlayer})
     */
    void onPlayerTickStart(Object player);

    /**
     * End of {@code EntityPlayer.onUpdate}: {@code TickEvent.PlayerTickEvent(END)}.
     *
     * @param player the player ({@code EntityPlayer})
     */
    void onPlayerTickEnd(Object player);

    // ------------------------------------------------------------------ keyboard / mouse input

    /**
     * Replaces {@code Mouse.next()} in the mouse loop of {@code Minecraft.runTick}:
     * {@code MouseEvent} (cancellable) and {@code InputEvent.MouseInputEvent}.
     *
     * @return what {@code Mouse.next()} would have returned for the next non-cancelled event
     */
    boolean nextMouseEvent();

    /**
     * Replaces {@code Keyboard.next()} in the keyboard loop of {@code Minecraft.runTick}:
     * {@code InputEvent.KeyInputEvent}.
     *
     * @return what {@code Keyboard.next()} would have returned
     */
    boolean nextKeyboardEvent();

    /**
     * Replaces {@code Mouse.next()} in {@code GuiScreen.handleInput}:
     * {@code GuiScreenEvent.MouseInputEvent.Pre} (cancellable) and {@code .Post}.
     *
     * @param screen the screen reading its input ({@code GuiScreen})
     * @return {@code true} if a mouse event is left for the screen to process
     */
    boolean nextGuiMouseEvent(Object screen);

    /**
     * Replaces {@code Keyboard.next()} in {@code GuiScreen.handleInput}:
     * {@code GuiScreenEvent.KeyboardInputEvent.Pre} (cancellable) and {@code .Post}.
     *
     * @param screen the screen reading its input ({@code GuiScreen})
     * @return {@code true} if a keyboard event is left for the screen to process
     */
    boolean nextGuiKeyboardEvent(Object screen);

    // ------------------------------------------------------------------ screens

    /**
     * Start of {@code Minecraft.displayGuiScreen}: {@code GuiOpenEvent}.
     *
     * @param screen the requested screen ({@code GuiScreen}); {@code null} closes the current screen
     * @return the mods' decision: opening cancelled, or the screen to show (possibly replaced)
     */
    GuiOpenDecision onGuiOpen(Object screen);

    /**
     * Start of {@code GuiScreen.setWorldAndResolution}: {@code GuiScreenEvent.InitGuiEvent.Pre}.
     *
     * @param screen the screen ({@code GuiScreen})
     */
    void onGuiInitPre(Object screen);

    /**
     * End of {@code GuiScreen.setWorldAndResolution}: {@code GuiScreenEvent.InitGuiEvent.Post}.
     *
     * @param screen the screen ({@code GuiScreen})
     */
    void onGuiInitPost(Object screen);

    /**
     * Replaces the renderer's {@code screen.drawScreen(...)} call:
     * {@code GuiScreenEvent.DrawScreenEvent.Pre}, draw unless cancelled, then {@code .Post}.
     *
     * @param screen the screen to draw ({@code GuiScreen})
     */
    void drawScreen(Object screen, int mouseX, int mouseY, float partialTicks);

    /**
     * Replaces the {@code screen.actionPerformed(button)} call in {@code GuiScreen}:
     * {@code GuiScreenEvent.ActionPerformedEvent.Pre}, call unless cancelled, then {@code .Post}.
     *
     * @param screen the screen ({@code GuiScreen})
     * @param button the clicked button ({@code GuiButton})
     */
    void actionPerformed(Object screen, Object button);

    /**
     * Start of {@code GuiScreen.sendChatMessage(String, boolean)}: client-side commands ({@code ClientCommandHandler}).
     *
     * @return {@code true} if a client command handled the message (it is then not sent to the server)
     */
    boolean onClientCommand(String message, boolean addToChat);

    /** Start of {@code GuiChat.sendAutocompleteRequest}: client command completion. */
    void onAutoCompleteRequest(String leftOfCursor, String full);

    /** Start of {@code GuiChat.onAutocompleteResponse}: merges the client command completions with the server's. */
    String[] onAutoCompleteResponse(String[] serverCompletions);

    // ------------------------------------------------------------------ chat and network

    /**
     * Start of {@code NetHandlerPlayClient.handleChat}: {@code ClientChatReceivedEvent}.
     * Must do nothing off the client thread (the packet is replayed there).
     *
     * @param chatPacket the received packet ({@code S02PacketChat}); its text may be replaced
     * @return {@code true} if a mod cancelled the message
     */
    boolean onChatReceived(Object chatPacket);

    /**
     * Start of {@code NetHandlerPlayClient.handleJoinGame}:
     * {@code FMLNetworkEvent.ClientConnectedToServerEvent}, once per connection.
     *
     * @param netHandler the network handler ({@code NetHandlerPlayClient})
     */
    void onJoinGame(Object netHandler);

    // ------------------------------------------------------------------ world and entities

    /** Start of {@code Minecraft.loadWorld}: {@code WorldEvent.Unload} for the current world, if any. */
    void onWorldUnload();

    /**
     * End of the {@code WorldClient} constructor: {@code WorldEvent.Load}.
     *
     * @param world the created world ({@code WorldClient})
     */
    void onWorldLoad(Object world);

    /**
     * Start of {@code World.spawnEntityInWorld} / {@code joinEntityInSurroundings}: {@code EntityJoinWorldEvent}.
     *
     * @param world  the world ({@code World})
     * @param entity the entity ({@code Entity})
     * @return {@code true} if the entity must not join the world
     */
    boolean onEntityJoinWorld(Object world, Object entity);

    /**
     * Start of {@code EntityLivingBase.onUpdate}: {@code LivingEvent.LivingUpdateEvent}.
     *
     * @param entity the living entity ({@code EntityLivingBase})
     * @return {@code true} if the update must be skipped
     */
    boolean onLivingUpdate(Object entity);

    /**
     * Start of {@code EntityPlayer.attackTargetEntityWithCurrentItem}: {@code AttackEntityEvent}.
     *
     * @param player the attacking player ({@code EntityPlayer})
     * @param target the target ({@code Entity})
     * @return {@code true} if the attack is cancelled
     */
    boolean onAttackEntity(Object player, Object target);

    // ------------------------------------------------------------------ rendering

    /**
     * Start of {@code GuiIngame.renderGameOverlay}: {@code RenderGameOverlayEvent.Pre(ALL)}.
     *
     * @return {@code true} if the whole in-game overlay is cancelled
     */
    boolean onOverlayPre(float partialTicks);

    /** End of {@code GuiIngame.renderGameOverlay}: {@code Text} event, then {@code Post(ALL)}. */
    void onOverlayPost();

    /**
     * Start of drawing one in-game overlay element: {@code RenderGameOverlayEvent.Pre(element)}.
     *
     * @param element name of a {@code RenderGameOverlayEvent.ElementType} constant (e.g. {@code "CHAT"})
     * @return {@code true} if the element must not be drawn
     */
    boolean onOverlayElementPre(String element);

    /**
     * End of drawing one in-game overlay element: {@code RenderGameOverlayEvent.Post(element)}.
     *
     * @param element name of a {@code RenderGameOverlayEvent.ElementType} constant
     */
    void onOverlayElementPost(String element);

    /**
     * Start of {@code RendererLivingEntity.doRender}: {@code RenderLivingEvent.Pre}.
     *
     * @param renderer the renderer ({@code RendererLivingEntity})
     * @param entity   the drawn entity ({@code EntityLivingBase})
     * @return {@code true} if the entity must not be drawn
     */
    boolean onRenderLivingPre(Object renderer, Object entity, double x, double y, double z);

    /**
     * End of {@code RendererLivingEntity.doRender}: {@code RenderLivingEvent.Post}.
     * Parameters as in {@link #onRenderLivingPre}.
     */
    void onRenderLivingPost(Object renderer, Object entity, double x, double y, double z);

    /**
     * Start of {@code RendererLivingEntity.renderName}: {@code RenderLivingEvent.Specials.Pre}.
     * Parameters as in {@link #onRenderLivingPre}.
     *
     * @return {@code true} if the name tag must not be drawn
     */
    boolean onRenderLivingSpecialsPre(Object renderer, Object entity, double x, double y, double z);

    /**
     * End of {@code RendererLivingEntity.renderName}: {@code RenderLivingEvent.Specials.Post}.
     * Parameters as in {@link #onRenderLivingPre}.
     */
    void onRenderLivingSpecialsPost(Object renderer, Object entity, double x, double y, double z);

    /**
     * Start of {@code RenderPlayer.doRender}: {@code RenderPlayerEvent.Pre}.
     *
     * @param renderer the renderer ({@code RenderPlayer})
     * @param player   the drawn player ({@code AbstractClientPlayer})
     * @return {@code true} if the player must not be drawn
     */
    boolean onRenderPlayerPre(Object renderer, Object player, double x, double y, double z, float partialTicks);

    /**
     * End of {@code RenderPlayer.doRender}: {@code RenderPlayerEvent.Post}.
     * Parameters as in {@link #onRenderPlayerPre}.
     */
    void onRenderPlayerPost(Object renderer, Object player, double x, double y, double z, float partialTicks);

    /** In {@code EntityRenderer.renderWorldPass}, just before the hand is drawn: {@code RenderWorldLastEvent}. */
    void onRenderWorldLast(float partialTicks);

    /**
     * Start of {@code TextureMap.loadTextureAtlas}: {@code TextureStitchEvent.Pre}.
     *
     * @param textureMap the texture atlas ({@code TextureMap})
     */
    void onTextureStitchPre(Object textureMap);

    /**
     * End of {@code TextureMap.loadTextureAtlas}: {@code TextureStitchEvent.Post}.
     *
     * @param textureMap the texture atlas ({@code TextureMap})
     */
    void onTextureStitchPost(Object textureMap);

    /**
     * Return value of {@code AbstractClientPlayer.getFovModifier}: {@code FOVUpdateEvent}.
     *
     * @param player the player ({@code EntityPlayer})
     * @return the field of view to use
     */
    float onFovUpdate(float fov, Object player);

    /**
     * Return value of {@code ItemStack.getTooltip}: {@code ItemTooltipEvent}.
     *
     * @param tooltip the tooltip lines ({@code List<String>})
     * @param stack   the item stack ({@code ItemStack})
     * @param player  the player ({@code EntityPlayer}, may be {@code null})
     * @return the list to display ({@code List<String>})
     */
    Object onItemTooltip(Object tooltip, Object stack, Object player, boolean advanced);

    /**
     * Start of {@code SoundManager.playSound}: {@code PlaySoundEvent}.
     *
     * @param soundManager the sound manager ({@code SoundManager})
     * @param sound        the requested sound ({@code ISound})
     * @return the sound to play ({@code ISound}), or {@code null} to play nothing
     */
    Object onPlaySound(Object soundManager, Object sound);

    /**
     * Start of {@code RenderGlobal.drawSelectionBox}: {@code DrawBlockHighlightEvent}.
     *
     * @param renderGlobal the world renderer ({@code RenderGlobal})
     * @param player       the player ({@code EntityPlayer})
     * @param target       what the player is aiming at ({@code MovingObjectPosition})
     * @return {@code true} if the outline of the targeted block must not be drawn
     */
    boolean onDrawBlockHighlight(Object renderGlobal, Object player, Object target, int subId, float partialTicks);
}
