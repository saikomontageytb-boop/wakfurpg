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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
    private static ZoneData current = null;
    private static Path path = null;

    public static ZoneData get(ServerLevel level) {
        if (current == null) {
            current = new ZoneData();
            path = java.nio.file.Paths.get("wakfurpg_zones.dat");
            load();
        }
        return current;
    }

    public static void reset() {
        current = null;
        path = null;
    }

    public Zone at(BlockPos p) {
        for (Zone z : zones) {
            if (z.contains(p)) return z;
        }
        return null;
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

    // ------------------------------------------------------------ persistence fichier
    private static void load() {
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
                ListTag mem = t.getList("members", Tag.TAG_STRING);
                for (int k = 0; k < mem.size(); k++) z.members.add(mem.getString(k));
                current.zones.add(z);
            }
        } catch (Exception ignored) {
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
                ListTag mem = new ListTag();
                for (String m : z.members) mem.add(net.minecraft.nbt.StringTag.valueOf(m));
                t.put("members", mem);
                list.add(t);
            }
            tag.put("zones", list);
            NbtIo.writeCompressed(tag, path.toFile());
        } catch (Exception ignored) {
        }
    }
}
