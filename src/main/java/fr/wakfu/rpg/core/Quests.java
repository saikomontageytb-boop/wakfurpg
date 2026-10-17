package fr.wakfu.rpg.core;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Quetes sequentielles du Héraut : une seule quete active a la fois
 * (la premiere non terminee de la liste), progression stockee dans
 * la capacite du joueur, recompense rendue par le Héraut.
 */
public class Quests {

    public static class Quest {
        public final String id;
        public final String title;
        public final String desc;
        public final String kind;   // kill:zombie | kill:skeleton | kill:bandit | forge | duel | level
        public final int target;
        public final int xp;
        public final int ingots;
        public final String rewardArmor; // null | "rare" | "epique" | "weapon"
        public final String icon;        // texture gui (v0.13 : une icone par quete)

        Quest(String id, String title, String desc, String kind, int target, int xp, int ingots, String rewardArmor, String icon) {
            this.id = id;
            this.title = title;
            this.desc = desc;
            this.kind = kind;
            this.target = target;
            this.xp = xp;
            this.ingots = ingots;
            this.rewardArmor = rewardArmor;
            this.icon = icon;
        }
    }

    public static final List<Quest> LIST = List.of(
            new Quest("q1", "Premier sang", "Vaincs 3 zombies putrides en combat singulier.", "kill:zombie", 3, 50, 2, null, "quest_q1"),
            new Quest("q2", "Nettoyeur d'os", "Vaincs 2 squelettes archers.", "kill:skeleton", 2, 80, 3, null, "quest_q2"),
            new Quest("q3", "Apprenti forgeron", "Forge une piece d'armure runique chez le Forgeron.", "forge", 1, 60, 0, "rare", "quest_q3"),
            new Quest("q4", "L'eveil du Wakfu", "Atteins le niveau 5.", "level", 5, 100, 2, null, "quest_q4"),
            new Quest("q5", "Duelliste", "Remporte un duel (/duel) contre un autre joueur.", "duel", 1, 120, 0, "epique", "quest_q5"),
            new Quest("q6", "Chasse aux bandits", "Vaincs 3 bandits maudits.", "kill:bandit", 3, 150, 5, "weapon", "quest_q6"),
            new Quest("q7", "Le Repaire du Chef", "Vaincs le Chef des Bandits dans son fortin, à l'est du village (suis les poteaux-torches).", "kill:chef_bandit", 1, 300, 8, "epique", "quest_q7"),
            new Quest("q8", "Fondeur d'étoiles", "Réalise une fusion ★ dans l'Atelier sombre du forgeron (4 équipements identiques).", "fusion", 1, 200, 4, "rare", "quest_q8"),
            new Quest("q9", "Explorateur", "Explore les 9 zones autour du village (ouvre le Grimoire [B] pour suivre).", "zones", 9, 250, 5, "weapon", "quest_q9"),
            new Quest("q10", "Vétéran", "Atteins le niveau 10.", "level", 10, 300, 6, "rare", "quest_q10"),
            new Quest("q11", "Terreur du Repaire", "Vaincs 3 fois le Chef des Bandits (il réapparaît 20 min après sa mort).", "kill:chef_bandit", 3, 400, 10, "epique", "quest_q11"),
            new Quest("q12", "Collectionneur", "Reçois 15 butins différents consignés au Codex (onglet Butin).", "butin", 15, 500, 12, "epique", "quest_q12")
    );

    public static Quest current(PlayerRpgData data) {
        int i = data.getQuestIndex();
        return i >= 0 && i < LIST.size() ? LIST.get(i) : null;
    }

    /** Progression additive (kill, forge, duel). */
    public static void progress(ServerPlayer player, String kind, int amount) {
        player.getCapability(ModCapabilities.RPG).ifPresent(data -> {
            Quest q = current(data);
            if (q == null || !q.kind.equals(kind)) return;
            int before = data.getQuestProgress();
            if (before >= q.target) return;
            data.addQuestProgress(amount);
            if (data.getQuestProgress() >= q.target) {
                player.displayClientMessage(Component.literal("§a✔ Objectif : §6" + q.title
                        + "§a — rapporte au Héraut."), true);
            } else {
                player.displayClientMessage(Component.literal("§7Quête §6" + q.title + " §7: "
                        + data.getQuestProgress() + "/" + q.target), true);
            }
        });
    }

    /** Progression par maximum (niveau). */
    public static void progressMax(ServerPlayer player, String kind, int value) {
        player.getCapability(ModCapabilities.RPG).ifPresent(data -> {
            Quest q = current(data);
            if (q == null || !q.kind.equals(kind)) return;
            if (data.getQuestProgress() >= q.target) return;
            data.setQuestProgress(Math.max(data.getQuestProgress(), value));
            if (data.getQuestProgress() >= q.target) {
                player.displayClientMessage(Component.literal("§a✔ Objectif : §6" + q.title
                        + "§a — vois le Héraut."), true);
            }
        });
    }

    public static boolean isComplete(PlayerRpgData data) {
        Quest q = current(data);
        return q != null && data.getQuestProgress() >= q.target;
    }

    /** Rend la recompense et passe a la quete suivante. */
    public static void claim(ServerPlayer player) {
        player.getCapability(ModCapabilities.RPG).ifPresent(data -> {
            Quest q = current(data);
            if (q == null || !isComplete(data)) return;
            data.addXp(q.xp);
            if (q.ingots > 0) {
                // v0.25 : argent ($) versé directement dans la bourse (fini les lingots)
                fr.wakfu.rpg.core.Money.add(player.getUUID(), q.ingots * 10L);
                fr.wakfu.rpg.core.Money.sync(player);
            }
            if ("rare".equals(q.rewardArmor) || "epique".equals(q.rewardArmor)) {
                ItemStack piece = fr.wakfu.rpg.item.WakfuArmorItem.makePiece(
                        new String[]{"head", "chest", "feet"}[player.getRandom().nextInt(3)],
                        q.rewardArmor, 1, player.getRandom());
                fr.wakfu.rpg.core.Codex.item(player, piece.copy());
                if (!player.getInventory().add(piece)) player.drop(piece, false);
            }
            if ("weapon".equals(q.rewardArmor)) {
                ItemStack wp = fr.wakfu.rpg.item.WakfuWeaponItem.randomWeapon(player.getRandom());
                fr.wakfu.rpg.core.Codex.item(player, wp.copy());
                if (!player.getInventory().add(wp)) player.drop(wp, false);
            }
            data.nextQuest();
            fr.wakfu.rpg.events.CommonEvents.sync(player);
            Quest next = current(data);
            // v0.24 : une seule ligne en barre d'action (le chat ne recoit plus rien)
            StringBuilder fin = new StringBuilder("§a✔ Quête terminée : §6" + q.title + " §a(+" + q.xp + " XP"
                    + (q.ingots > 0 ? ", +" + q.ingots * 10 + " $" : "")
                    + (q.rewardArmor != null ? ", récompense" : "") + ").");
            if (next != null) fin.append(" §6Nouvelle quête : §f").append(next.title);
            else fin.append(" §6Toutes les quêtes accomplies !");
            player.displayClientMessage(Component.literal(fin.toString()), true);
        });
    }
}
