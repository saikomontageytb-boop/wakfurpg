package fr.wakfu.rpg.network;

import fr.wakfu.rpg.core.ClientProxyHook;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Serveur -> client : effets visuels d'un sort (particules par cellule). */
public class CombatFxPacket {
    public final String spellId;
    public final int casterX;
    public final int casterZ;
    public final int floorY;
    public final List<BlockPos> cells;

    public CombatFxPacket(String spellId, int casterX, int casterZ, int floorY, List<BlockPos> cells) {
        this.spellId = spellId;
        this.casterX = casterX;
        this.casterZ = casterZ;
        this.floorY = floorY;
        this.cells = cells.size() > 32 ? cells.subList(0, 32) : cells;
    }

    public static void encode(CombatFxPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.spellId, 64);
        buf.writeVarInt(msg.casterX);
        buf.writeVarInt(msg.casterZ);
        buf.writeVarInt(msg.floorY);
        buf.writeVarInt(msg.cells.size());
        for (BlockPos p : msg.cells) buf.writeLong(p.asLong());
    }

    public static CombatFxPacket decode(FriendlyByteBuf buf) {
        String id = buf.readUtf(64);
        int cx = buf.readVarInt();
        int cz = buf.readVarInt();
        int fy = buf.readVarInt();
        int n = buf.readVarInt();
        List<BlockPos> cells = new ArrayList<>(n);
        for (int i = 0; i < n && i < 32; i++) cells.add(BlockPos.of(buf.readLong()));
        return new CombatFxPacket(id, cx, cz, fy, cells);
    }

    public static void handle(CombatFxPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientProxyHook.onCombatFx.accept(msg));
        ctx.get().setPacketHandled(true);
    }
}
