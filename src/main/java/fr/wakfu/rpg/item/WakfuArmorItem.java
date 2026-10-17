package fr.wakfu.rpg.item;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import net.minecraft.util.RandomSource;

/**
 * v0.16 : Piece d'armure Wakfu (heaume / cuirasse / jambieres / bottes).
 * STATS FIXES annoncees dans le bestiaire : {vie, force, sagesse, resistance}
 * selon l'emplacement et la rarete — plus aucun roll aleatoire.
 * Etoiles (fusion x4 chez le forgeron sombre) : stats x1.5 par etoile, max 5.
 * Clic droit = equipe dans la slot d'armure vanilla correspondante (echange).
 */
public class WakfuArmorItem extends Item {

    public static final int SLOT_HEAD = 3, SLOT_CHEST = 2, SLOT_LEGS = 1, SLOT_FEET = 0;

    public static final String[] SLOTS = {"head", "chest", "legs", "feet"};
    public static final String[] RARITIES = {"commune", "rare", "epique"};

    // ------------------------------------------------------------ v0.22 : panoplies de zone
    /** 4 panoplies : une par zone de monstres, pieces droppees par le type lie. */
    public static final String[] SETS = {"plaine", "os", "chemin", "chef"};
    private static final String[] SET_NAMES = {"Panoplie des Plaines", "Panoplie des Ossements",
            "Panoplie du Grand-Chemin", "Panoplie du Chef de Guerre"};
    private static final String[] SET_SOURCES = {
            "Zombies \u2014 campagne pres du village (< 96 m)",
            "Squelettes \u2014 carrieres a mi-distance (96-200 m)",
            "Bandits \u2014 grand chemin au loin (> 200 m)",
            "Chef des Bandits \u2014 donjon (boss, garanti)"};
    private static final int[] SET_COLORS = {0xFF3E6B28, 0xFF3E5A78, 0xFF8A2A20, 0xFF8A6A10};
    private static final String[] SET_CHAT = {"\u00a72", "\u00a7b", "\u00a7c", "\u00a76"};
    /** stats {vie, force, sagesse, resistance} par [panoplie][slot head/chest/legs/feet]. */
    private static final int[][][] SET_BASE = {
            {{10, 1, 0, 2}, {16, 1, 1, 2}, {12, 1, 2, 1}, {8, 2, 0, 1}},
            {{8, 2, 1, 1}, {12, 3, 1, 2}, {10, 2, 3, 1}, {6, 3, 0, 1}},
            {{8, 1, 3, 1}, {12, 1, 4, 2}, {10, 0, 6, 1}, {6, 2, 2, 1}},
            {{14, 2, 2, 3}, {20, 3, 2, 4}, {16, 2, 4, 2}, {12, 3, 1, 3}},
    };
    /** bonus panoplie complete {vie, force, sagesse, resistance, PA, PM}. */
    private static final int[][] SET_BONUS = {
            {25, 0, 0, 0, 0, 0},
            {0, 4, 0, 0, 0, 0},
            {0, 0, 8, 0, 0, 1},
            {0, 0, 0, 3, 1, 0},
    };

    /** stats {vie, force, sagesse, resistance} par [slot][rarete]. */
    private static final int[][][] BASE = {
            {{6, 1, 0, 1}, {12, 2, 1, 3}, {20, 3, 3, 5}},
            {{10, 1, 0, 0}, {18, 2, 1, 2}, {30, 3, 2, 4}},
            {{7, 0, 2, 0}, {14, 1, 4, 1}, {22, 2, 8, 3}},
            {{5, 2, 0, 0}, {10, 3, 0, 2}, {16, 5, 2, 3}},
    };

    public WakfuArmorItem(Properties p) {
        super(p);
    }

    public static String slotOf(ItemStack s) {
        String sl = s.getOrCreateTag().getString("slot");
        return sl.isEmpty() ? "feet" : sl;
    }

    public static int slotIndex(ItemStack s) {
        return switch (slotOf(s)) {
            case "head" -> SLOT_HEAD;
            case "chest" -> SLOT_CHEST;
            case "legs" -> SLOT_LEGS;
            default -> SLOT_FEET;
        };
    }

    public static int slotOrdinal(String slot) {
        for (int i = 0; i < SLOTS.length; i++) if (SLOTS[i].equals(slot)) return i;
        return 3;
    }

    public static String rarityOf(ItemStack s) {
        String r = s.getOrCreateTag().getString("rarity");
        return r.isEmpty() ? "commune" : r;
    }

    public static int rarityOrdinal(String rarity) {
        for (int i = 0; i < RARITIES.length; i++) if (RARITIES[i].equals(rarity)) return i;
        return 0;
    }

    public static String setOf(ItemStack s) {
        return s.getOrCreateTag().getString("set");
    }

    public static int setOrdinal(String set) {
        for (int i = 0; i < SETS.length; i++) if (SETS[i].equals(set)) return i;
        return 0;
    }

    public static String setNameFr(String set) { return SET_NAMES[setOrdinal(set)]; }
    public static String setSource(String set) { return SET_SOURCES[setOrdinal(set)]; }
    public static int setColor(String set) { return SET_COLORS[setOrdinal(set)]; }
    public static String setChatColor(String set) { return SET_CHAT[setOrdinal(set)]; }
    public static int[] setBonus(String set) { return SET_BONUS[setOrdinal(set)]; }

    public static String setBonusLabel(String set) {
        return switch (setOrdinal(set)) {
            case 0 -> "+25 PV max";
            case 1 -> "+4 Force (degats)";
            case 2 -> "+8 Sagesse (XP) et +1 PM";
            default -> "+1 PA et +3 Resistance";
        };
    }

    public static String setForMonster(String monsterId) {
        return switch (monsterId) {
            case "zombie" -> "plaine";
            case "skeleton" -> "os";
            case "bandit" -> "chemin";
            case "chef_bandit" -> "chef";
            default -> null;
        };
    }

    // ---- v0.27 : Fantasy Armor (CustomModelData 29-48) ----
    public static final String[] FANTASY_SETS = {
            "fantasy_dragonslayer", "fantasy_eclipse", "fantasy_golden_horns",
            "fantasy_hero", "fantasy_thief"
    };
    public static final String[] FANTASY_NAMES = {
            "Tueur de Dragon", "Soldat de l'Eclipse", "Cornes d'Or",
            "Heros", "Voleur"
    };
    public static final String[] FANTASY_COLORS = {
            "\u00a7c", "\u00a75", "\u00a76", "\u00a79", "\u00a78"
    };
    public static final int[][] FANTASY_STATS = {
            {18, 4, 0, 5}, {12, 3, 5, 3}, {10, 2, 8, 2}, {20, 3, 2, 4}, {8, 5, 6, 1},
    };
    public static final int FANTASY_CMD_BASE = 29;

    public static int fantasySetOrdinal(String setId) {
        for (int i = 0; i < FANTASY_SETS.length; i++) if (FANTASY_SETS[i].equals(setId)) return i;
        return -1;
    }

    public static boolean isFantasy(ItemStack s) {
        String set = setOf(s);
        return set != null && set.startsWith("fantasy_");
    }

    public static ItemStack makeFantasyPiece(String fantasySetId, String slot) {
        int setIdx = fantasySetOrdinal(fantasySetId);
        if (setIdx < 0) return ItemStack.EMPTY;
        ItemStack s = new ItemStack(fr.wakfu.rpg.registry.ModItems.WAKFU_ARMOR.get());
        var t = s.getOrCreateTag();
        t.putString("slot", slot);
        t.putString("set", fantasySetId);
        t.putInt("lvl", 1);
        t.putInt("stars", 0);
        t.putInt("CustomModelData", FANTASY_CMD_BASE + setIdx * 4 + slotOrdinal(slot));
        return s;
    }

    public static int[] fantasyFixedStats(ItemStack s) {
        int setIdx = fantasySetOrdinal(setOf(s));
        if (setIdx < 0) return new int[4];
        int[] base = FANTASY_STATS[setIdx];
        double starMult = Math.pow(1.5, starsOf(s));
        return new int[]{(int) Math.floor(base[0] * starMult), (int) Math.floor(base[1] * starMult),
                (int) Math.floor(base[2] * starMult), (int) Math.floor(base[3] * starMult)};
    }

    public static ItemStack randomFantasyPiece(RandomSource rng) {
        return makeFantasyPiece(FANTASY_SETS[rng.nextInt(FANTASY_SETS.length)], SLOTS[rng.nextInt(4)]);
    }

    public static ItemStack randomFantasyPiece(String setId, RandomSource rng) {
        return makeFantasyPiece(setId, SLOTS[rng.nextInt(4)]);
    }

    public static String activeSet(Player p) {
        String set = null;
        for (int i = 0; i < 4; i++) {
            ItemStack s = p.getInventory().getArmor(i);
            if (!(s.getItem() instanceof WakfuArmorItem)) return null;
            String ss = setOf(s);
            if (ss.isEmpty()) return null;
            if (set == null) set = ss;
            else if (!set.equals(ss)) return null;
        }
        return set;
    }

    public static int[] activeSetBonus(Player p) {
        String set = activeSet(p);
        if (set == null) return new int[6];
        int fIdx = fantasySetOrdinal(set);
        if (fIdx >= 0) {
            return switch (fIdx) {
                case 0 -> new int[]{30, 5, 0, 4, 0, 0};
                case 1 -> new int[]{15, 3, 8, 2, 0, 0};
                case 2 -> new int[]{10, 1, 12, 1, 0, 1};
                case 3 -> new int[]{25, 4, 2, 5, 1, 0};
                case 4 -> new int[]{8, 6, 10, 0, 0, 1};
                default -> new int[6];
            };
        }
        return SET_BONUS[setOrdinal(set)];
    }

    public static int levelOf(ItemStack s) { return Math.max(1, s.getOrCreateTag().getInt("lvl")); }
    public static int starsOf(ItemStack s) { return Math.max(0, Math.min(5, s.getOrCreateTag().getInt("stars"))); }
    public static void setStars(ItemStack s, int n) { s.getOrCreateTag().putInt("stars", Math.max(0, Math.min(5, n))); }

    public static int[] fixedStats(String slot, String rarity, int stars) {
        int[] b = BASE[slotOrdinal(slot)][rarityOrdinal(rarity)];
        double m = Math.pow(1.5, Math.max(0, Math.min(5, stars)));
        return new int[]{(int) Math.floor(b[0] * m), (int) Math.floor(b[1] * m),
                (int) Math.floor(b[2] * m), (int) Math.floor(b[3] * m)};
    }

    public static int[] fixedStats(ItemStack s) {
        String set = setOf(s);
        if (!set.isEmpty()) {
            if (set.startsWith("fantasy_")) return fantasyFixedStats(s);
            int[] b = SET_BASE[setOrdinal(set)][slotOrdinal(slotOf(s))];
            double m = Math.pow(1.5, starsOf(s));
            return new int[]{(int) Math.floor(b[0] * m), (int) Math.floor(b[1] * m),
                    (int) Math.floor(b[2] * m), (int) Math.floor(b[3] * m)};
        }
        return fixedStats(slotOf(s), rarityOf(s), starsOf(s));
    }

    public static int[] equippedFixed(Player p) {
        int[] t = new int[4];
        for (int i = 0; i < 4; i++) {
            ItemStack s = p.getInventory().getArmor(i);
            if (s.getItem() instanceof WakfuArmorItem) {
                int[] f = fixedStats(s);
                for (int k = 0; k < 4; k++) t[k] += f[k];
            }
        }
        p.getCapability(fr.wakfu.rpg.core.ModCapabilities.RPG).ifPresent(d -> {
            ItemStack w = d.getEquippedWeapon();
            if (WakfuWeaponItem.isWeapon(w)) {
                int[] f = WakfuWeaponItem.fixedStats(w);
                for (int k = 0; k < 4; k++) t[k] += f[k];
            }
        });
        return t;
    }

    public static String fusionKey(ItemStack s) {
        String set = setOf(s);
        if (!set.isEmpty()) return "S|" + set + "|" + slotOf(s) + "|" + starsOf(s);
        return "A|" + slotOf(s) + "|" + rarityOf(s) + "|" + starsOf(s);
    }

    public static ItemStack randomPiece(RandomSource rng) {
        int r = rng.nextInt(100);
        String rarity = r < 60 ? "commune" : (r < 90 ? "rare" : "epique");
        return makePiece(SLOTS[rng.nextInt(4)], rarity, 1, rng);
    }

    public static ItemStack makePiece(String slot, String rarity, int lvl, RandomSource rng) {
        ItemStack s = new ItemStack(fr.wakfu.rpg.registry.ModItems.WAKFU_ARMOR.get());
        var t = s.getOrCreateTag();
        t.putString("slot", slot);
        t.putString("rarity", rarity);
        t.putInt("lvl", lvl);
        t.putInt("stars", 0);
        t.putInt("CustomModelData", slotOrdinal(slot) * 3 + rarityOrdinal(rarity) + 1);
        return s;
    }

    public static ItemStack makeSetPiece(String set, String slot) {
        ItemStack s = new ItemStack(fr.wakfu.rpg.registry.ModItems.WAKFU_ARMOR.get());
        var t = s.getOrCreateTag();
        t.putString("slot", slot);
        t.putString("set", set);
        t.putInt("lvl", 1);
        t.putInt("stars", 0);
        t.putInt("CustomModelData", 13 + setOrdinal(set) * 4 + slotOrdinal(slot));
        return s;
    }

    public static ItemStack randomSetPiece(String set, RandomSource rng) {
        return makeSetPiece(set, SLOTS[rng.nextInt(4)]);
    }

    public static String slotNameFr(String slot) {
        return switch (slot) {
            case "head" -> "Heaume";
            case "chest" -> "Cuirasse";
            case "legs" -> "Jambi\u00e8res";
            default -> "Bottes";
        };
    }

    public static String starsLabel(int stars) {
        if (stars <= 0) return "";
        return " " + "\u2605".repeat(stars);
    }

    public static String statsLabel(int[] f) {
        StringBuilder b = new StringBuilder();
        if (f[0] > 0) b.append(" +").append(f[0]).append(" PV");
        if (f[1] > 0) b.append(" +").append(f[1]).append(" For");
        if (f[2] > 0) b.append(" +").append(f[2]).append(" Sag");
        if (f[3] > 0) b.append(" +").append(f[3]).append(" R\u00e9s");
        return b.toString();
    }

    @Override
    public Component getName(ItemStack s) {
        String set = setOf(s);
        if (!set.isEmpty()) {
            int fIdx = fantasySetOrdinal(set);
            if (fIdx >= 0) {
                return Component.literal(FANTASY_COLORS[fIdx] + "[" + FANTASY_NAMES[fIdx] + "] "
                        + slotNameFr(slotOf(s)) + "\u00a76" + starsLabel(starsOf(s))
                        + "\u00a77" + statsLabel(fixedStats(s)));
            }
            return Component.literal(setChatColor(set) + "[" + setNameFr(set) + "] "
                    + slotNameFr(slotOf(s)) + "\u00a76" + starsLabel(starsOf(s))
                    + "\u00a77" + statsLabel(fixedStats(s)));
        }
        String rarity = rarityOf(s);
        String color = "epique".equals(rarity) ? "\u00a7d" : ("rare".equals(rarity) ? "\u00a79" : "\u00a7f");
        int[] f = fixedStats(s);
        return Component.literal(color + "[" + rarity + "] " + slotNameFr(slotOf(s)) + " Runique"
                + "\u00a76" + starsLabel(starsOf(s)) + "\u00a77" + statsLabel(f));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.success(held);
        }
        int idx = slotIndex(held);
        ItemStack prev = player.getInventory().getArmor(idx);
        player.getInventory().armor.set(idx, held.copy());
        player.setItemInHand(hand, prev);
        player.playSound(SoundEvents.ARMOR_EQUIP_GENERIC, 0.6f, 1.0f);
        player.sendSystemMessage(Component.literal("\u00a77Tu \u00e9quipes : " + getName(held).getString()));
        return InteractionResultHolder.consume(held);
    }
}
