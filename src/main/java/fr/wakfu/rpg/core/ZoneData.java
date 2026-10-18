package fr.wakfu.rpg.core;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Zones protegees (tranche 3) : cercles persistes dans un fichier NBT
 * du dossier monde. Non-membres : pas de cassage/placement, pas de PvP.
 * Permission >= 2 (ops) : passe partout.
 */
public class ZoneData {

    public static class Zone {
        public String name;
        public String owner;
        public int x, z, r;
        public final Set<String> members = new HashSet<>();
        public final List<String> monsterIds = new ArrayList<>();
        public int minLevel = 1, maxLevel = 5, maxMobs = 3;
        public long nextSpawnTick;

        public boolean contains(BlockPos p) {
            long dx = p.getX() - x;
            long dz = p.getZ() - z;
            return dx * dx + dz * dz <= (long) r * r;
        }

        public boolean isMember(ServerPlayer player) {
            return owner.equals(player.getName().getString())
                    || members.contains(player.getName().getString());
        }
    }

    public final List<Zone> zones = new ArrayList<>();
    private static final Map<String, ZoneData> CURRENT = new HashMap<>();
    private final Path path;

    private ZoneData(Path path) {
        this.path = path;
        load();
    }

    public static ZoneData get(ServerLevel level) {
        String dimension = level.dimension().location().toString();
        return CURRENT.computeIfAbsent(dimension, key -> new ZoneData(pathFor(level)));
    }

    public static void reset() {
        CURRENT.clear();
    }

    private static Path pathFor(ServerLevel level) {
        String suffix = level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)
                ? "" : "_" + level.dimension().location().toString().replace(':', '_').replace('/', '_');
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve("wakfurpg_zones" + suffix + ".dat");
    }

    public Zone at(BlockPos p) {
        Zone best = null;
        for (Zone z : zones) {
            if (z.contains(p) && (best == null || z.r < best.r)) best = z;
        }
        return best;
    }

    public Zone byName(String name) {
        for (Zone z : zones) {
            if (z.name.equalsIgnoreCase(name)) return z;
        }
        return null;
    }

    /** true = action interdite pour ce joueur a cette position. */
    public static boolean isBlocked(ServerLevel level, BlockPos pos, ServerPlayer player) {
        Zone z = get(level).at(pos);
        if (z == null) return false;
        if (player.hasPermissions(2)) return false;
        return !z.isMember(player);
    }

    public void setDirty() {
        save();
    }

    public static void configureDefaults(Zone zone, ServerLevel level) {
        zone.monsterIds.clear();
        if (zone.name.equalsIgnoreCase("spawn")) {
            zone.minLevel = 1;
            zone.maxLevel = 1;
            zone.maxMobs = 1;
            return;
        }
        if (level.dimension().equals(IslandMap.DIMENSION)) {
            zone.monsterIds.addAll(List.of("bandit", "eclaireur_cendres", "brigand_grand_chemin",
                    "assassin_grand_chemin", "guerrier_lourd", "golem_siege", "archer_os"));
            zone.minLevel = 5; zone.maxLevel = 50; zone.maxMobs = 3;
        } else {
            zone.monsterIds.addAll(List.of("momic", "momic_venimeux", "gardien_racines",
                    "skeleton", "poupee_mecanique"));
            zone.minLevel = 1; zone.maxLevel = 16; zone.maxMobs = 3;
        }
    }

    public void ensureZoneConfigs(ServerLevel level) {
        boolean changed = false;
        for (Zone zone : zones) {
            if (zone.name.equalsIgnoreCase("spawn")) {
                if (!zone.monsterIds.isEmpty()) {
                    zone.monsterIds.clear();
                    changed = true;
                }
                continue;
            }
            if (zone.monsterIds.removeIf(id -> MonsterType.byId(id) == null)) changed = true;
            if (zone.monsterIds.isEmpty()) {
                configureDefaults(zone, level);
                changed = true;
            }
        }
        if (changed) setDirty();
    }

    public String notificationLabel(ServerLevel level, BlockPos pos) {
        Zone protectedZone = at(pos);
        if (protectedZone != null && !protectedZone.name.equalsIgnoreCase("spawn")) return protectedZone.name;
        BlockPos origin = level.dimension().equals(IslandMap.DIMENSION)
                ? IslandMap.spawn(level) : level.getSharedSpawnPos();
        int sx = Math.floorDiv(pos.getX() - origin.getX(), 64);
        int sz = Math.floorDiv(pos.getZ() - origin.getZ(), 64);
        if (sx == 0 && sz == 0) return "Village";
        String vertical = sz < 0 ? "Nord" : (sz > 0 ? "Sud" : "");
        String horizontal = sx < 0 ? "Ouest" : (sx > 0 ? "Est" : "");
        String direction = !vertical.isEmpty() && !horizontal.isEmpty()
                ? vertical + "-" + horizontal : vertical + horizontal;
        if (Math.abs(sx) > 1 || Math.abs(sz) > 1) direction += " · secteur " + Math.max(Math.abs(sx), Math.abs(sz));
        return direction.isEmpty() ? "Village" : direction;
    }

    // ------------------------------------------------------------ persistence fichier
    private void load() {
        try {
            if (path == null || !Files.exists(path)) return;
            CompoundTag tag = NbtIo.readCompressed(path.toFile());
            ListTag list = tag.getList("zones", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag t = list.getCompound(i);
                Zone z = new Zone();
                z.name = t.getString("name");
                z.owner = t.getString("owner");
                z.x = t.getInt("x");
                z.z = t.getInt("z");
                z.r = t.getInt("r");
                z.minLevel = t.contains("minLevel") ? t.getInt("minLevel") : 1;
                z.maxLevel = t.contains("maxLevel") ? t.getInt("maxLevel") : 5;
                z.maxMobs = t.contains("maxMobs") ? t.getInt("maxMobs") : 3;
                z.nextSpawnTick = t.getLong("nextSpawnTick");
                ListTag mem = t.getList("members", Tag.TAG_STRING);
                for (int k = 0; k < mem.size(); k++) z.members.add(mem.getString(k));
                ListTag monsters = t.getList("monsters", Tag.TAG_STRING);
                for (int k = 0; k < monsters.size(); k++) z.monsterIds.add(monsters.getString(k));
                zones.add(z);
            }
        } catch (Exception e) {
            org.apache.logging.log4j.LogManager.getLogger("wakfurpg")
                    .error("Impossible de lire les zones RPG depuis {}.", path, e);
        }
    }

    private void save() {
        try {
            if (path == null) return;
            CompoundTag tag = new CompoundTag();
            ListTag list = new ListTag();
            for (Zone z : zones) {
                CompoundTag t = new CompoundTag();
                t.putString("name", z.name);
                t.putString("owner", z.owner);
                t.putInt("x", z.x);
                t.putInt("z", z.z);
                t.putInt("r", z.r);
                t.putInt("minLevel", z.minLevel);
                t.putInt("maxLevel", z.maxLevel);
                t.putInt("maxMobs", z.maxMobs);
                t.putLong("nextSpawnTick", z.nextSpawnTick);
                ListTag monsters = new ListTag();
                for (String id : z.monsterIds) monsters.add(net.minecraft.nbt.StringTag.valueOf(id));
                t.put("monsters", monsters);
                ListTag mem = new ListTag();
                for (String m : z.members) mem.add(net.minecraft.nbt.StringTag.valueOf(m));
                t.put("members", mem);
                list.add(t);
            }
            tag.put("zones", list);
            NbtIo.writeCompressed(tag, path.toFile());
        } catch (Exception e) {
            org.apache.logging.log4j.LogManager.getLogger("wakfurpg")
                    .error("Impossible d'enregistrer les zones RPG dans {}.", path, e);
        }
    }
}
