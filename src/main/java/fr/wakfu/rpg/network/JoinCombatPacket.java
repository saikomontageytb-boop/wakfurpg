package fr.wakfu.rpg.network;

import fr.wakfu.rpg.core.combat.CombatManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * v0.19 : Client -> serveur — reponse a une invitation de combat de groupe.
 * accept=true : rejoindre (si la fenetre est encore ouverte) ; sinon refuser.
 */
public class JoinCombatPacket {
    public final int key;
    public final boolean accept;

    public JoinCombatPacket(int key, boolean accept) {
        this.key = key;
        this.accept = accept;
    }

    public static void encode(JoinCombatPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.key);
        buf.writeBoolean(msg.accept);
    }

    public static JoinCombatPacket decode(FriendlyByteBuf buf) {
        return new JoinCombatPacket(buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(JoinCombatPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = ctx.get().getSender();
            if (player == null) return;
            if (msg.accept) {
                CombatManager.joinCombat(player, msg.key);
            } else {
                CombatManager.declineInvite(player, msg.key);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
