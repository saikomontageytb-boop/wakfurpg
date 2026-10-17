package fr.wakfu.rpg.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * v0.27 : Packet de victoire de combat — envoie les donnees du combat
 * au client pour afficher l'ecran de victoire avec recompenses.
 */
public class VictoryPacket {

    public String title;          // "Victoire !" ou "Défaite..."
    public int totalXp;
    public long totalMoney;
    public List<String> mobNames; // noms des monstres vaincus
    public List<String> lootItems; // noms des objets obtenus
    public int round;
    public long duration;         // en ticks

    public VictoryPacket() {}

    public VictoryPacket(String title, int totalXp, long totalMoney,
                         List<String> mobNames, List<String> lootItems,
                         int round, long duration) {
        this.title = title;
        this.totalXp = totalXp;
        this.totalMoney = totalMoney;
        this.mobNames = mobNames;
        this.lootItems = lootItems;
        this.round = round;
        this.duration = duration;
    }

    public static void encode(VictoryPacket pkt, FriendlyByteBuf buf) {
        buf.writeUtf(pkt.title == null ? "" : pkt.title, 64);
        buf.writeVarInt(pkt.totalXp);
        buf.writeVarLong(pkt.totalMoney);
        buf.writeVarInt(pkt.mobNames.size());
        for (String s : pkt.mobNames) buf.writeUtf(s, 64);
        buf.writeVarInt(pkt.lootItems.size());
        for (String s : pkt.lootItems) buf.writeUtf(s, 128);
        buf.writeVarInt(pkt.round);
        buf.writeVarLong(pkt.duration);
    }

    public static VictoryPacket decode(FriendlyByteBuf buf) {
        VictoryPacket pkt = new VictoryPacket();
        pkt.title = buf.readUtf(64);
        pkt.totalXp = buf.readVarInt();
        pkt.totalMoney = buf.readVarLong();
        int mobCount = buf.readVarInt();
        pkt.mobNames = new ArrayList<>();
        for (int i = 0; i < mobCount; i++) pkt.mobNames.add(buf.readUtf(64));
        int lootCount = buf.readVarInt();
        pkt.lootItems = new ArrayList<>();
        for (int i = 0; i < lootCount; i++) pkt.lootItems.add(buf.readUtf(128));
        pkt.round = buf.readVarInt();
        pkt.duration = buf.readVarLong();
        return pkt;
    }

    public static void handle(VictoryPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            fr.wakfu.rpg.client.VictoryScreen.show(pkt);
        });
        ctx.get().setPacketHandled(true);
    }
}