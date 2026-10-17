package fr.wakfu.rpg.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.wakfu.rpg.WakfuRPG;
import fr.wakfu.rpg.core.ModCapabilities;
import fr.wakfu.rpg.core.PlayerRpgData;
import fr.wakfu.rpg.core.spell.Spell;
import fr.wakfu.rpg.core.spell.Spells;
import fr.wakfu.rpg.core.spell.ZoneType;
import fr.wakfu.rpg.network.ActionPacket;
import fr.wakfu.rpg.network.JoinCombatPacket;
import fr.wakfu.rpg.network.CombatStatePacket;
import fr.wakfu.rpg.network.Network;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Vue de combat façon Wakfu (client) :
 * - camera vue de haut verrouillee (attachee a une entite invisible)
 * - quadrillage des cases, portee du sort en vert, zone d'effet en jaune
 * - curseur libre : clic sur une case = se deplacer / lancer le sort
 * - touches 1-4 (sorts), R (fin du tour), clic droit (annuler)
 */
@Mod.EventBusSubscriber(modid = WakfuRPG.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CombatView {

    public static int selectedSlot = -1;
    public static BlockPos hoverCell = null;

    private static final Matrix4f VIEWPROJ = new Matrix4f();
    private static final Matrix4f INV = new Matrix4f();
    private static double camX, camY, camZ;

    private static boolean matricesValid = false;

    /** v0.14 : musique d'ambiance de combat (piste vanilla sombre, en boucle unique). */
    private static net.minecraft.client.resources.sounds.SimpleSoundInstance battleMusic = null;

    static void enterCombat() {
        selectedSlot = -1;
        hoverCell = null;
        Minecraft mc = Minecraft.getInstance();
        try {
            mc.mouseHandler.releaseMouse();
        } catch (Exception ignored) {
        }
        try {
            if (battleMusic == null) {
                battleMusic = new net.minecraft.client.resources.sounds.SimpleSoundInstance(
                        new net.minecraft.resources.ResourceLocation("minecraft", "music_disc.13"),
                        net.minecraft.sounds.SoundSource.MUSIC, 0.3f, 1f,
                        net.minecraft.util.RandomSource.create(), false, 0,
                        net.minecraft.client.resources.sounds.SoundInstance.Attenuation.NONE,
                        0, 0, 0, false);
                mc.getSoundManager().play(battleMusic);
            }
        } catch (Exception ignored) {
        }
    }

    static void exitCombat() {
        Minecraft mc = Minecraft.getInstance();
        selectedSlot = -1;
        hoverCell = null;
        try {
            mc.mouseHandler.grabMouse();
        } catch (Exception ignored) {
        }
        try {
            if (battleMusic != null) {
                mc.getSoundManager().stop(battleMusic);
                battleMusic = null;
            }
        } catch (Exception ignored) {
        }
    }

    static void reset() {
        selectedSlot = -1;
        hoverCell = null;
        matricesValid = false;
    }

    // ------------------------------------------------------------ tick : camera + curseur libre
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            reset();
            return;
        }
        CombatStatePacket st = ClientCombatHud.get();
        if (st.inCombat) {
            // v0.27 : interpolation fluide du zoom
            // v0.12 fix frappe infinie : jamais d'attaque/interaction vanilla en combat
            if (mc.options.keyAttack.isDown()) mc.options.keyAttack.setDown(false);
            if (mc.options.keyUse.isDown()) mc.options.keyUse.setDown(false);
            // v0.12 : deplacements animes (interpolation des changements de case)
            CombatLerp.tick();
            // (re)attacher la camera vue de haut — gere aussi le changement de dimension
            var camEnt = mc.getCameraEntity();
            if ((camEnt == null || camEnt == mc.player || camEnt.isRemoved()) && st.cameraId > 0) {
                var cam = mc.level.getEntity(st.cameraId);
                if (cam != null && !cam.isRemoved()) {
                    mc.setCameraEntity(cam);
                }
            }
            // epingle le joueur au sol cote client (evite la chute visuelle dans le vide)
            double dy = mc.player.getY() - st.floorY;
            if (dy < -0.01 || dy > 2.5) {
                mc.player.setPosRaw(mc.player.getX(), st.floorY, mc.player.getZ());
                mc.player.setDeltaMovement(0, 0, 0);
            }
            // garder le curseur libre tant qu'aucun ecran n'est ouvert
            if (mc.screen == null && mc.mouseHandler.isMouseGrabbed()) {
                mc.mouseHandler.releaseMouse();
            }
        } else if (mc.getCameraEntity() != mc.player) {
            mc.setCameraEntity(mc.player);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        ClientCombatHud.reset();
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn e) {
        ClientCombatHud.reset();
        fr.wakfu.rpg.network.Network.sendToServer(new fr.wakfu.rpg.network.ClientHelloPacket("0.26.1"));
    }

    /** Diagnostic visible DANS le menu pause (captures utilisateur toujours en pause). */
    @SubscribeEvent
    public static void onPauseRender(net.minecraftforge.client.event.ScreenEvent.Render.Post e) {
        if (!(e.getScreen() instanceof net.minecraft.client.gui.screens.PauseScreen)) return;
        Minecraft mc = Minecraft.getInstance();
        var st = ClientCombatHud.get();
        String dim = mc.level == null ? "?" : mc.level.dimension().location().toString();
        boolean camOk = mc.getCameraEntity() != null && mc.getCameraEntity() != mc.player;
        int chunks = mc.level == null ? -1 : mc.level.getChunkSource().getLoadedChunksCount();
        boolean ent = mc.level != null && st.cameraId > 0 && mc.level.getEntity(st.cameraId) != null;
        var g = e.getGuiGraphics();
        g.drawString(mc.font, "§ewakfurpg client 0.26.1", 4, 4, 0xFFFF55);
        String sol = "?";
        if (mc.level != null && st.inCombat) {
            sol = mc.level.getBlockState(new net.minecraft.core.BlockPos(st.baseX + st.size / 2, st.floorY - 1, st.baseZ + st.size / 2))
                    .getBlock() == net.minecraft.world.level.block.Blocks.AIR ? "vide" : "ok";
        }
        if (st.inCombat) {
            g.drawString(mc.font, "§funites=" + st.units.size() + " murs=" + st.walls.size() + " theme=" + st.theme, 4, 34, 0xFFFFFF);
        }
        g.drawString(mc.font, "§fcombat=" + st.inCombat + " dim=" + dim + " cam=" + (camOk ? "ok" : "KO")
                + " chunks=" + chunks + " camId=" + st.cameraId + " ent=" + (ent ? "present" : "absent"),
                4, 14, 0xFFFFFF);
        g.drawString(mc.font, "§fsolClient=" + sol, 4, 24, 0xFFFFFF);
    }

    // ------------------------------------------------------------ camera vue de haut
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles e) {
        if (ClientCombatHud.get().inCombat) {
            e.setPitch(90f);   // +90 = regarde vers le BAS (-90 = ciel, erreur v0.7-0.9)
            e.setYaw(180f);
            e.setRoll(0f);
        }
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov e) {
        if (ClientCombatHud.get().inCombat) {
            e.setFOV(80f);
        }
    }

    // ------------------------------------------------------------ rendu monde : cases + zones
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        CombatStatePacket st = ClientCombatHud.get();
        if (!st.inCombat) {
            matricesValid = false;
            return;
        }
        PoseStack pose = e.getPoseStack();
        if (pose.last() == null) return;
        VIEWPROJ.set(e.getProjectionMatrix()).mul(pose.last().pose());
        INV.set(VIEWPROJ).invert();
        camX = e.getCamera().getPosition().x;
        camY = e.getCamera().getPosition().y;
        camZ = e.getCamera().getPosition().z;
        matricesValid = true;
        updateHover(st);
        pose.pushPose();
        pose.translate(-camX, -camY, -camZ);
        drawArena(pose, st);
        pose.popPose();
    }

    private static void quadVX(BufferBuilder bb, Matrix4f m, double x, double z0, double z1, double y0, double y1, int argb) {
        float a = (argb >> 24 & 0xFF) / 255f, r = (argb >> 16 & 0xFF) / 255f,
                g = (argb >> 8 & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        bb.vertex(m, (float) x, (float) y0, (float) z0).color(r, g, b, a).endVertex();
        bb.vertex(m, (float) x, (float) y0, (float) z1).color(r, g, b, a).endVertex();
        bb.vertex(m, (float) x, (float) y1, (float) z1).color(r, g, b, a).endVertex();
        bb.vertex(m, (float) x, (float) y1, (float) z0).color(r, g, b, a).endVertex();
    }

    private static void quadVZ(BufferBuilder bb, Matrix4f m, double z, double x0, double x1, double y0, double y1, int argb) {
        float a = (argb >> 24 & 0xFF) / 255f, r = (argb >> 16 & 0xFF) / 255f,
                g = (argb >> 8 & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        bb.vertex(m, (float) x0, (float) y0, (float) z).color(r, g, b, a).endVertex();
        bb.vertex(m, (float) x1, (float) y0, (float) z).color(r, g, b, a).endVertex();
        bb.vertex(m, (float) x1, (float) y1, (float) z).color(r, g, b, a).endVertex();
        bb.vertex(m, (float) x0, (float) y1, (float) z).color(r, g, b, a).endVertex();
    }

    /** Case survolee par la souris (rayon depuis la camera vers le sol). */
    private static void updateHover(CombatStatePacket st) {
        Minecraft mc = Minecraft.getInstance();
        hoverCell = null;
        if (!matricesValid) return;
        double winW = mc.getWindow().getWidth();
        double winH = mc.getWindow().getHeight();
        if (winW <= 0 || winH <= 0) return;
        double ndcX = (mc.mouseHandler.xpos() / winW) * 2.0 - 1.0;
        double ndcY = 1.0 - (mc.mouseHandler.ypos() / winH) * 2.0;
        Vector4f v = new Vector4f((float) ndcX, (float) ndcY, 0.5f, 1f);
        v.mul(INV);
        if (Math.abs(v.w) < 1e-9f) return;
        v.div(v.w);
        if (Math.abs(v.y) < 1e-9f) return;
        double t = (st.floorY - camY) / v.y;
        if (t <= 0) return;
        double hx = camX + v.x * t;
        double hz = camZ + v.z * t;
        int cx = Mth.floor(hx);
        int cz = Mth.floor(hz);
        if (cx >= st.baseX && cx < st.baseX + st.size && cz >= st.baseZ && cz < st.baseZ + st.size) {
            hoverCell = new BlockPos(cx, st.floorY, cz);
        }
    }

    private static void drawArena(PoseStack pose, CombatStatePacket st) {
        Minecraft mc = Minecraft.getInstance();
        PlayerRpgData data = mc.player == null ? null
                : mc.player.getCapability(ModCapabilities.RPG).resolve().orElse(null);
        Spell selected = selectedSpell();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f m = pose.last().pose();

        // sol PLEIN + murs translucides : visibles meme si les chunks de la
        // dimension n'arrivent jamais au client (bug de sync vanilla)
        double ys = st.floorY + 0.005;
        for (int i = 0; i < st.size; i++) {
            for (int j = 0; j < st.size; j++) {
                boolean edge = i == 0 || j == 0 || i == st.size - 1 || j == st.size - 1;
                int x = st.baseX + i;
                int z = st.baseZ + j;
                int col = edge ? 0xFF5D6A72 : (((i + j) % 2 == 0) ? 0xFFA9A9A9 : 0xFF8F8F8F);
                quad(bb, m, x + 0.001, z + 0.001, x + 0.999, z + 0.999, ys, col);
            }
        }
        int w0 = st.baseX + 1, w1 = st.baseX + st.size - 1;
        int z0 = st.baseZ + 1, z1 = st.baseZ + st.size - 1;
        double yb = st.floorY, yt = st.floorY + 3;
        quadVX(bb, m, w0, z0, z1, yb, yt, 0x55BFE8FF);
        quadVX(bb, m, w1, z0, z1, yb, yt, 0x55BFE8FF);
        quadVZ(bb, m, z0, w0, w1, yb, yt, 0x55BFE8FF);
        quadVZ(bb, m, z1, w0, w1, yb, yt, 0x55BFE8FF);

        // damier (les cases se distinguent)
        double y0 = st.floorY + 0.010;
        for (int i = 1; i <= st.size - 2; i++) {
            for (int j = 1; j <= st.size - 2; j++) {
                int x = st.baseX + i;
                int z = st.baseZ + j;
                int argb = ((i + j) % 2 == 0) ? 0x0EFFFFFF : 0x14000000;
                quad(bb, m, x + 0.02, z + 0.02, x + 0.98, z + 0.98, y0, argb);
            }
        }

        // quadrillage
        double yg = st.floorY + 0.012;
        for (int i = 1; i <= st.size - 1; i++) {
            double gx = st.baseX + i;
            quad(bb, m, gx - 0.015, st.baseZ + 1, gx + 0.015, st.baseZ + st.size - 1, yg, 0x55000000);
            double gz = st.baseZ + i;
            quad(bb, m, st.baseX + 1, gz - 0.015, st.baseX + st.size - 1, gz + 0.015, yg, 0x55000000);
        }

        // v0.19 : murs internes (colonnes translucides, teinte selon le theme)
        int[] wallCols = {0xCC9A7A4A, 0xCC5A4232, 0xCC8A8A92, 0xCC4A4A54};
        int wallCol = wallCols[Math.max(0, Math.min(3, st.theme))];
        for (int w : st.walls) {
            int wx = st.baseX + w / 64;
            int wz = st.baseZ + w % 64;
            double yb2 = st.floorY, yt2 = st.floorY + 2;
            quadVX(bb, m, wx + 0.06, wz + 0.06, wz + 0.94, yb2, yt2, wallCol);
            quadVX(bb, m, wx + 0.94, wz + 0.06, wz + 0.94, yb2, yt2, wallCol);
            quadVZ(bb, m, wz + 0.06, wx + 0.06, wx + 0.94, yb2, yt2, wallCol);
            quadVZ(bb, m, wz + 0.94, wx + 0.06, wx + 0.94, yb2, yt2, wallCol);
            quad(bb, m, wx + 0.06, wz + 0.06, wx + 0.94, wz + 0.94, yt2, wallCol);
        }

        BlockPos self = new BlockPos(st.selfCellX, st.floorY, st.selfCellZ);
        BlockPos opp = new BlockPos(st.oppCellX, st.floorY, st.oppCellZ);

        // portee du sort selectionne (vert) — v0.19 : les murs masquent les cases sans ligne de vue
        if (selected != null && st.yourTurn) {
            for (BlockPos c : selected.rangeCells(self)) {
                if (!isPlayable(st, c)) continue;
                if (selected.needsLos() && !losClient(st, self, c)) continue;
                quad(bb, m, c.getX() + 0.03, c.getZ() + 0.03, c.getX() + 0.97, c.getZ() + 0.97,
                        st.floorY + 0.015, 0x4633FF66);
            }
        }

        // zone d'effet du sort (jaune) + case survolee
        if (hoverCell != null) {
            if (selected != null && st.yourTurn && selected.validTarget(self, hoverCell)
                    && (!selected.needsLos() || losClient(st, self, hoverCell))) {
                List<BlockPos> zone = selected.zoneCells(self, hoverCell);
                for (BlockPos c : zone) {
                    if (!isPlayable(st, c)) continue;
                    // v0.19 : les zones LINE s'arretent au premier mur (sorts non percants)
                    if (selected.zone == ZoneType.LINE && !selected.pierce && isWall(st, c)) break;
                    quad(bb, m, c.getX() + 0.02, c.getZ() + 0.02, c.getX() + 0.98, c.getZ() + 0.98,
                            st.floorY + 0.020, 0x66FFDD33);
                }
                outline(bb, m, hoverCell, st.floorY + 0.026, 0xDDFFFFFF);
            } else if (selected == null && st.yourTurn) {
                // v0.19 : chemin reel (BFS) — les murs et unites bloquent
                boolean ok = isPlayable(st, hoverCell) && !unitAt(st, hoverCell)
                        && bfsClient(st, self, hoverCell, st.mp) >= 1;
                outline(bb, m, hoverCell, st.floorY + 0.026, ok ? 0xDD55FF77 : 0xAAFF4444);
            }
        }

        // v0.19 : marqueurs de TOUTES les unites (allies bleus, ennemis rouges)
        for (int[] u : st.units) {
            BlockPos uc = new BlockPos(u[2], st.floorY, u[3]);
            if (u[0] == 0) {
                // allie : actif = blanc, sinon bleu
                outline(bb, m, uc, st.floorY + 0.024, u[6] == 1 ? 0xDDFFFFFF : 0x9955AAFF);
            } else {
                outline(bb, m, uc, st.floorY + 0.024, 0x99FF5555);
            }
            // mini-barre de vie
            float frac = u[5] > 0 ? Math.min(1f, u[4] / (float) u[5]) : 0f;
            quad(bb, m, uc.getX() + 0.1, uc.getZ() + 0.84, uc.getX() + 0.1 + 0.8 * frac, uc.getZ() + 0.94,
                    st.floorY + 0.030, u[0] == 0 ? 0xCC55CCFF : 0xCC55FF66);
        }

        // v0.18 : pieges (vert = les miens, rouge = ennemis)
        for (int[] t : st.traps) {
            BlockPos tc = new BlockPos(t[0], st.floorY, t[1]);
            outline(bb, m, tc, st.floorY + 0.026, t[2] == 0 ? 0xAA33FF66 : 0xAAFF3333);
        }

        // v0.14 : marqueurs des invocations (cyan = miennes, orange = adverses)
        for (int[] s : st.summons) {
            BlockPos sc = new BlockPos(s[1], st.floorY, s[2]);
            outline(bb, m, sc, st.floorY + 0.024, s[5] == 0 ? 0xAA55DDFF : 0xAAFFAA33);
            // mini-barre de vie sur la case
            float frac = s[4] > 0 ? Math.min(1f, s[3] / (float) s[4]) : 0f;
            quad(bb, m, sc.getX() + 0.1, sc.getZ() + 0.84, sc.getX() + 0.1 + 0.8 * frac, sc.getZ() + 0.94,
                    st.floorY + 0.028, 0xCC33FF66);
        }

        Tesselator.getInstance().end();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static boolean summonAt(CombatStatePacket st, BlockPos c) {
        for (int[] s : st.summons) {
            if (s[1] == c.getX() && s[2] == c.getZ()) return true;
        }
        return false;
    }

    /** v0.19 : case occupee par une unite du paquet (joueurs/mobs/invocations). */
    private static boolean unitAt(CombatStatePacket st, BlockPos c) {
        for (int[] u : st.units) {
            if (u[2] == c.getX() && u[3] == c.getZ()) return true;
        }
        return summonAt(st, c);
    }

    /** v0.19 : mur interne sur la case ? */
    private static boolean isWall(CombatStatePacket st, BlockPos c) {
        int packed = (c.getX() - st.baseX) * 64 + (c.getZ() - st.baseZ);
        for (int w : st.walls) {
            if (w == packed) return true;
        }
        return false;
    }

    /** v0.19 : ligne de vue client (Bresenham, murs bloquants). */
    private static boolean losClient(CombatStatePacket st, BlockPos a, BlockPos b) {
        int x = a.getX(), z = a.getZ();
        int x1 = b.getX(), z1 = b.getZ();
        int dx = Math.abs(x1 - x), dz = Math.abs(z1 - z);
        int sx = x < x1 ? 1 : -1, sz = z < z1 ? 1 : -1;
        int err = dx - dz;
        for (int guard = 0; guard < 64; guard++) {
            if (x == x1 && z == z1) return true;
            int e2 = 2 * err;
            if (e2 > -dz) { err -= dz; x += sx; }
            if (e2 < dx) { err += dx; z += sz; }
            if (x == x1 && z == z1) return true;
            if (isWall(st, new BlockPos(x, st.floorY, z))) return false;
        }
        return true;
    }

    /** v0.19 : distance BFS client (murs + unites bloquent) ; -1 si > limit. */
    private static int bfsClient(CombatStatePacket st, BlockPos from, BlockPos to, int limit) {
        java.util.Map<BlockPos, Integer> dist = new java.util.HashMap<>();
        java.util.List<BlockPos> queue = new java.util.ArrayList<>();
        dist.put(from, 0);
        queue.add(from);
        while (!queue.isEmpty()) {
            BlockPos c = queue.remove(0);
            int d = dist.get(c);
            if (c.equals(to)) return d;
            if (d >= limit) continue;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos n = new BlockPos(c.getX() + dx, st.floorY, c.getZ() + dz);
                    if (dist.containsKey(n)) continue;
                    if (!n.equals(to)) {
                        if (!isPlayable(st, n) || unitAt(st, n)) continue;
                    }
                    dist.put(n, d + 1);
                    queue.add(n);
                }
            }
        }
        return -1;
    }

    private static boolean isPlayable(CombatStatePacket st, BlockPos c) {
        if (c.getY() != st.floorY) return false;
        int rx = c.getX() - st.baseX;
        int rz = c.getZ() - st.baseZ;
        if (rx < 1 || rx > st.size - 2 || rz < 1 || rz > st.size - 2) return false;
        return !isWall(st, c);
    }

    private static void outline(BufferBuilder bb, Matrix4f m, BlockPos c, double y, int argb) {
        double x1 = c.getX();
        double z1 = c.getZ();
        double x2 = x1 + 1;
        double z2 = z1 + 1;
        double w = 0.05;
        quad(bb, m, x1, z1, x2, z1 + w, y, argb);
        quad(bb, m, x1, z2 - w, x2, z2, y, argb);
        quad(bb, m, x1, z1, x1 + w, z2, y, argb);
        quad(bb, m, x2 - w, z1, x2, z2, y, argb);
    }

    private static void quad(BufferBuilder bb, Matrix4f m, double x1, double z1, double x2, double z2, double y, int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        bb.vertex(m, (float) x1, (float) y, (float) z1).color(r, g, b, a).endVertex();
        bb.vertex(m, (float) x1, (float) y, (float) z2).color(r, g, b, a).endVertex();
        bb.vertex(m, (float) x2, (float) y, (float) z2).color(r, g, b, a).endVertex();
        bb.vertex(m, (float) x2, (float) y, (float) z1).color(r, g, b, a).endVertex();
    }

    // ------------------------------------------------------------ entrees souris / clavier
    public static Spell selectedSpell() {
        Minecraft mc = Minecraft.getInstance();
        if (selectedSlot < 0 || mc.player == null) return null;
        PlayerRpgData data = mc.player.getCapability(ModCapabilities.RPG).resolve().orElse(null);
        if (data == null) return null;
        List<String> eq = data.getEquipped();
        if (selectedSlot >= eq.size()) return null;
        return Spells.byId(eq.get(selectedSlot));
    }

    private static void toggleSlot(int i) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        PlayerRpgData data = mc.player.getCapability(ModCapabilities.RPG).resolve().orElse(null);
        if (data == null || i >= data.getEquipped().size()) return;
        selectedSlot = (selectedSlot == i) ? -1 : i;
    }


    // v0.27 : zoom molette souris
    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent e) {
        Minecraft mc = Minecraft.getInstance();
        CombatStatePacket st = ClientCombatHud.get();
        if (!st.inCombat || mc.screen != null) return;
        double delta = e.getScrollDelta();
        int direction = delta > 0 ? 1 : -1;
        fr.wakfu.rpg.network.Network.sendToServer(
            new fr.wakfu.rpg.network.ZoomPacket(direction));
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        CombatStatePacket st = ClientCombatHud.get();
        if (mc.screen != null || mc.player == null) return;
        if (e.getAction() != GLFW.GLFW_PRESS) {
            return; // v0.12 fix : laisser passer le RELEASE (sinon keyAttack reste
                    // "enfonce" cote vanilla -> frappes infinies en combat)
        }
        double gx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getWidth();
        double gy = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getHeight();
        // v0.19 : boutons de la notification d'invitation (hors combat)
        if (!st.inCombat && ClientInvites.isActive()) {
            int act = WakfuHud.hitTest(gx, gy);
            if (act == 20) {
                Network.sendToServer(new JoinCombatPacket(ClientInvites.key(), true));
                ClientInvites.clear();
                e.setCanceled(true);
                return;
            }
            if (act == 21) {
                Network.sendToServer(new JoinCombatPacket(ClientInvites.key(), false));
                ClientInvites.clear();
                e.setCanceled(true);
                return;
            }
        }
        if (!st.inCombat) return;
        if (e.getButton() == 0) {
            int action = WakfuHud.hitTest(gx, gy);
            if (action >= 0) {
                if (action < 10) {
                    toggleSlot(action);
                } else if (action == 10) {
                    Network.sendToServer(new ActionPacket(ActionPacket.END, "", 0, 0));
                } else if (action == 11) {
                    Network.sendToServer(new ActionPacket(ActionPacket.FLEE, "", 0, 0));
                }
                e.setCanceled(true);
                return;
            }
            if (st.yourTurn && hoverCell != null) {
                Spell sel = selectedSpell();
                if (sel != null) {
                    Network.sendToServer(new ActionPacket(ActionPacket.CAST, sel.id, hoverCell.getX(), hoverCell.getZ()));
                } else {
                    Network.sendToServer(new ActionPacket(ActionPacket.MOVE, "", hoverCell.getX(), hoverCell.getZ()));
                }
            }
            e.setCanceled(true);
        } else if (e.getButton() == 1) {
            selectedSlot = -1;
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key e) {
        Minecraft mc = Minecraft.getInstance();
        CombatStatePacket st = ClientCombatHud.get();
        if (!st.inCombat || mc.screen != null || mc.player == null) return;
        if (e.getAction() != GLFW.GLFW_PRESS) return;
        int k = e.getKey();
        if (k >= GLFW.GLFW_KEY_1 && k <= GLFW.GLFW_KEY_4) {
            toggleSlot(k - GLFW.GLFW_KEY_1);
        } else if (k == GLFW.GLFW_KEY_R) {
            Network.sendToServer(new ActionPacket(ActionPacket.END, "", 0, 0));
        } else if (k == GLFW.GLFW_KEY_ESCAPE && selectedSlot >= 0) {
            selectedSlot = -1;
        } else if (k == GLFW.GLFW_KEY_SPACE) {
            // v0.27 : spacebar = reset zoom
            fr.wakfu.rpg.network.Network.sendToServer(
                new fr.wakfu.rpg.network.ZoomPacket(0));
        }
    }

    /** Pas d'attaque/interaction vanilla pendant le combat. */
    @SubscribeEvent
    public static void onInteractKey(InputEvent.InteractionKeyMappingTriggered e) {
        if (ClientCombatHud.get().inCombat) {
            e.setCanceled(true);
        }
    }
}

