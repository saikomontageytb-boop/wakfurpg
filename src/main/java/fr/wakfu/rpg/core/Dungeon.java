package fr.wakfu.rpg.core;

import fr.wakfu.rpg.entity.MonsterNpc;
import fr.wakfu.rpg.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.Random;

/**
 * v0.14 : donjon « Repaire du Chef des Bandits ».
 * Fortin en pierre abimee construit a l'est du village (chemin balise de
 * poteaux-torches), salle du boss au nord-est avec coffre de butin,
 * 3 gardes bandits dans la cour. Boss Nv.10, respawn 20 min apres sa mort.
 */
public class Dungeon {

    public static final int DX = 150, DZ = 60;
    private static final Random R = new Random();
    private static long bossAbsentSince = -1;
    private static final long BOSS_RESPAWN_TICKS = 24000;   // 20 min

    public static BlockPos center(ServerLevel level) {
        var spawn = level.getSharedSpawnPos();
        return new BlockPos(spawn.getX() + DX, spawn.getY(), spawn.getZ() + DZ);
    }

    /** Appel au demarrage puis periodiquement : construit si absent, entretient boss/gardes. */
    public static void ensure(ServerLevel level, boolean initial) {
        BlockPos c = center(level);
        for (int cx = (c.getX() - 24) >> 4; cx <= (c.getX() + 24) >> 4; cx++) {
            for (int cz = (c.getZ() - 24) >> 4; cz <= (c.getZ() + 24) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        int gy = ground(level, c.getX(), c.getZ());
        if (!level.getBlockState(new BlockPos(c.getX(), gy - 1, c.getZ())).is(Blocks.CHISELED_STONE_BRICKS)) {
            build(level, c);
        }
        boolean boss = false;
        int guards = 0;
        AABB box = new AABB(c).inflate(26);
        for (MonsterNpc m : level.getEntitiesOfClass(MonsterNpc.class, box)) {
            if (m.getTags().contains("dungeon_boss")) boss = true;
            if (m.getTags().contains("dungeon_guard")) guards++;
        }
        long t = level.getGameTime();
        if (boss) {
            bossAbsentSince = -1;
        } else {
            if (bossAbsentSince < 0) bossAbsentSince = t;
            if (initial || t - bossAbsentSince >= BOSS_RESPAWN_TICKS) {
                spawnBoss(level, c);
                refillChest(level, c);
                bossAbsentSince = -1;
            }
        }
        if (guards < 3) spawnGuard(level, c);
    }

    // ------------------------------------------------------------ construction
    private static void build(ServerLevel level, BlockPos c) {
        int x0 = c.getX() - 10, x1 = c.getX() + 10;
        int z0 = c.getZ() - 10, z1 = c.getZ() + 10;

        // enceinte (crenelee) avec porte a l'ouest
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                if (!edge) continue;
                if (x == x0 && Math.abs(z - c.getZ()) <= 1) continue;   // passage
                int gy = ground(level, x, z);
                int h = ((x + z) % 2 == 0) ? 6 : 5;
                for (int y = gy; y < gy + h; y++) {
                    level.setBlock(new BlockPos(x, y, z), brick(), 3);
                }
            }
        }
        // montant + linteau de la porte (chene noir)
        int gyG = ground(level, x0, c.getZ());
        for (int y = gyG; y < gyG + 6; y++) {
            level.setBlock(new BlockPos(x0, y, c.getZ() - 2), Blocks.DARK_OAK_LOG.defaultBlockState(), 3);
            level.setBlock(new BlockPos(x0, y, c.getZ() + 2), Blocks.DARK_OAK_LOG.defaultBlockState(), 3);
        }
        for (int z = c.getZ() - 2; z <= c.getZ() + 2; z++) {
            level.setBlock(new BlockPos(x0, gyG + 5, z), Blocks.DARK_OAK_LOG.defaultBlockState(), 3);
        }
        // tours d'angle + torches
        for (int[] t : new int[][]{{x0, z0}, {x0, z1}, {x1, z0}, {x1, z1}}) {
            int gy = ground(level, t[0], t[1]);
            for (int y = gy; y < gy + 7; y++) {
                level.setBlock(new BlockPos(t[0], y, t[1]), Blocks.DARK_OAK_LOG.defaultBlockState(), 3);
            }
            level.setBlock(new BlockPos(t[0], gy + 7, t[1]), Blocks.TORCH.defaultBlockState(), 3);
        }
        // salle du boss (nord-est) : murs h6, toit plein, ouverture au sud
        int bx0 = c.getX() + 2, bx1 = c.getX() + 9, bz0 = c.getZ() - 9, bz1 = c.getZ() - 2;
        for (int x = bx0; x <= bx1; x++) {
            for (int z = bz0; z <= bz1; z++) {
                boolean edge = x == bx0 || x == bx1 || z == bz0 || z == bz1;
                int gy = ground(level, x, z);
                if (edge) {
                    if (z == bz1 && (x == c.getX() + 5 || x == c.getX() + 6)) {
                        for (int y = gy; y < gy + 3; y++) {
                            level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                        }
                        continue;
                    }
                    for (int y = gy; y < gy + 6; y++) {
                        level.setBlock(new BlockPos(x, y, z), brick(), 3);
                    }
                } else {
                    for (int y = gy; y < gy + 6; y++) {
                        level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                    }
                    level.setBlock(new BlockPos(x, gy + 6, z), Blocks.STONE_BRICKS.defaultBlockState(), 3);
                }
            }
        }
        // decor salle du boss : torches + coffre
        int gyT = ground(level, bx0 + 1, bz0 + 1);
        level.setBlock(new BlockPos(bx0 + 1, gyT, bz0 + 1), Blocks.TORCH.defaultBlockState(), 3);
        int gyT2 = ground(level, bx1 - 1, bz1 - 1);
        level.setBlock(new BlockPos(bx1 - 1, gyT2, bz1 - 1), Blocks.TORCH.defaultBlockState(), 3);
        int gyC = ground(level, bx1 - 1, bz0 + 1);
        level.setBlock(new BlockPos(bx1 - 1, gyC, bz0 + 1), Blocks.CHEST.defaultBlockState(), 3);

        // marqueur de construction au centre de la cour
        int gyM = ground(level, c.getX(), c.getZ());
        level.setBlock(new BlockPos(c.getX(), gyM - 1, c.getZ()), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), 3);

        // decor cour : feu de camp eteint, toiles d'araignee sur les murs
        int gyF = ground(level, c.getX() - 3, c.getZ() + 3);
        level.setBlock(new BlockPos(c.getX() - 3, gyF, c.getZ() + 3),
                Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false), 3);
        for (int i = 0; i < 8; i++) {
            int wx = x0 + R.nextInt(x1 - x0 + 1);
            int wz = (R.nextBoolean() ? z0 : z1);
            int wy = ground(level, wx, wz) + 4 + R.nextInt(2);
            level.setBlock(new BlockPos(wx, wy, wz), Blocks.COBWEB.defaultBlockState(), 3);
        }

        // chemin balise depuis le spawn (poteaux + torches tous les ~8 blocs)
        var spawn = level.getSharedSpawnPos();
        int steps = 18;
        for (int i = 1; i <= steps; i++) {
            double f = i / (double) (steps + 1);
            int px = (int) (spawn.getX() + (c.getX() - spawn.getX()) * f);
            int pz = (int) (spawn.getZ() + (c.getZ() - spawn.getZ()) * f);
            int py = ground(level, px, pz);
            level.setBlock(new BlockPos(px, py, pz), Blocks.DARK_OAK_FENCE.defaultBlockState(), 3);
            level.setBlock(new BlockPos(px, py + 1, pz), Blocks.DARK_OAK_FENCE.defaultBlockState(), 3);
            level.setBlock(new BlockPos(px, py + 2, pz), Blocks.TORCH.defaultBlockState(), 3);
        }
    }

    private static net.minecraft.world.level.block.state.BlockState brick() {
        int r = R.nextInt(100);
        if (r < 45) return Blocks.STONE_BRICKS.defaultBlockState();
        if (r < 70) return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
        if (r < 90) return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        return Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
    }

    private static int ground(ServerLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
    }

    // ------------------------------------------------------------ population
    private static void spawnBoss(ServerLevel level, BlockPos c) {
        int bx = c.getX() + 5, bz = c.getZ() - 5;
        int y = ground(level, bx, bz);
        MonsterNpc boss = new MonsterNpc(ModEntities.MONSTER.get(), level);
        boss.init(MonsterType.BOSS, 10);
        boss.addTag("dungeon_boss");
        boss.moveTo(bx + 0.5, y, bz + 0.5, 180, 0);
        level.addFreshEntity(boss);
    }

    private static void spawnGuard(ServerLevel level, BlockPos c) {
        for (int tries = 0; tries < 12; tries++) {
            int gx = c.getX() - 8 + R.nextInt(12);   // cour (moitie ouest)
            int gz = c.getZ() - 4 + R.nextInt(12);
            int gy = ground(level, gx, gz);
            BlockPos p = new BlockPos(gx, gy, gz);
            if (!level.canSeeSky(p)) continue;
            MonsterNpc g = new MonsterNpc(ModEntities.MONSTER.get(), level);
            g.init(MonsterType.BANDIT, 6 + R.nextInt(4), "");   // Nv. 6-9 (zone bandits)
            g.addTag("dungeon_guard");
            g.moveTo(gx + 0.5, gy, gz + 0.5, R.nextFloat() * 360, 0);
            level.addFreshEntity(g);
            return;
        }
    }

    private static void refillChest(ServerLevel level, BlockPos c) {
        BlockPos cp = new BlockPos(c.getX() + 8, ground(level, c.getX() + 8, c.getZ() - 8), c.getZ() - 8);
        if (level.getBlockEntity(cp) instanceof ChestBlockEntity chest) {
            chest.clearContent();
            // v0.25 : armure Fantasy (ou rare) à la place des lingots
            net.minecraft.world.item.ItemStack fa = fr.wakfu.rpg.core.FantasyLoot.randomPiece(
                    net.minecraft.util.RandomSource.create());
            chest.setItem(0, !fa.isEmpty() ? fa : fr.wakfu.rpg.item.WakfuArmorItem.makePiece(
                    new String[]{"head", "chest", "legs", "feet"}[R.nextInt(4)], "rare", 1,
                    net.minecraft.util.RandomSource.create()));
            chest.setItem(2, fr.wakfu.rpg.item.WakfuArmorItem.makePiece(
                    new String[]{"head", "chest", "feet"}[R.nextInt(3)], "epique", 1,
                    net.minecraft.util.RandomSource.create()));
            chest.setItem(4, fr.wakfu.rpg.item.WakfuWeaponItem.randomWeapon(
                    net.minecraft.util.RandomSource.create()));
            chest.setItem(6, new net.minecraft.world.item.ItemStack(
                    net.minecraft.world.item.Items.GOLDEN_APPLE, 2));
            // v0.22 : une piece de la panoplie du Chef de Guerre dans le coffre
            chest.setItem(8, fr.wakfu.rpg.item.WakfuArmorItem.makeSetPiece("chef",
                    new String[]{"head", "chest", "legs", "feet"}[R.nextInt(4)]));
        }
    }

    private Dungeon() {}
}
