package fr.wakfu.rpg.client;

import fr.wakfu.rpg.core.ModCapabilities;
import fr.wakfu.rpg.core.Quests;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Minicarte (touche M pour afficher/masquer, activee par defaut).
 * v0.12 : rendu 100% en g.fill() (plus de texture dynamique GL = plus de
 * risque d'ecran vide), echantillonnage des couleurs de carte vanilla
 * toutes les 500 ms, points d'entites (monstres / PNJ / joueurs).
 */
public class Minimap {

    private static boolean enabled = true;
    private static final int RANGE = 32;          // rayon en blocs
    private static final int SIZE = RANGE * 2;    // 64 px
    private static final int SCALE = 2;
    private static final int[] PIXELS = new int[SIZE * SIZE];
    private static long lastSample = 0;

    public static void toggle() {
        enabled = !enabled;
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void draw(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (!enabled || mc.player == null || mc.level == null) return;
        try {
            long now = System.currentTimeMillis();
            if (now - lastSample > 500) {
                sample(mc);
                lastSample = now;
            }

            int sw = mc.getWindow().getGuiScaledWidth();
            int x0 = sw - SIZE * SCALE - 10;
            int y0 = 10;

            // cadre gothique
            g.fill(x0 - 3, y0 - 3, x0 + SIZE * SCALE + 3, y0 + SIZE * SCALE + 3, 0xFF141018);
            g.fill(x0 - 2, y0 - 2, x0 + SIZE * SCALE + 2, y0 + SIZE * SCALE + 2, 0xFF6A4E8C);

            // terrain (cache de pixels -> quads pleins, sans texture GL)
            for (int dz = 0; dz < SIZE; dz++) {
                for (int dx = 0; dx < SIZE; dx++) {
                    int col = PIXELS[dz * SIZE + dx];
                    int px = x0 + dx * SCALE;
                    int py = y0 + dz * SCALE;
                    g.fill(px, py, px + SCALE, py + SCALE, col);
                }
            }

            // entites
            Vec3 pp = mc.player.position();
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e == mc.player) continue;
                double dx = e.getX() - pp.x;
                double dz = e.getZ() - pp.z;
                if (Math.abs(dx) > RANGE || Math.abs(dz) > RANGE) continue;
                int ex = x0 + (SIZE / 2) + (int) (dx * SCALE);
                int ey = y0 + (SIZE / 2) + (int) (dz * SCALE);
                int col;
                String name = e.getClass().getSimpleName();
                if (e instanceof Player) col = 0xFF55FF55;
                else if (name.contains("Monster")) col = 0xFFFF5555;
                else if (name.contains("Npc")) col = 0xFFFFDD55;
                else continue;
                g.fill(ex - 1, ey - 1, ex + 2, ey + 2, col);
            }

            // fleche joueur
            int cx = x0 + SIZE * SCALE / 2;
            int cy = y0 + SIZE * SCALE / 2;
            g.fill(cx - 1, cy - 2, cx + 2, cy + 1, 0xFFFFFFFF);
            g.fill(cx - 1, cy + 1, cx + 2, cy + 3, 0xFFCCCCCC);

            // titre + quete
            g.drawString(GothicFont.get(), "§dCarte", x0, y0 - 12, 0xDDCCFF);
            mc.player.getCapability(ModCapabilities.RPG).ifPresent(data -> {
                var q = Quests.current(data);
                if (q != null) {
                    String t = "§6" + q.title + " §7" + Math.min(data.getQuestProgress(), q.target) + "/" + q.target;
                    g.drawString(mc.font, t, x0 - 60, y0 + SIZE * SCALE + 6, 0xFFFFFF);
                }
            });
        } catch (Exception ignored) {
            // jamais de crash HUD : la carte disparait proprement si souci
        }
    }

    private static void sample(Minecraft mc) {
        BlockPos pp = mc.player.blockPosition();
        for (int dz = 0; dz < SIZE; dz++) {
            for (int dx = 0; dx < SIZE; dx++) {
                int wx = pp.getX() + dx - RANGE;
                int wz = pp.getZ() + dz - RANGE;
                int col = 0xFF1A1620;
                try {
                    int hy = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, wx, wz);
                    if (hy > mc.level.getMinBuildHeight()) {
                        var bs = mc.level.getBlockState(new BlockPos(wx, hy - 1, wz));
                        col = bs.getMapColor(mc.level, new BlockPos(wx, hy - 1, wz)).col | 0xFF000000;
                    }
                } catch (Exception ignored) {
                }
                PIXELS[dz * SIZE + dx] = col;
            }
        }
    }

    private Minimap() {}
}
