package fr.wakfu.rpg.client;

import fr.wakfu.rpg.WakfuRPG;
import fr.wakfu.rpg.core.ModCapabilities;
import fr.wakfu.rpg.core.PlayerRpgData;
import fr.wakfu.rpg.item.WakfuArmorItem;
import fr.wakfu.rpg.item.WakfuWeaponItem;
import fr.wakfu.rpg.item.WakfuMountItem;
import fr.wakfu.rpg.network.EquipPacket;
import fr.wakfu.rpg.network.Network;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.18 : Sac & Équipement RPG (touche I).
 * - Colonne gauche : slots dédiés (heaume, cuirasse, jambières, bottes, arme)
 *   encadrés d'or — clic pour retirer.
 * - Grille 36 slots de l'inventaire — clic pour équiper (armure -> slot dédié,
 *   arme -> slot d'arme RPG).
 * - Infobulle gothique custom au survol : nom teinté par rareté, étoiles,
 *   stats fixes, et DELTA vert/rouge contre la pièce déjà équipée.
 */
public class EquipmentScreen extends Screen {

    private static final ResourceLocation PANEL =
            new ResourceLocation(WakfuRPG.MODID, "textures/gui/equipment_panel.png");

    private static final int PW = 384, PH = 256;
    private static final int SLOT = 22;

    /** x,y,w,h,code — code >= 0 : index inventaire ; -1..-4 : retirer armure ; -5 : retirer arme ; -99 fermer. */
    private final List<int[]> rects = new ArrayList<>();
    private ItemStack hoverStack = ItemStack.EMPTY;
    private int hoverCode = 0;

    public EquipmentScreen() {
        super(Component.literal("Sac & Équipement"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static int rarityColor(String r) {
        return switch (r) {
            case "epique" -> 0xFFDD66FF;
            case "rare" -> 0xFF6699FF;
            default -> 0xFFE8E0D0;
        };
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        rects.clear();
        hoverStack = ItemStack.EMPTY;
        int px = width / 2 - PW / 2, py = height / 2 - PH / 2;
        // v0.23 : fond sombre PROGRAMMATIQUE — plus de dépendance à la texture,
        // textes clairs garantis lisibles
        g.fill(px, py, px + PW, py + PH, 0xFF141018);
        g.fill(px + 2, py + 2, px + PW - 2, py + PH - 2, 0xFF3A2E44);
        g.fill(px + 4, py + 4, px + PW - 4, py + PH - 4, 0xFF241C2A);
        g.fill(px + 4, py + 4, px + PW - 4, py + 26, 0xFF332840);
        g.fill(px + 4, py + 26, px + PW - 4, py + 27, 0xFFAA8844);

        var player = Minecraft.getInstance().player;
        if (player == null) return;

        g.drawString(font, "Sac & Équipement", px + 14, py + 10, 0xFFFFCC77);
        String hint = "I — clic : équiper / retirer";
        g.drawString(font, hint, px + PW - 10 - font.width(hint), py + 13, 0xFFB8A888);
        // v0.25 : bourse ($) dans l'en-tête
        String money = "$ " + fr.wakfu.rpg.client.ClientMoney.fmt(fr.wakfu.rpg.client.ClientMoney.get());
        g.drawString(font, money, px + PW - 18 - font.width(hint) - font.width(money), py + 13, 0xFFDD66);

        // ---------------- slots équipés (paper-doll)
        int ex = px + 36;
        int[] eys = {py + 46, py + 76, py + 106, py + 136};
        String[] elabels = {"Heaume", "Cuirasse", "Jambières", "Bottes"};
        int[] armorIdx = {WakfuArmorItem.SLOT_HEAD, WakfuArmorItem.SLOT_CHEST,
                WakfuArmorItem.SLOT_LEGS, WakfuArmorItem.SLOT_FEET};
        int totVie = 0, totFor = 0, totSag = 0, totRes = 0;
        for (int i = 0; i < 4; i++) {
            ItemStack eq = player.getInventory().getArmor(armorIdx[i]);
            drawSlot(g, ex, eys[i], eq, -(i + 1), mx, my);
            g.drawString(font, elabels[i], ex + SLOT + 6, eys[i] + 7, 0xFFC8B898);
            if (!eq.isEmpty()) {
                int[] f = WakfuArmorItem.fixedStats(eq);
                totVie += f[0];
                totFor += f[1];
                totSag += f[2];
                totRes += f[3];
            }
        }
        int wy = py + 172;
        PlayerRpgData data = player.getCapability(ModCapabilities.RPG).resolve().orElse(null);
        ItemStack wep = data != null ? data.getEquippedWeapon() : ItemStack.EMPTY;
        drawSlot(g, ex, wy, wep, -5, mx, my);
        g.drawString(font, "Arme", ex + SLOT + 6, wy + 7, 0xFFC8B898);
        if (!wep.isEmpty()) {
            int[] f = WakfuWeaponItem.fixedStats(wep);
            totVie += f[0];
            totFor += f[1];
            totSag += f[2];
            totRes += f[3];
        }
        // v0.31 : slot monture
        int mty = wy + SLOT + 8;
        String mountType = data != null ? data.getEquippedMount() : "";
        g.fill(ex, mty, ex + SLOT + 4, mty + SLOT + 4, 0xFF2A2A3A);
        g.fill(ex, mty, ex + SLOT + 4, mty + 1, mountType.isEmpty() ? 0xFF555555 : 0xFFCCAA33);
        g.fill(ex, mty + SLOT + 3, ex + SLOT + 4, mty + SLOT + 4, mountType.isEmpty() ? 0xFF555555 : 0xFFCCAA33);
        g.fill(ex, mty, ex + 1, mty + SLOT + 4, mountType.isEmpty() ? 0xFF555555 : 0xFFCCAA33);
        g.fill(ex + SLOT + 3, mty, ex + SLOT + 4, mty + SLOT + 4, mountType.isEmpty() ? 0xFF555555 : 0xFFCCAA33);
        if (!mountType.isEmpty()) {
            String icon = switch (mountType) {
                case "bear" -> "§6\uD83D\uDC3B";
                case "griffin" -> "§e\uD83E\uDD85";
                default -> "§b\uD83D\uDC3A";
            };
            g.drawString(font, icon, ex + 5, mty + 4, 0xFFFFFF);
        } else {
            g.drawString(font, "§7Monture", ex + 2, mty + 5, 0x999999);
        }
        rects.add(new int[]{ex, mty, SLOT + 4, SLOT + 4, -6});

        String tot = "Bonus : +" + totVie + " PV  +" + totFor + " For  +" + totSag + " Sag  +" + totRes + " Rés";
        g.drawString(font, tot, px + 20, py + PH - 24, 0xFFAACC88);

        // ---------------- grille inventaire 6x6 (36 slots)
        int gx = px + 196, gy = py + 46;
        var items = player.getInventory().items;
        for (int r = 0; r < 6; r++) {
            for (int c = 0; c < 6; c++) {
                int idx = r * 6 + c;
                int sx = gx + c * (SLOT + 2), sy = gy + r * (SLOT + 2);
                ItemStack st = idx < items.size() ? items.get(idx) : ItemStack.EMPTY;
                drawSlot(g, sx, sy, st, idx, mx, my);
            }
        }

        // ---------------- v0.25 : retour au Grimoire
        int rx = px + PW - 158, ry = py + PH - 30;
        g.fill(rx, ry, rx + 78, ry + 18, 0xFF3A2E44);
        g.fill(rx, ry, rx + 78, ry + 1, 0xFFAA8844);
        g.drawString(font, "← Grimoire", rx + 39 - font.width("← Grimoire") / 2, ry + 5, 0xFFCCAA77);
        rects.add(new int[]{rx, ry, 78, 18, -98});

        // ---------------- bouton fermer
        int bx = px + PW - 74, by = py + PH - 30;
        g.fill(bx, by, bx + 60, by + 18, 0xFF3A2A20);
        g.fill(bx, by, bx + 60, by + 1, 0xFFFFAA55);
        g.drawString(font, "Fermer", bx + 12, by + 5, 0xFFE8D8B8);
        rects.add(new int[]{bx, by, 60, 18, -99});

        // ---------------- infobulle gothique custom
        if (!hoverStack.isEmpty()) drawTooltip(g, mx, my);
    }

    private void drawSlot(GuiGraphics g, int x, int y, ItemStack st, int code, int mx, int my) {
        boolean hovered = mx >= x && mx < x + SLOT && my >= y && my < y + SLOT;
        boolean special = code < 0 && code != -99;
        boolean equipable = special || st.getItem() instanceof WakfuArmorItem || WakfuWeaponItem.isWeapon(st) || WakfuMountItem.isMountItem(st);
        int frame = special ? 0xFFFFAA55 : (equipable && !st.isEmpty() ? 0xFFCCAA66 : 0xFF776655);
        if (hovered) frame = 0xFFFFFFFF;
        g.fill(x - 1, y - 1, x + SLOT + 1, y, frame);
        g.fill(x - 1, y + SLOT, x + SLOT + 1, y + SLOT + 1, frame);
        g.fill(x - 1, y, x, y + SLOT, frame);
        g.fill(x + SLOT, y, x + SLOT + 1, y + SLOT, frame);
        g.fill(x, y, x + SLOT, y + SLOT, 0x66000000);
        if (!st.isEmpty()) g.renderItem(st, x + 3, y + 3);
        if (hovered && (special || !st.isEmpty())) {
            hoverStack = st;
            hoverCode = code;
        }
        rects.add(new int[]{x, y, SLOT, SLOT, code});
    }

    /** Infobulle custom : nom + rareté + étoiles + stats + delta vs équipé. */
    private void drawTooltip(GuiGraphics g, int mx, int my) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        List<String> lines = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        ItemStack st = hoverStack;
        int[] f = null;
        int[] old = null;
        boolean armor = st.getItem() instanceof WakfuArmorItem;
        boolean weapon = !armor && WakfuWeaponItem.isWeapon(st);

        if (hoverCode < 0 && hoverCode != -99 && st.isEmpty()) {
            String what = switch (hoverCode) {
                case -1 -> "Heaume";
                case -2 -> "Cuirasse";
                case -3 -> "Jambières";
                case -4 -> "Bottes";
                default -> "Arme";
            };
            lines.add(what);
            colors.add(0xFFB0A088);
            lines.add("Emplacement vide");
            colors.add(0xFFB0A088);
        } else if (armor || weapon) {
            String rarity = armor ? WakfuArmorItem.rarityOf(st) : WakfuWeaponItem.rarityOf(st);
            int stars = armor ? WakfuArmorItem.starsOf(st) : WakfuWeaponItem.starsOf(st);
            String set = armor ? WakfuArmorItem.setOf(st) : "";
            if (!set.isEmpty()) {
                // v0.22 : piece de panoplie de zone
                lines.add("[" + WakfuArmorItem.setNameFr(set) + "] "
                        + WakfuArmorItem.slotNameFr(WakfuArmorItem.slotOf(st)));
                colors.add(WakfuArmorItem.setColor(set));
            } else {
                String base = armor
                        ? WakfuArmorItem.slotNameFr(WakfuArmorItem.slotOf(st)) + " Runique"
                        : ("sword".equals(WakfuWeaponItem.kindOf(st)) ? "Lame Runique" : "Arc Runique");
                lines.add("[" + rarity + "] " + base);
                colors.add(rarityColor(rarity));
            }
            if (stars > 0) {
                lines.add("★".repeat(stars) + (stars > 1 ? "  (stats ×1.5/étoile)" : "  (stats ×1.5)"));
                colors.add(0xFFFFCC55);
            }
            f = armor ? WakfuArmorItem.fixedStats(st) : WakfuWeaponItem.fixedStats(st);
            ItemStack eq = ItemStack.EMPTY;
            if (hoverCode < 0) {
                eq = st;   // survol d'un slot équipé : pas de comparaison
            } else if (armor) {
                eq = player.getInventory().getArmor(WakfuArmorItem.slotIndex(st));
            } else {
                PlayerRpgData d = player.getCapability(ModCapabilities.RPG).resolve().orElse(null);
                eq = d != null ? d.getEquippedWeapon() : ItemStack.EMPTY;
            }
            if (hoverCode >= 0 && !eq.isEmpty()) {
                old = armor ? WakfuArmorItem.fixedStats(eq) : WakfuWeaponItem.fixedStats(eq);
            }
            String[] names = {"PV", "Force", "Sagesse", "Résistance"};
            for (int i = 0; i < 4; i++) {
                if (f[i] > 0 || (old != null && old[i] > 0)) {
                    StringBuilder b = new StringBuilder();
                    if (f[i] > 0) b.append("+").append(f[i]).append(" ").append(names[i]);
                    else b.append("-- ").append(names[i]);
                    int col = 0xFFD8CDB8;
                    if (old != null) {
                        int dlt = f[i] - old[i];
                        if (dlt > 0) {
                            b.append("   ▲ +").append(dlt);
                            col = 0xFF77DD66;
                        } else if (dlt < 0) {
                            b.append("   ▼ ").append(dlt);
                            col = 0xFFDD6655;
                        } else {
                            b.append("   =");
                            col = 0xFFB8A898;
                        }
                    }
                    lines.add(b.toString());
                    colors.add(col);
                }
            }
            if (hoverCode >= 0) {
                lines.add("Clic : équiper");
                colors.add(0xFFFFCC77);
            } else {
                lines.add("Clic : retirer");
                colors.add(0xFFCC9966);
            }
        } else if (!st.isEmpty()) {
            lines.add(st.getHoverName().getString().replaceAll("§.", ""));
            colors.add(0xFFE8E0D0);
            lines.add("Non équipable");
            colors.add(0xFFB0A088);
        } else {
            return;
        }

        int w = 0;
        for (int i = 0; i < lines.size(); i++) {
            w = Math.max(w, Math.max(font.width(lines.get(i)), font.width(lines.get(i))));
        }
        w += 14;
        int h = lines.size() * 11 + 8;
        int tx = mx + 14, ty = my + 10;
        if (tx + w > width - 4) tx = mx - w - 8;
        if (ty + h > height - 4) ty = my - h - 6;
        g.fill(tx, ty, tx + w, ty + h, 0xF2171019);
        g.fill(tx, ty, tx + w, ty + 1, 0xFFAA8844);
        g.fill(tx, ty + h - 1, tx + w, ty + h, 0xFFAA8844);
        g.fill(tx, ty, tx + 1, ty + h, 0xFFAA8844);
        g.fill(tx + w - 1, ty, tx + w, ty + h, 0xFFAA8844);
        for (int i = 0; i < lines.size(); i++) {
            g.drawString(font, lines.get(i), tx + 7, ty + 5 + i * 11, colors.get(i));
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (int[] r : rects) {
                if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                    if (r[4] == -99) {
                        onClose();
                        return true;
                    }
                    if (r[4] == -98) {
                        Minecraft.getInstance().setScreen(new GrimoireScreen());
                        return true;
                    }
                    // n'envoie que si pertinent (slot équipe non vide / inventaire non vide)
                    Network.CHANNEL.sendToServer(new EquipPacket(r[4]));
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ClientKeyBindings.EQUIP.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
