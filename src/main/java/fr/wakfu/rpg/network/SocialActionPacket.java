package fr.wakfu.rpg.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * v0.25 : Client -> serveur : action du menu social.
 * 0 ami+, 1 ami-, 2 invitation groupe, 3 duel 1v1, 4 accepter, 5 refuser, 6 quitter.
 */
public class SocialActionPacket {
    private final int action;
    private final String target;

    public SocialActionPacket(int action, String target) {
        this.action = action;
        this.target = target == null ? "" : target;
    }

    public static void encode(SocialActionPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.action);
        buf.writeUtf(msg.target);
    }

    public static SocialActionPacket decode(FriendlyByteBuf buf) {
        return new SocialActionPacket(buf.readVarInt(), buf.readUtf());
    }

    public static void handle(SocialActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp != null && msg.action >= 0 && msg.action <= 6 && msg.target.length() <= 64) {
                fr.wakfu.rpg.core.Social.handle(sp, msg.action, msg.target);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
