package fr.wakfu.rpg.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * v0.22 : trophée de chasse — icône unique par monstre de zone
 * (croc putride / os ancien / dague de bandit / écusson du chef).
 * CustomModelData 1-4, répertorié dans le bestiaire (onglet Butin).
 */
public class WakfuTrophyItem extends Item {

    public static final String[] KINDS = {"zombie", "skeleton", "bandit", "chef"};
    private static final String[] NAMES = {"Croc putride", "Os ancien", "Dague de bandit", "Écusson du chef"};
    private static final String[] COLORS = {"§2", "§f", "§c", "§6"};
    private static final String[] SOURCES = {"Zombies (plaine)", "Squelettes (carrières)",
            "Bandits (grand chemin)", "Chef des Bandits (donjon)"};

    public WakfuTrophyItem(Properties p) {
        super(p);
    }

    public static String kindOf(ItemStack s) {
        String k = s.getOrCreateTag().getString("kind");
        return k.isEmpty() ? "zombie" : k;
    }

    public static int kindOrdinal(String kind) {
        for (int i = 0; i < KINDS.length; i++) if (KINDS[i].equals(kind)) return i;
        return 0;
    }

    public static String nameFr(String kind) { return NAMES[kindOrdinal(kind)]; }

    public static String sourceFr(String kind) { return SOURCES[kindOrdinal(kind)]; }

    public static ItemStack make(String kind) {
        ItemStack s = new ItemStack(fr.wakfu.rpg.registry.ModItems.WAKFU_TROPHY.get());
        var t = s.getOrCreateTag();
        t.putString("kind", kind);
        t.putInt("CustomModelData", kindOrdinal(kind) + 1);
        return s;
    }

    /** Trophée lâché par un type de monstre (null si aucun). */
    public static String forMonster(String monsterId) {
        return switch (monsterId) {
            case "zombie" -> "zombie";
            case "skeleton" -> "skeleton";
            case "bandit" -> "bandit";
            case "chef_bandit" -> "chef";
            default -> null;
        };
    }

    @Override
    public Component getName(ItemStack s) {
        String k = kindOf(s);
        return Component.literal(COLORS[kindOrdinal(k)] + nameFr(k) + " §8(trophée)");
    }
}
