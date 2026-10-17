package fr.wakfu.rpg.network;

import fr.wakfu.rpg.core.ClientProxyHook;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Serveur -> client : etat complet du combat facon Wakfu (vue du joueur).
 *  v3 (0.14) : + invocations. v4 (0.19) : + unites du groupe {sideRel, kind,
 *  x, z, hp, maxHp, active} + noms, murs internes (offsets), theme d'arene. */
public class CombatStatePacket {
    public final boolean inCombat;
    public final boolean pvp;
    public final boolean yourTurn;
    public final int round;
    public final int ap;
    public final int mp;
    public final String info;
    public final int selfHp;
    public final int selfMaxHp;
    public final int selfCellX;
    public final int selfCellZ;
    public final int oppHp;
    public final int oppMaxHp;
    public final String oppName;
    public final int oppCellX;
    public final int oppCellZ;
    public final int baseX;
    public final int baseZ;
    public final int floorY;
    public final int size;
    public final int cameraId;
    public final List<int[]> summons;
    /** v0.18 : pieges {x, z, coteRelatif(0=moi)}. */
    public final List<int[]> traps;
    /** v0.19 : unites {sideRel(0=moi), kind(0 joueur/1 mob), x, z, hp, maxHp, active}. */
    public final List<int[]> units;
    /** v0.19 : noms des unites (parallele a units). */
    public final List<String> unitNames;
    /** v0.19 : murs internes — offsets compacts (x-baseX)*64 + (z-baseZ). */
    public final List<Integer> walls;
    /** v0.19 : theme d'arene (0 plaine, 1 foret, 2 montagne, 3 donjon). */
    public final int theme;

    public CombatStatePacket(boolean inCombat, boolean pvp, boolean yourTurn, int round, int ap, int mp, String info,
                             int selfHp, int selfMaxHp, int selfCellX, int selfCellZ,
                             int oppHp, int oppMaxHp, String oppName, int oppCellX, int oppCellZ,
                             int baseX, int baseZ, int floorY, int size, int cameraId) {
        this(inCombat, pvp, yourTurn, round, ap, mp, info, selfHp, selfMaxHp, selfCellX, selfCellZ,
                oppHp, oppMaxHp, oppName, oppCellX, oppCellZ, baseX, baseZ, floorY, size, cameraId,
                new ArrayList<>());
    }

    public CombatStatePacket(boolean inCombat, boolean pvp, boolean yourTurn, int round, int ap, int mp, String info,
                             int selfHp, int selfMaxHp, int selfCellX, int selfCellZ,
                             int oppHp, int oppMaxHp, String oppName, int oppCellX, int oppCellZ,
                             int baseX, int baseZ, int floorY, int size, int cameraId, List<int[]> summons) {
        this(inCombat, pvp, yourTurn, round, ap, mp, info, selfHp, selfMaxHp, selfCellX, selfCellZ,
                oppHp, oppMaxHp, oppName, oppCellX, oppCellZ, baseX, baseZ, floorY, size, cameraId,
                summons, new ArrayList<>());
    }

    public CombatStatePacket(boolean inCombat, boolean pvp, boolean yourTurn, int round, int ap, int mp, String info,
                             int selfHp, int selfMaxHp, int selfCellX, int selfCellZ,
                             int oppHp, int oppMaxHp, String oppName, int oppCellX, int oppCellZ,
                             int baseX, int baseZ, int floorY, int size, int cameraId,
                             List<int[]> summons, List<int[]> traps) {
        this(inCombat, pvp, yourTurn, round, ap, mp, info, selfHp, selfMaxHp, selfCellX, selfCellZ,
                oppHp, oppMaxHp, oppName, oppCellX, oppCellZ, baseX, baseZ, floorY, size, cameraId,
                summons, traps, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), 0);
    }

    public CombatStatePacket(boolean inCombat, boolean pvp, boolean yourTurn, int round, int ap, int mp, String info,
                             int selfHp, int selfMaxHp, int selfCellX, int selfCellZ,
                             int oppHp, int oppMaxHp, String oppName, int oppCellX, int oppCellZ,
                             int baseX, int baseZ, int floorY, int size, int cameraId,
                             List<int[]> summons, List<int[]> traps,
                             List<int[]> units, List<String> unitNames, List<Integer> walls, int theme) {
        this.inCombat = inCombat;
        this.pvp = pvp;
        this.yourTurn = yourTurn;
        this.round = round;
        this.ap = ap;
        this.mp = mp;
        this.info = info;
        this.selfHp = selfHp;
        this.selfMaxHp = selfMaxHp;
        this.selfCellX = selfCellX;
        this.selfCellZ = selfCellZ;
        this.oppHp = oppHp;
        this.oppMaxHp = oppMaxHp;
        this.oppName = oppName;
        this.oppCellX = oppCellX;
        this.oppCellZ = oppCellZ;
        this.baseX = baseX;
        this.baseZ = baseZ;
        this.floorY = floorY;
        this.size = size;
        this.cameraId = cameraId;
        this.summons = summons == null ? new ArrayList<>() : summons;
        this.traps = traps == null ? new ArrayList<>() : traps;
        this.units = units == null ? new ArrayList<>() : units;
        this.unitNames = unitNames == null ? new ArrayList<>() : unitNames;
        this.walls = walls == null ? new ArrayList<>() : walls;
        this.theme = theme;
    }

    public static CombatStatePacket empty() {
        return new CombatStatePacket(false, false, false, 0, 0, 0, "",
                0, 0, 0, 0, 0, 0, "", 0, 0, 0, 0, 0, 0, 0);
    }

    public static void encode(CombatStatePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.inCombat);
        buf.writeBoolean(msg.pvp);
        buf.writeBoolean(msg.yourTurn);
        buf.writeVarInt(msg.round);
        buf.writeVarInt(msg.ap);
        buf.writeVarInt(msg.mp);
        buf.writeUtf(msg.info, 256);
        buf.writeVarInt(msg.selfHp);
        buf.writeVarInt(msg.selfMaxHp);
        buf.writeVarInt(msg.selfCellX);
        buf.writeVarInt(msg.selfCellZ);
        buf.writeVarInt(msg.oppHp);
        buf.writeVarInt(msg.oppMaxHp);
        buf.writeUtf(msg.oppName, 128);
        buf.writeVarInt(msg.oppCellX);
        buf.writeVarInt(msg.oppCellZ);
        buf.writeVarInt(msg.baseX);
        buf.writeVarInt(msg.baseZ);
        buf.writeVarInt(msg.floorY);
        buf.writeVarInt(msg.size);
        buf.writeVarInt(msg.cameraId);
        buf.writeVarInt(msg.summons.size());
        for (int[] s : msg.summons) {
            for (int v : s) buf.writeVarInt(v);
        }
        buf.writeVarInt(msg.traps.size());
        for (int[] t : msg.traps) {
            for (int v : t) buf.writeVarInt(v);
        }
        // v0.19
        buf.writeVarInt(msg.units.size());
        for (int[] u : msg.units) {
            for (int v : u) buf.writeVarInt(v);
        }
        buf.writeVarInt(msg.unitNames.size());
        for (String n : msg.unitNames) buf.writeUtf(n, 64);
        buf.writeVarInt(msg.walls.size());
        for (int w : msg.walls) buf.writeVarInt(w);
        buf.writeVarInt(msg.theme);
    }

    public static CombatStatePacket decode(FriendlyByteBuf buf) {
        CombatStatePacket p = new CombatStatePacket(
                buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(256),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readUtf(128), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        int n = buf.readVarInt();
        List<int[]> sums = new ArrayList<>(n);
        for (int i = 0; i < n && i < 8; i++) {
            int[] s = new int[6];
            for (int k = 0; k < 6; k++) s[k] = buf.readVarInt();
            sums.add(s);
        }
        int nt = buf.readVarInt();
        List<int[]> traps = new ArrayList<>(nt);
        for (int i = 0; i < nt && i < 6; i++) {
            int[] t = new int[3];
            for (int k = 0; k < 3; k++) t[k] = buf.readVarInt();
            traps.add(t);
        }
        int nu = buf.readVarInt();
        List<int[]> units = new ArrayList<>(nu);
        List<String> names = new ArrayList<>(nu);
        for (int i = 0; i < nu && i < 12; i++) {
            int[] u = new int[7];
            for (int k = 0; k < 7; k++) u[k] = buf.readVarInt();
            units.add(u);
        }
        int nn = buf.readVarInt();
        for (int i = 0; i < nn && i < 12; i++) names.add(buf.readUtf(64));
        int nw = buf.readVarInt();
        List<Integer> walls = new ArrayList<>(nw);
        for (int i = 0; i < nw && i < 128; i++) walls.add(buf.readVarInt());
        int theme = buf.readVarInt();
        return new CombatStatePacket(p.inCombat, p.pvp, p.yourTurn, p.round, p.ap, p.mp, p.info,
                p.selfHp, p.selfMaxHp, p.selfCellX, p.selfCellZ, p.oppHp, p.oppMaxHp, p.oppName,
                p.oppCellX, p.oppCellZ, p.baseX, p.baseZ, p.floorY, p.size, p.cameraId,
                sums, traps, units, names, walls, theme);
    }

    public static void handle(CombatStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientProxyHook.onCombatState.accept(msg));
        ctx.get().setPacketHandled(true);
    }
}
