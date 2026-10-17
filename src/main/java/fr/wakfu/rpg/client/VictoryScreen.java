package fr.wakfu.rpg.client;

import fr.wakfu.rpg.network.VictoryPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * v0.27 : Ecran de victoire de combat — affiche les recompenses,
 * les monstres vaincus, l'XP gagnee et l'argent obtenu.
 * Se ferme avec Echap, Entree, ou clic.
 */
public class VictoryScreen extends Screen {

    private final VictoryPacket data;
    private int ticksOpen = 0;
    private float fadeAlpha = 0f;

    private VictoryScreen(VictoryPacket data) {
        super(Component.literal("Victoire"));
        this.data = data;
    }

    public static void show(VictoryPacket pkt) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new VictoryScreen(pkt));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        ticksOpen++;
        // Fade in pendant 10 ticks
        fadeAlpha = Math.min(1f, ticksOpen / 10f);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);

        int sw = width;
        int sh = height;
        int alpha = (int)(fadeAlpha * 220) << 24;

        // --- Fond semi-transparent ---
        g.fill(0, 0, sw, sh, 0x00000000 | alpha);

        // --- Cadre central ---
        int panelW = Math.min(360, sw - 40);
        int panelH = 260;
        int px = (sw - panelW) / 2;
        int py = (sh - panelH) / 2;

        // Bordure doree
        g.fill(px - 2, py - 2, px + panelW + 2, py + panelH + 2, 0xFFDDAA33);
        // Fond sombre
        g.fill(px, py, px + panelW, py + panelH, 0xEE111122);
        // Barre doree en haut
        g.fill(px, py, px + panelW, py + 3, 0xFFFFDD55);

        int cx = px + panelW / 2;
        int y = py + 12;

        // --- Titre ---
        String title = data.title != null ? data.title : "Victoire !";
        int titleW = font.width(title);
        g.drawString(font, "§6" + title, cx - titleW / 2, y, 0xFFFFDD55);
        y += 22;

        // --- Separateur ---
        g.fill(px + 20, y, px + panelW - 20, y + 1, 0xFF555577);
        y += 10;

        // --- Monstres vaincus ---
        g.drawString(font, "§7Monstres vaincus :", px + 16, y, 0xFFAAAAAA);
        y += 14;
        for (String mob : data.mobNames) {
            g.drawString(font, "§c  \u2022 §f" + mob, px + 20, y, 0xFFFFFFFF);
            y += 12;
            if (y > py + panelH - 100) {
                g.drawString(font, "§7  ... et " + (data.mobNames.size() - data.mobNames.indexOf(mob) - 1) + " autre(s)", px + 20, y, 0xFFAAAAAA);
                break;
            }
        }
        y += 8;

        // --- Separateur ---
        g.fill(px + 20, y, px + panelW - 20, y + 1, 0xFF555577);
        y += 10;

        // --- Recompenses ---
        g.drawString(font, "§6Recompenses :", px + 16, y, 0xFFDDAA33);
        y += 14;

        // XP
        g.drawString(font, "§a  + " + data.totalXp + " XP", px + 20, y, 0xFF55FF55);
        y += 12;

        // Argent
        if (data.totalMoney > 0) {
            g.drawString(font, "§6  + " + data.totalMoney + " $", px + 20, y, 0xFFFFDD55);
            y += 12;
        }

        // Loot
        for (String item : data.lootItems) {
            g.drawString(font, "§b  + " + item, px + 20, y, 0xFF55DDFF);
            y += 12;
            if (y > py + panelH - 30) break;
        }

        // --- Stats en bas ---
        int bottomY = py + panelH - 22;
        g.fill(px + 20, bottomY - 4, px + panelW - 20, bottomY - 3, 0xFF555577);
        int durationSec = (int)(data.duration / 20);
        String stats = "§7Tours : §f" + data.round + "  §7|  §7Duree : §f" + durationSec + "s";
        int statsW = font.width(stats);
        g.drawString(font, stats, cx - statsW / 2, bottomY, 0xFFAAAAAA);

        // --- Instruction ---
        String hint = "§7Cliquez ou appuyez sur Echap pour continuer";
        int hintW = font.width(hint);
        g.drawString(font, hint, cx - hintW / 2, py + panelH + 8, 0xFF888888);

        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        Minecraft.getInstance().setScreen(null);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Echap, Entree, Espace = fermer
        Minecraft.getInstance().setScreen(null);
        return true;
    }
}