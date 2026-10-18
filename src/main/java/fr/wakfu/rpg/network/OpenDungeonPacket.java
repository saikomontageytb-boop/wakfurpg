package fr.wakfu.rpg.network;

import fr.wakfu.rpg.core.ClientProxyHook;
import fr.wakfu.rpg.core.DungeonInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: show the dungeon entrance panel. */
public record OpenDungeonPacket(DungeonInfo dungeon) {
    public OpenDungeonPacket(String id, String name, int minLevel, int maxLevel) {
        this(new DungeonInfo(id, name, minLevel, maxLevel));
    }
    public static void encode(OpenDungeonPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.dungeon.id(), 64); buf.writeUtf(msg.dungeon.name(), 64);
        buf.writeVarInt(msg.dungeon.minLevel()); buf.writeVarInt(msg.dungeon.maxLevel());
    }
    public static OpenDungeonPacket decode(FriendlyByteBuf buf) {
        return new OpenDungeonPacket(buf.readUtf(64), buf.readUtf(64), buf.readVarInt(), buf.readVarInt());
    }
    public static void handle(OpenDungeonPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientProxyHook.openDungeon.accept(msg.dungeon));
        ctx.get().setPacketHandled(true);
    }
}
