package fr.wakfu.rpg.client;

import fr.wakfu.rpg.network.SocialDataPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * v0.25 : état social reçu du serveur (SocialDataPacket), lu par SocialScreen.
 */
public final class ClientSocial {

    public static List<SocialDataPacket.Plr> online = new ArrayList<>();
    public static List<SocialDataPacket.Plr> friends = new ArrayList<>();
    public static List<SocialDataPacket.Plr> members = new ArrayList<>();
    public static UUID leader = null;
    public static String pendingFrom = "";

    private ClientSocial() {
    }

    public static void set(List<SocialDataPacket.Plr> online, List<SocialDataPacket.Plr> friends,
                           UUID leader, List<SocialDataPacket.Plr> members, String pendingFrom) {
        ClientSocial.online = online;
        ClientSocial.friends = friends;
        ClientSocial.leader = leader;
        ClientSocial.members = members;
        ClientSocial.pendingFrom = pendingFrom == null ? "" : pendingFrom;
    }
}
