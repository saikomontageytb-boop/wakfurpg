package fr.wakfu.rpg.network;

import fr.wakfu.rpg.core.ClientProxyHook;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** Brief damage/heal number over the affected entity. */
public final class DamagePopupPacket {
    public final int entityId, amount;
    public final boolean healing;
    public DamagePopupPacket(int entityId, int amount, boolean healing) {
        this.entityId = entityId; this.amount = Math.max(0, amount); this.healing = healing;
    }
    public static void encode(DamagePopupPacket p, FriendlyByteBuf b) {
        b.writeVarInt(p.entityId); b.writeVarInt(p.amount); b.writeBoolean(p.healing);
    }
    public static DamagePopupPacket decode(FriendlyByteBuf b) {
        return new DamagePopupPacket(b.readVarInt(), b.readVarInt(), b.readBoolean());
    }
    public static void handle(DamagePopupPacket p, Supplier<NetworkEvent.Context> c) {
        c.get().enqueueWork(() -> ClientProxyHook.onDamagePopup.accept(p));
        c.get().setPacketHandled(true);
    }
}
