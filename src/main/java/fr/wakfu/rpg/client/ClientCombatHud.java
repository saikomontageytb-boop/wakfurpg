package fr.wakfu.rpg.client;

import fr.wakfu.rpg.network.CombatStatePacket;

/**
 * Dernier etat de combat recu du serveur + transitions entree/sortie
 * de combat (camera vue de haut, curseur libre).
 */
public class ClientCombatHud {
    private static volatile CombatStatePacket state = CombatStatePacket.empty();
    private static boolean prevInCombat = false;

    public static void set(CombatStatePacket s) {
        state = s;
        if (s.inCombat) fr.wakfu.rpg.client.ClientInvites.clear();   // v0.19
        CombatLerp.onState(s);   // v0.12 : animation des deplacements
        if (s.inCombat && !prevInCombat) {
            CombatView.enterCombat();
        } else if (!s.inCombat && prevInCombat) {
            CombatView.exitCombat();
        }
        prevInCombat = s.inCombat;
    }

    public static CombatStatePacket get() {
        return state;
    }

    /** Retour au menu / nouvelle connexion : remise a zero complete. */
    public static void reset() {
        state = CombatStatePacket.empty();
        prevInCombat = false;
        CombatView.reset();
    }

    private ClientCombatHud() {}
}
