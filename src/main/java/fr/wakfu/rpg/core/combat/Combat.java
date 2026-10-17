package fr.wakfu.rpg.core.combat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Etat serveur d'un combat tour par tour façon Wakfu.
 * v0.19 : combat de GROUPE — plusieurs joueurs (cote 0, tour a tour) contre
 * plusieurs monstres simultanes (cote 1), arene a taille/theme variables avec
 * murs internes (couvert / ligne de vue), fenetre d'invitation avant la
 * premiere action.
 */
public class Combat {
    public enum Phase { SIDE_A, SIDE_B, ENDED }

    public final ServerLevel level;
    public final UUID playerA;            // initiateur
    public final UUID playerB;            // null en PvE (duel 1v1 sinon)
    public final int arenaSlot;
    public final int baseX, baseZ;        // coin (0,0) de l'arene
    public final int floorY;
    public final int size;                // cote de l'arene (11 a 15 selon le theme)
    public final int cameraId;

    public long enterTick = 0;
    /** v0.19 : tick d'entree individuel (membres qui rejoignent en cours de route). */
    public final Map<UUID, Long> joinTicks = new HashMap<>();

    // ------------------------------------------------------------ v0.19 : groupe
    /** Joueurs du cote 0, dans l'ordre de tour (index 0 = initiateur). */
    public final List<UUID> members = new ArrayList<>();
    /** Cellule de chaque joueur (cote 0 et joueur PvP). */
    public final Map<UUID, BlockPos> cells = new HashMap<>();
    /** {ap, mp} de chaque joueur. */
    public final Map<UUID, int[]> apmp = new HashMap<>();
    /** Ancres de verrouillage position, par joueur. */
    public final Map<UUID, double[]> anchors = new HashMap<>();
    /** Index du membre actif (phase SIDE_A). */
    public int activeIdx = 0;
    /** Ids des copies de monstres dans l'arene (cote 1). */
    public final List<Integer> mobs = new ArrayList<>();
    /** Ids des monstres originaux paralleles a mobs (-1 = clone sans original). */
    public final List<Integer> mobOrigins = new ArrayList<>();
    /** v0.27 : ids des vraies entites Alex Mobs (visuel). */
    public final List<Integer> visualEntityIds = new ArrayList<>();
    /** Fenetre d'invitation : true jusqu'a la premiere action reelle. */
    public boolean joinable = true;
    /** Joueurs invites (pour revoquer la notif au verrouillage). */
    public final Set<UUID> invited = new HashSet<>();
    /** Cle unique du combat (invitations). */
    public int key = 0;

    // ------------------------------------------------------------ v0.19 : arene variable
    /** Murs internes (cases absolues, infranchissables, bloquent la ligne de vue). */
    public final Set<BlockPos> walls = new HashSet<>();
    /** Theme visuel (0 plaine, 1 foret, 2 montagne, 3 donjon). */
    public int theme = 0;

    // v0.12 : combos + grace deplacement (par cote)
    public final int[] casts = new int[2];
    private final Set<String> spellsA = new HashSet<>();
    private final Set<String> spellsB = new HashSet<>();
    public final long[] moveGrace = new long[2];
    // v0.13 (remplace par les mobs simultanes, conserve pour nettoyage)
    public int[] waveOrigins = new int[0];
    public int waveIdx = 0;
    // v0.14 : invocations par cote
    private final List<Integer> summonsA = new ArrayList<>();
    private final List<Integer> summonsB = new ArrayList<>();
    // v0.18 : pieges {x, z, coteProprietaire, degats}
    public final List<int[]> traps = new ArrayList<>();

    // ------------------------------------------------------------ champs legac
    /** Premier mob (id entite copie) — compat HUD/adversaire principal. */
    public int monsterId = -1;
    public int monsterOriginId = -1;
    /** Cellule de l'adversaire principal : premier mob vivant (PvE) ou joueur B (PvP). */
    public BlockPos cellB;

    public Phase phase = Phase.SIDE_A;
    public int round = 1;

    public Combat(ServerLevel level, UUID playerA, UUID playerB,
                  int arenaSlot, int baseX, int baseZ, int floorY, int size, int cameraId) {
        this.level = level;
        this.playerA = playerA;
        this.playerB = playerB;
        this.arenaSlot = arenaSlot;
        this.baseX = baseX;
        this.baseZ = baseZ;
        this.floorY = floorY;
        this.size = size;
        this.cameraId = cameraId;
        this.members.add(playerA);
    }

    public boolean pvp() {
        return playerB != null;
    }

    // ------------------------------------------------------------ acces membres
    public UUID activeMember() {
        if (members.isEmpty()) return null;
        return members.get(Math.min(activeIdx, members.size() - 1));
    }

    public boolean alive() {
        return !members.isEmpty();
    }

    public int sideOf(UUID u) {
        if (members.contains(u)) return 0;
        if (playerB != null && playerB.equals(u)) return 1;
        return -1;
    }

    public boolean isTurn(int side) {
        return phase == (side == 0 ? Phase.SIDE_A : Phase.SIDE_B);
    }

    /** Cellule du joueur donne (nul si absent). */
    public BlockPos cellOfPlayer(UUID u) {
        return cells.get(u);
    }

    /** Cellule "principale" du cote : membre actif (0) ou adversaire principal (1). */
    public BlockPos cellOf(int side) {
        if (side == 0) {
            UUID u = activeMember();
            return u == null ? null : cells.get(u);
        }
        if (pvp()) return cells.get(playerB);
        return firstMobCell();
    }

    public void setCell(int side, BlockPos c) {
        if (side == 0) {
            UUID u = activeMember();
            if (u != null) cells.put(u, c);
        } else if (pvp()) {
            cells.put(playerB, c);
        } else {
            cellB = c;
        }
    }

    public int apOf(int side) {
        int[] v = apmpOf(side);
        return v == null ? 0 : v[0];
    }

    public void setAp(int side, int v) {
        int[] a = apmpOf(side);
        if (a != null) a[0] = v;
    }

    public int mpOf(int side) {
        int[] v = apmpOf(side);
        return v == null ? 0 : v[1];
    }

    public void setMp(int side, int v) {
        int[] a = apmpOf(side);
        if (a != null) a[1] = v;
    }

    private int[] apmpOf(int side) {
        UUID u = side == 0 ? activeMember() : playerB;
        return u == null ? null : apmp.get(u);
    }

    public double anchorXFor(UUID u) {
        double[] a = anchors.get(u);
        return a == null ? 0 : a[0];
    }

    public double anchorZFor(UUID u) {
        double[] a = anchors.get(u);
        return a == null ? 0 : a[1];
    }

    public void setAnchor(int side, double x, double z) {
        UUID u = side == 0 ? activeMember() : playerB;
        if (u != null) anchors.put(u, new double[]{x, z});
    }

    public void setAnchorFor(UUID u, double x, double z) {
        anchors.put(u, new double[]{x, z});
    }

    // ------------------------------------------------------------ mobs (cote 1 PvE)
    /** Cellule du premier mob vivant (sinon cellB en secours). */
    public BlockPos firstMobCell() {
        for (int id : mobs) {
            Entity e = level.getEntity(id);
            if (e != null && e.isAlive()) return e.blockPosition();
        }
        return cellB;
    }

    /** Retire les mobs morts/disparus de la liste. */
    public void purgeMobs() {
        for (int i = mobs.size() - 1; i >= 0; i--) {
            Entity e = level.getEntity(mobs.get(i));
            if (!(e instanceof fr.wakfu.rpg.entity.MonsterNpc m) || !m.isAlive()) {
                mobs.remove(i);
                if (i < mobOrigins.size()) mobOrigins.remove(i);
            }
        }
    }

    // ------------------------------------------------------------ arene
    /** Case jouable : interieur de l'arene, hors murs internes. */
    public boolean isPlayable(BlockPos cell) {
        if (cell == null || cell.getY() != floorY) return false;
        int rx = cell.getX() - baseX;
        int rz = cell.getZ() - baseZ;
        if (rx < 1 || rx > size - 2 || rz < 1 || rz > size - 2) return false;
        return !walls.contains(cell);
    }

    public java.util.List<Integer> summonsOf(int side) {
        return side == 0 ? summonsA : summonsB;
    }

    public java.util.Set<String> spellsOf(int side) {
        return side == 0 ? spellsA : spellsB;
    }

    public void resetTurn(int side) {
        casts[side] = 0;
        spellsOf(side).clear();
    }
}
