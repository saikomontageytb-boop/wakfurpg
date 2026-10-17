package fr.wakfu.rpg.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * v0.25 : Serveur -> client : état du menu social — joueurs en ligne, amis,
 * groupe (chef + membres) et invitation de groupe en attente.
 */
public class SocialDataPacket {

    public record Plr(UUID id, String name, int level, boolean online) {}

    private final List<Plr> online;
    private final List<Plr> friends;
    private final UUID leader;          // null si pas de groupe
    private final List<Plr> members;
    private final String pendingFrom;   // "" si aucune invitation

    public SocialDataPacket(List<Plr> online, List<Plr> friends, UUID leader,
                            List<Plr> members, String pendingFrom) {
        this.online = online;
        this.friends = friends;
        this.leader = leader;
        this.members = members;
        this.pendingFrom = pendingFrom == null ? "" : pendingFrom;
    }

    private static void writeList(FriendlyByteBuf buf, List<Plr> l) {
        buf.writeVarInt(l.size());
        for (Plr p : l) {
            buf.writeUUID(p.id());
            buf.writeUtf(p.name());
            buf.writeVarInt(p.level());
            buf.writeBoolean(p.online());
        }
    }

    private static List<Plr> readList(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Plr> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            l.add(new Plr(buf.readUUID(), buf.readUtf(), buf.readVarInt(), buf.readBoolean()));
        }
        return l;
    }

    public static void encode(SocialDataPacket msg, FriendlyByteBuf buf) {
        writeList(buf, msg.online);
        writeList(buf, msg.friends);
        buf.writeBoolean(msg.leader != null);
        if (msg.leader != null) buf.writeUUID(msg.leader);
        writeList(buf, msg.members);
        buf.writeUtf(msg.pendingFrom);
    }

    public static SocialDataPacket decode(FriendlyByteBuf buf) {
        List<Plr> online = readList(buf);
        List<Plr> friends = readList(buf);
        UUID leader = buf.readBoolean() ? buf.readUUID() : null;
        List<Plr> members = readList(buf);
        String pending = buf.readUtf();
        return new SocialDataPacket(online, friends, leader, members, pending);
    }

    public static void handle(SocialDataPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> fr.wakfu.rpg.client.ClientSocial.set(
                msg.online, msg.friends, msg.leader, msg.members, msg.pendingFrom));
        ctx.get().setPacketHandled(true);
    }
}
