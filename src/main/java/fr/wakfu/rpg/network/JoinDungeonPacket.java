package fr.wakfu.rpg.network;

import fr.wakfu.rpg.core.DungeonManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: confirms the dungeon entrance panel. */
public record JoinDungeonPacket(String id) {
    public static void encode(JoinDungeonPacket msg, FriendlyByteBuf buf) { buf.writeUtf(msg.id, 64); }
    public static JoinDungeonPacket decode(FriendlyByteBuf buf) { return new JoinDungeonPacket(buf.readUtf(64)); }
    public static void handle(JoinDungeonPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = ctx.get().getSender();
            if (player != null) DungeonManager.join(player, msg.id);
        });
        ctx.get().setPacketHandled(true);
    }
}
