package fr.wakfu.rpg.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * v0.27 : zoom camera en combat (client -> server).
 * direction: 1 = zoom in, -1 = zoom out, 0 = reset.
 */
public class ZoomPacket {
    public int direction;

    public ZoomPacket() {}
    public ZoomPacket(int direction) { this.direction = direction; }

    public static void encode(ZoomPacket pkt, FriendlyByteBuf buf) {
        buf.writeVarInt(pkt.direction);
    }

    public static ZoomPacket decode(FriendlyByteBuf buf) {
        return new ZoomPacket(buf.readVarInt());
    }

    public static void handle(ZoomPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p == null) return;
            var combat = fr.wakfu.rpg.core.combat.CombatManager.getCombat(p.getUUID());
            if (combat == null) return;
            Entity cam = p.level().getEntity(combat.cameraId);
            if (cam == null) return;

            int size = combat.size;
            int defaultY = combat.floorY + Math.max(16, size + 8);
            int currentY = (int) cam.getY();
            int step = 3;

            if (pkt.direction == 0) {
                // Reset to default
                cam.teleportTo(cam.getX(), defaultY, cam.getZ());
            } else if (pkt.direction > 0) {
                // Zoom in (camera lower)
                int newY = Math.max(combat.floorY + 6, currentY - step);
                cam.setPos(cam.getX(), newY, cam.getZ());
            } else {
                // Zoom out (camera higher)
                int newY = Math.min(combat.floorY + 40, currentY + step);
                cam.setPos(cam.getX(), newY, cam.getZ());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
