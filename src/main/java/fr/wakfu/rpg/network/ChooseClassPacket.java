package fr.wakfu.rpg.network;

import fr.wakfu.rpg.core.ModCapabilities;
import fr.wakfu.rpg.core.RpgClass;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> serveur : choix de classe depuis l'ecran du grimoire. */
public class ChooseClassPacket {
    private final String classId;

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
                    player.displayClientMessage(Component.translatable("wakfurpg.already_class"), true);
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
