package fr.wakfu.rpg.client;

/**
 * v0.19 : invitation de combat de groupe cote client.
 * Une seule invitation active a la fois ; expire apres 30 s, et saute
 * immediatement quand le serveur revoque (le combat a vraiment commence)
 * ou quand le client entre lui-meme en combat.
 */
public final class ClientInvites {

    private static int key = 0;
    private static String leader = "";
    private static int mobs = 0;
    private static long expiresAt = 0;

    private ClientInvites() {
    }

    public static void set(int k, String l, int m) {
        key = k;
        leader = l;
        mobs = m;
        expiresAt = System.currentTimeMillis() + 30_000L;
    }

    public static void clear(int k) {
        if (k == key) expiresAt = 0;
    }

    public static void clear() {
        expiresAt = 0;
    }

    public static boolean isActive() {
        return expiresAt > System.currentTimeMillis();
    }

    public static int key() {
        return key;
    }

    public static String leader() {
        return leader;
    }

    public static int mobs() {
        return mobs;
    }
}
