package fr.wakfu.rpg.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * v0.19 : Serveur -> client — invitation a rejoindre le combat d'un membre
 * du groupe (notif cliquable), ou revoke (clear=true) quand le combat a
 * vraiment commence (premier deplacement/sort) ou expire.
 */
public class CombatInvitePacket {
    public final int key;
    public final boolean clear;
    public final String leader;
    public final int mobs;

    public CombatInvitePacket(int key, boolean clear, String leader, int mobs) {
        this.key = key;
        this.clear = clear;
        this.leader = leader;
        this.mobs = mobs;
    }

    public static void encode(CombatInvitePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.key);
        buf.writeBoolean(msg.clear);
        buf.writeUtf(msg.leader, 64);
        buf.writeVarInt(msg.mobs);
    }

    public static CombatInvitePacket decode(FriendlyByteBuf buf) {
        return new CombatInvitePacket(buf.readVarInt(), buf.readBoolean(), buf.readUtf(64), buf.readVarInt());
    }

    public static void handle(CombatInvitePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (msg.clear) {
                fr.wakfu.rpg.client.ClientInvites.clear(msg.key);
            } else {
                fr.wakfu.rpg.client.ClientInvites.set(msg.key, msg.leader, msg.mobs);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
