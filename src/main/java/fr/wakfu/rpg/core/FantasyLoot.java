package fr.wakfu.rpg.core;

import fr.wakfu.rpg.item.WakfuArmorItem;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/**
 * v0.27 : butin Fantasy Armor integre directement dans wakfurpg.
 * Les armures Fantasy sont des WakfuArmorItem avec un set "fantasy_*"
 * et un CustomModelData 29-48. Plus besoin du mod externe fantasy_armor.
 */
public final class FantasyLoot {

    private FantasyLoot() {}

    /** Une piece d'armure Fantasy aleatoire. */
    public static ItemStack randomPiece(RandomSource rng) {
        return WakfuArmorItem.randomFantasyPiece(rng);
    }

    /** Piece d'un set specifique. */
    public static ItemStack pieceOf(String setId, RandomSource rng) {
        return WakfuArmorItem.randomFantasyPiece(setId, rng);
    }

    /** L'objet est-il une armure Fantasy ? */
    public static boolean isFantasy(ItemStack s) {
        return WakfuArmorItem.isFantasy(s);
    }

    /** Nombre de sets fantasy disponibles. */
    public static int setCount() { return WakfuArmorItem.FANTASY_SETS.length; }
    public static String setId(int i) { return WakfuArmorItem.FANTASY_SETS[i]; }
    public static String setName(int i) { return WakfuArmorItem.FANTASY_NAMES[i]; }
    public static String setColor(int i) { return WakfuArmorItem.FANTASY_COLORS[i]; }
}

