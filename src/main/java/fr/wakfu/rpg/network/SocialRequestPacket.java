package fr.wakfu.rpg.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** v0.25 : Client -> serveur : demande l'état social (ouverture de l'écran). */
public class SocialRequestPacket {

    public SocialRequestPacket() {
    }

    public static void encode(SocialRequestPacket msg, FriendlyByteBuf buf) {
    }

    public static SocialRequestPacket decode(FriendlyByteBuf buf) {
        return new SocialRequestPacket();
    }

    public static void handle(SocialRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp != null) fr.wakfu.rpg.core.Social.push(sp);
        });
        ctx.get().setPacketHandled(true);
    }
}
