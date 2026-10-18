package fr.wakfu.rpg.client;

import fr.wakfu.rpg.WakfuRPG;
import fr.wakfu.rpg.core.Fusion;
import fr.wakfu.rpg.core.ModCapabilities;
import fr.wakfu.rpg.network.FusionPacket;
import fr.wakfu.rpg.network.Network;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * v0.16 : Atelier sombre (ouvert via le forgeron — choix « ★ Fusion »).
 * Panneau dark smith genere (forge_panel.png) : acier sombre, braises.
 * 4 equipements identiques (meme slot/rarete/etoiles) → 1 exemplaire
 * etoile : stats x1.5, jusqu'a 5 etoiles.
 */
public class ForgeScreen extends Screen {

    private static final ResourceLocation PANEL =
            new ResourceLocation(WakfuRPG.MODID, "textures/gui/forge_panel.png");

    private static final int PW = 320, PH = 210;
    private final List<int[]> rects = new ArrayList<>();   // x,y,w,h,groupIndex
    private int scroll = 0;

    public ForgeScreen() {
        super(Component.literal("Atelier sombre"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        rects.clear();
        int px = width / 2 - PW / 2, py = height / 2 - PH / 2;
        g.blit(PANEL, px, py, PW, PH, 0, 0, 160, 105, 160, 105);

        var player = Minecraft.getInstance().player;
        if (player == null) return;

        g.drawString(font, "Atelier Sombre", px + 14, py + 9, 0xFFFFAA55);
        String sub = "Fusion ★ : 4 identiques → stats ×1.5 (max 5★)";
        g.drawString(font, sub, px + PW - 12 - font.width(sub), py + 12, 0xFFB8A888);

        int cx = px + 12, cy = py + 28, cw = PW - 24, ch = PH - 28 - 26;

        List<Fusion.Group> groups = Fusion.groups(player);
        if (groups.isEmpty()) {
            g.drawString(font, "Aucune fusion possible pour l'instant.", cx + 6, cy + 10, 0xFFC8B898);
            g.drawString(font, "Il te faut 4 exemplaires d'un même équipement :", cx + 6, cy + 26, 0xFF9A8A72);
            g.drawString(font, "même emplacement, même rareté, même nombre d'étoiles.", cx + 6, cy + 38, 0xFF9A8A72);
            g.drawString(font, "Les monstres et le coffre du boss en lâchent — la", cx + 6, cy + 54, 0xFF9A8A72);
            g.drawString(font, "forge du village produit aussi des pièces.", cx + 6, cy + 66, 0xFF9A8A72);
        } else {
            int rowsH = groups.size() * 36;
            scroll = Math.max(0, Math.min(scroll, rowsH - ch));
            g.enableScissor(cx, cy, cx + cw, cy + ch);
            int y = cy + 2 - scroll;
            for (int i = 0; i < groups.size(); i++) {
                Fusion.Group gr = groups.get(i);
                if (y > cy - 36 && y < cy + ch) {
                    boolean can = gr.count >= 4;
                    int stars = gr.sample.getOrCreateTag().getInt("stars");
                    g.fill(cx + 2, y, cx + cw - 2, y + 34, can ? 0x33FFAA55 : 0x22807060);
                    g.fill(cx + 2, y, cx + cw - 2, y + 1, can ? 0x88FFAA55 : 0x88665544);
                    g.renderItem(gr.sample, cx + 6, y + 8);
                    String name = gr.sample.getHoverName().getString();
                    g.drawString(font, name, cx + 28, y + 3, 0xFFE8D8B8);
                    g.drawString(font, (can ? "§a" : "§7") + "en poche : ×" + gr.count + " / 4 requis"
                            + (stars > 0 ? "  §6" + "★".repeat(stars) : ""), cx + 28, y + 13, 0xFFC8B898);
                    g.drawString(font, "§7résultat : 1 exemplaire §6★" + (stars + 1)
                            + " §7(statistiques ×1.5)", cx + 28, y + 23, 0xFF9A8A72);
                    int bx = cx + cw - 84, by = y + 10;
                    String lb = can ? "★ Fusionner" : "×4 requis";
                    g.fill(bx, by, bx + 78, by + 14, can ? 0xFF3A2A20 : 0xFF2A2422);
                    g.fill(bx, by, bx + 78, by + 1, can ? 0xFFFFAA55 : 0xFF665544);
                    g.fill(bx, by + 13, bx + 78, by + 14, can ? 0xFFFFAA55 : 0xFF665544);
                    g.fill(bx, by, bx + 1, by + 14, can ? 0xFFFFAA55 : 0xFF665544);
                    g.fill(bx + 77, by, bx + 78, by + 14, can ? 0xFFFFAA55 : 0xFF665544);
                    g.drawString(font, lb, bx + 39 - font.width(lb) / 2, by + 3,
                            can ? 0xFFFFCC77 : 0xFF887766);
                    if (can) rects.add(new int[]{bx, by, 78, 14, i});
                }
                y += 36;
            }
            g.disableScissor();
        }

        int ingotCount = 0;
        for (var stack : player.getInventory().items)
            if (stack.is(fr.wakfu.rpg.registry.ModItems.WAKFU_INGOT.get())) ingotCount += stack.getCount();
        String[] craftLabels = {"Tête", "Torse", "Jambières", "Bottes"};
        int craftW = (PW - 32) / 4;
        int craftY = py + PH - 37;
        g.drawString(font, "Forger une pièce · 6 lingots", px + 14, craftY - 11, 0xFFE8D8B8);
        for (int i = 0; i < craftLabels.length; i++) {
            int bx = px + 12 + i * (craftW + 2);
            boolean canCraft = ingotCount >= 6;
            g.fill(bx, craftY, bx + craftW, craftY + 16, canCraft ? 0xFF354338 : 0xFF292B2A);
            g.fill(bx, craftY, bx + craftW, craftY + 1, canCraft ? 0xFF80B878 : 0xFF555555);
            String label = craftLabels[i];
            g.drawString(font, label, bx + (craftW - font.width(label)) / 2, craftY + 4,
                    canCraft ? 0xFFB9E3A2 : 0xFF898989);
            rects.add(0, new int[]{bx, craftY, craftW, 16, 100 + i});
        }

        // arme equipee (rappel)
        player.getCapability(ModCapabilities.RPG).ifPresent(data -> {
            String w = data.getEquippedWeapon().isEmpty()
                    ? "§8aucune — clic droit sur une arme pour l'équiper"
                    : data.getEquippedWeapon().getHoverName().getString();
            String line = "Arme équipée : " + w;
            g.drawString(font, line, px + 14, py + PH - 16, 0xFFB8A888);
        });
        String hint = "[Échap] Retour";
        g.drawString(font, hint, px + PW - 12 - font.width(hint), py + PH - 16, 0xFF9A8A72);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double amt) {
        scroll = Math.max(0, scroll + (amt > 0 ? -18 : 18));
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        for (int[] r : rects) {
            if (mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3]) {
                Network.sendToServer(new FusionPacket(r[4]));
                Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1f));
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }
}
