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

    private static final ResourceLocation PANEL =
            new ResourceLocation(WakfuRPG.MODID, "textures/gui/grimoire_panel.png");
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
    private static final int CX = 96, CY = 28, CW = 254, CH = 198;
    /** hauteur visible du contenu bestiaire (sous le champ de recherche). */
    private static final int BEST_H = CH - 30;

    private int main = -1;         // v0.25 : -1 menu (hub), 0 bestiaire, 1 sorts, 2 stats
    private int sub = 0;           // bestiaire : 0 monstres, 1 zones, 2 donjon, 3 butin, 4 panoplies
    private int spellScroll = 0;
    private int lootScroll = 0;
    private int monsterScroll = 0;
    private int setsScroll = 0;
    private String query = "";
    private boolean searchFocus = false;
    private final List<int[]> rects = new ArrayList<>();  // x,y,w,h,id
    private int px, py;

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
        px = width / 2 - PW / 2;
        py = height / 2 - PH / 2;
        // v0.23 : parchemin PROGRAMMATIQUE — fond clair garanti + encre foncée.
        // La lisibilité ne dépend plus d'aucune texture externe.
        g.fill(px, py, px + PW, py + PH, 0xFF3A2A1A);
        g.fill(px + 2, py + 2, px + PW - 2, py + PH - 2, 0xFF8A6A3A);
        g.fill(px + 4, py + 4, px + PW - 4, py + PH - 4, 0xFFEDE0C2);
        g.fill(px + 4, py + 4, px + PW - 4, py + 22, 0xFFE0CDA2);
        g.fill(px + 4, py + 22, px + PW - 4, py + 23, 0xFF9A7A2A);
        g.fill(px + 5, py + 24, px + 92, py + PH - 5, 0xFFE5D5B0);

        var player = Minecraft.getInstance().player;
        if (player == null) return;
        player.getCapability(ModCapabilities.RPG).ifPresent(data -> {
            g.drawString(GothicFont.get(), "Grimoire du Wakfu", px + 14, py + 9, INK_TITLE, false);
            // v0.25 : bourse ($) toujours visible dans l'en-tête
            String money = "$ " + ClientMoney.fmt(ClientMoney.get());
            g.drawString(font, money, px + PW - 12 - font.width(money), py + 12, INK_GOLD);
            String head = data.hasClass()
                    ? data.getRpgClass().getName().getString() + " · niv " + data.getLevel()
                    : "sans classe";
            g.drawString(font, head, px + PW - 18 - font.width(money) - font.width(head), py + 12, INK_MUTED);

            int cx = px + CX, cy = py + CY;
            if (main < 0) {
                // v0.25 : MENU PRINCIPAL — 5 cartes (bestiaire, inventaire,
                // statistiques, sorts, social) + bourse dans la colonne gauche
                drawHub(g, mx, my);
                String hintH = "[B/K] Grimoire  ·  [Échap] Fermer";
                g.drawString(font, hintH, px + PW / 2 - font.width(hintH) / 2, py + PH - 11, INK_MUTED);
                return;
            }
            drawMainTabs(g, mx, my);
            if (main == 0) drawSubTabs(g, mx, my);

            if (main == 0) {
                drawSearch(g);
                int yc = cy + 29;
                switch (sub) {
                    case 0 -> renderMonsters(g, cx, cy, yc, data);
                    case 1 -> renderZones(g, cx, cy, yc, data);
                    case 2 -> renderDungeon(g, cx, cy, yc, data);
                    case 3 -> renderLoot(g, cx, cy, yc, data);
                    case 4 -> renderSets(g, cx, cy, yc, data, player);
                    case 5 -> renderNpcs(g, cx, cy, yc);
                }
            } else if (main == 1) {
                renderSpells(g, cx, cy, data, mx, my);
            } else {
                renderStats(g, cx, cy, data, player);
            }

            String hint = "[B/K] Grimoire  ·  [Molette] défiler  ·  [Échap] Fermer";
            g.drawString(font, hint, px + PW / 2 - font.width(hint) / 2, py + PH - 11, INK_MUTED);
        });
    }

    // ---------------------------------------------------------------- recherche
    private void drawSearch(GuiGraphics g) {
        int bx = px + CX + 2, by = py + CY + 13, bw = 148, bh = 13;
        g.fill(bx, by, bx + bw, by + bh, searchFocus ? 0xFFF8F0D8 : 0xFFEADFC2);
        outline(g, bx, by, bw, bh, searchFocus ? INK_TITLE : INK_MUTED);
        if (query.isEmpty() && !searchFocus) {
            g.drawString(font, "Rechercher un monstre, un objet…", bx + 4, by + 3, INK_FAINT);
        } else {
            g.drawString(font, query, bx + 4, by + 3, INK);
            if (searchFocus && (System.currentTimeMillis() / 500) % 2 == 0) {
                int cw = font.width(query);
                g.fill(bx + 5 + cw, by + 3, bx + 6 + cw, by + 11, INK);
            }
        }
        if (!query.isEmpty()) {
            String clear = "✕ effacer";
            g.drawString(font, clear, bx + bw + 8, by + 3, INK_RED);
            rects.add(new int[]{bx + bw + 6, by, 46, bh, 900});
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
        while (font.width(s) > maxW && s.length() > 6) s = s.substring(0, s.length() - 2);
        return s;
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
        String m = "$ " + ClientMoney.fmt(ClientMoney.get());
        g.drawString(font, m, sx + 42 - font.width(m) / 2, sy + 40, INK_GOLD);
        String bourse = "Bourse";
        g.drawString(font, bourse, sx + 42 - font.width(bourse) / 2, sy + 54, INK_TITLE);
        String h1 = "L'argent se gagne en";
        String h2 = "combat, quêtes et ventes.";
        g.drawString(font, h1, sx + 42 - font.width(h1) / 2, sy + 66, INK_FAINT);
        g.drawString(font, h2, sx + 42 - font.width(h2) / 2, sy + 75, INK_FAINT);

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
            g.drawString(font, names[i], x + cw2 / 2 - font.width(names[i]) / 2, y + 40, INK_TITLE);
            g.drawString(font, desc1[i], x + cw2 / 2 - font.width(desc1[i]) / 2, y + 56, INK_MUTED);
            g.drawString(font, desc2[i], x + cw2 / 2 - font.width(desc2[i]) / 2, y + 66, INK_MUTED);
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
            g.drawString(font, lb, tx + tw / 2 - font.width(lb) / 2, ty + 5,
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
            g.drawString(font, labels[i], tx + tw / 2 - font.width(labels[i]) / 2, ty + 5,
                    sel ? INK_TITLE : INK_MUTED);
            rects.add(new int[]{tx, ty, tw, th, 10 + i});
        }
    }

    // ---------------------------------------------------------------- bestiaire : monstres
    private static final MonsterType[] BESTIARY = {
            MonsterType.ZOMBIE, MonsterType.SKELETON, MonsterType.BANDIT, MonsterType.BOSS,
            MonsterType.GUERRIER, MonsterType.SENTINELLE, MonsterType.POUPEE, MonsterType.GOLEM,
            MonsterType.COLOSSE,
            // v0.27 : Alex's Mobs creatures
            MonsterType.GRIZZLY, MonsterType.GAZELLE, MonsterType.RATTLESNAKE,
            MonsterType.ELEPHANT, MonsterType.GORILLA, MonsterType.FALCON,
            MonsterType.KOMODO_DRAGON, MonsterType.MOOSE, MonsterType.TIGER, MonsterType.CROW};

    private static String dropLine(MonsterType t) {
        if (t.xpReward == 0) return "Aucun butin — invocation";
        if (t == MonsterType.BOSS) {
            return "Butin : " + WakfuArmorItem.setChatColor("chef") + WakfuArmorItem.setNameFr("chef")
                    + " §7pièce 100 % · §6Écusson du chef §7100 % · §6argent $";
        }
        String set = WakfuArmorItem.setForMonster(t.getId());
        String tro = WakfuTrophyItem.forMonster(t.getId());
        StringBuilder b = new StringBuilder("Butin : ");
        if (set != null) {
            b.append(WakfuArmorItem.setChatColor(set)).append(WakfuArmorItem.setNameFr(set))
                    .append(" §730 % · ");
        }
        if (tro != null) b.append("§e").append(WakfuTrophyItem.nameFr(tro)).append(" §725 % · ");
        b.append("§7armure Fantasy 15 % · §6argent $");
        return b.toString();
    }

    private void renderMonsters(GuiGraphics g, int cx, int cy, int yc, PlayerRpgData data) {
        List<MonsterType> list = new ArrayList<>();
        for (MonsterType t : BESTIARY) if (matches(t.getName().getString())) list.add(t);
        int seen = 0;
        for (MonsterType t : BESTIARY) if (data.getCodexSeen().contains(t.getId())) seen++;
        header(g, cx, cy, "Bestiaire", seen + "/" + BESTIARY.length + " rencontres");
        int rowsH = list.size() * 38;
        monsterScroll = Math.max(0, Math.min(monsterScroll, rowsH - BEST_H));
        g.enableScissor(cx, yc, cx + CW, cy + CH);
        int y = yc - monsterScroll;
        for (MonsterType t : list) {
            if (y > yc - 38 && y < cy + CH) {
                boolean known = data.getCodexSeen().contains(t.getId());
                if (known) {
                    g.blit(portrait(t.getId()), cx + 2, y, 16, 16, 0, 0, 16, 16, 16, 16);
                    String name = t.getName().getString();
                    String tag = t == MonsterType.BOSS ? "  [BOSS]" : (t.xpReward == 0 ? "  [INVOCATION]" : "");
                    g.drawString(font, name, cx + 22, y, INK);
                    g.drawString(font, tag, cx + 22 + font.width(name), y,
                            t == MonsterType.BOSS ? INK_RED : INK_BLUE);
                    String stats = "PV " + t.maxHp + " · Dég " + t.damage + " · PM " + t.pm + " · Portée " + t.range
                            + (t.xpReward > 0 ? " · XP " + t.xpReward : "");
                    int[] range = MobVariants.levelRange(t.getId());
                    if (range != null) stats += " · §eNv. conseillé " + range[0] + "-" + range[1];
                    else if (t == MonsterType.BOSS) stats += " · §eNv. conseillé 10";
                    g.drawString(font, trunc(stats, CW - 46), cx + 22, y + 10, INK_MUTED);
                    if (range != null) {
                        g.drawString(font, trunc("Variantes : " + MobVariants.namesFor(t.getId()), CW - 26),
                                cx + 22, y + 19, INK_FAINT);
                    }
                    g.drawString(font, trunc(dropLine(t), CW - 26), cx + 22, y + 28, INK_MUTED);
                    int kills = data.getCodexKills().getOrDefault(t.getId(), 0);
                    String ks = kills > 0 ? "×" + kills : "—";
                    g.drawString(font, ks, cx + CW - 4 - font.width(ks), y, kills > 0 ? INK_GOLD : INK_MUTED);
                } else {
                    g.blit(LOCKED, cx + 2, y, 16, 16, 0, 0, 16, 16, 16, 16);
                    g.drawString(font, "??? ???", cx + 22, y, INK_MUTED);
                    g.drawString(font, "Créature jamais rencontrée.", cx + 22, y + 10, INK_FAINT);
                    g.drawString(font, "Son butin reste un mystère…", cx + 22, y + 19, INK_FAINT);
                }
                g.fill(cx + 2, y + 37, cx + CW - 2, y + 38, 0x334A3421);
            }
            y += 38;
        }
        g.disableScissor();
        if (list.isEmpty()) g.drawString(font, "Aucun résultat pour « " + query + " ».", cx + 6, yc + 6, INK_RED);
    }

    // ---------------------------------------------------------------- bestiaire : PNJ & lieux
    private void renderNpcs(GuiGraphics g, int cx, int cy, int yc) {
        header(g, cx, cy, "PNJ & Lieux",
                Waypoints.isActive() ? "suivi : " + Waypoints.name() : "aucun suivi");
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        var sp = mc.level.getSharedSpawnPos();
        int y = yc;
        for (int i = 0; i < NpcSpots.SPOTS.size(); i++) {
            NpcSpots.Spot s = NpcSpots.SPOTS.get(i);
            int wx = sp.getX() + s.dx(), wz = sp.getZ() + s.dz();
            double ddx = wx - mc.player.getX(), ddz = wz - mc.player.getZ();
            int dist = (int) Math.round(Math.sqrt(ddx * ddx + ddz * ddz));
            boolean on = Waypoints.isActive() && Waypoints.name().equals(s.nameFr());
            g.fill(cx + 2, y, cx + CW - 2, y + 23, on ? 0xFFE9E0BE : 0xFFE2D4B4);
            outline(g, cx + 2, y, CW - 4, 23, on ? INK_GREEN : INK_MUTED);
            g.drawString(font, s.nameFr(), cx + 8, y + 3, INK);
            g.drawString(font, dist + " m · vers " + dirOf(ddx, ddz), cx + 8, y + 13, INK_MUTED);
            button(g, cx + CW - 62, y + 5, 56, 13, on ? "✓ Suivi" : "Suivre",
                    on ? INK_GREEN : INK_TITLE, 300 + i);
            y += 26;
        }
        g.drawString(font, "« Suivre » trace une traînée lumineuse au sol jusqu'à la cible", cx + 6, y + 4, INK_FAINT);
        g.drawString(font, "et affiche la distance restante en haut de l'écran.", cx + 6, y + 15, INK_FAINT);
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
        int cwZ = 82, chZ = 50;
        for (int sz = -1; sz <= 1; sz++) {
            for (int sx = -1; sx <= 1; sx++) {
                int gx = cx + (sx + 1) * (cwZ + 4), gy = yc + (sz + 1) * (chZ + 3);
                boolean known = data.getCodexZones().contains(sx + "," + sz);
                g.fill(gx, gy, gx + cwZ, gy + chZ, known ? 0xFFE2D4B4 : 0xCBB99B);
                outline(g, gx, gy, cwZ, chZ, known ? INK_TITLE : INK_MUTED);
                g.drawString(font, zoneName(sx, sz), gx + 4, gy + 3, known ? INK_TITLE : INK_MUTED);
                if (known) {
                    g.drawString(font, "✓ Explorée", gx + 4, gy + 15, INK_GREEN);
                    int target = 3 + Math.abs(sx * 31 + sz * 17) % 2;
                    g.drawString(font, "≈" + target + " monstres", gx + 4, gy + 25, INK_MUTED);
                    if (sx == 0 && sz == 0) g.drawString(font, "Village & PNJ", gx + 4, gy + 35, INK_MUTED);
                } else {
                    g.drawString(font, "Non explorée", gx + 4, gy + 15, INK_FAINT);
                    g.drawString(font, "Zone inconnue", gx + 4, gy + 25, INK_FAINT);
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
            g.drawString(font, "??? — Repaire inconnu", cx + 42, yc + 8, INK_MUTED);
            g.drawString(font, "Des rumeurs parlent d'un fortin de pierre à", cx + 42, yc + 20, INK_FAINT);
            g.drawString(font, "l'est du village. Suis les poteaux-torches…", cx + 42, yc + 30, INK_FAINT);
            return;
        }
        int y = yc;
        g.blit(DUNGEON_ICON, cx + 2, y, 32, 32, 0, 0, 32, 32, 32, 32);
        g.drawString(font, "Repaire du Chef des Bandits", cx + 42, y + 2, INK_TITLE);
        g.drawString(font, "✓ Découvert · fortin de pierre à l'est", cx + 42, y + 14, INK_GREEN);
        g.drawString(font, "Muraille crénelée, tours, chemin balisé.", cx + 42, y + 24, INK_MUTED);
        y += 40;
        g.fill(cx + 2, y, cx + CW - 2, y + 1, 0x334A3421);
        y += 5;
        boolean bossSeen = data.getCodexSeen().contains("chef_bandit");
        int bossKills = data.getCodexKills().getOrDefault("chef_bandit", 0);
        g.blit(bossSeen ? portrait("chef_bandit") : LOCKED, cx + 2, y, 16, 16, 0, 0, 16, 16, 16, 16);
        g.drawString(font, bossSeen ? "Chef des Bandits  [BOSS]" : "??? ???", cx + 22, y, bossSeen ? INK_RED : INK_MUTED);
        String bs = bossKills > 0 ? "×" + bossKills + " vaincu — réapparaît 20 min après sa mort"
                : (bossSeen ? "Rencontré mais pas encore vaincu (Nv. 10, 130 PV)"
                : "Jamais affronté — il garde la salle scellée au nord-est.");
        g.drawString(font, trunc(bs, CW - 26), cx + 22, y + 10, INK_MUTED);
        y += 24;
        g.blit(portrait("bandit"), cx + 2, y, 16, 16, 0, 0, 16, 16, 16, 16);
        g.drawString(font, "Gardes bandits (Nv. 5-8)", cx + 22, y, INK);
        g.drawString(font, "Trois patrouillent la cour ; ils reviennent avec le temps.", cx + 22, y + 10, INK_MUTED);
        y += 24;
        g.fill(cx + 2, y, cx + CW - 2, y + 1, 0x334A3421);
        y += 5;
        g.drawString(font, "Coffre du boss (réapprovisionné à chaque réapparition) :", cx + 2, y, INK_TITLE);
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
        g.drawString(font, "rare/Fantasy · épique · arme · pommes · §6pièce Chef", ix + 4, y + 4, INK_MUTED);
    }

    // ---------------------------------------------------------------- bestiaire : butin
    private record LootEntry(String key, String name, String stats, ItemStack icon) {}

    private static List<LootEntry> lootEntries() {
        List<LootEntry> l = new ArrayList<>();
        // v0.22 : panoplies de zone d'abord
        for (String set : WakfuArmorItem.SETS) {
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
        l.add(new LootEntry("ingot", "§6Argent ($)", "bourse : monstres, quêtes, ventes", new ItemStack(Items.GOLD_INGOT)));
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
        g.enableScissor(cx, yc, cx + CW, cy + CH);
        int y = yc - lootScroll;
        for (LootEntry e : entries) {
            if (y > yc - 20 && y < cy + CH) {
                Integer n = data.getCodexItems().get(e.key());
                if (n != null) {
                    g.renderItem(e.icon(), cx + 2, y);
                    g.drawString(font, trunc(e.name(), CW - 70), cx + 22, y, INK);
                    g.drawString(font, trunc(e.stats(), CW - 26), cx + 22, y + 9, INK_MUTED);
                    String cs = "×" + n;
                    g.drawString(font, cs, cx + CW - 4 - font.width(cs), y + 4, INK_GOLD);
                } else {
                    g.blit(LOCKED, cx + 2, y, 16, 16, 0, 0, 16, 16, 16, 16);
                    g.drawString(font, "??? — jamais reçu", cx + 22, y, INK_FAINT);
                    g.drawString(font, trunc(e.stats(), CW - 26), cx + 22, y + 9, INK_FAINT);
                }
                g.fill(cx + 2, y + 19, cx + CW - 2, y + 20, 0x334A3421);
            }
            y += 20;
        }
        g.disableScissor();
        if (entries.isEmpty()) g.drawString(font, "Aucun résultat pour « " + query + " ».", cx + 6, yc + 6, INK_RED);
    }

    // ---------------------------------------------------------------- bestiaire : panoplies
    private void renderSets(GuiGraphics g, int cx, int cy, int yc, PlayerRpgData data,
                            net.minecraft.world.entity.player.Player player) {
        List<String> sets = new ArrayList<>();
        for (String set : WakfuArmorItem.SETS) {
            if (matches(WakfuArmorItem.setNameFr(set)) || matches(WakfuArmorItem.setSource(set))
                    || matches("panoplie")) sets.add(set);
        }
        int complete = 0;
        for (String set : WakfuArmorItem.SETS) {
            int n = ownedPieces(data, set);
            if (n == 4) complete++;
        }
        header(g, cx, cy, "Panoplies de zone", complete + "/" + WakfuArmorItem.SETS.length + " complètes");
        int rowsH = sets.size() * 62;
        setsScroll = Math.max(0, Math.min(setsScroll, rowsH - BEST_H));
        g.enableScissor(cx, yc, cx + CW, cy + CH);
        int y = yc - setsScroll;
        String active = WakfuArmorItem.activeSet(player);
        for (String set : sets) {
            if (y > yc - 62 && y < cy + CH) {
                int owned = ownedPieces(data, set);
                boolean isActive = set.equals(active);
                g.fill(cx + 2, y, cx + CW - 2, y + 60, isActive ? 0xFFE9E0BE : 0xFFE2D4B4);
                outline(g, cx + 2, y, cx + CW - 4, 60, isActive ? INK_GREEN : INK_MUTED);
                g.drawString(font, WakfuArmorItem.setChatColor(set) + WakfuArmorItem.setNameFr(set)
                        + " §8— " + trunc(WakfuArmorItem.setSource(set), CW - 130), cx + 6, y + 3, INK);
                String cnt = owned + "/4";
                g.drawString(font, cnt, cx + CW - 8 - font.width(cnt), y + 3,
                        owned == 4 ? INK_GREEN : INK_MUTED);
                for (int i = 0; i < 4; i++) {
                    String slot = WakfuArmorItem.SLOTS[i];
                    boolean has = data.getCodexItems().containsKey("set_" + set + "_" + slot);
                    int ix = cx + 8 + i * 62;
                    g.renderItem(WakfuArmorItem.makeSetPiece(set, slot), ix, y + 15);
                    if (!has) {
                        g.fill(ix, y + 15, ix + 16, y + 31, 0xB0101014);
                        g.blit(LOCKED, ix, y + 15, 16, 16, 0, 0, 16, 16, 16, 16);
                    }
                    g.drawString(font, has ? WakfuArmorItem.slotNameFr(slot) : "???",
                            ix + 17, y + 18, has ? INK : INK_FAINT);
                    int[] f = WakfuArmorItem.fixedStats(WakfuArmorItem.makeSetPiece(set, slot));
                    String sh = (f[0] > 0 ? f[0] + "V " : "") + (f[1] > 0 ? f[1] + "F " : "")
                            + (f[2] > 0 ? f[2] + "S " : "") + (f[3] > 0 ? f[3] + "R" : "");
                    g.drawString(font, sh.trim(), ix - 2, y + 33, INK_MUTED);
                }
                String bonus = "Bonus (4 pièces) : §e" + WakfuArmorItem.setBonusLabel(set)
                        + (isActive ? " §2✔ ACTIVE" : (owned == 4 ? " §7— équipe les 4 pièces" : " §8—incomplet"));
                g.drawString(font, trunc(bonus, CW - 12), cx + 6, y + 46, INK_TITLE);
            }
            y += 62;
        }
        g.disableScissor();
        if (sets.isEmpty()) g.drawString(font, "Aucun résultat pour « " + query + " ».", cx + 6, yc + 6, INK_RED);
    }

    private static int ownedPieces(PlayerRpgData data, String set) {
        int n = 0;
        for (String slot : WakfuArmorItem.SLOTS) {
            if (data.getCodexItems().containsKey("set_" + set + "_" + slot)) n++;
        }
        return n;
    }

    // ---------------------------------------------------------------- sorts
    private void renderSpells(GuiGraphics g, int cx, int cy, PlayerRpgData data, int mx, int my) {
        if (!data.hasClass()) {
            header(g, cx, cy, "Sorts", "");
            g.drawString(font, "Choisis d'abord une classe auprès du Maître", cx + 8, cy + 30, INK_MUTED);
            g.drawString(font, "au village (dialogue au spawn).", cx + 8, cy + 42, INK_MUTED);
            return;
        }
        List<Spell> spells = Spells.forClass(data.getRpgClass());
        header(g, cx, cy, "Sorts de " + data.getRpgClass().getName().getString(),
                "Points de sort : " + data.getSpellPts());
        int rowsH = spells.size() * 30;
        spellScroll = Math.max(0, Math.min(spellScroll, rowsH - (CH - 16)));
        g.enableScissor(cx, cy + 13, cx + CW, cy + CH);
        int y = cy + 14 - spellScroll;
        for (int i = 0; i < spells.size(); i++) {
            Spell s = spells.get(i);
            if (y > cy - 24 && y < cy + CH) {
                boolean unlocked = data.getLevel() >= s.unlockLevel;
                ResourceLocation icon = new ResourceLocation(WakfuRPG.MODID, "textures/item/spell_" + s.id + ".png");
                g.blit(icon, cx + 3, y + 5, 18, 18, 0, 0, 32, 32, 32, 32);
                if (!unlocked) g.fill(cx + 3, y + 5, cx + 21, y + 23, 0xB0101014);
                int lvl = data.spellLevel(s.id);
                String losTag = s.needsLos() ? " §8[murs]" : (s.pierce ? " §2[perce]" : "");
                String name = (unlocked ? "" : "§8🔒 ") + s.getName().getString() + losTag;
                g.drawString(font, name, cx + 25, y + 4, unlocked ? INK : INK_MUTED);
                String dmgTxt = s.summon != null ? "invoc." : (s.damage < 0 ? "soin " + (-s.damage) : "dég " + (s.damage + 2 * lvl));
                String info = s.apCost + " PA · " + s.rangeLabel() + " · " + s.zoneLabel() + " · " + dmgTxt
                        + " · Lv " + lvl + "/" + SpendPacket.MAX_SPELL_LEVEL;
                int maxW = CW - 73;
                while (font.width(info) > maxW && info.length() > 8) info = info.substring(0, info.length() - 2);
                g.drawString(font, info, cx + 25, y + 15, INK_MUTED);
                if (!unlocked) {
                    String lk = "niv. " + s.unlockLevel;
                    g.drawString(font, lk, cx + CW - 4 - font.width(lk), y + 9, INK_RED);
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

    private void button(GuiGraphics g, int x, int y, int w, int h, String label, int col, int id) {
        g.fill(x, y, x + w, y + h, 0xFFE2D4B4);
        outline(g, x, y, w, h, col);
        g.drawString(font, label, x + w / 2 - font.width(label) / 2, y + 3, col);
        rects.add(new int[]{x, y, w, h, id});
    }

    // ---------------------------------------------------------------- statistiques
    private void renderStats(GuiGraphics g, int cx, int cy, PlayerRpgData data,
                             net.minecraft.world.entity.player.Player player) {
        header(g, cx, cy, "Statistiques", "Points : " + data.getSkillPoints());
        int[] gear = WakfuArmorItem.equippedFixed(player);
        int[] sb = WakfuArmorItem.activeSetBonus(player);
        int y = cy + 16;
        int bw = CW - 8;
        g.fill(cx + 4, y, cx + 4 + bw, y + 5, 0xFFD8C8A8);
        int fw = data.getXpToNext() > 0 ? bw * data.getXp() / data.getXpToNext() : 0;
        g.fill(cx + 4, y, cx + 4 + Math.min(bw, fw), y + 5, 0xFF5A8AB4);
        String xps = "XP " + data.getXp() + "/" + data.getXpToNext();
        g.drawString(font, xps, cx + CW - 4 - font.width(xps), y + 7, INK_MUTED);
        y += 18;

        y = statRow(g, cx, y, 0, "§cVie", data.getBonusHp() / 5, gear[0] + sb[0],
                "+5 PV max / pt · équipé : +" + (gear[0] + sb[0]) + " PV", data.getSkillPoints() >= 1);
        y = statRow(g, cx, y, 1, "§eForce", data.getBonusStr(), gear[1] + sb[1],
                "+1 dégât / pt · équipé : +" + (gear[1] + sb[1]), data.getSkillPoints() >= 1);
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
            g.drawString(font, trunc("§2Panoplie active : " + WakfuArmorItem.setNameFr(act)
                    + " §7→ " + WakfuArmorItem.setBonusLabel(act)
                    + (sb[4] > 0 ? " · +" + sb[4] + " PA" : "") + (sb[5] > 0 ? " · +" + sb[5] + " PM" : ""),
                    CW - 12), cx + 6, y, INK_TITLE);
        } else {
            g.drawString(font, "Aucune panoplie complète équipée (onglet Panoplies).", cx + 6, y, INK_FAINT);
        }
        y += 11;
        g.drawString(font, trunc("Arme équipée (clic droit) : "
                + (data.getEquippedWeapon().isEmpty() ? "§8aucune"
                : data.getEquippedWeapon().getHoverName().getString()), CW - 12), cx + 6, y, INK_TITLE);
    }

    private int statRow(GuiGraphics g, int cx, int y, int idx, String label, int pts, int gearPts,
                        String effect, boolean can) {
        g.drawString(font, label, cx + 4, y + 2, INK);
        String val = pts + " pts" + (gearPts > 0 ? " §8(+" + gearPts + " équip)" : "");
        g.drawString(font, val, cx + 56, y + 2, INK_TITLE);
        g.drawString(font, trunc(effect, CW - 12), cx + 4, y + 12, INK_MUTED);
        if (can) {
            int bx = cx + CW - 26, by = y + 2;
            g.fill(bx, by, bx + 20, by + 14, 0xFFE2D4B4);
            outline(g, bx, by, 20, 14, INK_GREEN);
            g.drawString(font, "+1", bx + 4, by + 3, INK_GREEN);
            rects.add(new int[]{bx, by, 20, 14, 20 + idx});
        }
        g.fill(cx + 2, y + 24, cx + CW - 2, y + 25, 0x334A3421);
        return y + 27;
    }

    // ---------------------------------------------------------------- utilitaires
    private void header(GuiGraphics g, int cx, int cy, String title, String right) {
        g.drawString(font, title, cx + 2, cy + 1, INK_TITLE);
        if (!right.isEmpty()) {
            g.drawString(font, right, cx + CW - 4 - font.width(right), cy + 2, INK_MUTED);
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
                default -> { }
            }
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // champ de recherche du bestiaire
        if (main == 0) {
            int bx = px + CX + 2, by = py + CY + 13;
            if (mx >= bx && mx < bx + 148 && my >= by && my < by + 13) {
                searchFocus = true;
                return true;
            }
            searchFocus = false;
        }
        for (int[] r : rects) {
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                int id = r[4];
                click(id);
                Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
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
        if (id >= 10 && id < 16) { sub = id - 10; return; }
        if (id >= 300 && id < 300 + NpcSpots.SPOTS.size()) {
            var mc2 = Minecraft.getInstance();
            if (mc2.level == null) return;
            NpcSpots.Spot s = NpcSpots.SPOTS.get(id - 300);
            var sp2 = mc2.level.getSharedSpawnPos();
            Waypoints.toggle(s.nameFr(), sp2.getX() + s.dx(), sp2.getZ() + s.dz());
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
