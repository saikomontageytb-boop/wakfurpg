package fr.wakfu.rpg.core.combat;

import fr.wakfu.rpg.WakfuRPG;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Arenes de combat : cocon de verre construit DANS l'overworld au-dessus du
 * lieu du combat. v0.19 : VARIATIONS selon la zone — taille (11 a 15),
 * palette de blocs, et murs internes (couvert + ligne de vue).
 * Themes : 0 plaine, 1 foret, 2 montagne, 3 donjon.
 */
public class Arena {

    /** Ancienne dimension dediee (conservee pour nettoyage, plus utilisee). */
    public static final ResourceKey<Level> DIM_KEY =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation(WakfuRPG.MODID, "arena"));

    public static ServerLevel dim(MinecraftServer server) {
        return server == null ? null : server.getLevel(DIM_KEY);
    }

    private static final int BASE_X = 4000;
    private static final int BASE_Z = 4000;
    private static final int Y = 150;
    private static final int SPACING = 48;
    private static final int LEGACY_SIZE = 17;
    private static final int WALL_H = 8;

    /** Themes : taille, sol, bordure, murs internes. */
    private static final int[] THEME_SIZE = {15, 17, 17, 19};
    private static final Block[] THEME_FLOOR = {Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.SMOOTH_STONE, Blocks.DEEPSLATE_TILES};
    private static final Block[] THEME_FLOOR2 = {Blocks.DIRT_PATH, Blocks.ROOTED_DIRT, Blocks.POLISHED_ANDESITE, Blocks.CRACKED_DEEPSLATE_TILES};
    private static final Block[] THEME_EDGE = {Blocks.POLISHED_ANDESITE, Blocks.MOSSY_COBBLESTONE, Blocks.STONE_BRICKS, Blocks.POLISHED_BLACKSTONE};
    private static final Block[] THEME_WALL = {Blocks.OAK_LOG, Blocks.DARK_OAK_LOG, Blocks.STONE_BRICK_WALL, Blocks.DEEPSLATE_BRICK_WALL};

    public static Vec3 centerFor(int slot) {
        int gx = slot % 16;
        int gz = slot / 16;
        return new Vec3(BASE_X + gx * SPACING, Y, BASE_Z + gz * SPACING);
    }

    /** Nettoyage boot : vieilles arenes fixes 4000/4000. */
    public static void cleanupOld(ServerLevel overworld) {
        for (int slot = 0; slot < 16; slot++) {
            Vec3 c = centerFor(slot);
            int bx = (int) c.x - LEGACY_SIZE / 2;
            int bz = (int) c.z - LEGACY_SIZE / 2;
            overworld.getChunk(bx >> 4, bz >> 4);
            clearAt(overworld, bx, Y, bz, LEGACY_SIZE);
            overworld.setChunkForced(bx >> 4, bz >> 4, false);
            AABB box = new AABB(c.x - 8, Y - 2, c.z - 8, c.x + 8, Y + 14, c.z + 8);
            for (ArmorStand s : overworld.getEntitiesOfClass(ArmorStand.class, box)) {
                if (s.isInvisible()) s.discard();
            }
        }
    }

    public static int sizeFor(int theme) {
        return THEME_SIZE[Math.max(0, Math.min(3, theme))];
    }

    /** Choix du theme selon la position du combat (secteur de 64 blocs). */
    public static int themeFor(ServerLevel level, int x, int z) {
        BlockPos c = fr.wakfu.rpg.core.Dungeon.center(level);
        if (Math.abs(x - c.getX()) <= 24 && Math.abs(z - c.getZ()) <= 24) return 3;   // donjon
        int sx = Math.floorDiv(x, 64);
        int sz = Math.floorDiv(z, 64);
        int h = Math.abs(sx * 31 + sz * 17);
        return h % 3;   // 0 plaine, 1 foret, 2 montagne
    }

    /**
     * Construit le cocon (sol, parois verre, toit, lumiere) + murs internes du theme.
     * Retourne les cases de murs internes (coordonnees absolues, au niveau du sol).
     */
    public static Set<BlockPos> ensureAt(ServerLevel level, int bx, int by, int bz, int theme, long seed) {
        theme = Math.max(0, Math.min(3, theme));
        int size = THEME_SIZE[theme];
        Block floor = THEME_FLOOR[theme], floor2 = THEME_FLOOR2[theme];
        Block edge = THEME_EDGE[theme], wallB = THEME_WALL[theme];
        int half = size / 2;

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                boolean isEdge = i == 0 || j == 0 || i == size - 1 || j == size - 1;
                BlockPos f = new BlockPos(bx + i, by - 1, bz + j);
                level.setBlockAndUpdate(f, (isEdge ? edge
                        : ((i + j) % 5 == 0 ? floor2 : floor)).defaultBlockState());
                if (isEdge) {
                    for (int h = 0; h < WALL_H; h++) {
                        level.setBlockAndUpdate(new BlockPos(bx + i, by + h, bz + j), Blocks.BARRIER.defaultBlockState());
                    }
                }
                level.setBlockAndUpdate(new BlockPos(bx + i, by + WALL_H, bz + j), Blocks.BARRIER.defaultBlockState());
            }
        }

        // ---- murs internes (piliers + petits segments), hors zones de spawn
        Random rnd = new Random(seed);
        List<BlockPos> candidates = new ArrayList<>();
        int pillars = theme == 0 ? 2 : (theme == 3 ? 5 : 3 + rnd.nextInt(2));
        Set<BlockPos> chosen = new HashSet<>();
        for (int attempt = 0; attempt < 60 && chosen.size() < pillars; attempt++) {
            int rx = 3 + rnd.nextInt(size - 6);          // evite colonnes de spawn (0-2 et size-3..size-1)
            int rz = 2 + rnd.nextInt(size - 4);
            BlockPos c = new BlockPos(bx + rx, by, bz + rz);
            boolean tooClose = false;
            for (BlockPos o : chosen) {
                if (Math.max(Math.abs(o.getX() - c.getX()), Math.abs(o.getZ() - c.getZ())) < 2) {
                    tooClose = true;
                    break;
                }
            }
            if (tooClose) continue;
            chosen.add(c);
            // petit segment en L parfois
            if (rnd.nextInt(3) == 0 && rx + 1 < size - 3) {
                chosen.add(new BlockPos(bx + rx + 1, by, bz + rz));
            }
        }
        // connectivite : les deux camps doivent pouvoir se rejoindre
        BlockPos spawnA = new BlockPos(bx + 2, by, bz + half);
        BlockPos spawnB = new BlockPos(bx + size - 3, by, bz + half);
        List<BlockPos> removable = new ArrayList<>(chosen);
        while (!connected(bx, by, bz, size, chosen, spawnA, spawnB) && !removable.isEmpty()) {
            chosen.remove(removable.remove(removable.size() - 1));
        }
        // pose des blocs (hauteur 2)
        for (BlockPos c : chosen) {
            level.setBlockAndUpdate(c, wallB.defaultBlockState());
            level.setBlockAndUpdate(c.above(), wallB.defaultBlockState());
            candidates.add(c);
        }

        // ---- eclairage : coins lumineux + blocs de lumiere
        for (int sx2 = -1; sx2 <= 1; sx2 += 2) {
            for (int sz2 = -1; sz2 <= 1; sz2 += 2) {
                level.setBlockAndUpdate(new BlockPos(bx + half + sx2 * (half - 1), by - 1, bz + half + sz2 * (half - 1)),
                        Blocks.GLOWSTONE.defaultBlockState());
            }
        }
        var light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 15);
        for (int sx2 = -1; sx2 <= 1; sx2 += 2) {
            for (int sz2 = -1; sz2 <= 1; sz2 += 2) {
                level.setBlockAndUpdate(new BlockPos(bx + half + sx2 * (half - 2), by + 4, bz + half + sz2 * (half - 2)), light);
            }
        }
        level.setBlockAndUpdate(new BlockPos(bx + half, by + 6, bz + half), light);
        return new HashSet<>(candidates);
    }

    /** BFS de connectivite entre les deux zones de spawn (8 directions). */
    private static boolean connected(int bx, int by, int bz, int size, Set<BlockPos> walls,
                                     BlockPos from, BlockPos to) {
        Set<BlockPos> seen = new HashSet<>();
        List<BlockPos> queue = new ArrayList<>();
        queue.add(from);
        seen.add(from);
        while (!queue.isEmpty()) {
            BlockPos c = queue.remove(0);
            if (c.equals(to)) return true;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos n = new BlockPos(c.getX() + dx, by, c.getZ() + dz);
                    int rx = n.getX() - bx, rz = n.getZ() - bz;
                    if (rx < 1 || rx > size - 2 || rz < 1 || rz > size - 2) continue;
                    if (walls.contains(n) || seen.contains(n)) continue;
                    seen.add(n);
                    queue.add(n);
                }
            }
        }
        return false;
    }

    /** Detruit le cocon (sol, parois, toit, murs internes, lumieres). */
    public static void clearAt(ServerLevel level, int bx, int by, int bz, int size) {
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                for (int h = -1; h <= WALL_H; h++) {
                    BlockPos p = new BlockPos(bx + i, by + h, bz + j);
                    if (!level.getBlockState(p).isAir()) {
                        level.setBlockAndUpdate(p, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }

    /** Compat ancien code : arene legacique 13x13 sans murs. */
    public static void ensureAt(ServerLevel level, int bx, int by, int bz) {
        ensureAt(level, bx, by, bz, 2, 0L);
    }

    public static int size() {
        return LEGACY_SIZE;
    }

    public static int half() {
        return LEGACY_SIZE / 2;
    }
}
