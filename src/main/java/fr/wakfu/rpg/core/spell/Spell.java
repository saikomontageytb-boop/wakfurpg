package fr.wakfu.rpg.core.spell;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Un sort : cout en PA, portee min/max, zone d'effet, degats. */
public class Spell {
    public final String id;
    public final int apCost;
    public final int minRange;
    public final int maxRange;
    public final int damage;
    public final ZoneType zone;
    /** v0.14 : id de MonsterType a invoquer (null = sort classique). */
    public final String summon;
    /** v0.16 : niveau requis pour debloquer le sort. */
    public final int unlockLevel;
    /** v0.18 : mecanique speciale — null | "bond" | "piege" | "vol". */
    public final String mechanic;
    /** v0.19 : true = le sort traverse les murs de l'arene (pas de ligne de vue requise). */
    public boolean pierce = false;

    /** Le sort a-t-il besoin d'une ligne de vue (murs bloquants) ? */
    public boolean needsLos() {
        return !pierce && minRange >= 1
                && zone != ZoneType.SELF && zone != ZoneType.SELF_RING;
    }

    public Spell(String id, int apCost, int minRange, int maxRange, int damage, ZoneType zone) {
        this(id, apCost, minRange, maxRange, damage, zone, null, 1);
    }

    public Spell(String id, int apCost, int minRange, int maxRange, int damage, ZoneType zone, String summon) {
        this(id, apCost, minRange, maxRange, damage, zone, summon, 1);
    }

    public Spell(String id, int apCost, int minRange, int maxRange, int damage, ZoneType zone, String summon, int unlockLevel) {
        this(id, apCost, minRange, maxRange, damage, zone, summon, unlockLevel, null);
    }

    public Spell(String id, int apCost, int minRange, int maxRange, int damage, ZoneType zone, String summon, int unlockLevel, String mechanic) {
        this.id = id;
        this.apCost = apCost;
        this.minRange = minRange;
        this.maxRange = maxRange;
        this.damage = damage;
        this.zone = zone;
        this.summon = summon;
        this.unlockLevel = unlockLevel;
        this.mechanic = mechanic;
    }

    public Component getName() {
        return Component.translatable("spell.wakfurpg." + id);
    }

    /** La case cible est-elle valide depuis la case du lanceur ? */
    public boolean validTarget(BlockPos self, BlockPos target) {
        if (zone == ZoneType.SELF || zone == ZoneType.SELF_RING) {
            return target.equals(self);
        }
        int d = ZoneType.chebyshev(self, target);
        if (d < minRange || d > maxRange) return false;
        if (zone == ZoneType.LINE) {
            return self.getX() == target.getX() || self.getZ() == target.getZ();
        }
        return true;
    }

    public List<BlockPos> zoneCells(BlockPos self, BlockPos target) {
        return ZoneType.cells(zone, self, target, maxRange);
    }

    public List<BlockPos> rangeCells(BlockPos self) {
        return ZoneType.rangeCells(zone, self, minRange, maxRange);
    }

    public String rangeLabel() {
        return minRange == maxRange ? String.valueOf(maxRange) : minRange + "-" + maxRange;
    }

    /** Description courte de la zone (interfaces). */
    public String zoneLabel() {
        switch (zone) {
            case SINGLE: return "cible";
            case CROSS: return "croix";
            case SQUARE2: return "3x3";
            case SELF: return "soi";
            case SELF_RING: return "autour";
            case LINE: return "ligne";
            default: return "";
        }
    }
}
