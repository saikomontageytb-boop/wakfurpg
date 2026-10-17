package fr.wakfu.rpg.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;

/**
 * v0.23 : emplacements des PNJ et lieux — ancres NOMINALES partagées entre
 * le serveur (placement au niveau de la rue, jamais sur un toit) et le
 * client (onglet PNJ du bestiaire, traînée de guidage + distance).
 */
public class NpcSpots {

    public record Spot(String id, String nameFr, int dx, int dz) {}

    public static final List<Spot> SPOTS = List.of(
            new Spot("maitre", "Maître des Classes", 3, -5),
            new Spot("heraut", "Héraut des Quêtes", -9, 6),
            new Spot("forgeron", "Forgeron Sombre", 11, 8),
            new Spot("marchand", "Marchand", -13, -9),
            new Spot("banque", "Banque (coffre d'or)", -7, -2),
            new Spot("marche", "Hôtel des Ventes", 6, 13),
            new Spot("donjon", "Donjon — Chef des Bandits", 150, 60));

    public static Spot byId(String id) {
        for (Spot s : SPOTS) if (s.id().equals(id)) return s;
        return SPOTS.get(0);
    }

    /**
     * Position de rue la plus proche de l'ancre nominale (spirale, rayon 16) :
     * sol à ciel ouvert, pas d'eau, et hauteur proche du niveau le plus bas du
     * voisinage — les toits et balcons sont refusés.
     */
    public static BlockPos streetSpot(ServerLevel level, BlockPos spawn, String id) {
        Spot s = byId(id);
        int ax = spawn.getX() + s.dx(), az = spawn.getZ() + s.dz();
        // v0.24 : reference de la rue = PLUS BAS des hauteurs autour du spawn
        int ref = level.getHeight(Heightmap.Types.MOTION_BLOCKING, spawn.getX(), spawn.getZ());
        for (int dx = -8; dx <= 8; dx += 8) {
            for (int dz = -8; dz <= 8; dz += 8) {
                ref = Math.min(ref, level.getHeight(Heightmap.Types.MOTION_BLOCKING,
                        spawn.getX() + dx, spawn.getZ() + dz));
            }
        }
        BlockPos fallback = new BlockPos(ax,
                level.getHeight(Heightmap.Types.MOTION_BLOCKING, ax, az), az);
        for (int r = 0; r <= 24; r++) {
            for (int ox = -r; ox <= r; ox++) {
                for (int oz = -r; oz <= r; oz++) {
                    if (Math.max(Math.abs(ox), Math.abs(oz)) != r) continue;
                    int x = ax + ox, z = az + oz;
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                    var pos = new BlockPos(x, y, z);
                    if (!level.isLoaded(pos)) continue;
                    if (!level.getBlockState(pos).getFluidState().isEmpty()) continue;
                    if (!level.canSeeSky(pos)) continue;
                    // plafond absolu : un toit depasse toujours la rue de +6
                    if (y > ref + 6 || y < ref - 10) continue;
                    // test local : le sol doit rester au niveau le plus bas du voisinage
                    int m = y;
                    for (int dx = -8; dx <= 8; dx += 4) {
                        for (int dz = -8; dz <= 8; dz += 4) {
                            if (dx == 0 && dz == 0) continue;
                            m = Math.min(m, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + dx, z + dz));
                        }
                    }
                    if (y <= m + 3) return pos;
                }
            }
        }
        return fallback;
    }

    private NpcSpots() {}
}
