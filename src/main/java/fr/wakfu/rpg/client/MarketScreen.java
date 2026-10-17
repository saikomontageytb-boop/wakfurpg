package fr.wakfu.rpg.client;

import fr.wakfu.rpg.core.Market;
import fr.wakfu.rpg.network.MarketActionPacket;
import fr.wakfu.rpg.network.Network;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.25 : HÔTEL DES VENTES — thème sombre (même charte que Sac & Équipement).
 * Onglet Acheter : annonces des joueurs (icône, vendeur, prix $) + retrait de
 * ses propres annonces. Onglet Vendre : grille d'inventaire, vente immédiate
 * au serveur (prix très bas) ou mise en vente au prix de son choix.
 */
public class MarketScreen extends Screen {

    private static final int PW = 400, PH = 250;
    private static final int SLOT = 22;

    private static final int GOLD = 0xFFDD66;
    private static final int TITLE = 0xFFFFCC77;
    private static final int TEXT = 0xFFE8DCC8;
    private static final int MUTED = 0xFFB8A888;
    private static final int FAINT = 0xFF8A7A66;

    private int tab = 0;            // 0 acheter, 1 vendre
    private int scroll = 0;
    private int sel = -1;           // slot d'inventaire sélectionné (onglet vendre)
    private String priceIn = "";
    private boolean priceFocus = false;
    private final List<int[]> rects = new ArrayList<>();
    private ItemStack hoverStack = ItemStack.EMPTY;
    private int px, py;

    public MarketScreen() {
        super(Component.literal("Hôtel des Ventes"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        rects.clear();
        hoverStack = ItemStack.EMPTY;
        px = width / 2 - PW / 2;
        py = height / 2 - PH / 2;
        g.fill(px, py, px + PW, py + PH, 0xFF141018);
        g.fill(px + 2, py + 2, px + PW - 2, py + PH - 2, 0xFF3A2E44);
        g.fill(px + 4, py + 4, px + PW - 4, py + PH - 4, 0xFF241C2A);
        g.fill(px + 4, py + 4, px + PW - 4, py + 26, 0xFF332840);
        g.fill(px + 4, py + 26, px + PW - 4, py + 27, 0xFFAA8844);

        g.drawString(font, "Hôtel des Ventes", px + 14, py + 10, TITLE);
        String money = "$ " + ClientMoney.fmt(ClientMoney.get());
        g.drawString(font, money, px + PW - 14 - font.width(money), py + 13, GOLD);

        // onglets
        String[] labels = {"Acheter", "Vendre"};
        for (int i = 0; i < 2; i++) {
            int tx = px + 8, ty = py + 34 + i * 24, tw = 80, th = 20;
            boolean s = i == tab;
            boolean hover = mx >= tx && mx < tx + tw && my >= ty && my < ty + th;
            g.fill(tx, ty, tx + tw, ty + th, s ? 0xFF3A2E44 : (hover ? 0xFF2E2436 : 0xFF1E1826));
            outline(g, tx, ty, tw, th, 0xFFAA8844);
            g.drawString(font, labels[i], tx + tw / 2 - font.width(labels[i]) / 2, ty + 6,
                    s ? TITLE : MUTED);
            rects.add(new int[]{tx, ty, tw, th, i});
        }
        // colonne gauche : aide
        String[] aide = {"Vendre au serveur :", "prix très bas, immédiat.", "",
                "Entre joueurs : fixe", "ton prix ; l'acheteur", "paie en $ et tu es",
                "crédité même hors ligne.", "", "100 annonces max."};
        for (int i = 0; i < aide.length; i++) {
            g.drawString(font, aide[i], px + 10, py + 96 + i * 11, FAINT);
        }

        if (tab == 0) renderBuy(g, mx, my);
        else renderSell(g, mx, my);

        String hint = "Molette : défiler  ·  Échap : fermer";
        g.drawString(font, hint, px + PW / 2 - font.width(hint) / 2, py + PH - 12, FAINT);

        if (!hoverStack.isEmpty()) {
            g.renderTooltip(font, hoverStack, mx, my);
        }
    }

    // ---------------------------------------------------------------- acheter
    private void renderBuy(GuiGraphics g, int mx, int my) {
        int cx = px + 96, cy = py + 30, cw = PW - 96 - 8, ch = PH - 30 - 18;
        List<Market.Listing> list = ClientMarket.listings;
        if (list.isEmpty()) {
            g.drawString(font, "Aucune annonce pour le moment —", cx + 10, cy + 14, MUTED);
            g.drawString(font, "vends tes objets dans l'onglet « Vendre » !", cx + 10, cy + 26, MUTED);
            return;
        }
        java.util.UUID me = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.getUUID() : null;
        int maxRows = (ch - 8) / 26;
        scroll = Math.max(0, Math.min(scroll, list.size() - maxRows));
        for (int i = scroll; i < list.size(); i++) {
            int y = cy + 4 + (i - scroll) * 26;
            if (y + 24 > cy + ch) break;
            Market.Listing l = list.get(i);
            boolean mine = me != null && l.seller().equals(me);
            g.fill(cx + 2, y, cx + cw - 2, y + 24, (i % 2 == 0) ? 0xFF2C2434 : 0xFF281F30);
            g.renderItem(l.stack(), cx + 5, y + 4);
            g.renderItemDecorations(font, l.stack(), cx + 5, y + 4);
            if (mx >= cx + 2 && mx < cx + 160 && my >= y && my < y + 24) hoverStack = l.stack();
            String nm = trunc(l.stack().getHoverName().getString(), 108);
            g.drawString(font, nm, cx + 26, y + 3, TEXT);
            g.drawString(font, mine ? "§eton annonce" : "§8par " + trunc(l.sellerName(), 60),
                    cx + 26, y + 13, mine ? GOLD : FAINT);
            String pr = "$ " + ClientMoney.fmt(l.price());
            g.drawString(font, pr, cx + cw - 70 - font.width(pr), y + 8, GOLD);
            if (mine) {
                button(g, cx + cw - 62, y + 4, 58, 16, "Retirer", 200 + i, mx, my);
            } else {
                button(g, cx + cw - 62, y + 4, 58, 16, "Acheter", 100 + i, mx, my);
            }
        }
    }

    // ---------------------------------------------------------------- vendre
    private void renderSell(GuiGraphics g, int mx, int my) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        var items = player.getInventory().items;
        int gx = px + 12, gy = py + 36;
        for (int i = 0; i < 36; i++) {
            int sx = gx + (i % 6) * (SLOT + 2), sy = gy + (i / 6) * (SLOT + 2);
            ItemStack st = i < items.size() ? items.get(i) : ItemStack.EMPTY;
            g.fill(sx, sy, sx + SLOT, sy + SLOT, 0xFF1A1420);
            outline(g, sx, sy, SLOT, SLOT, i == sel ? 0xFFFFCC55 : 0xFF3A2E44);
            if (!st.isEmpty()) {
                g.renderItem(st, sx + 3, sy + 3);
                g.renderItemDecorations(font, st, sx + 3, sy + 3);
                if (mx >= sx && mx < sx + SLOT && my >= sy && my < sy + SLOT) hoverStack = st;
            }
            rects.add(new int[]{sx, sy, SLOT, SLOT, 300 + i});
        }
        g.drawString(font, "Clic : sélectionner un objet", gx, gy + 6 * (SLOT + 2) + 4, FAINT);

        int rx = px + 178, ry = py + 36, rw = 208;
        ItemStack st = (sel >= 0 && sel < items.size()) ? items.get(sel) : ItemStack.EMPTY;
        if (st.isEmpty()) {
            g.drawString(font, "Sélectionne un objet de ton", rx, ry + 8, MUTED);
            g.drawString(font, "inventaire pour voir ses prix.", rx, ry + 20, MUTED);
            return;
        }
        g.drawString(font, trunc(st.getHoverName().getString(), rw), rx, ry, TEXT);
        int unit = Market.serverPrice(st);
        if (unit <= 0) {
            g.drawString(font, "Cet objet est invendable", rx, ry + 18, 0xFFCC6655);
            g.drawString(font, "(objet de quête ou de combat).", rx, ry + 30, FAINT);
            return;
        }
        long total = (long) unit * st.getCount();
        g.drawString(font, "Valeur serveur : " + unit + " $/unité · §eTotal : " + total + " $", rx, ry + 18, MUTED);
        button(g, rx, ry + 32, rw, 17, "Vendre au serveur  +" + total + " $", 400, mx, my);

        g.fill(rx, ry + 58, rx + rw, ry + 59, 0xFFAA8844);
        g.drawString(font, "Mise en vente entre joueurs :", rx, ry + 64, TITLE);
        int fx = rx, fy = ry + 78, fw = 84, fh = 15;
        g.fill(fx, fy, fx + fw, fy + fh, priceFocus ? 0xFF342A3E : 0xFF1A1420);
        outline(g, fx, fy, fw, fh, priceFocus ? 0xFFFFCC55 : 0xFFAA8844);
        String shown = priceIn.isEmpty() ? (unit * 2) + " $" : priceIn;
        g.drawString(font, shown, fx + 4, fy + 4, priceIn.isEmpty() ? FAINT : GOLD);
        if (priceFocus && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cwid = font.width(priceIn);
            g.fill(fx + 5 + cwid, fy + 3, fx + 6 + cwid, fy + 12, GOLD);
        }
        button(g, rx + 90, fy - 1, rw - 90, 17, "Mettre en vente", 401, mx, my);
        g.drawString(font, "Prix vide → 2× la valeur serveur.", rx, ry + 100, FAINT);
        g.drawString(font, "L'objet quitte ton sac jusqu'à la vente.", rx, ry + 112, FAINT);
    }

    // ---------------------------------------------------------------- widgets
    private void button(GuiGraphics g, int x, int y, int w, int h, String label, int id, int mx, int my) {
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
        g.fill(x, y, x + w, y + h, hover ? 0xFF4A3A56 : 0xFF3A2E44);
        outline(g, x, y, w, h, 0xFFAA8844);
        g.drawString(font, label, x + w / 2 - font.width(label) / 2, y + h / 2 - 4, TEXT);
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
        // champ prix (onglet vendre)
        if (tab == 1 && sel >= 0) {
            int fx = px + 178, fy = py + 36 + 78;
            if (mx >= fx && mx < fx + 84 && my >= fy && my < fy + 15) {
                priceFocus = true;
                return true;
            }
            priceFocus = false;
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
        if (tab == 0) scroll = Math.max(0, scroll + (delta > 0 ? -1 : 1));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (priceFocus) {
            if (key == 259 && !priceIn.isEmpty()) {
                priceIn = priceIn.substring(0, priceIn.length() - 1);
                return true;
            }
            if (key == 257) {
                sendList();
                return true;
            }
            if (key == 256) {
                priceFocus = false;
                return true;
            }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (priceFocus && c >= '0' && c <= '9' && priceIn.length() < 7) {
            priceIn += c;
            return true;
        }
        return super.charTyped(c, mods);
    }

    private void sendList() {
        if (sel < 0) return;
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack st = player.getInventory().items.get(sel);
        int price;
        if (priceIn.isEmpty()) {
            price = Math.max(1, Market.serverPrice(st) * 2);
        } else {
            try {
                price = Integer.parseInt(priceIn);
            } catch (NumberFormatException e) {
                price = 0;
            }
        }
        Network.sendToServer(new MarketActionPacket(1, sel, price));
        sel = -1;
        priceIn = "";
        priceFocus = false;
    }

    private void click(int id) {
        if (id == 0 || id == 1) { tab = id; return; }
        if (id >= 300 && id < 336) {
            int slot = id - 300;
            var player = Minecraft.getInstance().player;
            if (player != null && !player.getInventory().items.get(slot).isEmpty()) {
                sel = slot == sel ? -1 : slot;
                priceIn = "";
            }
            return;
        }
        if (id == 400) {
            if (sel >= 0) Network.sendToServer(new MarketActionPacket(0, sel, 0));
            sel = -1;
            return;
        }
        if (id == 401) {
            sendList();
            return;
        }
        if (id >= 100 && id < 200) {
            int idx = id - 100;
            if (idx < ClientMarket.listings.size()) {
                Network.sendToServer(new MarketActionPacket(2, ClientMarket.listings.get(idx).id(), 0));
            }
            return;
        }
        if (id >= 200 && id < 300) {
            int idx = id - 200;
            if (idx < ClientMarket.listings.size()) {
                Network.sendToServer(new MarketActionPacket(3, ClientMarket.listings.get(idx).id(), 0));
            }
        }
    }
}
