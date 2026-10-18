package fr.wakfu.rpg.network;

import fr.wakfu.rpg.core.ClientProxyHook;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Serveur -> client : ouvre le choix de classe fourni. */
public final class OpenClassSelectPacket {
    public static void encode(OpenClassSelectPacket msg, FriendlyByteBuf buf) {}
    public static OpenClassSelectPacket decode(FriendlyByteBuf buf) { return new OpenClassSelectPacket(); }
    public static void handle(OpenClassSelectPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientProxyHook.openClassSelect.run());
        ctx.get().setPacketHandled(true);
    }
}
