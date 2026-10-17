package fr.wakfu.rpg.client;

import fr.wakfu.rpg.network.Network;
import fr.wakfu.rpg.network.SocialActionPacket;
import fr.wakfu.rpg.network.SocialDataPacket;
import fr.wakfu.rpg.network.SocialRequestPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * v0.25 : MENU SOCIAL — onglets Joueurs / Amis / Groupe.
 * Ajouter des amis par pseudo, inviter dans son groupe, défier en duel 1v1,
 * accepter / refuser / quitter — tout à la souris, sans commandes.
 * Parchemin clair (même charte que le Grimoire).
 */
public class SocialScreen extends Screen {

    private static final int INK = 0xFF241B10;
    private static final int INK_TITLE = 0xFF3E2C18;
    private static final int INK_MUTED = 0xFF55462E;
    private static final int INK_FAINT = 0xFF6F5F47;
    private static final int INK_GREEN = 0xFF2E6B2E;
    private static final int INK_RED = 0xFF8A2A20;
    private static final int INK_GOLD = 0xFF7A5A08;

    private static final int PW = 360, PH = 240;

    private int tab = 0;               // 0 joueurs, 1 amis, 2 groupe
    private int scrollJ = 0, scrollF = 0;
    private String pseudo = "";
    private boolean fieldFocus = false;
    private final List<int[]> rects = new ArrayList<>();
    private int px, py;

    public SocialScreen() {
        super(Component.literal("Menu Social"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void init() {
        Network.sendToServer(new SocialRequestPacket());
    }

    // ---------------------------------------------------------------- rendu
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        rects.clear();
        px = width / 2 - PW / 2;
        py = height / 2 - PH / 2;
        g.fill(px, py, px + PW, py + PH, 0xFF3A2A1A);
        g.fill(px + 2, py + 2, px + PW - 2, py + PH - 2, 0xFF8A6A3A);
        g.fill(px + 4, py + 4, px + PW - 4, py + PH - 4, 0xFFEDE0C2);
        g.fill(px + 4, py + 4, px + PW - 4, py + 22, 0xFFE0CDA2);
        g.fill(px + 4, py + 22, px + PW - 4, py + 23, 0xFF9A7A2A);

        // retour + titre + bourse
        button(g, px + 5, py + 5, 62, 13, "← Grimoire", 800, mx, my);
        g.drawString(GothicFont.get(), "Menu Social", px + 74, py + 7, INK_TITLE, false);
        String money = "$ " + ClientMoney.fmt(ClientMoney.get());
        g.drawString(font, money, px + PW - 12 - font.width(money), py + 11, INK_GOLD);

        // onglets
        String[] labels = {"Joueurs", "Amis", "Groupe"};
        for (int i = 0; i < 3; i++) {
            int tx = px + 8, ty = py + 30 + i * 24, tw = 80, th = 20;
            boolean sel = i == tab;
            boolean hover = mx >= tx && mx < tx + tw && my >= ty && my < ty + th;
            g.fill(tx, ty, tx + tw, ty + th, sel ? 0xFFE8DCC0 : (hover ? 0xD8C8A8 : 0xC4B294));
            outline(g, tx, ty, tw, th, sel ? INK_TITLE : INK_MUTED);
            g.drawString(font, labels[i], tx + tw / 2 - font.width(labels[i]) / 2, ty + 6,
                    sel ? INK_TITLE : INK_MUTED);
            rects.add(new int[]{tx, ty, tw, th, i});
        }
        // aide colonne gauche
        String[] aide = {"Tout se fait à la", "souris : plus besoin", "de commandes.", "",
                "● vert = en ligne", "○ gris = hors ligne"};
        for (int i = 0; i < aide.length; i++) {
            g.drawString(font, aide[i], px + 10, py + 112 + i * 11, INK_FAINT);
        }

        int cx = px + 96, cy = py + 28, cw = 254, ch = 200;
        if (tab == 0) renderOnline(g, cx, cy, cw, ch, mx, my);
        else if (tab == 1) renderFriends(g, cx, cy, cw, ch, mx, my);
        else renderParty(g, cx, cy, cw, ch, mx, my);

        String hint = "[Échap] Fermer";
        g.drawString(font, hint, px + PW - 12 - font.width(hint), py + PH - 12, INK_FAINT);
    }

    private void renderOnline(GuiGraphics g, int cx, int cy, int cw, int ch, int mx, int my) {
        List<SocialDataPacket.Plr> list = ClientSocial.online;
        if (list.isEmpty()) {
            g.drawString(font, "Aucun autre joueur en ligne pour le moment.", cx + 8, cy + 12, INK_MUTED);
            return;
        }
        int maxRows = (ch - 10) / 26;
        scrollJ = Math.max(0, Math.min(scrollJ, list.size() - maxRows));
        for (int i = scrollJ; i < list.size(); i++) {
            int y = cy + 6 + (i - scrollJ) * 26;
            if (y + 24 > cy + ch) break;
            SocialDataPacket.Plr p = list.get(i);
            g.fill(cx + 2, y, cx + cw - 2, y + 24, (i % 2 == 0) ? 0xFFE5D5B0 : 0xFFEADFC2);
            g.drawString(font, "●", cx + 6, y + 8, INK_GREEN);
            String nm = trunc(p.name(), 74);
            g.drawString(font, nm, cx + 16, y + 4, INK);
            g.drawString(font, "Niv " + p.level(), cx + 16, y + 13, INK_FAINT);
            button(g, cx + 98, y + 4, 52, 16, "Groupe", 10 + i, mx, my);
            button(g, cx + 154, y + 4, 44, 16, "Duel", 20 + i, mx, my);
            button(g, cx + 202, y + 4, 44, 16, "Ami", 30 + i, mx, my);
        }
        if (list.size() > maxRows) {
            g.drawString(font, "▲▼ molette", cx + cw - 56, cy + ch - 10, INK_FAINT);
        }
    }

    private void renderFriends(GuiGraphics g, int cx, int cy, int cw, int ch, int mx, int my) {
        // champ pseudo
        g.drawString(font, "Pseudo :", cx + 4, cy + 8, INK_MUTED);
        int fx = cx + 44, fy = cy + 4, fw = 118, fh = 14;
        g.fill(fx, fy, fx + fw, fy + fh, fieldFocus ? 0xFFF8F0D8 : 0xFFEADFC2);
        outline(g, fx, fy, fw, fh, fieldFocus ? INK_TITLE : INK_MUTED);
        if (pseudo.isEmpty() && !fieldFocus) {
            g.drawString(font, "nom du joueur…", fx + 4, fy + 3, INK_FAINT);
        } else {
            g.drawString(font, pseudo, fx + 4, fy + 3, INK);
            if (fieldFocus && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cwid = font.width(pseudo);
                g.fill(fx + 5 + cwid, fy + 3, fx + 6 + cwid, fy + 11, INK);
            }
        }
        button(g, cx + 166, fy, 52, 14, "Ajouter", 70, mx, my);
        if (!pseudo.isEmpty()) button(g, cx + 222, fy, 20, 14, "✕", 900, mx, my);

        List<SocialDataPacket.Plr> list = ClientSocial.friends;
        int y0 = cy + 24;
        if (list.isEmpty()) {
            g.drawString(font, "Aucun ami — ajoute un pseudo ci-dessus,", cx + 8, y0 + 8, INK_MUTED);
            g.drawString(font, "ou clique sur [Ami] dans l'onglet Joueurs.", cx + 8, y0 + 20, INK_MUTED);
            return;
        }
        int maxRows = (ch - 30) / 26;
        scrollF = Math.max(0, Math.min(scrollF, list.size() - maxRows));
        for (int i = scrollF; i < list.size(); i++) {
            int y = y0 + (i - scrollF) * 26;
            if (y + 24 > cy + ch) break;
            SocialDataPacket.Plr p = list.get(i);
            g.fill(cx + 2, y, cx + cw - 2, y + 24, (i % 2 == 0) ? 0xFFE5D5B0 : 0xFFEADFC2);
            g.drawString(font, p.online() ? "●" : "○", cx + 6, y + 8, p.online() ? INK_GREEN : INK_FAINT);
            g.drawString(font, trunc(p.name(), 74), cx + 16, y + 4, INK);
            g.drawString(font, p.online() ? "Niv " + p.level() : "hors ligne", cx + 16, y + 13, INK_FAINT);
            if (p.online()) {
                button(g, cx + 98, y + 4, 52, 16, "Groupe", 40 + i, mx, my);
                button(g, cx + 154, y + 4, 44, 16, "Duel", 50 + i, mx, my);
            }
            button(g, cx + 202, y + 4, 44, 16, "Retirer", 60 + i, mx, my);
        }
    }

    private void renderParty(GuiGraphics g, int cx, int cy, int cw, int ch, int mx, int my) {
        int y = cy + 8;
        if (!ClientSocial.pendingFrom.isEmpty()) {
            g.fill(cx + 2, y, cx + cw - 2, y + 34, 0xFFF2E4B8);
            outline(g, cx + 2, y, cw - 4, 34, INK_GOLD);
            g.drawString(font, "⚔ " + ClientSocial.pendingFrom + " t'invite dans son groupe.",
                    cx + 8, y + 4, INK_TITLE);
            button(g, cx + 8, y + 15, 58, 15, "Accepter", 71, mx, my);
            button(g, cx + 72, y + 15, 58, 15, "Refuser", 72, mx, my);
            y += 42;
        }
        if (ClientSocial.members.isEmpty()) {
            g.drawString(font, "Tu n'es pas dans un groupe.", cx + 8, y + 6, INK_MUTED);
            g.drawString(font, "Onglets Joueurs / Amis → [Groupe] pour inviter.", cx + 8, y + 20, INK_MUTED);
            g.drawString(font, "Le chef invite (4 max) ; en combat PvE, le groupe", cx + 8, y + 40, INK_FAINT);
            g.drawString(font, "est invité à rejoindre l'arène.", cx + 8, y + 52, INK_FAINT);
            return;
        }
        g.drawString(font, "Groupe (" + ClientSocial.members.size() + "/4) :", cx + 4, y, INK_TITLE);
        y += 14;
        UUID me = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null;
        for (int i = 0; i < ClientSocial.members.size() && i < 6; i++) {
            SocialDataPacket.Plr m = ClientSocial.members.get(i);
            boolean lead = m.id().equals(ClientSocial.leader);
            g.drawString(font, (lead ? "§6★ " : "§7• ") + m.name()
                            + (m.id().equals(me) ? " §8(toi)" : "") + (m.online() ? "" : " §8— hors ligne"),
                    cx + 10, y + 2, lead ? INK_GOLD : INK);
            y += 14;
        }
        button(g, cx + 4, y + 6, 92, 16, "Quitter le groupe", 73, mx, my);
    }

    // ---------------------------------------------------------------- widgets
    private void button(GuiGraphics g, int x, int y, int w, int h, String label, int id, int mx, int my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        g.fill(x, y, x + w, y + h, hover ? 0xFFD8C8A0 : 0xFFC4B294);
        outline(g, x, y, w, h, INK_MUTED);
        g.drawString(font, label, x + w / 2 - font.width(label) / 2, y + h / 2 - 4, INK_TITLE);
        rects.add(new int[]{x, y, w, h, id});
    }

    private void outline(GuiGraphics g, int x, int y, int w, int h, int col) {
        g.fill(x, y, x + w, y + 1, col);
        g.fill(x, y + h - 1, x + w, y + h, col);
        g.fill(x, y, x + 1, y + h, col);
        g.fill(x + w - 1, y, x + w, y + h, col);
    }

    private String trunc(String s, int maxW) {
        while (font.width(s) > maxW && s.length() > 6) s = s.substring(0, s.length() - 2);
        return s;
    }

    // ---------------------------------------------------------------- entrées
    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (tab == 1) {
            int fx = px + 96 + 44, fy = py + 28 + 4;
            if (mx >= fx && mx < fx + 118 && my >= fy && my < fy + 14) {
                fieldFocus = true;
                return true;
            }
            fieldFocus = false;
        }
        for (int[] r : rects) {
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                click(r[4]);
                Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int d = delta > 0 ? -1 : 1;
        if (tab == 0) scrollJ = Math.max(0, scrollJ + d);
        if (tab == 1) scrollF = Math.max(0, scrollF + d);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (fieldFocus) {
            if (key == 259) {   // backspace
                if (!pseudo.isEmpty()) pseudo = pseudo.substring(0, pseudo.length() - 1);
                return true;
            }
            if (key == 257) {   // entrée : ajouter
                if (!pseudo.isEmpty()) act(0, pseudo);
                pseudo = "";
                return true;
            }
            if (key == 256) {   // échap : ferme la saisie
                fieldFocus = false;
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (fieldFocus && pseudo.length() < 16
                && ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')) {
            pseudo += c;
            return true;
        }
        return super.charTyped(c, mods);
    }

    private void act(int action, String target) {
        Network.sendToServer(new SocialActionPacket(action, target));
    }

    private void click(int id) {
        if (id >= 0 && id <= 2) { tab = id; return; }
        if (id == 800) { Minecraft.getInstance().setScreen(new GrimoireScreen()); return; }
        if (id == 900) { pseudo = ""; return; }
        if (id == 70) {
            if (!pseudo.isEmpty()) act(0, pseudo);
            pseudo = "";
            return;
        }
        if (id == 71) { act(4, ""); return; }
        if (id == 72) { act(5, ""); return; }
        if (id == 73) { act(6, ""); return; }
        if (id >= 10 && id < 10 + ClientSocial.online.size()) {
            act(2, ClientSocial.online.get(id - 10).name());
            return;
        }
        if (id >= 20 && id < 20 + ClientSocial.online.size()) {
            act(3, ClientSocial.online.get(id - 20).name());
            return;
        }
        if (id >= 30 && id < 30 + ClientSocial.online.size()) {
            act(0, ClientSocial.online.get(id - 30).name());
            return;
        }
        if (id >= 40 && id < 40 + ClientSocial.friends.size()) {
            SocialDataPacket.Plr f = ClientSocial.friends.get(id - 40);
            if (f.online()) act(2, f.name());
            return;
        }
        if (id >= 50 && id < 50 + ClientSocial.friends.size()) {
            SocialDataPacket.Plr f = ClientSocial.friends.get(id - 50);
            if (f.online()) act(3, f.name());
            return;
        }
        if (id >= 60 && id < 60 + ClientSocial.friends.size()) {
            act(1, ClientSocial.friends.get(id - 60).name());
        }
    }
}
