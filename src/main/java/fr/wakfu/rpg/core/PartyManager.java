package fr.wakfu.rpg.core;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * v0.19 : groupes de joueurs (4 max).
 * /groupe invite <joueur> — /groupe accepter — /groupe quitter — /groupe exclure
 * /groupe info — /groupe rejoindre <cle> — /g <message> (chat de groupe).
 * Quand un membre engage un combat PvE, les autres sont invites a le rejoindre
 * (voir CombatManager.inviteParty / joinCombat).
 */
public final class PartyManager {

    public static final int MAX = 4;

    public static class Party {
        public UUID leader;
        public final List<UUID> members = new ArrayList<>();
    }

    private static final Map<UUID, Party> BY_MEMBER = new HashMap<>();
    /** joueur invite -> inviteur. */
    private static final Map<UUID, UUID> PENDING = new HashMap<>();

    private PartyManager() {
    }

    public static Party partyOf(UUID u) {
        return BY_MEMBER.get(u);
    }

    // ------------------------------------------------------------ v0.25 : accès menu social
    /** UUID de l'inviteur en attente pour ce joueur, ou null. */
    public static UUID pendingFrom(UUID u) {
        return PENDING.get(u);
    }

    public static List<UUID> partyMembers(UUID u) {
        Party p = BY_MEMBER.get(u);
        return p == null ? new ArrayList<>() : new ArrayList<>(p.members);
    }

    public static UUID partyLeader(UUID u) {
        Party p = BY_MEMBER.get(u);
        return p == null ? null : p.leader;
    }

    private static ServerPlayer player(MinecraftServer server, UUID u) {
        return server.getPlayerList().getPlayer(u);
    }

    private static void tell(ServerPlayer p, String text) {
        if (p != null) p.sendSystemMessage(Component.literal(text));
    }

    // ------------------------------------------------------------ invitation
    public static void invite(ServerPlayer src, String targetName) {
        MinecraftServer server = src.getServer();
        ServerPlayer target = server.getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            tell(src, "§cJoueur introuvable : " + targetName);
            return;
        }
        if (target == src) {
            tell(src, "§cTu ne peux pas t'inviter toi-même.");
            return;
        }
        Party ps = BY_MEMBER.get(src.getUUID());
        if (ps != null && !ps.leader.equals(src.getUUID())) {
            tell(src, "§cSeul le chef de groupe peut inviter.");
            return;
        }
        if (ps != null && ps.members.size() >= MAX) {
            tell(src, "§cGroupe plein (" + MAX + " maximum).");
            return;
        }
        if (BY_MEMBER.containsKey(target.getUUID())) {
            tell(src, "§c" + target.getName().getString() + " est déjà dans un groupe.");
            return;
        }
        PENDING.put(target.getUUID(), src.getUUID());
        tell(src, "§aInvitation envoyée à " + target.getName().getString() + ".");
        target.sendSystemMessage(Component.literal("§6⚔ " + src.getName().getString() + " t'invite dans son groupe. ")
                .append(Component.literal("§a[Accepter]")
                        .withStyle(st -> st.withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/groupe accepter"))))
                .append(Component.literal(" §8(" + "/groupe refuser pour décliner)")));
    }

    public static void accept(ServerPlayer target) {
        UUID inviter = PENDING.remove(target.getUUID());
        if (inviter == null) {
            tell(target, "§cTu n'as aucune invitation en attente.");
            return;
        }
        if (BY_MEMBER.containsKey(target.getUUID())) {
            tell(target, "§cTu es déjà dans un groupe.");
            return;
        }
        ServerPlayer src = player(target.getServer(), inviter);
        if (src == null) {
            tell(target, "§cL'inviteur n'est plus en ligne.");
            return;
        }
        Party party = BY_MEMBER.get(inviter);
        if (party == null) {
            party = new Party();
            party.leader = inviter;
            party.members.add(inviter);
            BY_MEMBER.put(inviter, party);
        }
        if (party.members.size() >= MAX) {
            tell(target, "§cLe groupe est déjà plein.");
            return;
        }
        party.members.add(target.getUUID());
        BY_MEMBER.put(target.getUUID(), party);
        broadcastParty(party, "§a✚ " + target.getName().getString() + " rejoint le groupe ("
                + party.members.size() + "/" + MAX + ").");
    }

    public static void decline(ServerPlayer target) {
        UUID inviter = PENDING.remove(target.getUUID());
        if (inviter == null) {
            tell(target, "§cTu n'as aucune invitation en attente.");
            return;
        }
        tell(target, "§7Invitation déclinée.");
        ServerPlayer src = player(target.getServer(), inviter);
        tell(src, "§7" + target.getName().getString() + " a décliné ton invitation.");
    }

    // ------------------------------------------------------------ sortie / exclusion
    public static void leave(ServerPlayer p) {
        Party party = BY_MEMBER.get(p.getUUID());
        if (party == null) {
            tell(p, "§cTu n'es pas dans un groupe.");
            return;
        }
        removeMember(party, p.getUUID());
        tell(p, "§7Tu quittes le groupe.");
        if (!party.members.isEmpty()) {
            broadcastParty(party, "§7" + p.getName().getString() + " quitte le groupe.");
        }
    }

    public static void kick(ServerPlayer leader, String targetName) {
        Party party = BY_MEMBER.get(leader.getUUID());
        if (party == null || !party.leader.equals(leader.getUUID())) {
            tell(leader, "§cRéservé au chef de groupe.");
            return;
        }
        ServerPlayer target = leader.getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null || !party.members.contains(target.getUUID())) {
            tell(leader, "§cCe joueur n'est pas dans ton groupe.");
            return;
        }
        if (target == leader) {
            tell(leader, "§cUtilise /groupe quitter.");
            return;
        }
        removeMember(party, target.getUUID());
        tell(target, "§cTu as été exclu du groupe.");
        broadcastParty(party, "§7" + target.getName().getString() + " a été exclu du groupe.");
    }

    private static void removeMember(Party party, UUID u) {
        party.members.remove(u);
        BY_MEMBER.remove(u);
        if (party.members.isEmpty()) return;
        if (party.leader.equals(u)) {
            party.leader = party.members.get(0);
            broadcastParty(party, "§6Le nouveau chef de groupe est désigné.");
        }
    }

    public static void info(ServerPlayer p) {
        Party party = BY_MEMBER.get(p.getUUID());
        if (party == null) {
            tell(p, "§7Tu n'es pas dans un groupe. §8/groupe invite <joueur>");
            return;
        }
        StringBuilder b = new StringBuilder("§6Groupe (" + party.members.size() + "/" + MAX + ") : ");
        for (UUID u : party.members) {
            ServerPlayer mp2 = player(p.getServer(), u);
            String name = mp2 != null ? mp2.getName().getString() : "?";
            b.append(u.equals(party.leader) ? "§e" : "§7").append(name).append("§r ");
        }
        p.sendSystemMessage(Component.literal(b.toString()));
    }

    // ------------------------------------------------------------ chat de groupe
    public static void chat(ServerPlayer p, String message) {
        Party party = BY_MEMBER.get(p.getUUID());
        if (party == null) {
            tell(p, "§cTu n'es pas dans un groupe.");
            return;
        }
        if (message == null || message.isBlank()) return;
        broadcastParty(party, "§d[G] §f" + p.getName().getString() + "§7 : " + message);
    }

    private static void broadcastParty(Party party, String text) {
        for (UUID u : party.members) {
            // le serveur est recupere via n'importe quel membre en ligne
            ServerPlayer mp2 = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer()
                    .getPlayerList().getPlayer(u);
            if (mp2 != null) mp2.sendSystemMessage(Component.literal(text));
        }
    }

    /** Deco : nettoyage des invitations en attente. */
    public static void onLogout(ServerPlayer p) {
        PENDING.remove(p.getUUID());
        PENDING.values().removeIf(u -> u.equals(p.getUUID()));
        Party party = BY_MEMBER.get(p.getUUID());
        if (party != null) {
            removeMemberSilent(party, p.getUUID());
            if (!party.members.isEmpty()) {
                broadcastParty(party, "§7" + p.getName().getString() + " est déconnecté du groupe.");
            }
        }
    }

    private static void removeMemberSilent(Party party, UUID u) {
        party.members.remove(u);
        BY_MEMBER.remove(u);
        if (party.members.isEmpty()) return;
        if (party.leader.equals(u)) {
            party.leader = party.members.get(0);
        }
    }
}
