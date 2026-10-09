package dev.alloy.forge.event;

import io.netty.channel.ChannelPipeline;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

/**
 * Chat and server-connection events. Nothing a server can see is changed: no Forge marker in the
 * handshake, no declared channel. Mods are only told what happens.
 */
final class NetworkEvents {

    private static final String VANILLA_HANDLER = "packet_handler";

    /** Connection type Forge reports when the server is not a Forge server. */
    private static final String VANILLA_CONNECTION = "VANILLA";

    /**
     * Connections already announced to mods. Weak keys, so a closed connection is forgotten;
     * synchronized because the first call comes from the network thread.
     */
    private final Set<NetworkManager> announced = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    /** See {@code GameEventSink.onChatReceived}. */
    boolean onChatReceived(S02PacketChat packet) {
        // The game handles the packet on the network thread, then replays it on the client thread:
        // the event must fire only the second time, as in Forge.
        if (!Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
            return false;
        }
        ClientChatReceivedEvent event = new ClientChatReceivedEvent(packet.getType(), packet.getChatComponent());
        if (MinecraftForge.EVENT_BUS.post(event) || event.message == null) {
            return true;
        }
        // A mod may have replaced the text: the game will display the packet's one.
        packet.chatComponent = event.message;
        return false;
    }

    /** See {@code GameEventSink.onJoinGame}. */
    void onJoinGame(NetHandlerPlayClient netHandler) {
        NetworkManager manager = netHandler.getNetworkManager();
        if (manager == null || !this.announced.add(manager)) {
            return;
        }
        ChannelPipeline pipeline = manager.channel.pipeline();
        if (pipeline.get(ConnectionWatcher.NAME) == null && pipeline.get(NetworkEvents.VANILLA_HANDLER) != null) {
            pipeline.addBefore(NetworkEvents.VANILLA_HANDLER, ConnectionWatcher.NAME, new ConnectionWatcher(manager));
        }
        MinecraftForge.EVENT_BUS.post(
                new FMLNetworkEvent.ClientConnectedToServerEvent(manager, NetworkEvents.VANILLA_CONNECTION));
    }
}
