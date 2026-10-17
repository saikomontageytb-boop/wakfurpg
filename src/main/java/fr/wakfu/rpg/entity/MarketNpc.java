package fr.wakfu.rpg.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;

/**
 * v0.25 : PNJ Hôtel des Ventes — marché entre joueurs (annonces en $)
 * et rachat des objets par le serveur à prix très bas.
 */
public class MarketNpc extends ClassMasterNpc {

    public MarketNpc(net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.PathfinderMob> type,
                     net.minecraft.world.level.Level level) {
        super(type, level);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide() && player instanceof ServerPlayer sp) {
            fr.wakfu.rpg.core.Market.open(sp);
        }
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    @Override
    protected Component defaultName() {
        return Component.literal("Hôtel des Ventes");
    }

    @Override
    public Component getName() {
        Component custom = getCustomName();
        return custom != null ? custom : defaultName();
    }
}
