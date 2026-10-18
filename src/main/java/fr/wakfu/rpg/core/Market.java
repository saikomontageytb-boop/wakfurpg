package fr.wakfu.rpg.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * v0.25 : HÔTEL DES VENTES — marché entre joueurs + rachat par le serveur.
 * - "Vendre au serveur" : prix TRÈS BAS, immédiat, l'objet disparaît.
 * - "Mettre en vente" : annonce publique à ton prix ; n'importe quel joueur
 *   peut l'acheter avec son argent ($) ; le vendeur est crédité même déconnecté.
 * Annonces persistantes (wakfurpg_market.dat).
 */
public final class Market {

    public record Listing(int id, UUID seller, String sellerName, ItemStack stack, int price) {}

    private static final List<Listing> LISTINGS = new ArrayList<>();
    private static int nextId = 1;
    private static File file;

    private Market() {
    }

    // ------------------------------------------------------------ persistance
    public static void load(MinecraftServer server) {
        try {
            file = new File(server.getWorldPath(LevelResource.ROOT).toFile(), "wakfurpg_market.dat");
            if (file.exists()) {
                CompoundTag tag;
                try (FileInputStream in = new FileInputStream(file)) {
                    tag = NbtIo.readCompressed(in);
                }
                nextId = Math.max(1, tag.getInt("next"));
                ListTag list = tag.getList("list", 10);
                for (int i = 0; i < list.size(); i++) {
                    CompoundTag c = list.getCompound(i);
                    LISTINGS.add(new Listing(
                            c.getInt("id"),
                            UUID.fromString(c.getString("seller")),
                            c.getString("sellerName"),
                            ItemStack.of(c.getCompound("item")),
                            Math.max(1, c.getInt("price"))));
                }
            }
        } catch (Exception e) {
            System.err.println("[wakfurpg] Market.load : " + e);
        }
    }

    public static void save() {
        if (file == null) return;
        try {
            ListTag list = new ListTag();
            for (Listing l : LISTINGS) {
                CompoundTag c = new CompoundTag();
                c.putInt("id", l.id());
                c.putString("seller", l.seller().toString());
                c.putString("sellerName", l.sellerName());
                c.put("item", l.stack().save(new CompoundTag()));
                c.putInt("price", l.price());
                list.add(c);
            }
            CompoundTag root = new CompoundTag();
            root.putInt("next", nextId);
            root.put("list", list);
            NbtIo.writeCompressed(root, file);
        } catch (Exception e) {
            System.err.println("[wakfurpg] Market.save : " + e);
        }
    }

    // ------------------------------------------------------------ accès
    public static List<Listing> listings() {
        return new ArrayList<>(LISTINGS);
    }

    /** Envoie les annonces et (optionnel) ouvre l'écran du marché côté client. */
    public static void sendData(ServerPlayer p, boolean open) {
        fr.wakfu.rpg.network.Network.sendToPlayer(p,
                new fr.wakfu.rpg.network.MarketDataPacket(listings(), open));
    }

    public static void open(ServerPlayer p) {
        Money.sync(p);
        sendData(p, true);
    }

    // ------------------------------------------------------------ prix serveur (très bas)
    /** Prix de rachat par le serveur, À L'UNITÉ. 0 = invendable. */
    public static int serverPrice(ItemStack s) {
        if (s == null || s.isEmpty()) return 0;
        var it = s.getItem();
        if (it instanceof fr.wakfu.rpg.item.SpellItem) return 0;
        if (it instanceof fr.wakfu.rpg.item.CombatUtilityItem) return 0;
        if (it == fr.wakfu.rpg.registry.ModItems.WAKFU_GRIMOIRE.get()) return 0;
        if (it instanceof fr.wakfu.rpg.item.WakfuArmorItem) {
            String r = fr.wakfu.rpg.item.WakfuArmorItem.rarityOf(s);
            boolean set = !fr.wakfu.rpg.item.WakfuArmorItem.setOf(s).isEmpty();
            int base = switch (r) {
                case "epique" -> 10;
                case "rare" -> 5;
                default -> 2;
            };
            return set ? base + 1 : base;
        }
        if (fr.wakfu.rpg.item.WakfuWeaponItem.isWeapon(s)) {
            return switch (fr.wakfu.rpg.item.WakfuWeaponItem.rarityOf(s)) {
                case "epique" -> 9;
                case "rare" -> 4;
                default -> 2;
            };
        }
        if (it == fr.wakfu.rpg.registry.ModItems.WAKFU_TROPHY.get()) return 1;
        if (it == fr.wakfu.rpg.registry.ModItems.WAKFU_INGOT.get()) return (int) Money.INGOT_VALUE / 2;
        if (it == fr.wakfu.rpg.registry.ModItems.WAKFU_GEAR.get()) return 2;
        if (it == Items.GOLDEN_APPLE) return 3;
        return 1;   // ressources diverses : 1 $ l'unité
    }

    private static void give(ServerPlayer p, ItemStack s) {
        if (!p.getInventory().add(s)) p.drop(s, false);
    }

    private static void say(ServerPlayer p, String text) {
        p.displayClientMessage(Component.literal(text), true);
    }

    // ------------------------------------------------------------ actions (op, a, b)
    public static void handle(ServerPlayer sp, int op, int a, int b) {
        switch (op) {
            case 0 -> sellToServer(sp, a);
            case 1 -> listItem(sp, a, b);
            case 2 -> buy(sp, a);
            case 3 -> cancel(sp, a);
            default -> { }
        }
    }

    /** op 0 : vente immédiate au serveur (slot d'inventaire principal 0-35). */
    private static void sellToServer(ServerPlayer sp, int slot) {
        if (slot < 0 || slot > 35) return;
        ItemStack s = sp.getInventory().items.get(slot);
        if (s.isEmpty()) return;
        int unit = serverPrice(s);
        if (unit <= 0) {
            say(sp, "§cCet objet ne se revend pas au serveur.");
            return;
        }
        String name = s.getHoverName().getString();
        int count = s.getCount();
        long total = (long) unit * count;
        sp.getInventory().items.set(slot, ItemStack.EMPTY);
        Money.add(sp.getUUID(), total);
        Money.sync(sp);
        say(sp, "§eVendu au serveur : §f" + name + " §7×" + count + " §e→ +" + total + " pièces");
        sendData(sp, false);
    }

    /** op 1 : mise en vente entre joueurs (slot 0-35, prix b). */
    private static void listItem(ServerPlayer sp, int slot, int price) {
        if (slot < 0 || slot > 35) return;
        ItemStack s = sp.getInventory().items.get(slot);
        if (s.isEmpty()) return;
        if (serverPrice(s) <= 0) {
            say(sp, "§cCet objet ne peut pas être mis en vente.");
            return;
        }
        if (price < 1 || price > 1_000_000) {
            say(sp, "§cPrix invalide (1 pièce minimum).");
            return;
        }
        if (LISTINGS.size() >= 100) {
            say(sp, "§cLe marché est plein (100 annonces). Retire une de tes ventes.");
            return;
        }
        sp.getInventory().items.set(slot, ItemStack.EMPTY);
        LISTINGS.add(new Listing(nextId++, sp.getUUID(), sp.getName().getString(), s.copy(), price));
        save();
        say(sp, "§6Annonce créée : §f" + s.getHoverName().getString() + " §6à §e" + price + " pièces");
        sendData(sp, false);
        broadcastOthers(sp);
    }

    /** op 2 : achat d'une annonce (id). */
    private static void buy(ServerPlayer sp, int id) {
        Listing l = find(id);
        if (l == null) {
            say(sp, "§cCette annonce n'existe plus.");
            sendData(sp, false);
            return;
        }
        if (l.seller().equals(sp.getUUID())) {
            say(sp, "§7C'est ta propre annonce — utilise « Retirer ».");
            return;
        }
        if (Money.balance(sp.getUUID()) < l.price()) {
            say(sp, "§cIl te manque §e" + (l.price() - Money.balance(sp.getUUID())) + " pièces §cpour cet achat.");
            return;
        }
        LISTINGS.remove(l);
        Money.transfer(sp.getUUID(), l.seller(), l.price());
        ItemStack bought = l.stack().copy();
        String name = bought.getHoverName().getString();
        give(sp, bought);
        Codex.item(sp, bought.copy());
        Money.sync(sp);
        say(sp, "§aAcheté : §f" + name + " §a(-" + l.price() + " pièces)");
        save();
        sendData(sp, false);
        // vendeur crédité — même déconnecté (grand livre) ; notifié si en ligne
        ServerPlayer seller = sp.getServer().getPlayerList().getPlayer(l.seller());
        if (seller != null) {
            Money.sync(seller);
            say(seller, "§6Vente conclue : §f" + name + " §6→ " + sp.getName().getString()
                    + " §6(§e+" + l.price() + " pièces§6)");
            sendData(seller, false);
        }
        broadcastOthers(sp);
    }

    /** op 3 : retrait de sa propre annonce (id). */
    private static void cancel(ServerPlayer sp, int id) {
        Listing l = find(id);
        if (l == null || !l.seller().equals(sp.getUUID())) {
            say(sp, "§cAnnonce introuvable.");
            sendData(sp, false);
            return;
        }
        LISTINGS.remove(l);
        give(sp, l.stack().copy());
        save();
        say(sp, "§7Annonce retirée : §f" + l.stack().getHoverName().getString());
        sendData(sp, false);
        broadcastOthers(sp);
    }

    private static Listing find(int id) {
        for (Listing l : LISTINGS) if (l.id() == id) return l;
        return null;
    }

    /** Rafraîchit l'écran des autres joueurs qui ont le marché ouvert. */
    private static void broadcastOthers(ServerPlayer except) {
        for (ServerPlayer p : except.getServer().getPlayerList().getPlayers()) {
            if (p != except) sendData(p, false);
        }
    }
}
