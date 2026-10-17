package fr.wakfu.rpg.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * v0.25 : Client -> serveur : action de l'Hôtel des Ventes.
 * op 0 = vendre au serveur (a = slot 0-35) ;
 * op 1 = mettre en vente (a = slot, b = prix) ;
 * op 2 = acheter (a = id annonce) ; op 3 = retirer son annonce (a = id).
 */
public class MarketActionPacket {
    private final int op;
    private final int a;
    private final int b;

    public MarketActionPacket(int op, int a, int b) {
        this.op = op;
        this.a = a;
        this.b = b;
    }

    public static void encode(MarketActionPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.op);
        buf.writeVarInt(msg.a);
        buf.writeVarInt(msg.b);
    }

    public static MarketActionPacket decode(FriendlyByteBuf buf) {
        return new MarketActionPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(MarketActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp != null && msg.op >= 0 && msg.op <= 3
                    && msg.a >= 0 && msg.a < 1_000_000 && msg.b >= 0 && msg.b <= 1_000_000) {
                fr.wakfu.rpg.core.Market.handle(sp, msg.op, msg.a, msg.b);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
