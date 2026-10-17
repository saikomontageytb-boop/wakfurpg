package fr.wakfu.rpg.client;

import fr.wakfu.rpg.core.Market;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.25 : annonces de l'Hôtel des Ventes reçues du serveur (MarketDataPacket),
 * lues par MarketScreen (rafraîchies en direct sans rouvrir l'écran).
 */
public final class ClientMarket {

    public static List<Market.Listing> listings = new ArrayList<>();

    private ClientMarket() {
    }

    public static void set(List<Market.Listing> l) {
        listings = l == null ? new ArrayList<>() : l;
    }
}
