package fr.wakfu.rpg.client;

import fr.wakfu.rpg.WakfuRPG;
import fr.wakfu.rpg.core.ModCapabilities;
import fr.wakfu.rpg.core.PlayerRpgData;
import fr.wakfu.rpg.core.spell.Spell;
import fr.wakfu.rpg.core.spell.Spells;
import fr.wakfu.rpg.network.CombatStatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD custom style Paladium/Wakfu :
 * - permanent (bas-gauche, discret) : bandeau pixel-art pierre/or avec
 *   PV, armure, niveau, XP + icone livre avec la touche [K]
 * - en combat : tours a gauche, PV adversaire en haut, barre de sorts
 *   en bas (icones, fin du tour, fuite), curseur libre
 */
public class WakfuHud implements IGuiOverlay {

    /** Atlas GUI v0.5 (utilise aussi par DialogueScreen). */
    public static final ResourceLocation ATLAS =
            new ResourceLocation(WakfuRPG.MODID, "textures/gui/atlas.png");

    private static final ResourceLocation PANEL =
            new ResourceLocation(WakfuRPG.MODID, "textures/gui/hud_panel.png");
    private static final ResourceLocation STRIP =
            new ResourceLocation(WakfuRPG.MODID, "textures/gui/hud_strip.png");
    private static final ResourceLocation ICON_HEART =
            new ResourceLocation(WakfuRPG.MODID, "textures/gui/icons/heart.png");
    private static final ResourceLocation ICON_SHIELD =
            new ResourceLocation(WakfuRPG.MODID, "textures/gui/icons/shield.png");
    private static final ResourceLocation ICON_STAR =
            new ResourceLocation(WakfuRPG.MODID, "textures/gui/icons/star.png");
    private static final ResourceLocation ICON_BOOK =
            new ResourceLocation(WakfuRPG.MODID, "textures/gui/icons/book.png");

    /** Zones cliquables du HUD de combat : x,y,w,h,action (0-3 sort, 10 fin tour, 11 fuite). */
    private static final List<int[]> HUD_RECTS = new ArrayList<>();

    public static int hitTest(double gx, double gy) {
        for (int[] r : HUD_RECTS) {
            if (gx >= r[0] && gx <= r[0] + r[2] && gy >= r[1] && gy <= r[1] + r[3]) {
                return r[4];
            }
        }
        return -1;
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        HUD_RECTS.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null || mc.options.hideGui) return;

        mc.player.getCapability(ModCapabilities.RPG).ifPresent(data -> {
            if (!data.hasClass()) return;
            CombatStatePacket st = ClientCombatHud.get();
            drawStripHud(g, mc, data, screenWidth, screenHeight);
            if (st.inCombat) {
                drawCombatHud(g, mc, data, st, screenWidth, screenHeight);
            } else if (ClientInvites.isActive()) {
                drawInvite(g, mc, screenWidth);
            }
            fr.wakfu.rpg.client.DiscoveryToasts.render(g, screenWidth);
        });
    }

    // ------------------------------------------------------------------
    // HUD permanent : bandeau pixel-art bas-gauche (le meme hors/in combat)
    // ------------------------------------------------------------------
    private static void drawStripHud(GuiGraphics g, Minecraft mc, PlayerRpgData data,
                                     int screenWidth, int screenHeight) {
        boolean inCombat = fr.wakfu.rpg.client.ClientCombatHud.get().inCombat;
        // v0.23 : QUÊTE en haut à DROITE (panneau propre, aucun chevauchement)
        var q = fr.wakfu.rpg.core.Quests.current(data);
        if (q != null) {
            int qw = 182, qx = screenWidth - qw - 4, qy = 4;
            g.fill(qx, qy, qx + qw, qy + 36, 0x90101014);
            g.fill(qx, qy, qx + qw, qy + 1, 0xFFCCAA33);
            g.blit(new ResourceLocation(WakfuRPG.MODID, "textures/gui/" + q.icon + ".png"),
                    qx + 3, qy + 3, 11, 11, 0, 0, 32, 32, 32, 32);
            g.drawString(mc.font, "§6" + q.title, qx + 17, qy + 4, 0xFFDD66);
            String desc = "§7" + q.desc;
            while (mc.font.width(desc) > qw - 22 && desc.length() > 8) {
                desc = desc.substring(0, desc.length() - 2);
            }
            g.drawString(mc.font, desc, qx + 17, qy + 14, 0xDDDDDD);
            g.drawString(mc.font, "§e" + Math.min(data.getQuestProgress(), q.target) + "/" + q.target,
                    qx + 17, qy + 24, 0xFFFF55);
        }
        // v0.23 : suivi de PNJ/lieu — bannière en haut au centre (hors combat)
        if (!inCombat && fr.wakfu.rpg.client.Waypoints.isActive()) {
            String t = "§e➤ §f" + fr.wakfu.rpg.client.Waypoints.name()
                    + " §7— §e" + fr.wakfu.rpg.client.Waypoints.distance(mc) + " m §8("
                    + fr.wakfu.rpg.client.Waypoints.direction(mc) + ")";
            g.drawString(mc.font, t, screenWidth / 2 - mc.font.width(t) / 2, 6, 0xFFFFFF);
        }
        // v0.23 : bandeau VIE/NIVEAU en bas à DROITE — v0.25 : + ligne argent ($), h 45
        int w = 128, h = 45;
        int x = screenWidth - w - 4;
        int y = screenHeight - h - 4;

        g.fill(x, y, x + w, y + h, 0xC0101018);   // fond garanti lisible
        g.blit(STRIP, x, y, w, 32, 0, 0, 128, 32, 128, 32);
        g.fill(x + 1, y + 32, x + w - 1, y + 33, 0xFF3A3A55);

        int ix = x + 5;
        int iy = y + 4;
        int iconS = 9;

        // coeur + PV
        g.blit(ICON_HEART, ix, iy, iconS, iconS, 0, 0, 32, 32, 32, 32);
        int hpBonus = fr.wakfu.rpg.item.WakfuArmorItem.equippedFixed(mc.player)[0];
        g.drawString(mc.font, "§f" + data.getHp() + "/" + (data.maxHp() + hpBonus), ix + 12, iy + 1, 0xFFFFFF);
        // v0.20 : petit "+" vert clignotant quand la vie regenere (hors combat)
        if (data.getHp() < data.maxHp() + hpBonus
                && !fr.wakfu.rpg.client.ClientCombatHud.get().inCombat
                && (System.currentTimeMillis() / 500) % 2 == 0) {
            g.drawString(mc.font, "§a+", ix + 46, iy + 1, 0x55FF55);
        }

        // bouclier + armure
        int armor = mc.player != null ? Math.max(0, mc.player.getArmorValue()) : 0;
        g.blit(ICON_SHIELD, ix + 62, iy, iconS, iconS, 0, 0, 32, 32, 32, 32);
        g.drawString(mc.font, "§f" + armor, ix + 74, iy + 1, armor > 0 ? 0xFFFFFF : 0x999999);

        // etoile + niveau + micro-barre XP
        int iy2 = iy + 13;
        g.blit(ICON_STAR, ix, iy2, iconS, iconS, 0, 0, 32, 32, 32, 32);
        g.drawString(mc.font, "§eNiv " + data.getLevel(), ix + 12, iy2 + 1, 0xFFDD55);
        int barX = ix + 56, barW = 44, barY = iy2 + 3;
        g.fill(barX, barY, barX + barW, barY + 4, 0xAA111122);
        int fw = data.getXpToNext() > 0 ? Math.min(barW, barW * data.getXp() / data.getXpToNext()) : 0;
        g.fill(barX, barY, barX + fw, barY + 4, 0xFF55CCFF);

        // grimoire : icone livre + touche [B]
        g.blit(ICON_BOOK, ix + 104, iy2, iconS, iconS, 0, 0, 32, 32, 32, 32);
        g.drawString(mc.font, "§7B", ix + 115, iy2 + 1, 0xCCCCCC);

        // v0.25 : argent ($) — pièce pixel + solde, 3e ligne du bandeau
        int iy3 = iy2 + 13;
        g.fill(ix + 1, iy3, ix + 8, iy3 + 9, 0xFF8A6A10);
        g.fill(ix, iy3 + 1, ix + 9, iy3 + 8, 0xFF8A6A10);
        g.fill(ix + 2, iy3 + 1, ix + 7, iy3 + 8, 0xFFE8B830);
        g.fill(ix + 1, iy3 + 2, ix + 8, iy3 + 7, 0xFFE8B830);
        g.fill(ix + 3, iy3 + 3, ix + 6, iy3 + 6, 0xFFFFE080);
        g.drawString(mc.font, "§6" + fr.wakfu.rpg.client.ClientMoney.str(), ix + 12, iy3, 0xFFDD55);
    }

    // ------------------------------------------------------------------
    // v0.19 : notification d'invitation au combat de groupe (cliquable)
    // ------------------------------------------------------------------
    private static void drawInvite(GuiGraphics g, Minecraft mc, int sw) {
        int w = 280, h = 46;
        int x = sw / 2 - w / 2, y = 36;
        g.fill(x, y, x + w, y + h, 0xEE14101C);
        g.fill(x, y, x + w, y + 1, 0xFFCCAA33);
        g.fill(x, y + h - 1, x + w, y + h, 0xFFCCAA33);
        g.fill(x, y, x + 1, y + h, 0xFFCCAA33);
        g.fill(x + w - 1, y, x + w, y + h, 0xFFCCAA33);
        g.drawString(GothicFont.get(), "§6⚔ " + ClientInvites.leader() + " engage un combat !", x + 10, y + 6, 0xFFFFCC77, false);
        g.drawString(mc.font, "§7" + ClientInvites.mobs() + " monstre(s) — l'invitation expire au premier mouvement.",
                x + 10, y + 19, 0xCCCCCC);
        int bx = x + w - 152, by = y + 30, bw2 = 68, bh2 = 13;
        g.fill(bx, by, bx + bw2, by + bh2, 0xFF2E5A2E);
        g.fill(bx + 1, by + 1, bx + bw2 - 1, by + bh2 - 1, 0xFF3E7A3E);
        g.drawString(mc.font, "§fRejoindre", bx + 8, by + 3, 0xFFFFFF);
        HUD_RECTS.add(new int[]{bx, by, bw2, bh2, 20});
        int rx = bx + bw2 + 8;
        g.fill(rx, by, rx + bw2, by + bh2, 0xFF5A2E2E);
        g.fill(rx + 1, by + 1, rx + bw2 - 1, by + bh2 - 1, 0xFF7A3E3E);
        g.drawString(mc.font, "§fRefuser", rx + 12, by + 3, 0xFFFFFF);
        HUD_RECTS.add(new int[]{rx, by, bw2, bh2, 21});
    }

    // ------------------------------------------------------------------
    // HUD de combat façon Wakfu
    // ------------------------------------------------------------------
    private static void drawCombatHud(GuiGraphics g, Minecraft mc, PlayerRpgData data,
                                      CombatStatePacket st, int sw, int sh) {
        // (v0.13 : diag retire d'ici — il passait SOUS le panneau des tours ;
        //  le diagnostic complet reste visible dans le menu pause)
        // --- panneau des tours (gauche) — v0.19 : liste du groupe
        int nAllies = 0, nFoes = 0;
        for (int[] u : st.units) {
            if (u[0] == 0) nAllies++;
            else nFoes++;
        }
        int px = 4, py = 4, pw = 120, ph = 26 + Math.max(1, nAllies) * 11 + 13;
        g.blit(PANEL, px, py, pw, ph, 0, 64, 128, 64, 128, 128);
        g.drawString(GothicFont.get(), "§6Round §e" + st.round, px + 8, py + 5, 0xFFFFFF);
        int ly = py + 18;
        int idx = 0;
        for (int[] u : st.units) {
            if (u[0] != 0) continue;
            String nm = idx < st.unitNames.size() ? st.unitNames.get(idx) : "?";
            idx++;
            if (nm.length() > 10) nm = nm.substring(0, 10);
            String mark = u[6] == 1 ? "§2► " : "§8· ";
            g.drawString(mc.font, mark + "§f" + nm + " §a" + u[4], px + 8, ly, 0xFFFFFF);
            ly += 11;
        }
        if (nAllies == 0) {
            g.drawString(mc.font, "§f" + mc.player.getName().getString(), px + 8, ly, 0xFFFFFF);
            ly += 11;
        }
        String foeLine = st.pvp
                ? (st.yourTurn ? "§8· " : "§c► ") + "§7" + (st.oppName.length() > 10 ? st.oppName.substring(0, 10) : st.oppName)
                : (st.yourTurn ? "§8· " : "§c► ") + "§7" + (nFoes > 1 ? nFoes + " monstres" : (nFoes == 1 ? "1 monstre" : "0 monstre"));
        g.drawString(mc.font, foeLine, px + 8, ly, 0xFFFFFF);

        // --- PV adversaire (haut centre)
        int bw = 180, bh = 22;
        int bx = sw / 2 - bw / 2, by = 6;
        g.fill(bx, by, bx + bw, by + bh, 0xCC0A0A12);
        g.fill(bx, by, bx + bw, by + 1, 0xFFCCAA33);
        g.fill(bx, by + bh - 1, bx + bw, by + bh, 0xFFCCAA33);
        g.fill(bx, by, bx + 1, by + bh, 0xFFCCAA33);
        g.fill(bx + bw - 1, by, bx + bw, by + bh, 0xFFCCAA33);
        g.fill(bx + 2, by + 11, bx + bw - 2, by + bh - 2, 0xFF1E3B1E);
        int fwid = st.oppMaxHp > 0 ? Math.min(bw - 4, (bw - 4) * st.oppHp / st.oppMaxHp) : 0;
        g.fill(bx + 2, by + 11, bx + 2 + fwid, by + bh - 2, 0xFF55CC55);
        int foeCount = 0;
        for (int[] u : st.units) if (u[0] == 1) foeCount++;
        String topName = st.oppName + (foeCount > 1 ? " §e×" + foeCount : "");
        g.drawCenteredString(mc.font, "§f" + topName + "  §a" + st.oppHp + "§7/§a" + st.oppMaxHp,
                bx + bw / 2, by + 2, 0xFFFFFF);

        // --- v0.20 : barre de PV DU JOUEUR (grande, explicite, sous celle de l'ennemi)
        int mw = 180, mh = 16;
        int mx2 = sw / 2 - mw / 2, my2 = by + bh + 4;
        int mmax = data.maxHp() + fr.wakfu.rpg.item.WakfuArmorItem.equippedFixed(mc.player)[0];
        int mhp = data.getHp();
        float mfrac = mmax > 0 ? Math.max(0f, Math.min(1f, mhp / (float) mmax)) : 0f;
        int mcol = mfrac > 0.5f ? 0xFF55CC55 : (mfrac > 0.25f ? 0xFFCCAA33 : 0xFFCC4433);
        g.fill(mx2, my2, mx2 + mw, my2 + mh, 0xCC0A0A12);
        g.fill(mx2, my2, mx2 + mw, my2 + 1, 0xFF55AA55);
        g.fill(mx2, my2 + mh - 1, mx2 + mw, my2 + mh, 0xFF55AA55);
        g.fill(mx2, my2, mx2 + 1, my2 + mh, 0xFF55AA55);
        g.fill(mx2 + mw - 1, my2, mx2 + mw, my2 + mh, 0xFF55AA55);
        g.fill(mx2 + 2, my2 + 8, mx2 + mw - 2, my2 + mh - 2, 0xFF3B1E1E);
        g.fill(mx2 + 2, my2 + 8, mx2 + 2 + (int) ((mw - 4) * mfrac), my2 + mh - 2, mcol);
        g.drawCenteredString(mc.font, "§c♥ §f" + mhp + "§7/§f" + mmax + " §8PV (pas de régén en combat)",
                mx2 + mw / 2, my2 + 1, 0xFFFFFF);

        // --- barre de sorts (bas centre)
        int slotS = 22;
        int barW = 282, barH = 30;
        int barX = sw / 2 - barW / 2;
        int barY = sh - barH - 8;
        g.blit(PANEL, barX, barY, barW, barH, 0, 64, 128, 64, 128, 128);

        g.drawString(mc.font, "§ePA §f" + st.ap, barX + 8, barY + 5, 0xFFFFFF);
        g.drawString(mc.font, "§9PM §f" + st.mp, barX + 8, barY + 16, 0xFFFFFF);

        List<String> eq = data.getEquipped();
        int slotIdx = 0;
        for (int i = 0; i < eq.size() && slotIdx < 4; i++) {
            String id = eq.get(i);
            Spell sp = Spells.byId(id);
            if (sp != null && sp.unlockLevel > data.getLevel()) continue;
            int sx = barX + 56 + slotIdx * (slotS + 4);
            int sy = barY + 4;
            g.fill(sx, sy, sx + slotS, sy + slotS, 0xFF1A1A26);
            ResourceLocation icon = new ResourceLocation(WakfuRPG.MODID, "textures/item/spell_" + id + ".png");
            g.blit(icon, sx + 3, sy + 3, 16, 16, 0, 0, 32, 32, 32, 32);
            int cost = sp == null ? 0 : sp.apCost;
            boolean usable = st.yourTurn && st.ap >= cost;
            if (!usable) g.fill(sx, sy, sx + slotS, sy + slotS, 0x99000000);
            g.drawString(mc.font, "§7" + (slotIdx + 1), sx + 2, sy + slotS - 9, 0xCCCCCC);
            g.drawString(mc.font, "§e" + cost, sx + slotS - 9, sy + 1, 0xFFDD55);
            if (CombatView.selectedSlot == slotIdx) {
                g.fill(sx - 1, sy - 1, sx + slotS + 1, sy, 0xFFFFDD55);
                g.fill(sx - 1, sy + slotS, sx + slotS + 1, sy + slotS + 1, 0xFFFFDD55);
                g.fill(sx - 1, sy, sx, sy + slotS, 0xFFFFDD55);
                g.fill(sx + slotS, sy, sx + slotS + 1, sy + slotS, 0xFFFFDD55);
            }
            HUD_RECTS.add(new int[]{sx, sy, slotS, slotS, slotIdx});
            slotIdx++;
        }

        // fin du tour
        int ex = barX + 166, ey = barY + 5, ew = 62, eh = 20;
        boolean canEnd = st.yourTurn;
        g.fill(ex, ey, ex + ew, ey + eh, canEnd ? 0xFFCCAA33 : 0xFF555555);
        g.fill(ex + 1, ey + 1, ex + ew - 1, ey + eh - 1, canEnd ? 0xFFEECC55 : 0xFF666666);
        g.drawString(mc.font, "§0Fin du tour", ex + 5, ey + 6, 0x222222);
        HUD_RECTS.add(new int[]{ex, ey, ew, eh, 10});

        // fuite
        int fx = barX + 234, flw = 40;
        g.fill(fx, ey, fx + flw, ey + eh, 0xFF884433);
        g.fill(fx + 1, ey + 1, fx + flw - 1, ey + eh - 1, 0xFFAA5544);
        g.drawString(mc.font, "§fFuir", fx + 9, ey + 6, 0xFFFFFF);
        HUD_RECTS.add(new int[]{fx, ey, flw, eh, 11});

        // --- info au-dessus de la barre
        Spell sel = CombatView.selectedSpell();
        String line;
        if (sel != null) {
            line = "§e" + sel.getName().getString() + " §7— zone §a" + sel.zoneLabel()
                    + " §7· portée §a" + sel.rangeLabel() + " §7· clic pour lancer, clic droit pour annuler";
        } else if (st.yourTurn) {
            line = "§7Clique une case pour te déplacer (§a" + st.mp + " PM§7) · touches §e1-4§7 : sorts · §eR§7 : fin du tour";
        } else {
            // v0.19 : en groupe, nomme l'allie qui joue
            String actor = null;
            for (int i2 = 0; i2 < st.units.size(); i2++) {
                int[] u = st.units.get(i2);
                if (u[0] == 0 && u[6] == 1 && i2 < st.unitNames.size()) actor = st.unitNames.get(i2);
            }
            line = actor != null && !actor.equals(mc.player.getName().getString())
                    ? "§7Tour de §f" + actor + "§7…"
                    : "§cTour de l'adversaire…";
        }
        g.drawCenteredString(mc.font, line, sw / 2, barY - 14, 0xFFFFFF);

        // --- curseur custom (souris libre)
        double gx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getWidth();
        double gy = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getHeight();
        int mx = (int) gx, my = (int) gy;
        g.fill(mx - 4, my, mx + 5, my + 1, 0xEEFFFFFF);
        g.fill(mx, my - 4, mx + 1, my + 5, 0xEEFFFFFF);
    }
}
