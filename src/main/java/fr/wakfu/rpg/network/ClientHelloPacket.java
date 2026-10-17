package fr.wakfu.rpg.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> serveur : annonce la version du mod cote client (diagnostic launcher). */
public class ClientHelloPacket {
    public final String version;

    public ClientHelloPacket(String version) {
        this.version = version;
    }

    public static void encode(ClientHelloPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.version, 32);
    }

    public static ClientHelloPacket decode(FriendlyByteBuf buf) {
        return new ClientHelloPacket(buf.readUtf(32));
    }

    public static void handle(ClientHelloPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var sender = ctx.get().getSender();
            org.apache.logging.log4j.LogManager.getLogger("wakfurpg")
                    .info("Hello client v{} de {}", msg.version, sender == null ? "?" : sender.getName().getString());
        });
        ctx.get().setPacketHandled(true);
    }
}
