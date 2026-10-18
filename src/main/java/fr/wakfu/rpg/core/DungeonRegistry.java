package fr.wakfu.rpg.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persistent list of user-authored dungeon entrances. Stored once in the world. */
public final class DungeonRegistry extends SavedData {
    public static final class Gate {
        public String id, name, dimension;
        public int x, y, z, minLevel, maxLevel;

        public DungeonInfo info() { return new DungeonInfo(id, name, minLevel, maxLevel); }
    }

    private final Map<String, Gate> gates = new LinkedHashMap<>();

    public static DungeonRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(DungeonRegistry::load,
                DungeonRegistry::new, "wakfurpg_dungeons");
    }

    public Collection<Gate> all() { return new ArrayList<>(gates.values()); }
    public Gate get(String id) { return gates.get(id.toLowerCase()); }
    public void put(Gate gate) { gates.put(gate.id.toLowerCase(), gate); setDirty(); }
    public Gate remove(String id) { Gate g = gates.remove(id.toLowerCase()); if (g != null) setDirty(); return g; }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Gate gate : gates.values()) {
            CompoundTag t = new CompoundTag();
            t.putString("id", gate.id); t.putString("name", gate.name); t.putString("dimension", gate.dimension);
            t.putInt("x", gate.x); t.putInt("y", gate.y); t.putInt("z", gate.z);
            t.putInt("minLevel", gate.minLevel); t.putInt("maxLevel", gate.maxLevel);
            list.add(t);
        }
        tag.put("gates", list);
        return tag;
    }

    private static DungeonRegistry load(CompoundTag tag) {
        DungeonRegistry data = new DungeonRegistry();
        ListTag list = tag.getList("gates", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            Gate g = new Gate();
            g.id = t.getString("id"); g.name = t.getString("name"); g.dimension = t.getString("dimension");
            g.x = t.getInt("x"); g.y = t.getInt("y"); g.z = t.getInt("z");
            g.minLevel = t.getInt("minLevel"); g.maxLevel = t.getInt("maxLevel");
            if (!g.id.isBlank() && !g.dimension.isBlank()) data.gates.put(g.id.toLowerCase(), g);
        }
        return data;
    }
}
