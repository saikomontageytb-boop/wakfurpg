package fr.wakfu.rpg.item;

import fr.wakfu.rpg.core.ModCapabilities;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Grimoire du voyageur : affiche les stats si une classe est choisie,
 * sinon renvoie vers le Maitre des Classes du spawn (didacticiel).
 */
public class WakfuGrimoireItem extends Item {
    public WakfuGrimoireItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            player.getCapability(ModCapabilities.RPG).ifPresent(data -> {
                if (!data.hasClass()) {
                    player.sendSystemMessage(Component.translatable("wakfurpg.tutorial_hint"));
                } else {
                    player.sendSystemMessage(Component.literal(
                            "§6" + data.getRpgClass().getName().getString() +
                                    " §7Niv §f" + data.getLevel() +
                                    " §7| PV §c" + data.getHp() + "/" + data.maxHp() +
                                    " §7| PA §e" + data.maxAp() +
                                    " §7| PM §a" + data.maxMp() +
                                    " §7| XP §b" + data.getXp() + "/" + data.getXpToNext()));
                }
            });
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }
}
