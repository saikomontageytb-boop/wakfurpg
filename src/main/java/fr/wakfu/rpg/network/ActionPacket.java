package fr.wakfu.rpg.network;

import fr.wakfu.rpg.core.combat.CombatManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> serveur : action de combat (sort zone / deplacement case / fin tour / fuite). */
public class ActionPacket {
    public static final int CAST = 0;
    public static final int MOVE = 1;
    public static final int END = 2;
    public static final int FLEE = 3;

    public final int kind;
    public final String spellId;
    public final int cellX;
    public final int cellZ;

    public ActionPacket(int kind, String spellId, int cellX, int cellZ) {
        this.kind = kind;
        this.spellId = spellId;
        this.cellX = cellX;
        this.cellZ = cellZ;
    }

    public static void encode(ActionPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.kind);
        buf.writeUtf(msg.spellId, 32);
        buf.writeVarInt(msg.cellX);
        buf.writeVarInt(msg.cellZ);
    }

    public static ActionPacket decode(FriendlyByteBuf buf) {
        return new ActionPacket(buf.readVarInt(), buf.readUtf(32), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(ActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var p = ctx.get().getSender();
            if (p == null) return;
            switch (msg.kind) {
                case CAST -> CombatManager.castAtCell(p, msg.spellId, msg.cellX, msg.cellZ);
                case MOVE -> CombatManager.moveToCell(p, msg.cellX, msg.cellZ);
                case END -> CombatManager.endTurn(p);
                case FLEE -> CombatManager.flee(p);
                default -> { }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
