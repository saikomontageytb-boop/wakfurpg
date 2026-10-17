package fr.wakfu.rpg.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** v0.25 : Serveur -> client : solde d'argent ($) pour le HUD et les écrans. */
public class MoneyPacket {
    private final long amount;

    public MoneyPacket(long amount) {
        this.amount = amount;
    }

    public static void encode(MoneyPacket msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.amount);
    }

    public static MoneyPacket decode(FriendlyByteBuf buf) {
        return new MoneyPacket(buf.readLong());
    }

    public static void handle(MoneyPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> fr.wakfu.rpg.client.ClientMoney.set(msg.amount));
        ctx.get().setPacketHandled(true);
    }
}
