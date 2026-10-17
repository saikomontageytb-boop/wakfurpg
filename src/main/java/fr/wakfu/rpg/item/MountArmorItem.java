package fr.wakfu.rpg.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * v0.29 : Armure de monture (Ours uniquement).
 * 4 tiers : Fer, Or, Diamant, Netherite.
 * Chaque tier donne des stats {PV, Force, Sag, Res} au cavalier via la monture.
 */
public class MountArmorItem extends Item {

    private final String tier;
    private final int[] stats; // {PV, Force, Sagesse, Resistance}

    private static final String[] TIERS = {"iron", "gold", "diamond", "netherite"};
    private static final int[][] STATS = {
            {10, 1, 0, 3},   // Iron
            {15, 2, 2, 4},   // Gold
            {25, 4, 3, 7},   // Diamond
            {35, 6, 5, 10},  // Netherite
    };
    private static final String[] TIER_NAMES = {
            "Fer", "Or", "Diamant", "Netherite"
    };
    private static final String[] TIER_COLORS = {
            "§f", "§6", "§b", "§4"
    };
    private static final int[] TIER_COLORS_HEX = {
            0xFFAAAAAA, 0xFFFFDD00, 0xFF55FFFF, 0xFF440000
    };

    public MountArmorItem(String tier, Properties props) {
        super(props);
        this.tier = tier;
        int idx = tierIndex(tier);
        this.stats = idx >= 0 ? STATS[idx] : new int[4];
    }

    public static int tierIndex(String t) {
        for (int i = 0; i < TIERS.length; i++) {
            if (TIERS[i].equals(t)) return i;
        }
        return -1;
    }

    public static String tierName(String t) {
        int idx = tierIndex(t);
        return idx >= 0 ? TIER_NAMES[idx] : t;
    }

    public static String tierColor(String t) {
        int idx = tierIndex(t);
        return idx >= 0 ? TIER_COLORS[idx] : "§f";
    }

    public static int[] getStats(ItemStack s) {
        if (s == null || s.isEmpty() || !(s.getItem() instanceof MountArmorItem mai)) return new int[4];
        return mai.stats.clone();
    }

    @Override
    public Component getName(ItemStack s) {
        int idx = tierIndex(tier);
        String color = idx >= 0 ? TIER_COLORS[idx] : "§f";
        String name = idx >= 0 ? TIER_NAMES[idx] : tier;
        int[] st = stats;
        return Component.literal(color + "[Monture] Armure " + name
                + " §7(+§c" + st[0] + " PV §7+§e" + st[1] + " For §7+§a" + st[2] + " Sag §7+§9" + st[3] + " Rés§7)");
    }
}