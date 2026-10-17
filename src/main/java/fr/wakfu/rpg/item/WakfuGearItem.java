package fr.wakfu.rpg.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Equipement drope par les monstres : rarete + bonus de degats
 * applique quand l'objet est en main principale.
 */
public class WakfuGearItem extends Item {
    public WakfuGearItem(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (stack.hasTag()) {
            String rarity = stack.getTag().getString("rarity");
            int bonus = stack.getTag().getInt("bonusDamage");
            ChatFormatting color = switch (rarity) {
                case "rare" -> ChatFormatting.AQUA;
                case "epique" -> ChatFormatting.LIGHT_PURPLE;
                default -> ChatFormatting.GRAY;
            };
            tooltip.add(Component.literal("Rareté : " + rarity).withStyle(color));
            tooltip.add(Component.literal("+" + bonus + " dégâts (en main)").withStyle(ChatFormatting.GREEN));
            tooltip.add(Component.literal("Équipe-le en main pour bonus en combat.").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
