package fr.wakfu.rpg.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import fr.wakfu.rpg.registry.ModItems;

/**
 * v0.15 : Codex — suivi persistant des decouvertes du joueur :
 * monstres rencontres/tues, zones explorees, donjon trouve, objets recus.
 * Tout est stocke dans PlayerRpgData (capacite) donc automatiquement
 * persiste et synchronise vers le client via SyncRpgDataPacket.
 */
public class Codex {

    // ---- monstres
    /** Monstre rencontre (debut de combat) : le joueur en connait l'existence. */
    public static void encounter(ServerPlayer p, String monsterId) {
        if (p == null || monsterId == null) return;
        p.getCapability(ModCapabilities.RPG).ifPresent(d -> {
            if (d.codexSee(monsterId)) fr.wakfu.rpg.events.CommonEvents.sync(p);
        });
    }

    /** Monstre vaincu : +1 au compteur de kills du Codex. */
    public static void kill(ServerPlayer p, String monsterId) {
        if (p == null || monsterId == null) return;
        p.getCapability(ModCapabilities.RPG).ifPresent(d -> {
            d.codexKill(monsterId);
            fr.wakfu.rpg.events.CommonEvents.sync(p);
        });
    }

    // ---- objets recus (loot, quetes, forge, coffres)
    /** Cle Codex d'un ItemStack, ou null si hors Codex. */
    public static String keyOf(ItemStack s) {
        if (s == null || s.isEmpty()) return null;
        var item = s.getItem();
        if (item == ModItems.WAKFU_INGOT.get()) return "ingot";
        if (item == ModItems.WAKFU_WEAPON.get())
            return fr.wakfu.rpg.item.WakfuWeaponItem.kindOf(s) + "_"
                    + fr.wakfu.rpg.item.WakfuWeaponItem.rarityOf(s);
        if (item == ModItems.WAKFU_GRIMOIRE.get()) return "grimoire";
        if (item instanceof fr.wakfu.rpg.item.WakfuArmorItem) {
            String set = fr.wakfu.rpg.item.WakfuArmorItem.setOf(s);
            if (!set.isEmpty()) {
                return "set_" + set + "_" + fr.wakfu.rpg.item.WakfuArmorItem.slotOf(s);
            }
            return "armor_" + fr.wakfu.rpg.item.WakfuArmorItem.slotOf(s)
                    + "_" + fr.wakfu.rpg.item.WakfuArmorItem.rarityOf(s);
        }
        if (item == ModItems.WAKFU_TROPHY.get()) {
            return "trophy_" + fr.wakfu.rpg.item.WakfuTrophyItem.kindOf(s);
        }
        if (item == Items.GOLDEN_APPLE) return "golden_apple";
        return null;
    }

    public static void item(ServerPlayer p, ItemStack s) {
        String key = keyOf(s);
        if (key == null) return;
        p.getCapability(ModCapabilities.RPG).ifPresent(d -> {
            d.codexReceive(key, s.getCount());
            fr.wakfu.rpg.core.Quests.progressMax(p, "butin", d.getCodexItems().size());
            fr.wakfu.rpg.events.CommonEvents.sync(p);
        });
    }

    // ---- exploration (zones + donjon), appele toutes les 2s hors combat
    public static void trackPosition(ServerPlayer p) {
        if (p == null || !p.level().isLoaded(p.blockPosition())) return;
        var level = p.serverLevel();
        var spawn = level.getSharedSpawnPos();
        int ox = spawn.getX() & ~63, oz = spawn.getZ() & ~63;   // meme origine que les secteurs de monstres
        BlockPos pos = p.blockPosition();
        int sx = Math.floorDiv(pos.getX() - ox, 64);
        int sz = Math.floorDiv(pos.getZ() - oz, 64);
        p.getCapability(ModCapabilities.RPG).ifPresent(d -> {
            boolean changed = false;
            if (sx >= -3 && sx <= 3 && sz >= -3 && sz <= 3) {
                changed |= d.codexVisitZone(sx, sz);
            }
            BlockPos c = Dungeon.center(level);
            if (pos.closerThan(c, 34)) {
                changed |= d.codexFindDungeon();
            }
            fr.wakfu.rpg.core.Quests.progressMax(p, "zones", d.getCodexZones().size());
            if (changed) fr.wakfu.rpg.events.CommonEvents.sync(p);
        });
    }

    private Codex() {}
}
