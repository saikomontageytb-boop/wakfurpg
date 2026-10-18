package fr.wakfu.rpg.client;

import fr.wakfu.rpg.WakfuRPG;
import fr.wakfu.rpg.core.MobVariants;
import fr.wakfu.rpg.core.ModCapabilities;
import fr.wakfu.rpg.core.MonsterType;
import fr.wakfu.rpg.core.NpcSpots;
import fr.wakfu.rpg.core.PlayerRpgData;
import fr.wakfu.rpg.core.spell.Spell;
import fr.wakfu.rpg.core.spell.Spells;
import fr.wakfu.rpg.item.WakfuArmorItem;
import fr.wakfu.rpg.item.WakfuTrophyItem;
import fr.wakfu.rpg.item.WakfuWeaponItem;
import fr.wakfu.rpg.network.Network;
import fr.wakfu.rpg.network.SpendPacket;
import fr.wakfu.rpg.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.22 : GRIMOIRE — ecran unique (B ou K) :
 *  - BESTIAIRE : monstres (avec leur butin par zone), zones, donjon,
 *    butin repertorie et PANOPLIES (bonus 4 pieces) + RECHERCHE d'objet.
 *  - SORTS : deblocages 1→50, amelioration par points de sort.
 *  - STATISTIQUES : points de statut + panoplie active.
 * Police vanilla partout (lisible) sauf le titre ; encre foncee sur
 * parchemin ; aucun chevauchement.
 */
public class GrimoireScreen extends Screen {

    private static final ResourceLocation HUB_REF =
            new ResourceLocation(WakfuRPG.MODID, "textures/gui/grimoire_hub_ref.png");
    private static final ResourceLocation PAGE_REF =
            new ResourceLocation(WakfuRPG.MODID, "textures/gui/grimoire_page_ref.png");
    private static final ResourceLocation LOCKED =
            new ResourceLocation(WakfuRPG.MODID, "textures/gui/codex_locked.png");
    private static final ResourceLocation DUNGEON_ICON =
            new ResourceLocation(WakfuRPG.MODID, "textures/gui/codex_dungeon.png");

    private static final int INK = 0xFF241B10;
    private static final int INK_TITLE = 0xFF3E2C18;
    private static final int INK_MUTED = 0xFF55462E;
    private static final int INK_FAINT = 0xFF6F5F47;
    private static final int INK_GREEN = 0xFF2E6B2E;
    private static final int INK_RED = 0xFF8A2A20;
    private static final int INK_GOLD = 0xFF7A5A08;
    private static final int INK_BLUE = 0xFF2E5A8A;

    private static final int PW = 360, PH = 240;
    private static final int CX = 114, CY = 26, CW = 236, CH = 178;
    /** hauteur visible du contenu bestiaire (sous le champ de recherche). */
    private static final int BEST_H = CH - 30;

    private int main = -1;         // v0.25 : -1 menu (hub), 0 bestiaire, 1 sorts, 2 stats
    private int sub = 0;           // bestiaire : 0 monstres, 1 zones, 2 donjon, 3 butin, 4 panoplies
    private int spellScroll = 0;
    private int lootScroll = 0;
    private int monsterScroll = 0;
    private int setsScroll = 0;
    private int npcScroll = 0;
    private String query = "";
    private boolean searchFocus = false;
    private int focusedCard = 0;
    private final List<int[]> rects = new ArrayList<>();  // x,y,w,h,id
    private int px, py;
    private float uiScale = 1.0f;
    private Font uiFont;

    public GrimoireScreen() {
        super(Component.literal("Grimoire"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        rects.clear();
        uiScale = Math.max(0.55f, Math.min(3.0f,
                Math.min((width - 24.0f) / PW, (height - 24.0f) / PH)));
        uiFont = CleanFont.get();
        px = -PW / 2; py = -PH / 2;
        int uiMx = (int) toUiX(mx), uiMy = (int) toUiY(my);
        g.pose().pushPose();
        g.pose().translate(width / 2.0f, height / 2.0f, 0);
        g.pose().scale(uiScale, uiScale, 1.0f);
        var player = Minecraft.getInstance().player;
        if (player == null) { g.pose().popPose(); return; }

        if (main < 0) {
            g.blit(HUB_REF, px, py, PW, PH, 0, 0, 1152, 768, 1152, 768);
            drawHubText(g);
            addHubHitboxes();
            g.pose().popPose();
            super.render(g, mx, my, pt);
            return;
        }

        g.blit(PAGE_REF, px, py, PW, PH, 0, 0, 1152, 768, 1152, 768);
        fillImageRect(g, 430, 83, 685, 46, 0xFFF3EFE1);
        fillImageRect(g, 367, 185, 753, 464, 0xFFF3EFE1);
        drawPageSelection(g);
        drawPageLabels(g);
        addPageHitboxes();
        int cx = px + CX, cy = py + CY;
        drawSearch(g);
        int yc = cy + 32;
        player.getCapability(ModCapabilities.RPG).ifPresent(data -> {
            if (main == 0) {
                switch (sub) {
                    case 0 -> renderMonsters(g, cx, cy, yc, data);
                    case 1 -> renderZones(g, cx, cy, yc, data);
                    case 2 -> renderDungeon(g, cx, cy, yc, data);
                    case 3 -> renderLoot(g, cx, cy, yc, data);
                    case 4 -> renderSets(g, cx, cy, yc, data, player);
                    case 5 -> renderNpcs(g, cx, cy, yc);
                }
            } else if (main == 1) renderSpells(g, cx, cy, yc, data, uiMx, uiMy);
            else renderStats(g, cx, cy, data, player);
        });
        g.pose().popPose();
        super.render(g, mx, my, pt);
    }

    /** Compact, flat text for the Grimoire overlay. */
    private void smallText(GuiGraphics g, String text, int x, int y, int color) {
        text = normalizeUiText(text);
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        float counterScale = 1.0f / uiScale;
        g.pose().scale(counterScale, counterScale, 1.0f);
        g.drawString(uiFont, text, 0, 0, color, false);
        g.pose().popPose();
    }

    /** Text stays crisp at every GUI scale; layout measurements use the same font. */
    private int textWidth(String text) {
        return (int) Math.ceil(uiFont.width(normalizeUiText(text)) / uiScale);
    }

    private static String normalizeUiText(String text) {
        if (text == null || text.isEmpty()) return text;
        return text.replace("\u00c3\u00a9", "é").replace("\u00c3\u00a8", "è")
                .replace("\u00c3\u00aa", "ê").replace("\u00c3\u00a0", "à")
                .replace("\u00c3\u00ae", "î").replace("\u00c3\u00a2", "â")
                .replace("\u00c3\u00b4", "ô").replace("\u00c3\u00a7", "ç")
                .replace("\u00c3\u00b9", "ù").replace("\u00c3\u00bb", "û")
                .replace("\u00c3\u2030", "É").replace("\u00c3\u2014", "×")
                .replace("\u00c2\u00a7", "§").replace("\u00e2\u20ac\u201d", "—")
                .replace("\u00e2\u20ac\u201c", "–").replace("\u00e2\u20ac\u00a6", "…")
                .replace("\u00e2\u2020\u2019", "→").replace("\u00e2\u0153\u201c", "✓")
                .replace("\u00e2\u0080\u00a2", "·").replace("\u00c2\u00ab", "«")
                .replace("\u00c2\u00bb", "»");
    }

    /** GuiGraphics scissors use screen GUI coordinates and do not inherit the canvas pose. */
    private void enableUiScissor(GuiGraphics g, int x1, int y1, int x2, int y2) {
        g.enableScissor((int) Math.floor(width / 2.0 + x1 * uiScale),
                (int) Math.floor(height / 2.0 + y1 * uiScale),
                (int) Math.ceil(width / 2.0 + x2 * uiScale),
                (int) Math.ceil(height / 2.0 + y2 * uiScale));
    }

    private double toUiX(double x) { return (x - width / 2.0) / uiScale; }
    private double toUiY(double y) { return (y - height / 2.0) / uiScale; }
    private int imageX(int x) { return px + Math.round(x * (PW / 1152.0f)); }
    private int imageY(int y) { return py + Math.round(y * (PH / 768.0f)); }
    private int imageW(int w) { return Math.round(w * (PW / 1152.0f)); }
    private int imageH(int h) { return Math.round(h * (PH / 768.0f)); }
    private void fillImageRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(imageX(x), imageY(y), imageX(x + w), imageY(y + h), color);
    }
    private void drawImageText(GuiGraphics g, String text, int x, int y, int color) {
        text = normalizeUiText(text);
        g.pose().pushPose();
        g.pose().translate(imageX(x), imageY(y), 0);
        g.pose().scale(0.78f, 0.78f, 1.0f);
        g.drawString(uiFont, text, 0, 0, color, false);
        g.pose().popPose();
    }

    private void drawImageTextCentered(GuiGraphics g, String text, int x, int y, int color) {
        drawImageTextCentered(g, text, x, y, color, 0.78f);
    }

    private void drawImageTextCentered(GuiGraphics g, String text, int x, int y, int color, float scale) {
        text = normalizeUiText(text);
        g.pose().pushPose();
        g.pose().translate(imageX(x), imageY(y), 0);
        g.pose().scale(scale, scale, 1.0f);
        g.drawCenteredString(uiFont, text, 0, 0, color);
        g.pose().popPose();
    }

    private void drawImageTextCenteredFit(GuiGraphics g, String text, int x, int y, int color,
                                          float maxScale, int maxImageWidth) {
        float textWidth = uiFont.width(normalizeUiText(text));
        float fitScale = textWidth == 0 ? maxScale
                : Math.min(maxScale, Math.max(0.1f, (imageW(maxImageWidth) - 6.0f) / textWidth));
        drawImageTextCentered(g, text, x, y, color, fitScale);
    }

    /** Replace bitmap labels in the reference art with live, scale-safe text. */
    private void drawHubText(GuiGraphics g) {
        fillImageRect(g, 190, 38, 500, 48, 0xFFF4E8D1);
        drawImageTextCentered(g, "GRIMOIRE DU WAKFU", 438, 66, INK_TITLE);
        fillImageRect(g, 827, 45, 162, 28, 0xFFF5EBDD);
        drawImageTextCenteredFit(g, ClientMoney.fmt(ClientMoney.get()) + " pièces", 920, 62,
                INK_GOLD, 0.78f, 162);

        fillImageRect(g, 143, 259, 139, 62, 0xFFF3E9D7);
        drawImageTextCenteredFit(g, ClientMoney.fmt(ClientMoney.get()), 212, 281,
                INK_GOLD, 0.78f, 139);
        drawImageTextCenteredFit(g, "PIÈCES", 212, 305, INK_TITLE, 0.78f, 139);
        fillImageRect(g, 144, 359, 140, 98, 0xFFF3E9D7);
        drawImageTextCenteredFit(g, "BOURSE", 214, 376, INK_TITLE, 0.78f, 140);
        drawImageTextCenteredFit(g, "Pièces gagnées", 214, 401, INK_MUTED, 0.4f, 140);
        drawImageTextCenteredFit(g, "en combat, quêtes", 214, 417, INK_MUTED, 0.4f, 140);
        drawImageTextCenteredFit(g, "et ventes.", 214, 433, INK_MUTED, 0.4f, 140);

        String[] names = {"BESTIAIRE", "INVENTAIRE", "STATISTIQUES", "SORTS", "SOCIAL"};
        String[][] descriptions = {
                {"Monstres, zones,", "butin et panoplies."},
                {"Sac, armures et", "armes équipées."},
                {"PV, force, sagesse", "et défense."},
                {"Améliore tes sorts", "du niveau 1 au 50."},
                {"Amis, groupes et", "duels entre joueurs."}
        };
        int[] centers = {436, 664, 904, 522, 763};
        int[] nameY = {277, 277, 277, 548, 548};
        int[] descY = {315, 315, 315, 594, 594};
        for (int i = 0; i < names.length; i++) {
            int left = i < 3 ? centers[i] - 91 : centers[i] - 106;
            int width = i < 3 ? 182 : 212;
            int fillTop = i < 3 ? 257 : 530;
            fillImageRect(g, left, fillTop, width, 83, 0xFFF3E9D7);
            drawImageTextCenteredFit(g, names[i], centers[i], nameY[i], INK_TITLE, 0.78f, width);
            drawImageTextCenteredFit(g, descriptions[i][0], centers[i], descY[i], INK_MUTED, 0.55f, width);
            drawImageTextCenteredFit(g, descriptions[i][1], centers[i], descY[i] + 16,
                    INK_MUTED, 0.55f, width);
        }
    }

    private void drawPageLabels(GuiGraphics g) {
        fillImageRect(g, 159, 47, 224, 45, 0xFF876747);
        drawImageTextCenteredFit(g, "GRIMOIRE DU WAKFU", 270, 76, 0xFFFFF1D7, 0.78f, 224);
        String[] mainLabels = {"MENU", "BESTIAIRE", "SORTS", "STATISTIQUES"};
        int[] mainRows = {116, 169, 220, 272};
        for (int i = 0; i < mainLabels.length; i++) {
            boolean selected = i > 0 && i - 1 == main;
            fillImageRect(g, 137, mainRows[i] + 6, 183, 31,
                    selected ? 0xFFD6E2D7 : 0xFFF1EBDD);
            drawImageTextCenteredFit(g, mainLabels[i], 229, mainRows[i] + 27,
                    selected ? INK_GREEN : INK_TITLE, 0.78f, 183);
        }
        String[] subLabels = {"MONSTRES", "ZONES", "DONJON", "BUTIN", "PANOPLIES", "PNJ"};
        int[] subRows = {324, 375, 426, 477, 528, 579};
        for (int i = 0; i < subLabels.length; i++) {
            boolean selected = main == 0 && i == sub;
            fillImageRect(g, 137, subRows[i] + 5, 183, 31,
                    selected ? 0xFFD6E2D7 : 0xFFF1EBDD);
            drawImageTextCenteredFit(g, subLabels[i], 229, subRows[i] + 25,
                    selected ? INK_GREEN : INK_TITLE, 0.78f, 183);
        }
    }
    private void addImageHitbox(int x, int y, int w, int h, int id) {
        rects.add(new int[]{imageX(x), imageY(y), imageW(w), imageH(h), id});
    }
    private void addHubHitboxes() {
        addImageHitbox(333, 116, 207, 294, 700); addImageHitbox(555, 116, 222, 294, 701);
        addImageHitbox(794, 116, 219, 294, 702); addImageHitbox(404, 428, 235, 239, 703);
        addImageHitbox(650, 428, 226, 239, 704);
    }
    private void addPageHitboxes() {
        addImageHitbox(69,116,266,48,0); addImageHitbox(69,169,266,48,1);
        addImageHitbox(69,220,266,49,2); addImageHitbox(69,272,266,49,3);
        int[] ys={324,375,426,477,528,579};
        for(int i=0;i<ys.length;i++) addImageHitbox(69,ys[i],266,47,10+i);
        addImageHitbox(419,136,677,34,901);
    }
    private void drawPageSelection(GuiGraphics g) {
        int y=main==0?169:main==1?220:272; fillImageRect(g,70,y,263,47,0x557BA79B);
        if(main==0){int[] ys={324,375,426,477,528,579};fillImageRect(g,70,ys[Math.max(0,Math.min(5,sub))],263,46,0x557BA79B);}
    }
    private int getSeenCount(net.minecraft.world.entity.player.Player player) {
        return player.getCapability(ModCapabilities.RPG).map(data -> data.getCodexSeen().size()).orElse(0);
    }
    // ---------------------------------------------------------------- recherche
    private void drawSearch(GuiGraphics g) {
        int bx = imageX(420), by = imageY(137), bw = imageW(674), bh = imageH(29);
        g.fill(bx, by, bx + bw, by + bh, searchFocus ? 0xFFFFFBF0 : 0xFFF3EFE1);
        outline(g, bx, by, bw, bh, searchFocus ? INK_TITLE : 0xFFB6AA94);
        if (query.isEmpty() && !searchFocus) {
            smallText(g, "Rechercher un monstre, un objet...", imageX(431), imageY(146), INK_FAINT);
        } else {
            smallText(g, query, imageX(431), imageY(146), INK);
            if (searchFocus && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cw = textWidth(query);
                g.fill(imageX(431) + cw, imageY(146), imageX(432) + cw, imageY(157), INK);
            }
        }
        if (!query.isEmpty()) {
            smallText(g, "x", imageX(1098), imageY(146), INK_RED);
            addImageHitbox(1085, 137, 25, 30, 900);
        }
    }
    private boolean matches(String s) {
        if (query.isEmpty()) return true;
        return strip(s).toLowerCase().contains(query.toLowerCase());
    }

    private static String strip(String s) {
        return s.replaceAll("§.", "");
    }

    private String trunc(String s, int maxW) {
        if (textWidth(s) <= maxW) return s;
        String suffix = "…";
        while (textWidth(s + suffix) > maxW && s.length() > 1) s = s.substring(0, s.length() - 1);
        return s + suffix;
    }

    // ---------------------------------------------------------------- onglets
    // ---------------------------------------------------------------- v0.25 : menu hub
    private static final String[] IC_MONSTER = {
            "............",
            "..GGGGGGGG..",
            ".GGGGGGGGGG.",
            ".GGDDGGDDGG.",
            ".GGDDGGDDGG.",
            ".GGGGGGGGGG.",
            ".GGDGGGGDGG.",
            ".GGDDDDDDGG.",
            ".GGGDDDDGGG.",
            ".GGGGGGGGGG.",
            "..GGGGGGGG..",
            "............"};
    private static final String[] IC_CHEST = {
            "............",
            ".BBBBBBBBBB.",
            ".BHHHHHHHHB.",
            ".BHHHHHHHHB.",
            ".BBBBBBBBBB.",
            ".BBBBOOBBBB.",
            ".BBBBOOBBBB.",
            ".BBBBBBBBBB.",
            ".BBBBBBBBBB.",
            ".BBBBBBBBBB.",
            "............",
            "............"};
    private static final String[] IC_HEART = {
            "............",
            "..RRR.RRR...",
            ".RRRRRRRRR..",
            ".RRWRRRRRR..",
            ".RRRRRRRRR..",
            ".RRRRRRRRR..",
            "..RRRRRRR...",
            "...RRRRR....",
            "....RRR.....",
            ".....R......",
            "............",
            "............"};
    private static final String[] IC_STAR = {
            ".....SS.....",
            ".....SS.....",
            "....SSSS....",
            "....SSSS....",
            "SSSSSSSSSSSS",
            ".SSSWWWWSSS.",
            "..SSSWWSSS..",
            "...SSSSSS...",
            "...SSSSSS...",
            "..SSS..SSS..",
            "..SS....SS..",
            "............"};
    private static final String[] IC_PEOPLE = {
            "............",
            "..SSS..SSS..",
            "..SSS..SSS..",
            "..SSS..SSS..",
            ".BBBB..BBBB.",
            ".BBBB..BBBB.",
            ".BBBB..BBBB.",
            "..BB....BB..",
            "..BB....BB..",
            "............",
            "............",
            "............"};
    private static final String[] IC_COIN = {
            "....GGGG....",
            "..GGYYYYGG..",
            ".GYYYYYYYYG.",
            "GYWWYYYYYYG.",
            "GWWWWYYYYYG.",
            "GYWWWWYYYYG.",
            "GYYYYWWWWYG.",
            "GYYYYYWWWWG.",
            ".GYYYYYYYYG.",
            "..GGYYYYGG..",
            "....GGGG....",
            "............"};

    private static int colorOf(int kind, char ch) {
        return switch (kind) {
            case 0 -> switch (ch) {
                case 'G' -> 0xFF4A8C3C;
                case 'D' -> 0xFF1E281A;
                default -> 0;
            };
            case 1 -> switch (ch) {
                case 'B' -> 0xFF5C3E22;
                case 'H' -> 0xFF8C6234;
                case 'O' -> 0xFFE8B830;
                default -> 0;
            };
            case 2 -> switch (ch) {
                case 'R' -> 0xFFBE2828;
                case 'W' -> 0xFFF08C8C;
                default -> 0;
            };
            case 3 -> switch (ch) {
                case 'S' -> 0xFF5AAAE6;
                case 'W' -> 0xFFEAF6FF;
                default -> 0;
            };
            case 4 -> switch (ch) {
                case 'S' -> 0xFFCDA578;
                case 'B' -> 0xFF466EB4;
                default -> 0;
            };
            default -> switch (ch) {
                case 'G' -> 0xFF8C6410;
                case 'Y' -> 0xFFE8B830;
                case 'W' -> 0xFFFFE080;
                default -> 0;
            };
        };
    }

    private static void drawIcon(GuiGraphics g, int kind, int x, int y) {
        String[] rows = switch (kind) {
            case 0 -> IC_MONSTER;
            case 1 -> IC_CHEST;
            case 2 -> IC_HEART;
            case 3 -> IC_STAR;
            case 4 -> IC_PEOPLE;
            default -> IC_COIN;
        };
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            for (int c = 0; c < row.length(); c++) {
                int col = colorOf(kind, row.charAt(c));
                if (col != 0) g.fill(x + c * 2, y + r * 2, x + c * 2 + 2, y + r * 2 + 2, col);
            }
        }
    }

    /** Menu principal : bourse (colonne gauche) + 5 cartes cliquables. */
    private void drawHub(GuiGraphics g, int mx, int my) {
        int sx = px + 7, sy = py + CY;
        g.fill(sx, sy, sx + 84, sy + 88, 0xFFE2D0A8);
        outline(g, sx, sy, 84, 88, INK_MUTED);
        drawIcon(g, 5, sx + 30, sy + 8);
        String m = ClientMoney.fmt(ClientMoney.get()) + " pièces";
        smallText(g, m, sx + 42 - textWidth(m) / 2, sy + 40, INK_GOLD);
        String bourse = "Bourse";
        smallText(g, bourse, sx + 42 - textWidth(bourse) / 2, sy + 54, INK_TITLE);
        String h1 = "L'argent se gagne en";
        String h2 = "combat, quêtes et ventes.";
        smallText(g, h1, sx + 42 - textWidth(h1) / 2, sy + 66, INK_FAINT);
        smallText(g, h2, sx + 42 - textWidth(h2) / 2, sy + 75, INK_FAINT);

        String[] names = {"Bestiaire", "Inventaire", "Statistiques", "Sorts", "Social"};
        String[] desc1 = {"Monstres, zones,", "Sac, armures,", "PV, force,", "Améliore tes", "Amis, groupe,"};
        String[] desc2 = {"butin, panoplies", "armes équipées", "sagesse, défense", "sorts (1→50)", "duels 1v1"};
        int cx0 = px + CX;
        for (int i = 0; i < 5; i++) {
            int row = i < 3 ? 0 : 1;
            int col = i < 3 ? i : i - 3;
            int cw2 = 80, chh = 92;
            int x = cx0 + (row == 0 ? col * 87 : 33 + col * 87);
            int y = py + CY + 2 + row * 100;
            boolean hover = mx >= x && mx < x + cw2 && my >= y && my < y + chh;
            g.fill(x, y, x + cw2, y + chh, hover ? 0xFFF2E8CC : 0xFFE5D5B0);
            outline(g, x, y, cw2, chh, hover ? INK_TITLE : INK_MUTED);
            drawIcon(g, i, x + cw2 / 2 - 12, y + 8);
            smallText(g, names[i], x + cw2 / 2 - textWidth(names[i]) / 2, y + 40, INK_TITLE);
            smallText(g, desc1[i], x + cw2 / 2 - textWidth(desc1[i]) / 2, y + 56, INK_MUTED);
            smallText(g, desc2[i], x + cw2 / 2 - textWidth(desc2[i]) / 2, y + 66, INK_MUTED);
            rects.add(new int[]{x, y, cw2, chh, 700 + i});
        }
    }

    private void drawMainTabs(GuiGraphics g, int mx, int my) {
        String[] labels = {"← Menu", "Bestiaire", "Sorts", "Statistiques"};
        for (int i = 0; i < 4; i++) {
            int tx = px + 8, ty = py + CY + i * 21, tw = 80, th = 19;
            boolean sel = i - 1 == main;
            boolean hover = mx >= tx && mx < tx + tw && my >= ty && my < ty + th;
            g.fill(tx, ty, tx + tw, ty + th, sel ? 0xFFE8DCC0 : (hover ? 0xD8C8A8 : 0xC4B294));
            outline(g, tx, ty, tw, th, sel ? INK_TITLE : INK_MUTED);
            String lb = labels[i];
            smallText(g, lb, tx + tw / 2 - textWidth(lb) / 2, ty + 5,
                    sel ? INK_TITLE : INK_MUTED);
            rects.add(new int[]{tx, ty, tw, th, i});
        }
    }

    private void drawSubTabs(GuiGraphics g, int mx, int my) {
        String[] labels = {"Monstres", "Zones", "Donjon", "Butin", "Panoplies", "PNJ"};
        for (int i = 0; i < labels.length; i++) {
            int tx = px + 10, ty = py + CY + 86 + i * 21, tw = 76, th = 17;
            boolean sel = i == sub;
            boolean hover = mx >= tx && mx < tx + tw && my >= ty && my < ty + th;
            g.fill(tx, ty, tx + tw, ty + th, sel ? 0xFFE2D4B4 : (hover ? 0xD2C2A2 : 0xBBA98B));
            outline(g, tx, ty, tw, th, sel ? INK_TITLE : INK_MUTED);
            smallText(g, labels[i], tx + tw / 2 - textWidth(labels[i]) / 2, ty + 5,
                    sel ? INK_TITLE : INK_MUTED);
            rects.add(new int[]{tx, ty, tw, th, 10 + i});
        }
    }

    // ---------------------------------------------------------------- bestiaire : monstres
    private static final MonsterType[] BESTIARY = {
            MonsterType.ZOMBIE, MonsterType.SKELETON, MonsterType.BANDIT, MonsterType.BOSS,
            MonsterType.GUERRIER, MonsterType.SENTINELLE, MonsterType.POUPEE, MonsterType.GOLEM,
            MonsterType.COLOSSE,
            // Bestiaire original Wakfu : gardiens, monstres des cendres et constructs.
            MonsterType.ROOT_WARDEN, MonsterType.ASH_SCOUT, MonsterType.TOXIC_MOMIC,
            MonsterType.SIEGE_GOLEM, MonsterType.HEAVY_WARRIOR, MonsterType.BONE_ARCHER,
            MonsterType.FOREST_SENTINEL, MonsterType.ROAD_BRIGAND, MonsterType.ROAD_ASSASSIN,
            MonsterType.MECHANICAL_DOLL};

    private static String dropLine(MonsterType t) {
        if (t.xpReward == 0) return "Aucun butin — invocation";
        if (t == MonsterType.BOSS) {
            return "Butin garanti : 1 pièce de la " + WakfuArmorItem.setNameFr("chef")
                    + " et l'écusson du chef · pièces";
        }
        String set = WakfuArmorItem.setForMonster(t.getId());
        return set == null ? "Butin garanti : armure runique · pièces"
                : "Butin garanti : 1 pièce de la " + WakfuArmorItem.setNameFr(set) + " · pièces";
    }

    private void renderMonsters(GuiGraphics g, int cx, int cy, int yc, PlayerRpgData data) {
        List<MonsterType> list = new ArrayList<>();
        for (MonsterType t : BESTIARY) if (matches(t.getName().getString())) list.add(t);
        int seen = 0;
        for (MonsterType t : BESTIARY) if (data.getCodexSeen().contains(t.getId())) seen++;
        header(g, cx, cy, "Bestiaire", seen + "/" + BESTIARY.length + " rencontres");
        int rowsH = list.size() * 38;
        monsterScroll = Math.max(0, Math.min(monsterScroll, rowsH - BEST_H));
        enableUiScissor(g, cx, yc, cx + CW, cy + CH);
        int y = yc - monsterScroll;
        for (MonsterType t : list) {
            if (y > yc - 38 && y < cy + CH) {
                boolean known = data.getCodexSeen().contains(t.getId());
                if (known) {
                    g.blit(portrait(t.getId()), cx + 2, y, 16, 16, 0, 0, 16, 16, 16, 16);
                    String name = t.getName().getString();
                    String tag = t == MonsterType.BOSS ? "  [BOSS]" : (t.xpReward == 0 ? "  [INVOCATION]" : "");
                    smallText(g, name, cx + 22, y, INK);
                    smallText(g, tag, cx + 22 + textWidth(name), y,
                            t == MonsterType.BOSS ? INK_RED : INK_BLUE);
                    String stats = "PV " + t.maxHp + " · Dég " + t.damage + " · PM " + t.pm + " · Portée " + t.range
                            + (t.xpReward > 0 ? " · XP " + t.xpReward : "");
                    int[] range = MobVariants.levelRange(t.getId());
                    if (range != null) stats += " · §eNv. conseillé " + range[0] + "-" + range[1];
                    else if (t == MonsterType.BOSS) stats += " · §eNv. conseillé 10";
                    smallText(g, trunc(stats, CW - 46), cx + 22, y + 10, INK_MUTED);
                    if (range != null) {
                        smallText(g, trunc("Variantes : " + MobVariants.namesFor(t.getId()), CW - 26),
                                cx + 22, y + 19, INK_FAINT);
                    }
                    smallText(g, trunc(dropLine(t), CW - 26), cx + 22, y + 28, INK_MUTED);
                    int kills = data.getCodexKills().getOrDefault(t.getId(), 0);
                    String ks = kills > 0 ? "×" + kills : "—";
                    smallText(g, ks, cx + CW - 4 - textWidth(ks), y, kills > 0 ? INK_GOLD : INK_MUTED);
                } else {
                    g.blit(LOCKED, cx + 2, y, 16, 16, 0, 0, 16, 16, 16, 16);
                    smallText(g, "? ?", cx + 22, y, INK_MUTED);
                    smallText(g, "Créature jamais rencontrée.", cx + 22, y + 10, INK_FAINT);
                    smallText(g, "Son butin reste un mystère…", cx + 22, y + 19, INK_FAINT);
                }
                g.fill(cx + 2, y + 37, cx + CW - 2, y + 38, 0x334A3421);
            }
            y += 38;
        }
        g.disableScissor();
        if (list.isEmpty()) smallText(g, "Aucun résultat pour « " + query + " ».", cx + 6, yc + 6, INK_RED);
    }

    // ---------------------------------------------------------------- bestiaire : PNJ & lieux
    private void renderNpcs(GuiGraphics g, int cx, int cy, int yc) {
        header(g, cx, cy, "PNJ & Lieux",
                Waypoints.isActive() ? "suivi : " + Waypoints.name() : "aucun suivi");
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        var sp = mc.level.getSharedSpawnPos();
        int contentBottom = cy + CH - 18;
        int maxScroll = Math.max(0, NpcSpots.SPOTS.size() * 26 + 30 - (contentBottom - yc));
        npcScroll = Math.max(0, Math.min(npcScroll, maxScroll));
        enableUiScissor(g, cx + 2, yc, cx + CW - 2, contentBottom);
        int y = yc - npcScroll;
        for (int i = 0; i < NpcSpots.SPOTS.size(); i++) {
            NpcSpots.Spot s = NpcSpots.SPOTS.get(i);
            if (y + 23 <= yc || y >= contentBottom) { y += 26; continue; }
            boolean marinAtGrecosSpawn = s.id().equals("marin")
                    && mc.level.dimension().location().getNamespace().equals("wakfurpg")
                    && mc.level.dimension().location().getPath().equals("archipel");
            int wx = marinAtGrecosSpawn ? sp.getX() : sp.getX() + s.dx();
            int wz = marinAtGrecosSpawn ? sp.getZ() : sp.getZ() + s.dz();
            double ddx = wx - mc.player.getX(), ddz = wz - mc.player.getZ();
            int dist = (int) Math.round(Math.sqrt(ddx * ddx + ddz * ddz));
            boolean on = Waypoints.isActive() && Waypoints.name().equals(s.nameFr());
            g.fill(cx + 2, y, cx + CW - 2, y + 23, on ? 0xFFE9E0BE : 0xFFE2D4B4);
            outline(g, cx + 2, y, CW - 4, 23, on ? INK_GREEN : INK_MUTED);
            smallText(g, s.nameFr(), cx + 8, y + 3, INK);
            smallText(g, dist + " m · vers " + dirOf(ddx, ddz), cx + 8, y + 13, INK_MUTED);
            button(g, cx + CW - 62, y + 5, 56, 13, on ? "✓ Suivi" : "Suivre",
                    on ? INK_GREEN : INK_TITLE, 300 + i);
            y += 26;
        }
        g.disableScissor();
        smallText(g, "Molette : défiler · « Suivre » : afficher le chemin", cx + 6, cy + CH - 13, INK_FAINT);
    }

    private static String dirOf(double dx, double dz) {
        String ns = dz < 0 ? "N" : "S";
        String ew = dx > 0 ? "E" : "O";
        if (Math.abs(dx) > Math.abs(dz) * 2) return ew;
        if (Math.abs(dz) > Math.abs(dx) * 2) return ns;
        return ns + ew;
    }

    // ---------------------------------------------------------------- bestiaire : zones
    private void renderZones(GuiGraphics g, int cx, int cy, int yc, PlayerRpgData data) {
        int found = 0;
        for (int sx = -1; sx <= 1; sx++)
            for (int sz = -1; sz <= 1; sz++)
                if (data.getCodexZones().contains(sx + "," + sz)) found++;
        header(g, cx, cy, "Zones explorées", found + "/9");
        int cwZ = 75, chZ = 45;
        for (int sz = -1; sz <= 1; sz++) {
            for (int sx = -1; sx <= 1; sx++) {
                int gx = cx + (sx + 1) * (cwZ + 4), gy = yc + (sz + 1) * (chZ + 3);
                boolean known = data.getCodexZones().contains(sx + "," + sz);
                g.fill(gx, gy, gx + cwZ, gy + chZ, known ? 0xFFE2D4B4 : 0xCBB99B);
                outline(g, gx, gy, cwZ, chZ, known ? INK_TITLE : INK_MUTED);
                smallText(g, zoneName(sx, sz), gx + 4, gy + 3, known ? INK_TITLE : INK_MUTED);
                if (known) {
                    smallText(g, "✓ Explorée", gx + 4, gy + 15, INK_GREEN);
                    int target = 3 + Math.abs(sx * 31 + sz * 17) % 2;
                    smallText(g, "≈" + target + " monstres", gx + 4, gy + 25, INK_MUTED);
                    if (sx == 0 && sz == 0) smallText(g, "Village & PNJ", gx + 4, gy + 35, INK_MUTED);
                } else {
                    smallText(g, "Non explorée", gx + 4, gy + 15, INK_FAINT);
                    smallText(g, "Zone inconnue", gx + 4, gy + 25, INK_FAINT);
                }
            }
        }
    }

    private static String zoneName(int sx, int sz) {
        if (sx == 0 && sz == 0) return "Village";
        String v = sz < 0 ? "Nord" : (sz > 0 ? "Sud" : "");
        String h = sx < 0 ? "Ouest" : (sx > 0 ? "Est" : "");
        if (!v.isEmpty() && !h.isEmpty()) return v + "-" + h;
        return v + h;
    }

    // ---------------------------------------------------------------- bestiaire : donjon
    private void renderDungeon(GuiGraphics g, int cx, int cy, int yc, PlayerRpgData data) {
        header(g, cx, cy, "Donjons", data.isCodexDungeon() ? "1/1 découvert" : "0/1");
        if (!data.isCodexDungeon()) {
            g.blit(DUNGEON_ICON, cx + 2, yc + 4, 32, 32, 0, 0, 32, 32, 32, 32);
            g.fill(cx + 2, yc + 4, cx + 34, yc + 36, 0xB0101014);
            smallText(g, "? — Repaire inconnu", cx + 42, yc + 8, INK_MUTED);
            smallText(g, "Des rumeurs parlent d'un fortin de pierre à", cx + 42, yc + 20, INK_FAINT);
            smallText(g, "l'est du village. Suis les poteaux-torches…", cx + 42, yc + 30, INK_FAINT);
            return;
        }
        int y = yc;
        g.blit(DUNGEON_ICON, cx + 2, y, 32, 32, 0, 0, 32, 32, 32, 32);
        smallText(g, "Repaire du Chef des Bandits", cx + 42, y + 2, INK_TITLE);
        smallText(g, "✓ Découvert · fortin de pierre à l'est", cx + 42, y + 14, INK_GREEN);
        smallText(g, "Muraille crénelée, tours, chemin balisé.", cx + 42, y + 24, INK_MUTED);
        y += 40;
        g.fill(cx + 2, y, cx + CW - 2, y + 1, 0x334A3421);
        y += 5;
        boolean bossSeen = data.getCodexSeen().contains("chef_bandit");
        int bossKills = data.getCodexKills().getOrDefault("chef_bandit", 0);
        g.blit(bossSeen ? portrait("chef_bandit") : LOCKED, cx + 2, y, 16, 16, 0, 0, 16, 16, 16, 16);
        smallText(g, bossSeen ? "Chef des Bandits  [BOSS]" : "? ?", cx + 22, y, bossSeen ? INK_RED : INK_MUTED);
        String bs = bossKills > 0 ? "×" + bossKills + " vaincu — réapparaît 20 min après sa mort"
                : (bossSeen ? "Rencontré mais pas encore vaincu (Nv. 10, 130 PV)"
                : "Jamais affronté — il garde la salle scellée au nord-est.");
        smallText(g, trunc(bs, CW - 26), cx + 22, y + 10, INK_MUTED);
        y += 24;
        g.blit(portrait("bandit"), cx + 2, y, 16, 16, 0, 0, 16, 16, 16, 16);
        smallText(g, "Gardes bandits (Nv. 5-8)", cx + 22, y, INK);
        smallText(g, "Trois patrouillent la cour ; ils reviennent avec le temps.", cx + 22, y + 10, INK_MUTED);
        y += 24;
        g.fill(cx + 2, y, cx + CW - 2, y + 1, 0x334A3421);
        y += 5;
        smallText(g, "Coffre du boss (réapprovisionné à chaque réapparition) :", cx + 2, y, INK_TITLE);
        y += 12;
        ItemStack[] chest = {
                WakfuArmorItem.makePiece("head", "rare", 1, RandomSource.create()),
                WakfuArmorItem.makePiece("chest", "epique", 1, RandomSource.create()),
                WakfuWeaponItem.makeWeapon("sword", "rare", 1, RandomSource.create()),
                new ItemStack(Items.GOLDEN_APPLE, 2),
                WakfuArmorItem.makeSetPiece("chef", "head"),
        };
        int ix = cx + 4;
        for (ItemStack s : chest) {
            g.renderItem(s, ix, y);
            ix += 22;
        }
        smallText(g, "rare/Fantasy · épique · arme · pommes · §6pièce Chef", ix + 4, y + 4, INK_MUTED);
    }

    // ---------------------------------------------------------------- bestiaire : butin
    private record LootEntry(String key, String name, String stats, ItemStack icon) {}

    private static List<LootEntry> lootEntries() {
        List<LootEntry> l = new ArrayList<>();
        // v0.22 : panoplies de zone d'abord
        for (String set : WakfuArmorItem.allSetIds()) {
            for (String slot : WakfuArmorItem.SLOTS) {
                int[] f = WakfuArmorItem.fixedStats(WakfuArmorItem.makeSetPiece(set, slot));
                l.add(new LootEntry("set_" + set + "_" + slot,
                        WakfuArmorItem.setChatColor(set) + WakfuArmorItem.setNameFr(set)
                                + "§7 · " + WakfuArmorItem.slotNameFr(slot),
                        WakfuArmorItem.statsLabel(f).trim(),
                        WakfuArmorItem.makeSetPiece(set, slot)));
            }
        }
        // v0.22 : trophées de chasse
        for (String k : WakfuTrophyItem.KINDS) {
            l.add(new LootEntry("trophy_" + k, "§e" + WakfuTrophyItem.nameFr(k),
                    "trophée — " + WakfuTrophyItem.sourceFr(k), WakfuTrophyItem.make(k)));
        }
        for (String slot : WakfuArmorItem.SLOTS) {
            for (String rar : WakfuArmorItem.RARITIES) {
                int[] f = WakfuArmorItem.fixedStats(slot, rar, 0);
                l.add(new LootEntry("armor_" + slot + "_" + rar,
                        colorOf(rar) + WakfuArmorItem.slotNameFr(slot) + " Runique",
                        WakfuArmorItem.statsLabel(f).trim(),
                        WakfuArmorItem.makePiece(slot, rar, 1, RandomSource.create())));
            }
        }
        for (String kind : new String[]{"sword", "bow"}) {
            for (String rar : WakfuWeaponItem.RARITIES) {
                int[] f = WakfuWeaponItem.fixedStats(kind, rar, 0);
                l.add(new LootEntry(kind + "_" + rar,
                        colorOf(rar) + ("sword".equals(kind) ? "Lame Runique" : "Arc Runique"),
                        WakfuArmorItem.statsLabel(f).trim(),
                        WakfuWeaponItem.makeWeapon(kind, rar, 1, RandomSource.create())));
            }
        }
        l.add(new LootEntry("ingot", "§6Pièces", "bourse : monstres, quêtes, ventes", new ItemStack(Items.GOLD_INGOT)));
        l.add(new LootEntry("grimoire", "§fGrimoire du Wakfu", "objet des sorts", new ItemStack(ModItems.WAKFU_GRIMOIRE.get())));
        l.add(new LootEntry("golden_apple", "§fPomme dorée", "coffre du boss", new ItemStack(Items.GOLDEN_APPLE)));
        return l;
    }

    private static String colorOf(String rar) {
        return "epique".equals(rar) ? "§d[épique] " : ("rare".equals(rar) ? "§9[rare] " : "§f[commune] ");
    }

    private void renderLoot(GuiGraphics g, int cx, int cy, int yc, PlayerRpgData data) {
        List<LootEntry> entries = new ArrayList<>();
        for (LootEntry e : lootEntries()) if (matches(e.name())) entries.add(e);
        int found = 0;
        for (LootEntry e : entries) if (data.getCodexItems().containsKey(e.key())) found++;
        header(g, cx, cy, "Butin — stats annoncées", found + "/" + entries.size());
        int rowsH = entries.size() * 20;
        lootScroll = Math.max(0, Math.min(lootScroll, rowsH - BEST_H));
        enableUiScissor(g, cx, yc, cx + CW, cy + CH);
        int y = yc - lootScroll;
        for (LootEntry e : entries) {
            if (y > yc - 20 && y < cy + CH) {
                Integer n = data.getCodexItems().get(e.key());
                if (n != null) {
                    g.renderItem(e.icon(), cx + 2, y);
                    smallText(g, trunc(e.name(), CW - 70), cx + 22, y, INK);
                    smallText(g, trunc(e.stats(), CW - 26), cx + 22, y + 9, INK_MUTED);
                    String cs = "×" + n;
                    smallText(g, cs, cx + CW - 4 - textWidth(cs), y + 4, INK_GOLD);
                } else {
                    g.blit(LOCKED, cx + 2, y, 16, 16, 0, 0, 16, 16, 16, 16);
                    smallText(g, "? — jamais reçu", cx + 22, y, INK_FAINT);
                    smallText(g, trunc(e.stats(), CW - 26), cx + 22, y + 9, INK_FAINT);
                }
                g.fill(cx + 2, y + 19, cx + CW - 2, y + 20, 0x334A3421);
            }
            y += 20;
        }
        g.disableScissor();
        if (entries.isEmpty()) smallText(g, "Aucun résultat pour « " + query + " ».", cx + 6, yc + 6, INK_RED);
    }

    // ---------------------------------------------------------------- bestiaire : panoplies
    private void renderSets(GuiGraphics g, int cx, int cy, int yc, PlayerRpgData data,
                            net.minecraft.world.entity.player.Player player) {
        List<String> sets = new ArrayList<>();
        for (String set : WakfuArmorItem.allSetIds()) {
            if (matches(WakfuArmorItem.setNameFr(set)) || matches(WakfuArmorItem.setSource(set))
                    || matches("panoplie")) sets.add(set);
        }
        int complete = 0;
        for (String set : WakfuArmorItem.allSetIds()) {
            int n = ownedPieces(data, player, set);
            if (n == 4) complete++;
        }
        header(g, cx, cy, "Panoplies", complete + "/" + WakfuArmorItem.allSetIds().length + " complètes");
        int rowsH = sets.size() * 62;
        setsScroll = Math.max(0, Math.min(setsScroll, rowsH - BEST_H));
        enableUiScissor(g, cx, yc, cx + CW, cy + CH);
        int y = yc - setsScroll;
        String active = WakfuArmorItem.activeSet(player);
        for (String set : sets) {
            if (y > yc - 62 && y < cy + CH) {
                int owned = ownedPieces(data, player, set);
                boolean isActive = set.equals(active);
                g.fill(cx + 2, y, cx + CW - 2, y + 60, isActive ? 0xFFE9E0BE : 0xFFE2D4B4);
                outline(g, cx + 2, y, cx + CW - 4, 60, isActive ? INK_GREEN : INK_MUTED);
                smallText(g, WakfuArmorItem.setChatColor(set) + WakfuArmorItem.setNameFr(set)
                        + " §8— " + trunc(WakfuArmorItem.setSource(set), CW - 130), cx + 6, y + 3, INK);
                String cnt = owned + "/4";
                smallText(g, cnt, cx + CW - 8 - textWidth(cnt), y + 3,
                        owned == 4 ? INK_GREEN : INK_MUTED);
                for (int i = 0; i < 4; i++) {
                    String slot = WakfuArmorItem.SLOTS[i];
                    boolean has = ownsPiece(data, player, set, slot);
                    int ix = cx + 8 + i * 62;
                    g.renderItem(WakfuArmorItem.makeSetPiece(set, slot), ix, y + 15);
                    if (!has) {
                        g.fill(ix, y + 15, ix + 16, y + 31, 0xB0101014);
                        g.blit(LOCKED, ix, y + 15, 16, 16, 0, 0, 16, 16, 16, 16);
                    }
                    smallText(g, has ? WakfuArmorItem.slotNameFr(slot) : "?",
                            ix + 17, y + 18, has ? INK : INK_FAINT);
                    int[] f = WakfuArmorItem.fixedStats(WakfuArmorItem.makeSetPiece(set, slot));
                    String sh = (f[0] > 0 ? f[0] + "V " : "") + (f[1] > 0 ? f[1] + "F " : "")
                            + (f[2] > 0 ? f[2] + "S " : "") + (f[3] > 0 ? f[3] + "R" : "");
                    smallText(g, sh.trim(), ix - 2, y + 33, INK_MUTED);
                }
                String bonus = "Bonus (4 pièces) : §e" + WakfuArmorItem.setBonusLabel(set)
                        + (isActive ? " §2✔ ACTIVE" : (owned == 4 ? " §7— équipe les 4 pièces" : " §8—incomplet"));
                smallText(g, trunc(bonus, CW - 12), cx + 6, y + 46, INK_TITLE);
            }
            y += 62;
        }
        g.disableScissor();
        if (sets.isEmpty()) smallText(g, "Aucun résultat pour « " + query + " ».", cx + 6, yc + 6, INK_RED);
    }

    private static boolean ownsPiece(PlayerRpgData data, net.minecraft.world.entity.player.Player player,
                                     String set, String slot) {
        if (data.getCodexItems().containsKey("set_" + set + "_" + slot)) return true;
        for (ItemStack stack : player.getInventory().items)
            if (stack.getItem() instanceof WakfuArmorItem && set.equals(WakfuArmorItem.setOf(stack))
                    && slot.equals(WakfuArmorItem.slotOf(stack))) return true;
        for (ItemStack stack : player.getInventory().armor)
            if (stack.getItem() instanceof WakfuArmorItem && set.equals(WakfuArmorItem.setOf(stack))
                    && slot.equals(WakfuArmorItem.slotOf(stack))) return true;
        return false;
    }

    private static int ownedPieces(PlayerRpgData data, net.minecraft.world.entity.player.Player player, String set) {
        int n = 0;
        for (String slot : WakfuArmorItem.SLOTS) if (ownsPiece(data, player, set, slot)) n++;
        return n;
    }

    // ---------------------------------------------------------------- sorts
    private void renderSpells(GuiGraphics g, int cx, int cy, int yc, PlayerRpgData data, int mx, int my) {
        if (!data.hasClass()) {
            header(g, cx, cy, "Sorts", "");
            smallText(g, "Choisis d'abord une classe auprès du Maître", cx + 8, cy + 30, INK_MUTED);
            smallText(g, "au village (dialogue au spawn).", cx + 8, cy + 42, INK_MUTED);
            return;
        }
        List<Spell> spells = Spells.forClass(data.getRpgClass());
        header(g, cx, cy, "Sorts de " + data.getRpgClass().getName().getString(),
                "Points de sort : " + data.getSpellPts());
        if (spells.isEmpty()) {
            smallText(g, "Aucun sort n'est associé à cette classe.", cx + 8, yc + 8, INK_MUTED);
            return;
        }
        int rowsH = spells.size() * 30;
        spellScroll = Math.max(0, Math.min(spellScroll, rowsH - (CH - 32)));
        enableUiScissor(g, cx, yc, cx + CW, cy + CH);
        int y = yc + 2 - spellScroll;
        for (int i = 0; i < spells.size(); i++) {
            Spell s = spells.get(i);
            if (y > yc - 24 && y < cy + CH) {
                boolean unlocked = data.getLevel() >= s.unlockLevel;
                ResourceLocation icon = new ResourceLocation(WakfuRPG.MODID, "textures/item/spell_" + s.id + ".png");
                if (Minecraft.getInstance().getResourceManager().getResource(icon).isPresent()) {
                    g.blit(icon, cx + 3, y + 4, 20, 20, 0, 0, 32, 32, 32, 32);
                } else {
                    drawSpellIcon(g, s, cx + 3, y + 4);
                }
                if (!unlocked) g.fill(cx + 3, y + 4, cx + 23, y + 24, 0xB0101014);
                int lvl = data.spellLevel(s.id);
                String losTag = s.needsLos() ? " §8[murs]" : (s.pierce ? " §2[perce]" : "");
                String name = (unlocked ? "" : "§8🔒 ") + s.getName().getString() + losTag;
                smallText(g, name, cx + 25, y + 4, unlocked ? INK : INK_MUTED);
                String dmgTxt = s.summon != null ? "invoc." : (s.damage < 0 ? "soin " + (-s.damage) : "dég " + (s.damage + 2 * lvl));
                String info = s.apCost + " PA · " + s.rangeLabel() + " · " + s.zoneLabel() + " · " + dmgTxt
                        + " · Lv " + lvl + "/" + SpendPacket.MAX_SPELL_LEVEL;
                int maxW = CW - 73;
                while (textWidth(info) > maxW && info.length() > 8) info = info.substring(0, info.length() - 2);
                smallText(g, info, cx + 25, y + 15, INK_MUTED);
                if (!unlocked) {
                    String lk = "niv. " + s.unlockLevel;
                    smallText(g, lk, cx + CW - 4 - textWidth(lk), y + 9, INK_RED);
                } else {
                    boolean eq = data.isEquipped(s.id);
                    button(g, cx + CW - 44, y + 2, 40, 12, eq ? "✓ Équipé" : "Équiper", eq ? INK_GREEN : INK_TITLE, 200 + i);
                    if (lvl < SpendPacket.MAX_SPELL_LEVEL) {
                        int cost = SpendPacket.SPELL_COSTS[Math.min(lvl, SpendPacket.SPELL_COSTS.length - 1)];
                        boolean afford = data.getSpellPts() >= cost;
                        button(g, cx + CW - 44, y + 16, 40, 12, "+" + cost + " pt",
                                afford ? INK_GOLD : INK_MUTED, 100 + i);
                    }
                }
                g.fill(cx + 2, y + 28, cx + CW - 2, y + 29, 0x334A3421);
            }
            y += 30;
        }
        g.disableScissor();
    }

    static void drawSpellIcon(GuiGraphics g, Spell spell, int x, int y) {
        int accent = spell.id.startsWith("sykar_") ? 0xFFE277B6
                : spell.id.startsWith("tor_") ? 0xFFE3A24F
                : spell.id.startsWith("splian_") ? 0xFF82C957
                : spell.id.startsWith("gema_") ? 0xFF68D7E8
                : spell.id.startsWith("enestras_") ? 0xFFAC8BE8
                : spell.id.startsWith("iop_") ? 0xFFFF7651
                : spell.id.startsWith("cra_") ? 0xFF78BCEB
                : spell.id.startsWith("sadida_") ? 0xFFA9D35D : 0xFFE6C46B;
        int dark = ((accent & 0x00FEFEFE) >> 1) | 0xFF000000;
        g.fill(x, y, x + 20, y + 20, 0xFF171521);
        g.fill(x + 1, y + 1, x + 19, y + 19, dark);
        g.fill(x + 2, y + 2, x + 18, y + 18, 0xFF282637);
        g.fill(x + 2, y + 2, x + 7, y + 3, accent);
        g.fill(x + 15, y + 17, x + 18, y + 18, accent);

        String id = spell.id;
        String[] glyph = spell.damage < 0 || id.contains("soin") || id.contains("heal")
                || id.contains("renouveau")
                ? new String[]{"..##.##", ".######", "#######", "#######", ".#####.", "..###..", "...#..."}
                : spell.summon != null || id.contains("poupee") || id.contains("golem")
                ? new String[]{"...#...", "..###..", ".#####.", "#######", ".#####.", "..###..", "...#..."}
                : id.contains("invinc") || id.contains("reflet") || id.contains("garde")
                || id.contains("rempart") || id.contains("cocon")
                ? new String[]{"#######", "##...##", "##...##", ".##.##.", "..###..", "...#...", "......."}
                : id.contains("piege") || id.contains("ronce") || id.contains("racines")
                ? new String[]{"#.....#", "##...##", ".#.#.#.", ".#####.", "..###..", ".#.#.#.", "##...##"}
                : id.contains("froid") || id.contains("lent") || id.contains("entrave")
                ? new String[]{"...#...", "#..#..#", ".#.#.#.", "...#...", ".#.#.#.", "#..#..#", "...#..."}
                : id.contains("venin") || id.contains("malefice") || id.contains("tourment")
                || id.contains("sang") || id.contains("dot")
                ? new String[]{"..###..", ".#####.", "##...##", "##...##", ".##.##.", "..###..", "...#..."}
                : id.contains("teleport") || id.contains("pas") || id.contains("faille")
                || id.contains("bond")
                ? new String[]{"..###..", ".#...#.", "#.###.#", "#.#.#.#", "#.###.#", ".#...#.", "..###.."}
                : id.contains("seisme") || id.contains("tremblement") || id.contains("apoca")
                || id.contains("colere") || id.contains("explos")
                ? new String[]{"...#...", "..###..", ".#####.", "#######", ".#####.", "..###..", "...#..."}
                : spell.id.startsWith("splian_") || spell.id.startsWith("sadida_")
                ? new String[]{"....#..", "...##..", ".#####.", "####...", ".#####.", "...##..", "....#.."}
                : spell.id.startsWith("gema_")
                ? new String[]{"...#...", "..###..", ".#####.", "##.#.##", "...#...", "...#...", "...#..."}
                : new String[]{".....#.", "....##.", "...##..", "..##...", ".##....", "##.....", "#......"};
        for (int row = 0; row < glyph.length; row++) {
            for (int col = 0; col < glyph[row].length(); col++) {
                if (glyph[row].charAt(col) != '#') continue;
                int color = (row + col + id.hashCode() & 3) == 0 ? 0xFFFFE7A0 : accent;
                g.fill(x + 3 + col * 2, y + 3 + row * 2,
                        x + 5 + col * 2, y + 5 + row * 2, color);
            }
        }
        int rune = Math.floorMod(id.hashCode(), 4);
        g.fill(x + 9 + rune * 2, y + 1, x + 10 + rune * 2, y + 2, 0xFFFFE7A0);
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, String label, int col, int id) {
        g.fill(x, y, x + w, y + h, 0xFFE2D4B4);
        outline(g, x, y, w, h, col);
        smallText(g, label, x + w / 2 - textWidth(label) / 2, y + 3, col);
        rects.add(new int[]{x, y, w, h, id});
    }

    // ---------------------------------------------------------------- statistiques
    private void renderStats(GuiGraphics g, int cx, int cy, PlayerRpgData data,
                             net.minecraft.world.entity.player.Player player) {
        header(g, cx, cy, "Statistiques", "Points : " + data.getSkillPoints());
        int[] gear = WakfuArmorItem.equippedFixed(player);
        int[] sb = WakfuArmorItem.activeSetBonus(player);
        int y = cy + 34;

        y = statRow(g, cx, y, 0, "§cVie", data.getBonusHp() / 5, gear[0] + sb[0],
                "+5 PV max / pt · équipé : +" + (gear[0] + sb[0]) + " PV", data.getSkillPoints() >= 1);
        y = statRow(g, cx, y, 1, "§eForce", data.getBonusStr(), gear[1] + sb[1],
                "+1 dégât / pt à équipé : +" + (gear[1] + sb[1]), data.getSkillPoints() >= 1);
        int wisT = data.getBonusWis() + gear[2] + sb[2];
        y = statRow(g, cx, y, 2, "§bSagesse", data.getBonusWis(), gear[2] + sb[2],
                "XP ×" + (1 + wisT / 10) + " · prochain ×" + (2 + wisT / 10) + " à " + ((wisT / 10 + 1) * 10)
                        + " (" + wisT + "/" + ((wisT / 10 + 1) * 10) + ")",
                data.getSkillPoints() >= 1);
        int resT = data.getBonusRes() + gear[3] + sb[3];
        y = statRow(g, cx, y, 3, "§aRésistance", data.getBonusRes(), gear[3] + sb[3],
                "-" + Math.min(50, resT * 2) + "% dégâts subis (max -50%)", data.getSkillPoints() >= 1);

        y += 2;
        g.fill(cx + 4, y, cx + CW - 4, y + 1, 0x664A3421);
        y += 5;
        String act = WakfuArmorItem.activeSet(player);
        if (act != null) {
            smallText(g, trunc("§2Panoplie active : " + WakfuArmorItem.setNameFr(act)
                    + " §7→ " + WakfuArmorItem.setBonusLabel(act)
                    + (sb[4] > 0 ? " · +" + sb[4] + " PA" : "") + (sb[5] > 0 ? " · +" + sb[5] + " PM" : ""),
                    CW - 12), cx + 6, y, INK_TITLE);
        } else {
            smallText(g, "Aucune panoplie complète équipée (onglet Panoplies).", cx + 6, y, INK_FAINT);
        }
        y += 11;
        smallText(g, trunc("Arme équipée (clic droit) : "
                + (data.getEquippedWeapon().isEmpty() ? "§8aucune"
                : data.getEquippedWeapon().getHoverName().getString()), CW - 12), cx + 6, y, INK_TITLE);
    }

    private int statRow(GuiGraphics g, int cx, int y, int idx, String label, int pts, int gearPts,
                        String effect, boolean can) {
        smallText(g, label, cx + 4, y + 2, INK);
        String val = pts + " pts" + (gearPts > 0 ? " §8(+" + gearPts + " équip)" : "");
        smallText(g, val, cx + 56, y + 2, INK_TITLE);
        smallText(g, trunc(effect, CW - 12), cx + 4, y + 12, INK_MUTED);
        if (can) {
            int bx = cx + CW - 26, by = y + 2;
            g.fill(bx, by, bx + 20, by + 14, 0xFFE2D4B4);
            outline(g, bx, by, 20, 14, INK_GREEN);
            smallText(g, "+1", bx + 4, by + 3, INK_GREEN);
            rects.add(new int[]{bx, by, 20, 14, 20 + idx});
        }
        g.fill(cx + 2, y + 24, cx + CW - 2, y + 25, 0x334A3421);
        return y + 27;
    }

    // ---------------------------------------------------------------- utilitaires
    private void header(GuiGraphics g, int cx, int cy, String title, String right) {
        // Le titre du panneau commence après l'emblème déjà présent sur l'image.
        smallText(g, trunc(title, CW - (right.isEmpty() ? 20 : 100)), cx + 20, cy + 1, INK_TITLE);
        if (!right.isEmpty()) {
            String fittedRight = trunc(right, 92);
            smallText(g, fittedRight, cx + CW - 4 - textWidth(fittedRight), cy + 2, INK_MUTED);
        }
        g.fill(cx + 2, cy + 11, cx + CW - 2, cy + 12, 0x664A3421);
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int col) {
        g.fill(x, y, x + w, y + 1, col);
        g.fill(x, y + h - 1, x + w, y + h, col);
        g.fill(x, y, x + 1, y + h, col);
        g.fill(x + w - 1, y, x + w, y + h, col);
    }

    private static ResourceLocation portrait(String monsterId) {
        return new ResourceLocation(WakfuRPG.MODID, "textures/gui/codex_" + monsterId + ".png");
    }

    // ---------------------------------------------------------------- entrées
    @Override
    public boolean mouseScrolled(double mx, double my, double amt) {
        int d = amt > 0 ? -3 : 3;
        if (main == 1) spellScroll = Math.max(0, spellScroll + d * 8);
        else if (main == 0) {
            switch (sub) {
                case 0 -> monsterScroll = Math.max(0, monsterScroll + d * 8);
                case 3 -> lootScroll = Math.max(0, lootScroll + d * 8);
                case 4 -> setsScroll = Math.max(0, setsScroll + d * 8);
                case 5 -> npcScroll = Math.max(0, npcScroll + d * 8);
                default -> { }
            }
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        mx = toUiX(mx);
        my = toUiY(my);
        // Navigation calée sur les coordonnées de l'image source pour éviter les
        // erreurs d'arrondi qui rendaient certains sous-onglets difficiles à cliquer.
        if (main >= 0) {
            int ix = Math.round((float) ((mx - px) * 1152.0 / PW));
            int iy = Math.round((float) ((my - py) * 768.0 / PH));
            if (ix >= 60 && ix < 340 && iy >= 112 && iy < 630) {
                if (iy < 166) { main = -1; return true; }
                if (iy < 219) { main = 0; return true; }
                if (iy < 271) { main = 1; return true; }
                if (iy < 323) { main = 2; return true; }
                int[] starts = {323, 374, 425, 476, 527, 578};
                for (int i = 0; i < starts.length; i++) {
                    if (iy >= starts[i] && iy < starts[i] + 49) {
                        main = 0;
                        sub = i;
                        searchFocus = false;
                        spellScroll = 0;
                        return true;
                    }
                }
            }
        }
        if (main >= 0) {
            int bx = imageX(419), by = imageY(136), bw = imageW(677), bh = imageH(34);
            if (mx >= bx && mx < bx + bw && my >= by && my < by + bh) {
                searchFocus = true;
                return true;
            }
        }
        searchFocus = false;
        for (int[] r : rects) {
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                int id = r[4];
                if (id == 901) { searchFocus = true; return true; }
                click(id);
                Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (searchFocus) {
            if (key == 259) {   // backspace
                if (!query.isEmpty()) query = query.substring(0, query.length() - 1);
                return true;
            }
            if (key == 256 || key == 257) {   // echap / entree : ferme la saisie
                searchFocus = false;
                return true;
            }
        }
        if (main < 0) {
            if (key == 263 || key == 262) { // gauche / droite
                focusedCard = Math.floorMod(focusedCard + (key == 262 ? 1 : -1), 5);
                return true;
            }
            if (key == 69 || key == 257) { // E / Entrée ouvre la carte choisie
                click(700 + focusedCard);
                return true;
            }
        }
        if (key == 256 && main >= 0) {
            main = -1;
            searchFocus = false;
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (searchFocus && c >= ' ' && query.length() < 24) {
            query += c;
            return true;
        }
        return super.charTyped(c, mods);
    }

    private void click(int id) {
        if (id == 900) { query = ""; return; }          // effacer la recherche
        if (id < 4) { main = id - 1; return; }
        if (id >= 700 && id < 705) {
            switch (id) {
                case 700 -> main = 0;
                case 701 -> Minecraft.getInstance().setScreen(new EquipmentScreen());
                case 702 -> main = 2;
                case 703 -> main = 1;
                case 704 -> Minecraft.getInstance().setScreen(new SocialScreen());
            }
            return;
        }
        if (id >= 10 && id < 16) {
            main = 0; sub = id - 10; searchFocus = false; spellScroll = 0; return;
        }
        if (id >= 300 && id < 300 + NpcSpots.SPOTS.size()) {
            var mc2 = Minecraft.getInstance();
            if (mc2.level == null) return;
            NpcSpots.Spot s = NpcSpots.SPOTS.get(id - 300);
            var sp2 = mc2.level.getSharedSpawnPos();
            boolean marinAtGrecosSpawn = s.id().equals("marin")
                    && mc2.level.dimension().location().getNamespace().equals("wakfurpg")
                    && mc2.level.dimension().location().getPath().equals("archipel");
            Waypoints.toggle(s.nameFr(), marinAtGrecosSpawn ? sp2.getX() : sp2.getX() + s.dx(),
                    marinAtGrecosSpawn ? sp2.getZ() : sp2.getZ() + s.dz());
            return;
        }
        if (id >= 20 && id < 24) {
            String kind = switch (id - 20) {
                case 0 -> "hp";
                case 1 -> "str";
                case 2 -> "wis";
                default -> "res";
            };
            Network.sendToServer(new SpendPacket(kind, ""));
            return;
        }
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        player.getCapability(ModCapabilities.RPG).ifPresent(data -> {
            if (!data.hasClass()) return;
            List<Spell> spells = Spells.forClass(data.getRpgClass());
            if (id >= 100 && id < 200) {
                int i = id - 100;
                if (i < spells.size()) Network.sendToServer(new SpendPacket("spell", spells.get(i).id));
            } else if (id >= 200 && id < 300) {
                int i = id - 200;
                if (i < spells.size()) Network.sendToServer(new SpendPacket("equip", spells.get(i).id));
            }
        });
    }
}
