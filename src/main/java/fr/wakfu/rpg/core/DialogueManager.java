package fr.wakfu.rpg.core;

import fr.wakfu.rpg.entity.ClassMasterNpc;
import fr.wakfu.rpg.network.DialoguePacket;
import fr.wakfu.rpg.network.Network;
import fr.wakfu.rpg.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Logique serveur des dialogues. Le systeme est generique : chaque type de PNJ
 * pourra brancher ses propres noeuds (maitre des classes aujourd'hui,
 * PNJ monstres pour le combat tour par tour ensuite).
 */
public class DialogueManager {

    public static final String ROOT = "root";
    public static final String DONE = "done";

    public static void openRoot(ClassMasterNpc npc, ServerPlayer player) {
        player.getCapability(ModCapabilities.RPG).ifPresent(data -> {
            String name = npc.getName().getString();
            if (!data.hasClass()) {
                send(player, npc, new DialogueNode(name,
                        "§6Bienvenue, voyageur.§r Le Wakfu coule en toi, mais ton chemin n'est pas encore trace.\nEn ces terres, chacun doit choisir sa voie : le guerrier qui fend, l'archer qui perce, ou l'invocateur qui commande aux poupees.\n§7Qui veux-tu devenir ?",
                        List.of("§cIop§r — la force et la melee",
                                "§aCrâ§r — l'arc et la precision",
                                "§2Sadida§r — la nature et les poupées",
                                "§7Partir")));
            } else {
                String cur = data.getRpgClass().getId();
                send(player, npc, new DialogueNode(name,
                        "Que le Wakfu te guide, " + data.getRpgClass().getName().getString() + " de niveau " + data.getLevel() + ".\n"
                                + "§8La voie peut changer : niveau, XP et bonus sont conservés, mais tes sorts seront réinitialisés.\n§7Embrasser une autre voie ?",
                        List.of(
                                "§cDevenir Iop§r — la force et la mêlée" + (cur.equals("iop") ? " §8(actuelle)" : ""),
                                "§aDevenir Crâ§r — l'arc et la précision" + (cur.equals("cra") ? " §8(actuelle)" : ""),
                                "§2Devenir Sadida§r — la nature et les poupées" + (cur.equals("sadida") ? " §8(actuelle)" : ""),
                                "§7Partir")));
            }
        });
    }

    public static void openDone(ClassMasterNpc npc, ServerPlayer player) {
        player.getCapability(ModCapabilities.RPG).ifPresent(data -> {
            String name = npc.getName().getString();
            send(player, npc, new DialogueNode(name,
                    "§6Te voila " + (data.hasClass() ? data.getRpgClass().getName().getString() : "") + " !§r\nLes monstres de la region n'ont qu'a bien se tenir... Bientot, je t'apprendrai a les defier en duel, dans les regles de l'art du tour par tour.",
                    List.of("§7Merci maître")));
        });
    }

    /** Traitement d'un choix recu du client pour le maitre des classes. */
    public static void handleChoice(ClassMasterNpc npc, ServerPlayer player, int choice) {
        player.getCapability(ModCapabilities.RPG).ifPresent(data -> {
            if (!data.hasClass()) {
                RpgClass want = choice == 0 ? RpgClass.IOP : (choice == 1 ? RpgClass.CRA : (choice == 2 ? RpgClass.SADIDA : null));
                if (want != null) {
                    data.chooseClass(want);
                    player.sendSystemMessage(Component.translatable("wakfurpg.class_chosen", want.getName()));
                    fr.wakfu.rpg.events.CommonEvents.sync(player);
                    openDone(npc, player);
                }
                // choix 3 = partir : rien a faire, le client ferme
            } else {
                // v0.12 : changement de classe (niveau conserve, sorts reset)
                RpgClass want = choice == 0 ? RpgClass.IOP
                        : (choice == 1 ? RpgClass.CRA : (choice == 2 ? RpgClass.SADIDA : null));
                if (want != null) {
                    if (data.getRpgClass() == want) {
                        player.sendSystemMessage(Component.literal("§7Tu es déjà " + want.getName().getString() + "."));
                    } else {
                        data.changeClass(want);
                        player.sendSystemMessage(Component.literal(
                                "§6Tu embrasses la voie du " + want.getName().getString()
                                        + " §6! Sorts réinitialisés, niveau " + data.getLevel() + " conservé."));
                        fr.wakfu.rpg.events.CommonEvents.sync(player);
                        openDone(npc, player);
                    }
                }
            }
        });
    }

    public static void send(ServerPlayer player, ClassMasterNpc npc, DialogueNode node) {
        Network.sendToPlayer(player, new DialoguePacket(npc.getId(), node));
    }

    /**
     * Garantit la presence du Maitre des Classes pres du spawn du monde.
     * Appele au demarrage du serveur et au login d'un joueur sans classe.
     */
    public static void ensureClassMasterAtSpawn(ServerLevel level, boolean forceLoad) {
        BlockPos spawn = level.getSharedSpawnPos();
        if (forceLoad) {
            // charger les chunks autour du spawn pour voir les PNJ existants
            for (int cx = (spawn.getX() - 32) >> 4; cx <= (spawn.getX() + 32) >> 4; cx++) {
                for (int cz = (spawn.getZ() - 32) >> 4; cz <= (spawn.getZ() + 32) >> 4; cz++) {
                    level.getChunk(cx, cz);
                }
            }
        }
        AABB zone = new AABB(spawn).inflate(48);
        List<ClassMasterNpc> found = level.getEntitiesOfClass(ClassMasterNpc.class, zone);
        found.removeIf(n -> n.getClass() != ClassMasterNpc.class); // le forgeron est une sous-classe
        boolean manual = found.removeIf(n -> n.getTags().contains("manual"));
        // v0.23 : au demarrage, repositionnement complet (niveau de la rue,
        // jamais sur un toit, emplacements separes) ; sinon simple dedoublonnage
        if (forceLoad) {
            for (var n : found) n.discard();
            if (manual) return;   // v0.25 : PNJ place a l'oeuf -> pas de doublon auto
        } else {
            for (int i = 1; i < found.size(); i++) found.get(i).discard();
            return; // creation uniquement au demarrage (chunks charges)
        }
        BlockPos p = fr.wakfu.rpg.core.NpcSpots.streetSpot(level, spawn, "maitre");
        ClassMasterNpc npc = new ClassMasterNpc(ModEntities.CLASS_MASTER.get(), level);
        npc.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
        npc.setCustomName(Component.literal("Ma\u00eetre des Classes"));
        npc.setCustomNameVisible(true);
        level.addFreshEntity(npc);
    }

    // ------------------------------------------------------------ heraut (quetes)
    public static void openHerald(ClassMasterNpc npc, ServerPlayer player) {
        player.getCapability(ModCapabilities.RPG).ifPresent(data -> {
            String name = npc.getName().getString();
            var q = Quests.current(data);
            if (q == null) {
                send(player, npc, new DialogueNode(name,
                        "§6Tu as accompli toutes mes quêtes.§r Le Wakfu chante ton nom, héros.",
                        List.of("§7Partir")));
                return;
            }
            boolean done = Quests.isComplete(data);
            String txt = (done ? "§aObjectif atteint !§r " : "")
                    + "§6Quête : " + q.title + "§r\n" + q.desc
                    + "\n§8Progression : §e" + Math.min(data.getQuestProgress(), q.target) + "/" + q.target
                    + "\n§8Récompense : §e" + q.xp + " XP"
                    + (q.ingots > 0 ? " + " + q.ingots * 10 + " $" : "")
                    + (q.rewardArmor != null ? " + récompense spéciale" : "");
            send(player, npc, new DialogueNode(name, txt, done
                    ? List.of("§aRendre la quête", "§7Partir")
                    : List.of("§7Partir")));
        });
    }

    public static void handleHeraldChoice(ClassMasterNpc npc, ServerPlayer player, int choice) {
        if (choice != 0) return;
        player.getCapability(ModCapabilities.RPG).ifPresent(data -> {
            if (Quests.isComplete(data)) {
                Quests.claim(player);
                openHerald(npc, player);
            }
        });
    }

    /** Garantit la presence du Héraut pres du spawn (deduplique). */
    // ------------------------------------------------------------ v0.18 : marchand
    /** Prix du marche : varie deterministe chaque jour de jeu (+/- 15%). */
    private static int marketPrice(ServerPlayer player, int base, int salt) {
        long day = player.serverLevel().getDayTime() / 24000L;
        double f = ((day * 31 + salt * 17L) % 100) / 100.0;   // 0..1
        return Math.max(1, (int) Math.round(base * (0.85 + 0.30 * f)));
    }

    public static void ensureMerchantAtSpawn(ServerLevel level, boolean forceLoad) {
        BlockPos spawn = level.getSharedSpawnPos();
        if (forceLoad) {
            for (int cx = (spawn.getX() - 32) >> 4; cx <= (spawn.getX() + 32) >> 4; cx++) {
                for (int cz = (spawn.getZ() - 32) >> 4; cz <= (spawn.getZ() + 32) >> 4; cz++) {
                    level.getChunk(cx, cz);
                }
            }
        }
        AABB zone = new AABB(spawn).inflate(48);
        List<fr.wakfu.rpg.entity.MerchantNpc> found =
                level.getEntitiesOfClass(fr.wakfu.rpg.entity.MerchantNpc.class, zone);
        boolean manual = found.removeIf(n -> n.getTags().contains("manual"));
        if (forceLoad) {
            for (var n : found) n.discard();
            if (manual) return;   // v0.25 : PNJ place a l'oeuf -> pas de doublon auto
        } else {
            for (int i = 1; i < found.size(); i++) found.get(i).discard();
            return;
        }
        BlockPos p = fr.wakfu.rpg.core.NpcSpots.streetSpot(level, spawn, "marchand");
        fr.wakfu.rpg.entity.MerchantNpc npc =
                new fr.wakfu.rpg.entity.MerchantNpc(ModEntities.MERCHANT.get(), level);
        npc.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 90, 0);
        npc.setCustomName(Component.literal("Marchand"));
        npc.setCustomNameVisible(true);
        level.addFreshEntity(npc);
    }

    /** v0.25 : Hôtel des Ventes — marché entre joueurs + rachat serveur. */
    public static void ensureMarketAtSpawn(ServerLevel level, boolean forceLoad) {
        BlockPos spawn = level.getSharedSpawnPos();
        if (forceLoad) {
            for (int cx = (spawn.getX() - 32) >> 4; cx <= (spawn.getX() + 32) >> 4; cx++) {
                for (int cz = (spawn.getZ() - 32) >> 4; cz <= (spawn.getZ() + 32) >> 4; cz++) {
                    level.getChunk(cx, cz);
                }
            }
        }
        AABB zone = new AABB(spawn).inflate(48);
        List<fr.wakfu.rpg.entity.MarketNpc> found =
                level.getEntitiesOfClass(fr.wakfu.rpg.entity.MarketNpc.class, zone);
        boolean manual = found.removeIf(n -> n.getTags().contains("manual"));
        if (forceLoad) {
            for (var n : found) n.discard();
            if (manual) return;
        } else {
            for (int i = 1; i < found.size(); i++) found.get(i).discard();
            return;
        }
        BlockPos p = fr.wakfu.rpg.core.NpcSpots.streetSpot(level, spawn, "marche");
        fr.wakfu.rpg.entity.MarketNpc npc =
                new fr.wakfu.rpg.entity.MarketNpc(ModEntities.MARKET.get(), level);
        npc.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
        npc.setCustomName(Component.literal("Hôtel des Ventes"));
        npc.setCustomNameVisible(true);
        level.addFreshEntity(npc);
    }

    public static void openMerchant(ClassMasterNpc npc, ServerPlayer player, String result) {
        String name = npc.getName().getString();
        StringBuilder txt = new StringBuilder();
        if (result != null) txt.append(result).append("\n");
        txt.append("§6Bienvenue sur mon étal, voyageur !§r\n")
                .append("Mes prix suivent le marché du Wakfu : ils changent chaque jour.\n")
                .append("§8Ta bourse : §e$ ").append(countIngots(player));
        int pApple = marketPrice(player, 80, 1);
        int pRare = marketPrice(player, 140, 2);
        int pWeapon = marketPrice(player, 120, 3);
        int pEpic = marketPrice(player, 280, 4);
        int vArmorC = marketPrice(player, 20, 5);
        int vArmorR = marketPrice(player, 60, 6);
        int vArmorE = marketPrice(player, 140, 7);
        int vWepC = marketPrice(player, 20, 8);
        int vWepR = marketPrice(player, 50, 9);
        int vWepE = marketPrice(player, 120, 10);
        send(player, npc, new DialogueNode(name, txt.toString(), List.of(
                "§6Acheter§r Pomme dorée §8(" + pApple + " $§8)",
                "§6Acheter§r Armure rare §8(" + pRare + " $§8)",
                "§6Acheter§r Arme runique §8(" + pWeapon + " $§8)",
                "§6Acheter§r Armure épique §8(" + pEpic + " $§8)",
                "§eVendre§r armure commune §8(+" + vArmorC + " $§8)",
                "§eVendre§r armure rare §8(+" + vArmorR + " $§8)",
                "§eVendre§r armure épique §8(+" + vArmorE + " $§8)",
                "§eVendre§r arme commune §8(+" + vWepC + " $§8)",
                "§eVendre§r arme rare §8(+" + vWepR + " $§8)",
                "§eVendre§r arme épique §8(+" + vWepE + " $§8)",
                "§7Partir")));
    }

    public static void handleMerchantChoice(ClassMasterNpc npc, ServerPlayer player, int choice) {
        if (choice == 10 || choice < 0) return;
        if (choice < 4) {
            int salt = choice + 1;
            int[] bases = {80, 140, 120, 280};   // v0.25 : prix en $
            int price = marketPrice(player, bases[choice], salt);
            if (!consumeIngots(player, price)) {
                openMerchant(npc, player, "§cIl te faut " + price + " $ pour cet achat.");
                return;
            }
            ItemStack bought;
            switch (choice) {
                case 0 -> bought = new ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE, 1);
                case 1 -> bought = fr.wakfu.rpg.item.WakfuArmorItem.makePiece(
                        fr.wakfu.rpg.item.WakfuArmorItem.SLOTS[player.getRandom().nextInt(4)],
                        "rare", 1, net.minecraft.util.RandomSource.create());
                case 2 -> bought = fr.wakfu.rpg.item.WakfuWeaponItem.randomWeapon(net.minecraft.util.RandomSource.create());
                default -> bought = fr.wakfu.rpg.item.WakfuArmorItem.makePiece(
                        fr.wakfu.rpg.item.WakfuArmorItem.SLOTS[player.getRandom().nextInt(4)],
                        "epique", 1, net.minecraft.util.RandomSource.create());
            }
            player.getInventory().add(bought);
            fr.wakfu.rpg.core.Codex.item(player, bought);
            openMerchant(npc, player, "§aAcheté : " + bought.getHoverName().getString() + " §apour " + price + " $.");
            return;
        }
        // vente : cherche le premier objet correspondant dans l'inventaire
        String wantRarity = switch (choice) {
            case 4, 7 -> "commune";
            case 5, 8 -> "rare";
            default -> "epique";
        };
        boolean wantWeapon = choice >= 7;
        int[] salts = {0, 0, 0, 0, 5, 6, 7, 8, 9, 10};
        int[] bases = {0, 0, 0, 0, 20, 60, 140, 20, 50, 120};   // v0.25 : gains en $
        int gain = marketPrice(player, bases[choice], salts[choice]);
        var items = player.getInventory().items;
        for (int i = 0; i < items.size(); i++) {
            ItemStack s = items.get(i);
            boolean match = wantWeapon
                    ? (fr.wakfu.rpg.item.WakfuWeaponItem.isWeapon(s)
                        && fr.wakfu.rpg.item.WakfuWeaponItem.rarityOf(s).equals(wantRarity))
                    : (s.getItem() instanceof fr.wakfu.rpg.item.WakfuArmorItem
                        && fr.wakfu.rpg.item.WakfuArmorItem.rarityOf(s).equals(wantRarity));
            if (match) {
                String soldName = s.getHoverName().getString();
                items.set(i, ItemStack.EMPTY);
                fr.wakfu.rpg.core.Money.add(player.getUUID(), gain);
                fr.wakfu.rpg.core.Money.sync(player);
                openMerchant(npc, player, "§eVendu : " + soldName + " §epour " + gain + " $.");
                return;
            }
        }
        openMerchant(npc, player, "§cTu n'as aucun équipement " + wantRarity + " "
                + (wantWeapon ? "(arme)" : "(armure)") + " à vendre.");
    }

    public static void ensureHeraldAtSpawn(ServerLevel level, boolean forceLoad) {
        BlockPos spawn = level.getSharedSpawnPos();
        if (forceLoad) {
            for (int cx = (spawn.getX() - 32) >> 4; cx <= (spawn.getX() + 32) >> 4; cx++) {
                for (int cz = (spawn.getZ() - 32) >> 4; cz <= (spawn.getZ() + 32) >> 4; cz++) {
                    level.getChunk(cx, cz);
                }
            }
        }
        AABB zone = new AABB(spawn).inflate(48);
        List<fr.wakfu.rpg.entity.HeraldNpc> found =
                level.getEntitiesOfClass(fr.wakfu.rpg.entity.HeraldNpc.class, zone);
        boolean manual = found.removeIf(n -> n.getTags().contains("manual"));
        if (forceLoad) {
            for (var n : found) n.discard();
            if (manual) return;   // v0.25 : PNJ place a l'oeuf -> pas de doublon auto
        } else {
            for (int i = 1; i < found.size(); i++) found.get(i).discard();
            return;
        }
        BlockPos p = fr.wakfu.rpg.core.NpcSpots.streetSpot(level, spawn, "heraut");
        fr.wakfu.rpg.entity.HeraldNpc npc =
                new fr.wakfu.rpg.entity.HeraldNpc(ModEntities.HERALD.get(), level);
        npc.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
        npc.setCustomName(Component.literal("H\u00e9raut des Qu\u00eates"));
        npc.setCustomNameVisible(true);
        level.addFreshEntity(npc);
    }

    // ------------------------------------------------------------ forgeron (tranche 2)
    /** v0.25 : « lingots » = argent ($) dans la bourse du joueur. */
    public static int countIngots(ServerPlayer p) {
        return (int) Math.min(Integer.MAX_VALUE, fr.wakfu.rpg.core.Money.balance(p.getUUID()));
    }

    private static boolean consumeIngots(ServerPlayer p, int n) {
        boolean ok = fr.wakfu.rpg.core.Money.take(p.getUUID(), n);
        if (ok) fr.wakfu.rpg.core.Money.sync(p);
        return ok;
    }

    public static void openBlacksmith(ClassMasterNpc npc, ServerPlayer player, String result) {
        String name = npc.getName().getString();
        StringBuilder txt = new StringBuilder();
        if (result != null) txt.append(result).append("\n");
        txt.append("§6Entre, entre !§r Le feu est chaud et l'enclume chante.\n")
                .append("Avec ton argent, je forge des pieces benies du Wakfu — aux stats fixes, comme annonce dans ton bestiaire.\n")
                .append("§8Ta bourse : §e$ ").append(countIngots(player));
        send(player, npc, new DialogueNode(name, txt.toString(), List.of(
                "§6Heaume§8 (30 $)",
                "§6Cuirasse§8 (50 $)",
                "§6Jambières§8 (40 $)",
                "§6Bottes§8 (40 $)",
                "§5★ Fusion — Atelier sombre",
                "§7Partir")));
    }

    public static void handleBlacksmithChoice(ClassMasterNpc npc, ServerPlayer player, int choice) {
        // v0.16 : 0-3 = forger armure (stats fixes), 4 = fusion (ecran client Atelier sombre), 5 = partir
        if (choice == 4) return;   // la fusion est geree cote client (ForgeScreen + FusionPacket)
        if (choice >= 4 || choice < 0) return;
        String[] slots = {"head", "chest", "legs", "feet"};
        int[] costs = {30, 50, 40, 40};   // v0.25 : en $
        if (!consumeIngots(player, costs[choice])) {
            openBlacksmith(npc, player, "§cIl te faut " + costs[choice] + " $ pour ça. Les monstres donnent de l'argent !");
            return;
        }
        ItemStack piece = fr.wakfu.rpg.item.WakfuArmorItem.makePiece(slots[choice], rollRarity(), 1, net.minecraft.util.RandomSource.create());
        player.getInventory().add(piece);
        fr.wakfu.rpg.core.Codex.item(player, piece);
        Quests.progress(player, "forge", 1);
        openBlacksmith(npc, player, "§aForge réussie : " + piece.getHoverName().getString() + " !");
    }

    private static String rollRarity() {
        int r = net.minecraft.util.RandomSource.create().nextInt(100);
        return r < 60 ? "commune" : (r < 90 ? "rare" : "epique");
    }

    /** Garantit la presence du Forgeron pres du spawn (deduplique). */
    public static void ensureBlacksmithAtSpawn(ServerLevel level, boolean forceLoad) {
        BlockPos spawn = level.getSharedSpawnPos();
        if (forceLoad) {
            for (int cx = (spawn.getX() - 32) >> 4; cx <= (spawn.getX() + 32) >> 4; cx++) {
                for (int cz = (spawn.getZ() - 32) >> 4; cz <= (spawn.getZ() + 32) >> 4; cz++) {
                    level.getChunk(cx, cz);
                }
            }
        }
        AABB zone = new AABB(spawn).inflate(48);
        List<fr.wakfu.rpg.entity.BlacksmithNpc> found =
                level.getEntitiesOfClass(fr.wakfu.rpg.entity.BlacksmithNpc.class, zone);
        boolean manual = found.removeIf(n -> n.getTags().contains("manual"));
        if (forceLoad) {
            for (var n : found) n.discard();
            if (manual) return;   // v0.25 : PNJ place a l'oeuf -> pas de doublon auto
        } else {
            for (int i = 1; i < found.size(); i++) found.get(i).discard();
            return;
        }
        BlockPos p = fr.wakfu.rpg.core.NpcSpots.streetSpot(level, spawn, "forgeron");
        fr.wakfu.rpg.entity.BlacksmithNpc npc =
                new fr.wakfu.rpg.entity.BlacksmithNpc(ModEntities.BLACKSMITH.get(), level);
        npc.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
        npc.setCustomName(Component.literal("Forgeron Sombre"));
        npc.setCustomNameVisible(true);
        level.addFreshEntity(npc);
    }

    /** Petit rappel tutoriel pour un joueur sans classe. */
    public static void tutorialHint(ServerPlayer player) {
        player.getCapability(ModCapabilities.RPG).ifPresent(data -> {
            if (!data.hasClass()) {
                player.displayClientMessage(Component.translatable("wakfurpg.tutorial_hint"), true);
            }
        });
    }
}
