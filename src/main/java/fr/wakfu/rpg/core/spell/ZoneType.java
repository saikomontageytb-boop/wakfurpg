package fr.wakfu.rpg.core.spell;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/** Forme de la zone d'effet d'un sort (façon Wakfu, cases). */
public enum ZoneType {
    SINGLE,     // 1 case ciblée
    CROSS,      // croix : cible + 4 cases adjacentes
    SQUARE2,    // 3x3 autour de la cible
    SELF,       // lanceur uniquement
    SELF_RING,  // 8 cases autour du lanceur
    LINE;       // ligne traversant le lanceur (axe cardinal, 2 directions)

    public static int chebyshev(BlockPos a, BlockPos b) {
        return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getZ() - b.getZ()));
    }

    /** Cases affectées par le sort. */
    public static List<BlockPos> cells(ZoneType z, BlockPos self, BlockPos target, int maxRange) {
        List<BlockPos> out = new ArrayList<>();
        switch (z) {
            case SINGLE -> out.add(target);
            case CROSS -> {
                out.add(target);
                out.add(target.north());
                out.add(target.south());
                out.add(target.east());
                out.add(target.west());
            }
            case SQUARE2 -> {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        out.add(new BlockPos(target.getX() + dx, target.getY(), target.getZ() + dz));
                    }
                }
            }
            case SELF -> out.add(self);
            case SELF_RING -> {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx != 0 || dz != 0) {
                            out.add(new BlockPos(self.getX() + dx, self.getY(), self.getZ() + dz));
                        }
                    }
                }
            }
            case LINE -> {
                int dx = target.getX() - self.getX();
                int dz = target.getZ() - self.getZ();
                int sx = dx == 0 ? 0 : (dx > 0 ? 1 : -1);
                int sz = dz == 0 ? 0 : (dz > 0 ? 1 : -1);
                for (int i = -maxRange; i <= maxRange; i++) {
                    if (i == 0) continue;
                    out.add(new BlockPos(self.getX() + sx * i, self.getY(), self.getZ() + sz * i));
                }
            }
        }
        return out;
    }

    /** Cases ciblables par le sort (affichage de la portée). */
    public static List<BlockPos> rangeCells(ZoneType z, BlockPos self, int minRange, int maxRange) {
        List<BlockPos> out = new ArrayList<>();
        if (z == SELF || z == SELF_RING) {
            out.add(self);
            return out;
        }
        for (int dx = -maxRange; dx <= maxRange; dx++) {
            for (int dz = -maxRange; dz <= maxRange; dz++) {
                int d = Math.max(Math.abs(dx), Math.abs(dz));
                if (d < minRange || d > maxRange) continue;
                if (z == LINE && dx != 0 && dz != 0) continue;
                out.add(new BlockPos(self.getX() + dx, self.getY(), self.getZ() + dz));
            }
        }
        return out;
    }
}
