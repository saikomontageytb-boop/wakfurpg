package fr.wakfu.rpg.entity;

import fr.wakfu.rpg.core.DialogueManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;

/**
 * PNJ Héraut : donne l'etat de la quete en cours et rend les recompenses.
 */
public class HeraldNpc extends ClassMasterNpc {

    public HeraldNpc(net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.PathfinderMob> type,
                     net.minecraft.world.level.Level level) {
        super(type, level);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide() && player instanceof ServerPlayer sp) {
            DialogueManager.openHerald(this, sp);
        }
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    @Override
    protected Component defaultName() {
        return Component.literal("Héraut des Quêtes");
    }

    @Override
    public Component getName() {
        Component custom = getCustomName();
        return custom != null ? custom : Component.translatable("entity.wakfurpg.herald");
    }
}
