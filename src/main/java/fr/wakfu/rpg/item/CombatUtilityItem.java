package fr.wakfu.rpg.item;

import fr.wakfu.rpg.core.combat.CombatManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Objets utilitaires de combat : deplacement (PM), fin de tour, fuite.
 * Places dans la hotbar pendant le combat instancie.
 */
public class CombatUtilityItem extends Item {

    public enum Kind { MOVE, END, FLEE }

    private final Kind kind;

    public CombatUtilityItem(Properties props, Kind kind) {
        super(props);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            CombatManager.onUtilityUse(sp, kind);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }
}
