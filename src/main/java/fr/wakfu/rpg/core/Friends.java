package fr.wakfu.rpg.core;

import com.mojang.authlib.GameProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * v0.25 : liste d'amis persistante (wakfurpg_friends.dat).
 * Amitié MUTUELLE : ajouter un joueur vous ajoute aussi dans sa liste.
 * Résolution du pseudo : joueurs en ligne, cache de profils du serveur,
 * puis registre des noms vus au moins une fois (login).
 */
public final class Friends {

    private static final Map<UUID, List<UUID>> FRIENDS = new HashMap<>();
    private static final Map<UUID, String> NAMES = new HashMap<>();
    private static File file;

    private Friends() {
    }

    // ------------------------------------------------------------ persistance
    public static void load(MinecraftServer server) {
        try {
            file = new File(server.getWorldPath(LevelResource.ROOT).toFile(), "wakfurpg_friends.dat");
            if (file.exists()) {
                CompoundTag tag;
                try (FileInputStream in = new FileInputStream(file)) {
                    tag = NbtIo.readCompressed(in);
                }
                ListTag list = tag.getList("friends", 10);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag c = list.getCompound(i);
                    UUID u = UUID.fromString(c.getString("u"));
                    List<UUID> l = new ArrayList<>();
                    ListTag fl = c.getList("l", 8);
                    for (int j = 0; j < fl.size(); j++) l.add(UUID.fromString(fl.getString(j)));
                    FRIENDS.put(u, l);
                }
                CompoundTag names = tag.getCompound("names");
                for (String k : names.getAllKeys()) NAMES.put(UUID.fromString(k), names.getString(k));
            }
        } catch (Exception e) {
            System.err.println("[wakfurpg] Friends.load : " + e);
        }
    }

    public static void save() {
        if (file == null) return;
        try {
            ListTag list = new ListTag();
            for (var en : FRIENDS.entrySet()) {
                CompoundTag c = new CompoundTag();
                c.putString("u", en.getKey().toString());
                ListTag fl = new ListTag();
                for (UUID f : en.getValue()) fl.add(net.minecraft.nbt.StringTag.valueOf(f.toString()));
                c.put("l", fl);
                list.add(c);
            }
            CompoundTag names = new CompoundTag();
            for (var en : NAMES.entrySet()) names.putString(en.getKey().toString(), en.getValue());
            CompoundTag root = new CompoundTag();
            root.put("friends", list);
            root.put("names", names);
            NbtIo.writeCompressed(root, file);
        } catch (Exception e) {
            System.err.println("[wakfurpg] Friends.save : " + e);
        }
    }

    // ------------------------------------------------------------ noms
    /** Enregistre le pseudo actuel (login) — sert à résoudre les noms hors ligne. */
    public static void registerName(UUID u, String name) {
        if (u == null || name == null || name.isEmpty()) return;
        if (!name.equals(NAMES.put(u, name))) save();
    }

    public static String nameOf(UUID u) {
        String n = NAMES.get(u);
        return n != null ? n : "?";
    }

    private static UUID byName(MinecraftServer server, String name) {
        ServerPlayer online = server.getPlayerList().getPlayerByName(name);
        if (online != null) return online.getUUID();
        net.minecraft.server.players.GameProfileCache cache = server.getProfileCache();
        if (cache != null) {
            java.util.Optional<GameProfile> opt = cache.get(name);
            if (opt.isPresent()) {
                GameProfile prof = opt.get();
                if (prof.getName() != null) registerName(prof.getId(), prof.getName());
                return prof.getId();
            }
        }
        for (var en : NAMES.entrySet()) {
            if (en.getValue().equalsIgnoreCase(name)) return en.getKey();
        }
        return null;
    }

    // ------------------------------------------------------------ amitiés
    public static List<UUID> friendsOf(UUID u) {
        List<UUID> l = FRIENDS.get(u);
        return l == null ? List.of() : new ArrayList<>(l);
    }

    public static boolean isFriend(UUID a, UUID b) {
        List<UUID> l = FRIENDS.get(a);
        return l != null && l.contains(b);
    }

    /** @return message d'erreur (action bar), ou null si l'ami a été ajouté. */
    public static String add(ServerPlayer src, String targetName) {
        if (targetName == null || targetName.isBlank()) return "§cPseudo vide.";
        MinecraftServer server = src.getServer();
        UUID tu = byName(server, targetName.trim());
        if (tu == null) return "§cJoueur inconnu : " + targetName + " §7(il doit s'être connecté au moins une fois).";
        if (tu.equals(src.getUUID())) return "§cTu ne peux pas t'ajouter toi-même.";
        if (isFriend(src.getUUID(), tu)) return "§7" + nameOf(tu) + " est déjà ton ami.";
        FRIENDS.computeIfAbsent(src.getUUID(), k -> new ArrayList<>()).add(tu);
        FRIENDS.computeIfAbsent(tu, k -> new ArrayList<>()).add(src.getUUID());
        save();
        return null;
    }

    /** @return message d'erreur, ou null si l'ami a été retiré (des deux listes). */
    public static String remove(ServerPlayer src, String targetName) {
        UUID tu = byName(src.getServer(), targetName == null ? "" : targetName.trim());
        if (tu == null || !isFriend(src.getUUID(), tu)) return "§cCe joueur n'est pas dans tes amis.";
        List<UUID> mine = FRIENDS.get(src.getUUID());
        if (mine != null) mine.remove(tu);
        List<UUID> his = FRIENDS.get(tu);
        if (his != null) his.remove(src.getUUID());
        save();
        return null;
    }
}
