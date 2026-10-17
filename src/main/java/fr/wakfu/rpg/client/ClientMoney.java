package fr.wakfu.rpg.client;

/**
 * v0.25 : solde d'argent ($) reçu du serveur (MoneyPacket).
 * Lu par le HUD, le Grimoire, le marché et l'équipement.
 */
public final class ClientMoney {

    private static long value = 0;

    private ClientMoney() {
    }

    public static void set(long v) {
        value = Math.max(0, v);
    }

    public static long get() {
        return value;
    }

    /** "1 234" groupé par 3. */
    public static String fmt(long v) {
        return fr.wakfu.rpg.core.Money.fmt(v);
    }

    /** "$ 1 234" */
    public static String str() {
        return "$ " + fmt(value);
    }
}
