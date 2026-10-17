package fr.wakfu.rpg.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * v0.23 : suivi d'un lieu/PNJ choisi dans le bestiaire (onglet PNJ).
 * Purement client : traînée de particules au sol vers la cible + distance
 * en mètres dans le HUD. Le suivi s'arrête à l'arrivée (< 3 m).
 */
public class Waypoints {

    private static String name = null;
    private static double wx, wz;
    private static boolean active = false;

    public static void toggle(String n, double x, double z) {
        if (active && n.equals(name)) {
            active = false;
            name = null;
        } else {
            name = n;
            wx = x;
            wz = z;
            active = true;
        }
    }

    public static boolean isActive() { return active; }

    public static String name() { return name; }

    public static int distance(Minecraft mc) {
        if (!active || mc.player == null) return 0;
        double dx = wx - mc.player.getX(), dz = wz - mc.player.getZ();
        return (int) Math.round(Math.sqrt(dx * dx + dz * dz));
    }

    public static String direction(Minecraft mc) {
        if (!active || mc.player == null) return "";
        double dx = wx - mc.player.getX(), dz = wz - mc.player.getZ();
        String ns = dz < 0 ? "N" : "S";
        String ew = dx > 0 ? "E" : "O";
        if (Math.abs(dx) > Math.abs(dz) * 2) return ew;
        if (Math.abs(dz) > Math.abs(dx) * 2) return ns;
        return ns + ew;
    }

    /** Traînée de particules : appelée toutes les ~8 ticks côté client. */
    public static void tickTrail(Minecraft mc) {
        if (!active || mc.player == null || mc.level == null) return;
        if (distance(mc) < 3) {
            active = false;
            return;
        }
        double dx = wx - mc.player.getX(), dz = wz - mc.player.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.01) return;
        double ux = dx / len, uz = dz / len;
        double max = Math.min(len, 48);
        for (double i = 2; i <= max; i += 2.5) {
            int x = (int) Math.floor(mc.player.getX() + ux * i);
            int z = (int) Math.floor(mc.player.getZ() + uz * i);
            int y = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            mc.level.addParticle(ParticleTypes.END_ROD, x + 0.5, y + 0.3, z + 0.5, 0, 0.02, 0);
        }
    }

    private Waypoints() {}
}
