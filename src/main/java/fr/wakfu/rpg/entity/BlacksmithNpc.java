package fr.wakfu.rpg.entity;

import fr.wakfu.rpg.core.DialogueManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;

/**
 * PNJ Forgeron : forge et ameliore les armures Wakfu (lingots de monstres).
 */
public class BlacksmithNpc extends ClassMasterNpc {

    public BlacksmithNpc(net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.PathfinderMob> type,
                         net.minecraft.world.level.Level level) {
        super(type, level);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide() && player instanceof ServerPlayer sp) {
            DialogueManager.openBlacksmith(this, sp, null);
        }
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    @Override
    protected Component defaultName() {
        return Component.literal("Forgeron Sombre");
    }

    @Override
    public Component getName() {
        Component custom = getCustomName();
        return custom != null ? custom : Component.translatable("entity.wakfurpg.blacksmith");
    }
}
