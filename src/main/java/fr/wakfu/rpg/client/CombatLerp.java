package fr.wakfu.rpg.client;

import fr.wakfu.rpg.entity.MonsterNpc;
import fr.wakfu.rpg.network.CombatStatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Deplacements fluides en combat (v0.12) : quand une unite change de case,
 * le client interpole sa position au lieu du "teleport" serveur.
 * Le rendu vanilla interpole deja entre ticks (xo -> x), donc un setPos
 * par tick client suffit pour un mouvement lisse.
 */
public class CombatLerp {

    private static class L {
        final Entity e;
        final double fx, fz, tx, tz, y;
        final long start;

        L(Entity e, double fx, double fz, double tx, double tz, double y, long start) {
            this.e = e;
            this.fx = fx;
            this.fz = fz;
            this.tx = tx;
            this.tz = tz;
            this.y = y;
            this.start = start;
        }
    }

    private static final long DUR = 380;
    private static final List<L> ACTIVE = new ArrayList<>();
    private static Integer lastSelfX, lastSelfZ, lastOppX, lastOppZ;
    private static int lastOppId = -1;

    /** A chaque paquet d'etat recu : detecte les changements de case. */
    public static void onState(CombatStatePacket st) {
        Minecraft mc = Minecraft.getInstance();
        if (!st.inCombat) {
            clear();
            return;
        }
        if (mc.player == null || mc.level == null) return;
        long now = System.currentTimeMillis();

        if (lastSelfX != null && (lastSelfX != st.selfCellX || lastSelfZ != st.selfCellZ)) {
            start(mc.player, lastSelfX, lastSelfZ, st.selfCellX, st.selfCellZ, st.floorY, now);
        }
        lastSelfX = st.selfCellX;
        lastSelfZ = st.selfCellZ;

        Entity opp = findOpp(mc, st);
        if (opp != null) {
            if (lastOppX != null && opp.getId() == lastOppId
                    && (lastOppX != st.oppCellX || lastOppZ != st.oppCellZ)) {
                start(opp, lastOppX, lastOppZ, st.oppCellX, st.oppCellZ, st.floorY, now);
            }
            lastOppId = opp.getId();
        } else {
            lastOppId = -1;
        }
        lastOppX = st.oppCellX;
        lastOppZ = st.oppCellZ;
    }

    private static void start(Entity e, int fx, int fz, int tx, int tz, int floorY, long now) {
        ACTIVE.removeIf(l -> l.e == e);
        ACTIVE.add(new L(e, fx + 0.5, fz + 0.5, tx + 0.5, tz + 0.5, floorY, now));
    }

    private static Entity findOpp(Minecraft mc, CombatStatePacket st) {
        Entity best = null;
        double bd = 1e9;
        double cx = st.oppCellX + 0.5;
        double cz = st.oppCellZ + 0.5;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (Math.abs(e.getY() - st.floorY) > 2.5) continue;
            boolean alexModel = e.getTags().contains("wakfurpg_alex_model");
            boolean ok = st.pvp
                    ? (e instanceof Player && e != mc.player)
                    : (alexModel || e instanceof MonsterNpc);
            if (!ok) continue;
            double d = e.distanceToSqr(cx, st.floorY, cz);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return bd < 100 ? best : null;
    }

    /** A appeler chaque tick client pendant le combat. */
    public static void tick() {
        if (ACTIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        for (int i = ACTIVE.size() - 1; i >= 0; i--) {
            L l = ACTIVE.get(i);
            if (l.e.isRemoved()) {
                ACTIVE.remove(i);
                continue;
            }
            double t = (now - l.start) / (double) DUR;
            if (t >= 1) {
                setPos(l.e, l.tx, l.y, l.tz, mc);
                ACTIVE.remove(i);
                continue;
            }
            double s = t * t * (3 - 2 * t); // smoothstep
            double x = l.fx + (l.tx - l.fx) * s;
            double z = l.fz + (l.tz - l.fz) * s;
            setPos(l.e, x, l.y, z, mc);
            if (l.e instanceof net.minecraft.world.entity.LivingEntity living) {
                float speed = (float) Math.hypot(l.tx - l.fx, l.tz - l.fz) / (DUR / 50.0f);
                living.walkAnimation.update(Math.min(0.22f, speed), 1.0f);
            }
            l.e.setDeltaMovement(0, 0, 0);
        }
    }

    private static void setPos(Entity e, double x, double y, double z, Minecraft mc) {
        if (e == mc.player) e.setPosRaw(x, y, z);
        else e.setPos(x, y, z);
    }

    public static void clear() {
        ACTIVE.clear();
        lastSelfX = lastSelfZ = lastOppX = lastOppZ = null;
        lastOppId = -1;
    }

    private CombatLerp() {}
}
