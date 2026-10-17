package fr.wakfu.rpg.network;

import fr.wakfu.rpg.core.DialogueManager;
import fr.wakfu.rpg.entity.ClassMasterNpc;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> serveur : le joueur a clique sur un choix de dialogue. */
public class NpcChoicePacket {
    private final int entityId;
    private final int choice;

    public NpcChoicePacket(int entityId, int choice) {
        this.entityId = entityId;
        this.choice = choice;
    }

    public static void encode(NpcChoicePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeVarInt(msg.choice);
    }

    public static NpcChoicePacket decode(FriendlyByteBuf buf) {
        return new NpcChoicePacket(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(NpcChoicePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = ctx.get().getSender();
            if (player == null) return;
            Entity e = player.level().getEntity(msg.entityId);
            if (e instanceof fr.wakfu.rpg.entity.HeraldNpc he) {
                DialogueManager.handleHeraldChoice(he, player, msg.choice);
            } else if (e instanceof fr.wakfu.rpg.entity.BlacksmithNpc bs) {
                DialogueManager.handleBlacksmithChoice(bs, player, msg.choice);
            } else if (e instanceof fr.wakfu.rpg.entity.MerchantNpc me) {
                DialogueManager.handleMerchantChoice(me, player, msg.choice);
            } else if (e instanceof ClassMasterNpc npc) {
                DialogueManager.handleChoice(npc, player, msg.choice);
            } else if (e instanceof fr.wakfu.rpg.entity.MonsterNpc monster) {
                fr.wakfu.rpg.core.combat.CombatManager.menuChoice(player, monster, msg.choice);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
