package fr.wakfu.rpg.core;

import fr.wakfu.rpg.item.WakfuArmorItem;
import fr.wakfu.rpg.item.WakfuWeaponItem;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * v0.16 : Fusion de l'Atelier sombre — 4 equipements IDENTIQUES
 * (meme emplacement, meme rarete, memes etoiles) se fondent en un
 * exemplaire etoile : stats x1.5 par etoile, jusqu'a 5 etoiles.
 * La logique de groupement est partagee client/serveur (ordre
 * deterministe : cles triees) pour que l'index du clic corresponde.
 */
public class Fusion {

    public static class Group {
        public String key;
        public int count;
        public ItemStack sample;
        public int stars;
    }

    /** Cle de groupe d'un equipement, ou null si non fusionnable. */
    public static String keyOf(ItemStack s) {
        if (s == null || s.isEmpty()) return null;
        if (s.getItem() instanceof WakfuArmorItem) return WakfuArmorItem.fusionKey(s);
        if (WakfuWeaponItem.isWeapon(s)) return WakfuWeaponItem.fusionKey(s);
        return null;
    }

    private static int starsOf(ItemStack s) {
        if (s.getItem() instanceof WakfuArmorItem) return WakfuArmorItem.starsOf(s);
        return WakfuWeaponItem.starsOf(s);
    }

    /** Groupes fusionnables (>=4 exemplaires, <5 etoiles), tries par cle. */
    public static List<Group> groups(Player p) {
        TreeMap<String, Group> map = new TreeMap<>();
        List<ItemStack> items = p.getInventory().items;
        for (ItemStack s : items) {
            String k = keyOf(s);
            if (k == null) continue;
            Group g = map.get(k);
            if (g == null) {
                g = new Group();
                g.key = k;
                g.sample = s.copy();
                g.stars = starsOf(s);
                map.put(k, g);
            }
            g.count += s.getCount();
        }
        List<Group> out = new ArrayList<>();
        for (Group g : map.values()) {
            if (g.count >= 4 && g.stars < 5) out.add(g);
        }
        return out;
    }

    /** Cree l'objet fusionne : meme modele, etoiles + 1. */
    public static ItemStack fused(Group g) {
        ItemStack src = g.sample;
        RandomSource rng = RandomSource.create();
        ItemStack out;
        if (src.getItem() instanceof WakfuArmorItem) {
            String set = WakfuArmorItem.setOf(src);
            out = set.isEmpty()
                    ? WakfuArmorItem.makePiece(WakfuArmorItem.slotOf(src), WakfuArmorItem.rarityOf(src), 1, rng)
                    : WakfuArmorItem.makeSetPiece(set, WakfuArmorItem.slotOf(src));
            WakfuArmorItem.setStars(out, g.stars + 1);
        } else {
            out = WakfuWeaponItem.makeWeapon(WakfuWeaponItem.kindOf(src), WakfuWeaponItem.rarityOf(src), 1, rng);
            WakfuWeaponItem.setStars(out, g.stars + 1);
        }
        return out;
    }

    private Fusion() {}
}
