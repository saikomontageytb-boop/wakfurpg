package fr.wakfu.rpg.core;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/** Access to the imported Grecos map in its own dimension. */
public final class IslandMap {
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(
            Registries.DIMENSION, new ResourceLocation("wakfurpg", "archipel"));
    public static final int MIN_LEVEL = 5;
    // Spawn saved in grecos.zip (Minecraft 1.17.1). The map is imported at its original coordinates.
    public static final BlockPos MAP_SPAWN = new BlockPos(-1031, 62, -69903);

    /** The dimension's current configured spawn (including /setworldspawn). */
    public static BlockPos spawn(ServerLevel level) {
        BlockPos configured = level.getSharedSpawnPos();
        return configured == null ? MAP_SPAWN : configured;
    }

    public static void prepare(ServerLevel level) {
        BlockPos spawn = spawn(level);
        int cx = spawn.getX() >> 4, cz = spawn.getZ() >> 4;
        for (int x = cx - 3; x <= cx + 3; x++)
            for (int z = cz - 3; z <= cz + 3; z++) level.getChunk(x, z);
    }

    /** Finds safe ground close to the old world's spawn without changing the supplied build. */
    public static BlockPos safeArrival(ServerLevel level) {
        prepare(level);
        BlockPos spawn = spawn(level);
        for (int r = 0; r <= 48; r += 2) {
            for (int dx = -r; dx <= r; dx += 2) {
                for (int dz = -r; dz <= r; dz += 2) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    int x = spawn.getX() + dx, z = spawn.getZ() + dz;
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                    BlockPos feet = new BlockPos(x, y, z);
                    if (level.getBlockState(feet.below()).isSolid()
                            && level.getBlockState(feet).getFluidState().isEmpty()
                            && level.getBlockState(feet).canBeReplaced()
                            && level.getBlockState(feet.above()).canBeReplaced()) return feet;
                }
            }
        }
        return new BlockPos(spawn.getX(), Math.max(spawn.getY(), level.getSeaLevel() + 2), spawn.getZ());
    }

    private IslandMap() { }
}
