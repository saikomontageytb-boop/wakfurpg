package fr.wakfu.rpg.core;

import fr.wakfu.rpg.entity.MonsterNpc;
import fr.wakfu.rpg.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Invisible, persistent spawn sites. Each site is scoped to its own dimension save. */
public final class MobSpawnerData extends SavedData {
    private static final String SAVE_ID = "wakfurpg_mob_spawners";
    private static final Random RANDOM = new Random();
    /** Tentatives aléatoires pour trouver un sol libre (aucun balayage exhaustif). */
    private static final int GROUND_ATTEMPTS = 40;
    /** Les monstres n'apparaissent jamais plus près que cette distance d'un joueur. */
    private static final double MIN_PLAYER_DISTANCE_SQ = 20.0 * 20.0;
    private final Map<String, SpawnSite> sites = new LinkedHashMap<>();
    private boolean initialized;

    public static final class SpawnSite {
        public final String name;
        public final int x, y, z, radius, minLevel, maxLevel, cap;
        private long nextSpawnTick;

        private SpawnSite(String name, int x, int y, int z, int radius, int minLevel, int maxLevel,
                          int cap, long nextSpawnTick) {
            this.name = name; this.x = x; this.y = y; this.z = z;
            this.radius = radius; this.minLevel = minLevel; this.maxLevel = maxLevel;
            this.cap = cap; this.nextSpawnTick = nextSpawnTick;
        }

        public BlockPos center() { return new BlockPos(x, y, z); }
    }

    public static MobSpawnerData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(MobSpawnerData::load, MobSpawnerData::new, SAVE_ID);
    }

    private static MobSpawnerData load(CompoundTag tag) {
        MobSpawnerData data = new MobSpawnerData();
        data.initialized = tag.getBoolean("Initialized");
        ListTag list = tag.getList("Sites", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag s = list.getCompound(i);
            SpawnSite site = new SpawnSite(s.getString("Name"), s.getInt("X"), s.getInt("Y"), s.getInt("Z"),
                    s.getInt("Radius"), s.getInt("Min"), s.getInt("Max"), s.getInt("Cap"),
                    s.getLong("NextSpawn"));
            if (!site.name.isBlank() && site.radius >= 8 && site.minLevel >= 1 && site.maxLevel <= 50
                    && site.minLevel <= site.maxLevel && site.cap >= 1) data.sites.put(site.name, site);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (SpawnSite site : sites.values()) {
            CompoundTag s = new CompoundTag();
            s.putString("Name", site.name); s.putInt("X", site.x); s.putInt("Y", site.y); s.putInt("Z", site.z);
            s.putInt("Radius", site.radius); s.putInt("Min", site.minLevel); s.putInt("Max", site.maxLevel);
            s.putInt("Cap", site.cap); s.putLong("NextSpawn", site.nextSpawnTick);
            list.add(s);
        }
        tag.put("Sites", list);
        tag.putBoolean("Initialized", initialized);
        return tag;
    }

    public SpawnSite get(String name) { return sites.get(name); }
    public List<SpawnSite> all() { return new ArrayList<>(sites.values()); }

    public SpawnSite add(String name, BlockPos center, int radius, int minLevel, int maxLevel, int cap) {
        SpawnSite site = new SpawnSite(name, center.getX(), center.getY(), center.getZ(), radius,
                minLevel, maxLevel, cap, 0);
        sites.put(name, site);
        initialized = true;
        setDirty();
        return site;
    }

    public boolean remove(String name) {
        boolean changed = sites.remove(name) != null;
        if (changed) setDirty();
        return changed;
    }

    public void clear() {
        sites.clear();
        initialized = true;
        setDirty();
    }

    /** Create a broad, sparse starter grid only when this dimension has no owner-configured sites. */
    public void ensureDefaults(ServerLevel level) {
        if (initialized) return;
        if (!sites.isEmpty()) {
            initialized = true;
            setDirty();
            return;
        }
        boolean grecos = level.dimension().equals(IslandMap.DIMENSION);
        BlockPos origin = grecos ? IslandMap.spawn(level) : level.getSharedSpawnPos();
        for (int sx = -2; sx <= 2; sx++) {
            for (int sz = -2; sz <= 2; sz++) {
                int x = origin.getX() + sx * 320;
                int z = origin.getZ() + sz * 320;
                double distance = Math.hypot(x - origin.getX(), z - origin.getZ());
                int min, max;
                if (grecos) {
                    min = Math.min(48, 5 + (int) (distance / 1000.0 * 45.0));
                    max = Math.min(50, min + 5);
                } else if (distance < 110) { min = 1; max = 5; }
                else if (distance < 300) { min = 4; max = 10; }
                else { min = 8; max = 16; }
                String name = "auto_" + sx + "_" + sz;
                add(name, new BlockPos(x, origin.getY(), z), 160, min, max, 4);
            }
        }
        initialized = true;
        setDirty();
    }

    /** Remove only old wild Wakfu mobs and their Alex's Mobs display proxies. */
    public static int purgeLegacyMobs(ServerLevel level) {
        int removed = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof MonsterNpc mob) {
                if (mob.isSummon() || mob.getTags().contains("arena_copy")) continue;
                mob.discardWithVisual();
                removed++;
            } else if (entity.getTags().contains("wakfurpg_alex_model")) {
                entity.discard();
                removed++;
            } else if (entity instanceof net.minecraft.world.entity.monster.Monster) {
                entity.discard();
                removed++;
            }
        }
        return removed;
    }

    /** Called every 40 server ticks. A point attempts one separated spawn per minute at most. */
    public static void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (now % 40 != 0) return;
        // personne en ligne dans cette dimension : aucun travail à faire
        if (level.players().isEmpty()) return;
        MobSpawnerData data = get(level);
        for (SpawnSite site : data.sites.values()) {
            if (!hasNearbyPlayer(level, site.x, site.z, site.radius)) continue;
            int alive = countWild(level, site.x, site.z, site.radius);
            if (alive >= site.cap) continue;
            if (alive > 0 && now < site.nextSpawnTick) continue;
            BlockPos spawn = findDryGround(level, site);
            if (spawn == null) {
                site.nextSpawnTick = now + 200;
                data.setDirty();
                continue;
            }
            if (!spawnMonster(level, site, spawn, site.cap - alive)) {
                site.nextSpawnTick = now + 40;
                data.setDirty();
                continue;
            }
            site.nextSpawnTick = now + 1200;
            data.setDirty();
        }
        tickZones(level, now);
    }

    public static void refreshZone(ServerLevel level, ZoneData.Zone zone) {
        tickZone(level, zone, level.getGameTime(), true);
    }

    private static void tickZones(ServerLevel level, long now) {
        for (ZoneData.Zone zone : ZoneData.get(level).zones) tickZone(level, zone, now, false);
    }

    private static void tickZone(ServerLevel level, ZoneData.Zone zone, long now, boolean immediate) {
        if (zone.name.equalsIgnoreCase("spawn") || zone.monsterIds.isEmpty()) return;
        // gardes bon marché d'abord : pas de scan d'entités tant qu'aucun joueur n'est à portée
        if (!immediate && !hasNearbyPlayer(level, zone.x, zone.z, zone.r)) return;
        BlockPos center = new BlockPos(zone.x, level.getSharedSpawnPos().getY(), zone.z);
        if (!level.hasChunkAt(center)) return;
        int alive = countWild(level, zone.x, zone.z, zone.r);
        if (alive >= zone.maxMobs) return;
        if (!immediate && alive > 0 && now < zone.nextSpawnTick) return;
        SpawnSite site = new SpawnSite("zone_" + zone.name, zone.x, center.getY(), zone.z,
                zone.r, zone.minLevel, zone.maxLevel, zone.maxMobs, zone.nextSpawnTick);
        BlockPos spawn = findDryGround(level, site);
        if (spawn == null) {
            zone.nextSpawnTick = now + 200;
            return;
        }
        String id = zone.monsterIds.get(RANDOM.nextInt(zone.monsterIds.size()));
        if (!spawnZoneMonster(level, site, spawn, id, zone.maxMobs - alive)) {
            zone.nextSpawnTick = now + 40;
            return;
        }
        // le délai d'apparition n'est volontairement pas écrit sur disque à chaque monstre
        zone.nextSpawnTick = now + (alive == 0 ? 200 : 600);
    }

    /** Monstres sauvages (hors invocations et copies d'arène) dans un disque horizontal. */
    private static int countWild(ServerLevel level, int cx, int cz, int radius) {
        AABB area = new AABB(cx - radius, level.getMinBuildHeight(), cz - radius,
                cx + radius + 1, level.getMaxBuildHeight(), cz + radius + 1);
        long maxSq = (long) radius * radius;
        int alive = 0;
        for (MonsterNpc mob : level.getEntitiesOfClass(MonsterNpc.class, area)) {
            if (mob.isSummon() || mob.getTags().contains("arena_copy")) continue;
            double dx = mob.getX() - cx, dz = mob.getZ() - cz;
            if (dx * dx + dz * dz <= maxSq) alive++;
        }
        return alive;
    }

    private static boolean hasNearbyPlayer(ServerLevel level, int x, int z, int radius) {
        double reach = radius + 64.0;
        double maxSq = reach * reach;
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - (x + 0.5), dz = player.getZ() - (z + 0.5);
            if (dx * dx + dz * dz <= maxSq) return true;
        }
        return false;
    }

    /** Vrai si aucun joueur n'est à moins de 20 blocs : pas d'apparition dans le dos du joueur. */
    private static boolean farFromPlayers(ServerLevel level, BlockPos feet) {
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5) < MIN_PLAYER_DISTANCE_SQ)
                return false;
        }
        return true;
    }

    private static boolean isSeparated(ServerLevel level, BlockPos feet) {
        for (MonsterNpc other : level.getEntitiesOfClass(MonsterNpc.class, new AABB(feet).inflate(10))) {
            if (!other.isSummon() && !other.getTags().contains("arena_copy")
                    && other.distanceToSqr(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5) < 100) return false;
        }
        return true;
    }

    /**
     * Cherche un sol libre par tirages aléatoires dans le disque du site. Les chunks non chargés
     * sont ignorés : getHeight les chargerait (et les générerait) sur le thread serveur.
     */
    private static BlockPos findDryGround(ServerLevel level, SpawnSite site) {
        for (int attempt = 0; attempt < GROUND_ATTEMPTS; attempt++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            double radius = Math.sqrt(RANDOM.nextDouble()) * site.radius;
            int x = site.x + (int) Math.round(Math.cos(angle) * radius);
            int z = site.z + (int) Math.round(Math.sin(angle) * radius);
            if (!level.hasChunkAt(new BlockPos(x, level.getMinBuildHeight(), z))) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos feet = new BlockPos(x, y, z);
            if (validSpawnSpace(level, feet) && farFromPlayers(level, feet) && isSeparated(level, feet)) return feet;
        }
        return null;
    }

    private static boolean validSpawnSpace(ServerLevel level, BlockPos feet) {
        return level.hasChunkAt(feet) && level.getBlockState(feet.below()).blocksMotion()
                && level.getBlockState(feet).getFluidState().isEmpty()
                && level.getBlockState(feet.above()).getFluidState().isEmpty()
                && level.getBlockState(feet).canBeReplaced()
                && level.getBlockState(feet.above()).canBeReplaced();
    }

    /** Bestiaire du spawner selon la dimension et le niveau tiré : plus on monte, plus c'est dangereux. */
    private static String[] familyFor(boolean grecos, int mobLevel) {
        if (grecos) {
            if (mobLevel <= 15) return new String[]{"eclaireur_cendres", "bandit", "archer_os", "momic_venimeux"};
            if (mobLevel <= 30) return new String[]{"bandit", "brigand_grand_chemin", "assassin_grand_chemin",
                    "archer_os", "guerrier_lourd"};
            return new String[]{"brigand_grand_chemin", "assassin_grand_chemin", "guerrier_lourd",
                    "golem_siege", "archer_os"};
        }
        if (mobLevel <= 5) return new String[]{"momic", "momic_venimeux", "poupee_mecanique", "eclaireur_cendres"};
        if (mobLevel <= 10) return new String[]{"skeleton", "archer_os", "sentinelle_forest", "gardien_racines",
                "momic_venimeux", "bandit"};
        return new String[]{"bandit", "brigand_grand_chemin", "assassin_grand_chemin", "guerrier_lourd",
                "golem_siege", "archer_os"};
    }

    /** Apparition d'un monstre du bestiaire du site ; room = places restantes avant le plafond. */
    private static boolean spawnMonster(ServerLevel level, SpawnSite site, BlockPos pos, int room) {
        boolean grecos = level.dimension().equals(IslandMap.DIMENSION);
        int levelRange = Math.max(0, site.maxLevel - site.minLevel + 1);
        int mobLevel = site.minLevel + (levelRange == 0 ? 0 : RANDOM.nextInt(levelRange));
        String[] family = familyFor(grecos, mobLevel);
        String id = family[RANDOM.nextInt(family.length)];
        return spawnWithPack(level, site, pos, id, mobLevel, room);
    }

    /** Apparition d'un monstre choisi par la zone ; room = places restantes avant le plafond. */
    private static boolean spawnZoneMonster(ServerLevel level, SpawnSite site, BlockPos pos, String id, int room) {
        int range = Math.max(0, site.maxLevel - site.minLevel + 1);
        int mobLevel = site.minLevel + (range == 0 ? 0 : RANDOM.nextInt(range));
        return spawnWithPack(level, site, pos, id, mobLevel, room);
    }

    /**
     * Fait apparaître le monstre, puis (35 % du temps) 1 à 2 compagnons de la même espèce à
     * quelques blocs, sans jamais dépasser la place restante : le terrain a des meutes, pas
     * seulement des monstres isolés.
     */
    private static boolean spawnWithPack(ServerLevel level, SpawnSite site, BlockPos pos, String id,
                                         int mobLevel, int room) {
        if (!spawnMonster(level, site, pos, id, mobLevel)) return false;
        int maxExtra = Math.min(room - 1, 2);
        if (maxExtra <= 0 || RANDOM.nextInt(100) >= 35) return true;
        int wanted = 1 + RANDOM.nextInt(maxExtra);
        int spawned = 0;
        for (int tries = 0; spawned < wanted && tries < 8; tries++) {
            int x = pos.getX() + RANDOM.nextInt(7) - 3;
            int z = pos.getZ() + RANDOM.nextInt(7) - 3;
            if (x == pos.getX() && z == pos.getZ()) continue;
            if (!level.hasChunkAt(new BlockPos(x, level.getMinBuildHeight(), z))) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos feet = new BlockPos(x, y, z);
            if (!validSpawnSpace(level, feet) || !farFromPlayers(level, feet)) continue;
            int companionLevel = Math.max(site.minLevel, mobLevel - RANDOM.nextInt(2));
            if (spawnMonster(level, site, feet, id, companionLevel)) spawned++;
        }
        return true;
    }

    private static boolean spawnMonster(ServerLevel level, SpawnSite site, BlockPos pos, String id, int mobLevel) {
        MonsterType type = MonsterType.byId(id);
        if (type == null) return false;
        MobVariants.Variant variant = MobVariants.roll(id, RANDOM);
        MonsterNpc mob = new MonsterNpc(ModEntities.MONSTER.get(), level);
        mob.init(type, mobLevel, variant == null ? "" : variant.id);
        mob.addTag("wakfurpg_spawner_managed");
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, RANDOM.nextFloat() * 360, 0);
        mob.setHome(pos.getX(), pos.getY(), pos.getZ());
        return level.addFreshEntity(mob);
    }

    private MobSpawnerData() {}
}