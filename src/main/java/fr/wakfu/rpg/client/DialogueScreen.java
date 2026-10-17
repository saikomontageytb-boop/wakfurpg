package fr.wakfu.rpg.client;

import fr.wakfu.rpg.core.DialogueNode;
import fr.wakfu.rpg.network.Network;
import net.minecraft.client.Minecraft;
import fr.wakfu.rpg.network.NpcChoicePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Ecran de dialogue (v0.13) : panneau parchemin dont la hauteur s'adapte au
 * texte, boutons sur 1 colonne (ou 2 si beaucoup de choix) — plus aucun
 * chevauchement texte/boutons, meme avec 9 choix (forgeron).
 */
public class DialogueScreen extends Screen {

    private final int entityId;
    private final DialogueNode node;
    private final List<int[]> rects = new ArrayList<>(); // x,y,w,h,idx

    public DialogueScreen(int entityId, DialogueNode node) {
        super(Component.literal(node.npcName));
        this.entityId = entityId;
        this.node = node;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        rects.clear();

        int n = node.choices.size();
        int cols = n >= 7 ? 2 : 1;
        int rows = (n + cols - 1) / cols;
        int bw = cols == 1 ? 220 : 148;
        int bh = 20;
        int step = 23;
        int gap = cols == 2 ? 8 : 0;

        int panelW = cols == 2 ? Math.max(280, bw * 2 + gap + 24) : 248;
        int cx = width / 2;

        // texte : hauteur mesuree avant de poser le panneau
        List<FormattedCharSequence> lines = font.split(Component.literal(node.text), panelW - 28);
        int textH = lines.size() * 11;
        int panelH = 24 + textH + 8;

        int buttonsH = rows * step + 4;
        int totalH = panelH + 8 + buttonsH;
        int py = Math.max(6, height / 2 - totalH / 2 - 8);
        int px = cx - panelW / 2;

        // panneau parchemin (atlas etire : bordure fine, rendu propre a toute taille)
        g.blit(WakfuHud.ATLAS, px, py, 0, 128, panelW, panelH);

        // nom du PNJ (titre)
        g.drawCenteredString(font, "§6" + node.npcName, cx, py + 6, 0x442211);

        // texte wrappe style encre
        int ty = py + 20;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, px + 14, ty, 0x33261A);
            ty += 11;
        }

        // boutons sous le panneau, centre par colonne
        int by0 = py + panelH + 8;
        int idx = 0;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                if (idx >= n) break;
                int bx = cols == 1 ? cx - bw / 2 : cx - (bw * 2 + gap) / 2 + col * (bw + gap);
                int by = by0 + row * step;
                boolean hover = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
                g.blit(WakfuHud.ATLAS, bx, by, 0, hover ? 104 : 80, bw, bh);
                g.drawCenteredString(font, Component.literal(node.choices.get(idx)),
                        bx + bw / 2, by + 5, hover ? 0xFFFFAA : 0xE8E8E8);
                rects.add(new int[]{bx, by, bw, bh, idx});
                idx++;
            }
        }

        super.render(g, mx, my, pt);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int[] r : rects) {
                if (mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]) {
                    pick(r[4]);
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void pick(int idx) {
        // v0.16 : le choix « Fusion » du forgeron ouvre l'Atelier sombre (ecran client)
        if (idx >= 0 && idx < node.choices.size() && node.choices.get(idx).contains("Fusion")) {
            Minecraft.getInstance().setScreen(new ForgeScreen());
            return;
        }
        Network.sendToServer(new NpcChoicePacket(entityId, idx));
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
