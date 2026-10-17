package fr.wakfu.rpg.item;

import fr.wakfu.rpg.core.ModCapabilities;
import fr.wakfu.rpg.core.PlayerRpgData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import net.minecraft.util.RandomSource;

/**
 * v0.16 : Armes Wakfu (lame / arc) — stats FIXES {vie, force, sagesse,
 * resistance} comme les armures, etoiles de fusion x1.5, et surtout :
 * EQUIPABLES au clic droit (stockees dans la capacite du joueur, elles
 * survivent au vidage d'inventaire des combats).
 */
public class WakfuWeaponItem extends Item {

    public final String kind; // "sword" | "bow"

    public static final String[] RARITIES = {"commune", "rare", "epique"};

    /** stats {vie, force, sagesse, resistance} par [kind][rarete]. */
    private static final int[][] SWORD = {{0, 3, 0, 0}, {6, 5, 0, 2}, {12, 8, 0, 4}};
    private static final int[][] BOW = {{0, 2, 1, 0}, {4, 4, 3, 0}, {8, 6, 6, 2}};

    public WakfuWeaponItem(Properties p, String kind) {
        super(p);
        this.kind = kind;
    }

    public static String kindOf(ItemStack s) {
        return s.getItem() instanceof WakfuWeaponItem w ? w.kind : "";
    }

    public static boolean isWeapon(ItemStack s) {
        return s.getItem() instanceof WakfuWeaponItem;
    }

    public static String rarityOf(ItemStack s) {
        String r = s.getOrCreateTag().getString("rarity");
        return r.isEmpty() ? "commune" : r;
    }

    public static int rarityOrdinal(String rarity) {
        for (int i = 0; i < RARITIES.length; i++) if (RARITIES[i].equals(rarity)) return i;
        return 0;
    }

    public static int levelOf(ItemStack s) {
        return Math.max(1, s.getOrCreateTag().getInt("lvl"));
    }

    public static int starsOf(ItemStack s) {
        return Math.max(0, Math.min(5, s.getOrCreateTag().getInt("stars")));
    }

    public static void setStars(ItemStack s, int n) {
        s.getOrCreateTag().putInt("stars", Math.max(0, Math.min(5, n)));
    }

    public static int[] fixedStats(String kind, String rarity, int stars) {
        int[] b = "bow".equals(kind) ? BOW[rarityOrdinal(rarity)] : SWORD[rarityOrdinal(rarity)];
        double m = Math.pow(1.5, Math.max(0, Math.min(5, stars)));
        return new int[]{(int) Math.floor(b[0] * m), (int) Math.floor(b[1] * m),
                (int) Math.floor(b[2] * m), (int) Math.floor(b[3] * m)};
    }

    public static int[] fixedStats(ItemStack s) {
        return fixedStats(kindOf(s), rarityOf(s), starsOf(s));
    }

    public static String fusionKey(ItemStack s) {
        return "W|" + kindOf(s) + "|" + rarityOf(s) + "|" + starsOf(s);
    }

    public static ItemStack randomWeapon(RandomSource rng) {
        int r = rng.nextInt(100);
        String rarity = r < 60 ? "commune" : (r < 90 ? "rare" : "epique");
        return makeWeapon(rng.nextBoolean() ? "sword" : "bow", rarity, 1, rng);
    }

    public static ItemStack makeWeapon(String kind, String rarity, int lvl, RandomSource rng) {
        ItemStack s = new ItemStack("sword".equals(kind)
                ? fr.wakfu.rpg.registry.ModItems.WAKFU_WEAPON.get()
                : fr.wakfu.rpg.registry.ModItems.WAKFU_WEAPON.get());
        var t = s.getOrCreateTag();
        t.putString("rarity", rarity);
        t.putInt("lvl", lvl);
        t.putInt("stars", 0);
        t.putInt("CustomModelData", rarityOrdinal(rarity) + 1);
        return s;
    }

    // ------------------------------------------------------------ affichage / equipement
    @Override
    public Component getName(ItemStack s) {
        String rarity = rarityOf(s);
        String color = "epique".equals(rarity) ? "§d" : ("rare".equals(rarity) ? "§9" : "§f");
        String base = "sword".equals(kindOf(s)) ? "Lame Runique" : "Arc Runique";
        int[] f = fixedStats(s);
        return Component.literal(color + "[" + rarity + "] " + base
                + "§6" + WakfuArmorItem.starsLabel(starsOf(s)) + "§7" + WakfuArmorItem.statsLabel(f));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResultHolder.success(held);
        }
        if (!(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.pass(held);
        }
        PlayerRpgData data = sp.getCapability(ModCapabilities.RPG).resolve().orElse(null);
        if (data == null) return InteractionResultHolder.pass(held);
        ItemStack prev = data.getEquippedWeapon();
        data.setEquippedWeapon(held.copy());
        player.setItemInHand(hand, ItemStack.EMPTY);
        if (!prev.isEmpty()) {
            player.getInventory().add(prev);
        }
        player.playSound(SoundEvents.ARMOR_EQUIP_GENERIC, 0.6f, 1.0f);
        player.sendSystemMessage(Component.literal("§7Tu équipes : " + getName(held).getString()
                + (prev.isEmpty() ? "" : " §8(remplace " + prev.getHoverName().getString() + "§8)")));
        fr.wakfu.rpg.events.CommonEvents.sync(sp);
        return InteractionResultHolder.consume(held);
    }
}
