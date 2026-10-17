package fr.wakfu.rpg.network;

import fr.wakfu.rpg.core.ModCapabilities;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Serveur -> client : met a jour les donnees RPG locales du joueur. */
public class SyncRpgDataPacket {
    private final CompoundTag tag;

    public SyncRpgDataPacket(CompoundTag tag) {
        this.tag = tag;
    }

    public static void encode(SyncRpgDataPacket msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.tag);
    }

    public static SyncRpgDataPacket decode(FriendlyByteBuf buf) {
        return new SyncRpgDataPacket(buf.readNbt());
    }

    public static void handle(SyncRpgDataPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.getCapability(ModCapabilities.RPG).ifPresent(data -> {
                    data.deserializeNBT(msg.tag);
                    fr.wakfu.rpg.client.DiscoveryToasts.check(data);
                });
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
