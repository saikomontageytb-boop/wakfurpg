package fr.wakfu.rpg.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * v0.25 : ARGENT ($) — monnaie virtuelle unique du serveur.
 * Remplace les lingots de Wakfu (convertis automatiquement au login, 1 lingot = 10 $).
 * Grand livre persistant cote serveur (wakfurpg_money.dat dans le dossier monde) :
 * les transactions marchent meme quand le vendeur est deconnecte.
 * Le solde est synchronise vers le client via MoneyPacket (HUD + interfaces).
 */
public final class Money {

    /** 1 ancien lingot de Wakfu = 10 $. */
    public static final long INGOT_VALUE = 10L;

    private static final Map<UUID, Long> BAL = new HashMap<>();
    private static File file;

    private Money() {
    }

    // ------------------------------------------------------------ persistance
    public static void load(MinecraftServer server) {
        try {
            file = new File(server.getWorldPath(LevelResource.ROOT).toFile(), "wakfurpg_money.dat");
            if (file.exists()) {
                CompoundTag tag;
                try (FileInputStream in = new FileInputStream(file)) {
                    tag = NbtIo.readCompressed(in);
                }
                CompoundTag bal = tag.getCompound("bal");
                for (String key : bal.getAllKeys()) {
                    BAL.put(UUID.fromString(key), bal.getLong(key));
                }
            }
        } catch (Exception e) {
            System.err.println("[wakfurpg] Money.load : " + e);
        }
    }

    public static void save() {
        if (file == null) return;
        try {
            CompoundTag bal = new CompoundTag();
            for (var en : BAL.entrySet()) bal.putLong(en.getKey().toString(), en.getValue());
            CompoundTag root = new CompoundTag();
            root.put("bal", bal);
            NbtIo.writeCompressed(root, file);
        } catch (Exception e) {
            System.err.println("[wakfurpg] Money.save : " + e);
        }
    }

    // ------------------------------------------------------------ operations
    public static long balance(UUID u) {
        return BAL.getOrDefault(u, 0L);
    }

    public static void add(UUID u, long n) {
        if (u == null || n <= 0) return;
        BAL.merge(u, n, Long::sum);
        save();
    }

    /** @return false si solde insuffisant (rien n'est retire). */
    public static boolean take(UUID u, long n) {
        if (u == null || n <= 0) return true;
        long cur = balance(u);
        if (cur < n) return false;
        BAL.put(u, cur - n);
        save();
        return true;
    }

    /** Transfert atomique : retire a `from`, ajoute a `to`. */
    public static boolean transfer(UUID from, UUID to, long n) {
        if (!take(from, n)) return false;
        add(to, n);
        return true;
    }

    /** Envoie le solde au client (HUD, grimoire, marche). */
    public static void sync(ServerPlayer p) {
        if (p == null) return;
        fr.wakfu.rpg.network.Network.sendToPlayer(p,
                new fr.wakfu.rpg.network.MoneyPacket(balance(p.getUUID())));
    }

    /** Ajoute + synchronise + message barre d'action. */
    public static void grant(ServerPlayer p, long n, String label) {
        if (p == null || n <= 0) return;
        add(p.getUUID(), n);
        sync(p);
        if (label != null && !label.isEmpty()) {
            p.displayClientMessage(Component.literal(label), true);
        }
    }

    // ------------------------------------------------------------ migration v0.25
    /** Convertit les lingots de Wakfu restants en argent (au login). */
    public static void convertIngots(ServerPlayer p) {
        int n = p.getInventory().countItem(fr.wakfu.rpg.registry.ModItems.WAKFU_INGOT.get());
        if (n <= 0) return;
        int need = n;
        for (int i = 0; i < 36 && need > 0; i++) {
            ItemStack st = p.getInventory().getItem(i);
            if (st.getItem() == fr.wakfu.rpg.registry.ModItems.WAKFU_INGOT.get()) {
                ItemStack taken = p.getInventory().removeItem(i, need);
                need -= taken.getCount();
            }
        }
        long gained = n * INGOT_VALUE;
        add(p.getUUID(), gained);
        sync(p);
        p.displayClientMessage(Component.literal(
                "§6" + n + " lingot" + (n > 1 ? "s" : "") + " converti" + (n > 1 ? "s" : "")
                        + " en §e" + gained + " $§6 — l'argent s'affiche dans le HUD et le Grimoire."), true);
    }

    /** Formatage français : 1 234 567 (groupes de 3, espace fine remplacee par espace simple). */
    public static String fmt(long v) {
        String s = Long.toString(Math.abs(v));
        StringBuilder b = new StringBuilder();
        int c = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            if (c > 0 && c % 3 == 0) b.append(' ');
            b.append(s.charAt(i));
            c++;
        }
        return (v < 0 ? "-" : "") + b.reverse().toString();
    }
}
