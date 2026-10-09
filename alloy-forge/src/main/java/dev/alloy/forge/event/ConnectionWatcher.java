package dev.alloy.forge.event;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.network.NetworkManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

/**
 * Handler added to the game's connection to know when it closes. It has the same name as Forge's
 * {@code fml:packet_handler}, which mods rely on to insert their own handler in front of it. It
 * does not touch any packet and sends nothing to the server.
 */
final class ConnectionWatcher extends ChannelDuplexHandler {

    /** Handler name, identical to Forge's. */
    static final String NAME = "fml:packet_handler";

    private final NetworkManager manager;

    /** Ensures the disconnection event is published once, whichever way the connection closes. */
    private final AtomicBoolean disconnected = new AtomicBoolean();

    ConnectionWatcher(NetworkManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager");
    }

    @Override
    public void disconnect(ChannelHandlerContext context, ChannelPromise promise) throws Exception {
        this.announceDisconnection();
        super.disconnect(context, promise);
    }

    @Override
    public void close(ChannelHandlerContext context, ChannelPromise promise) throws Exception {
        this.announceDisconnection();
        super.close(context, promise);
    }

    @Override
    public void channelInactive(ChannelHandlerContext context) throws Exception {
        this.announceDisconnection();
        super.channelInactive(context);
    }

    private void announceDisconnection() {
        if (this.disconnected.compareAndSet(false, true)) {
            MinecraftForge.EVENT_BUS.post(new FMLNetworkEvent.ClientDisconnectionFromServerEvent(this.manager));
        }
    }
}
