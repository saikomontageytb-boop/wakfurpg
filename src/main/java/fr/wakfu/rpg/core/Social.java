package fr.wakfu.rpg.core;

import fr.wakfu.rpg.network.SocialDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * v0.25 : logique serveur du MENU SOCIAL (écran Social du Grimoire).
 * Construit et pousse SocialDataPacket (joueurs en ligne, amis, groupe,
 * invitation en attente) et traite les actions : ami +/-, invitation de
 * groupe, duel 1v1, accepter / refuser / quitter.
 */
public final class Social {

    private Social() {
    }

    private static int levelOf(ServerPlayer p) {
        PlayerRpgData d = p.getCapability(ModCapabilities.RPG).resolve().orElse(null);
        return d != null ? d.getLevel() : 1;
    }

    /** Envoie l'état social complet au joueur. */
    public static void push(ServerPlayer p) {
        if (p == null) return;
        var server = p.getServer();

        List<SocialDataPacket.Plr> online = new ArrayList<>();
        for (ServerPlayer o : server.getPlayerList().getPlayers()) {
            if (o != p) online.add(new SocialDataPacket.Plr(o.getUUID(), o.getName().getString(), levelOf(o), true));
        }

        List<SocialDataPacket.Plr> friends = new ArrayList<>();
        for (UUID f : Friends.friendsOf(p.getUUID())) {
            ServerPlayer fo = server.getPlayerList().getPlayer(f);
            if (fo != null) {
                friends.add(new SocialDataPacket.Plr(f, fo.getName().getString(), levelOf(fo), true));
            } else {
                friends.add(new SocialDataPacket.Plr(f, Friends.nameOf(f), 0, false));
            }
        }

        UUID leader = PartyManager.partyLeader(p.getUUID());
        List<SocialDataPacket.Plr> members = new ArrayList<>();
        for (UUID u : PartyManager.partyMembers(p.getUUID())) {
            ServerPlayer mo = server.getPlayerList().getPlayer(u);
            String name = mo != null ? mo.getName().getString() : Friends.nameOf(u);
            members.add(new SocialDataPacket.Plr(u, name, mo != null ? levelOf(mo) : 0, mo != null));
        }

        UUID pend = PartyManager.pendingFrom(p.getUUID());
        String pendingFrom = "";
        if (pend != null) {
            ServerPlayer po = server.getPlayerList().getPlayer(pend);
            pendingFrom = po != null ? po.getName().getString() : Friends.nameOf(pend);
        }

        fr.wakfu.rpg.network.Network.sendToPlayer(p,
                new SocialDataPacket(online, friends, leader, members, pendingFrom));
    }

    private static void say(ServerPlayer p, String text) {
        if (text != null && !text.isEmpty()) p.displayClientMessage(Component.literal(text), true);
    }

    /** Actions : 0 ami+, 1 ami-, 2 invite groupe, 3 duel, 4 accepter, 5 refuser, 6 quitter. */
    public static void handle(ServerPlayer src, int action, String target) {
        switch (action) {
            case 0 -> {
                String err = Friends.add(src, target);
                say(src, err != null ? err : "§a✚ " + target + " ajouté à tes amis.");
                push(src);
                pushIfOnline(src, target);
            }
            case 1 -> {
                String err = Friends.remove(src, target);
                say(src, err != null ? err : "§7" + target + " retiré de tes amis.");
                push(src);
                pushIfOnline(src, target);
            }
            case 2 -> {
                PartyManager.invite(src, target);
                push(src);
                ServerPlayer t = src.getServer().getPlayerList().getPlayerByName(target);
                if (t != null) push(t);
            }
            case 3 -> {
                ServerPlayer t = src.getServer().getPlayerList().getPlayerByName(target);
                if (t == null) {
                    say(src, "§c" + target + " n'est plus en ligne.");
                } else {
                    fr.wakfu.rpg.core.combat.CombatManager.requestDuel(src, t);
                }
                push(src);
            }
            case 4 -> {
                PartyManager.accept(src);
                pushAll(src);
            }
            case 5 -> {
                UUID from = PartyManager.pendingFrom(src.getUUID());
                PartyManager.decline(src);
                push(src);
                if (from != null) {
                    ServerPlayer f = src.getServer().getPlayerList().getPlayer(from);
                    if (f != null) push(f);
                }
            }
            case 6 -> {
                List<UUID> before = PartyManager.partyMembers(src.getUUID());
                PartyManager.leave(src);
                push(src);
                for (UUID u : before) {
                    ServerPlayer m = src.getServer().getPlayerList().getPlayer(u);
                    if (m != null) push(m);
                }
            }
            default -> { }
        }
    }

    private static void pushIfOnline(ServerPlayer src, String name) {
        ServerPlayer t = src.getServer().getPlayerList().getPlayerByName(name);
        if (t != null) push(t);
    }

    /** Pousse l'état du groupe à tous ses membres (après acceptation). */
    private static void pushAll(ServerPlayer src) {
        push(src);
        for (UUID u : PartyManager.partyMembers(src.getUUID())) {
            ServerPlayer m = src.getServer().getPlayerList().getPlayer(u);
            if (m != null && m != src) push(m);
        }
    }
}
