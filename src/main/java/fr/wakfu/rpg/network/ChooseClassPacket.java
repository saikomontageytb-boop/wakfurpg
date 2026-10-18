package fr.wakfu.rpg.network;

import fr.wakfu.rpg.core.ModCapabilities;
import fr.wakfu.rpg.core.RpgClass;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Client -> serveur : choix de classe depuis l'ecran du grimoire. */
public class ChooseClassPacket {
    private static final ConcurrentHashMap<UUID, Long> CLASS_CHANGE_PERMISSIONS = new ConcurrentHashMap<>();
    private static final long CLASS_CHANGE_PERMISSION_TTL_MS = 10 * 60 * 1000L;

    private final String classId;

    public static void allowClassChange(UUID playerId) {
        CLASS_CHANGE_PERMISSIONS.put(playerId, System.currentTimeMillis());
    }

    public static boolean consumeClassChangePermission(UUID playerId) {
        Long grantedAt = CLASS_CHANGE_PERMISSIONS.remove(playerId);
        return grantedAt != null && System.currentTimeMillis() - grantedAt <= CLASS_CHANGE_PERMISSION_TTL_MS;
    }

    public ChooseClassPacket(String classId) {
        this.classId = classId;
    }

    public static void encode(ChooseClassPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.classId, 32);
    }

    public static ChooseClassPacket decode(FriendlyByteBuf buf) {
        return new ChooseClassPacket(buf.readUtf(32));
    }

    public static void handle(ChooseClassPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = ctx.get().getSender();
            if (player == null) return;
            RpgClass c = RpgClass.byId(msg.classId);
            if (c == null) return;
            player.getCapability(ModCapabilities.RPG).ifPresent(data -> {
                if (data.hasClass()) {
                    if (!consumeClassChangePermission(player.getUUID())) {
                        player.displayClientMessage(Component.literal(
                                "Utilise /wakfu classe pour rouvrir le choix des classes."), true);
                        return;
                    }
                    if (data.getRpgClass() == c) {
                        player.displayClientMessage(Component.literal(
                                "Tu es déjà " + c.getName().getString() + "."), true);
                        return;
                    }
                    data.changeClass(c);
                    player.displayClientMessage(Component.literal("Classe changée pour "
                            + c.getName().getString() + " ; niveau conservé, sorts réinitialisés."), true);
                    fr.wakfu.rpg.events.CommonEvents.sync(player);
                    return;
                }
                data.chooseClass(c);
                player.displayClientMessage(Component.translatable("wakfurpg.class_chosen", c.getName()), true);
                Network.sendToPlayer(player, new SyncRpgDataPacket(data.serializeNBT()));
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
