package fr.wakfu.rpg.entity;

import fr.wakfu.rpg.core.DialogueManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;

/**
 * v0.18 : PNJ Marchand — achete/vend contre des lingots de Wakfu,
 * avec des prix qui fluctuent chaque jour de jeu (marche dynamique).
 */
public class MerchantNpc extends ClassMasterNpc {

    public MerchantNpc(net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.PathfinderMob> type,
                       net.minecraft.world.level.Level level) {
        super(type, level);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide() && player instanceof ServerPlayer sp) {
            DialogueManager.openMerchant(this, sp, null);
        }
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    @Override
    protected Component defaultName() {
        return Component.literal("Marchand");
    }

    @Override
    public Component getName() {
        Component custom = getCustomName();
        return custom != null ? custom : Component.translatable("entity.wakfurpg.merchant");
    }
}
