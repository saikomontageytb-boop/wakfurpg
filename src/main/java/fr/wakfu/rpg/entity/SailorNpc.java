package fr.wakfu.rpg.entity;

import fr.wakfu.rpg.core.DialogueManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Harbor sailor who ferries eligible adventurers between the tutorial and the archipelago. */
public class SailorNpc extends ClassMasterNpc {
    public SailorNpc(EntityType<? extends net.minecraft.world.entity.PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide() && player instanceof ServerPlayer serverPlayer)
            DialogueManager.openSailor(this, serverPlayer);
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    @Override
    protected Component defaultName() {
        return Component.literal("Marin");
    }

    @Override
    public Component getName() {
        Component custom = getCustomName();
        return custom != null ? custom : Component.translatable("entity.wakfurpg.sailor");
    }
}
