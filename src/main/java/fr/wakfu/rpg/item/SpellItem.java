package fr.wakfu.rpg.item;

import fr.wakfu.rpg.core.combat.CombatManager;
import fr.wakfu.rpg.core.spell.Spell;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Objet-sort place dans la hotbar pendant le combat.
 * Clic droit = lancer le sort (cible = le monstre clique ensuite,
 * ou application directe si le monstre est a portee).
 */
public class SpellItem extends Item {

    private final Spell spell;

    public SpellItem(Properties props, Spell spell) {
        super(props);
        this.spell = spell;
    }

    public Spell getSpell() {
        return spell;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            CombatManager.onSpellItemUse(sp, spell);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Sort de combat").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("Cout : " + spell.apCost + " PA | Portee : " + spell.rangeLabel() + " | Zone : " + spell.zoneLabel() + " | Degats : ~" + spell.damage).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("En combat : barre de sorts (1-4), clic sur une case.").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
