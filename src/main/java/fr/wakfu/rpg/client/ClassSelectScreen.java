package fr.wakfu.rpg.client;

import fr.wakfu.rpg.WakfuRPG;
import fr.wakfu.rpg.core.ModCapabilities;
import fr.wakfu.rpg.core.RpgClass;
import fr.wakfu.rpg.network.ChooseClassPacket;
import fr.wakfu.rpg.network.Network;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Choix de classe sur le visuel fourni : chaque carte est cliquable. */
public class ClassSelectScreen extends Screen {
    private static final int IMAGE_W = 1152;
    private static final int IMAGE_H = 768;
    private static final ResourceLocation PANEL = new ResourceLocation(WakfuRPG.MODID,
            "textures/gui/class_select.png");
    private static final int[][] CARDS = {
            {72, 161, 268, 608}, {273, 161, 469, 608}, {472, 161, 660, 608},
            {663, 161, 855, 608}, {873, 161, 1075, 608}
    };
    private static final RpgClass[] CLASSES = {
            RpgClass.SYKAR, RpgClass.TOR, RpgClass.SPLIAN, RpgClass.GEMA, RpgClass.ENESTRAS
    };
    private float scale;
    private int left, top;

    public ClassSelectScreen() {
        super(Component.translatable("wakfurpg.select_title"));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        scale = Math.min((width - 20f) / IMAGE_W, (height - 24f) / IMAGE_H);
        scale = Math.max(0.35f, scale);
        int drawW = Math.round(IMAGE_W * scale);
        int drawH = Math.round(IMAGE_H * scale);
        left = (width - drawW) / 2;
        top = (height - drawH) / 2;

        g.pose().pushPose();
        g.pose().translate(left, top, 0);
        g.pose().scale(scale, scale, 1f);
        g.blit(PANEL, 0, 0, IMAGE_W, IMAGE_H, 0, 0, IMAGE_W, IMAGE_H, IMAGE_W, IMAGE_H);
        boolean changingClass = Minecraft.getInstance().player != null
                && Minecraft.getInstance().player.getCapability(ModCapabilities.RPG)
                        .map(fr.wakfu.rpg.core.PlayerRpgData::hasClass)
                        .orElse(false);
        if (changingClass) {
            g.fill(220, 620, 932, 656, 0xDD15121A);
            g.drawCenteredString(font,
                    "Changer de classe conserve le niveau mais réinitialise les sorts.",
                    IMAGE_W / 2, 632, 0xFFFFD080);
        }
        double ix = (mx - left) / scale;
        double iy = (my - top) / scale;
        for (int[] card : CARDS) {
            if (ix >= card[0] && ix <= card[2] && iy >= card[1] && iy <= card[3]) {
                g.fill(card[0] + 14, 526, card[2] - 14, 579, 0x3322DDAA);
            }
        }
        g.pose().popPose();
        super.render(g, mx, my, partial);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button != 0 || scale <= 0) return super.mouseClicked(x, y, button);
        double ix = (x - left) / scale;
        double iy = (y - top) / scale;
        for (int i = 0; i < CARDS.length; i++) {
            int[] card = CARDS[i];
            if (ix >= card[0] && ix <= card[2] && iy >= card[1] && iy <= card[3]) {
                Network.sendToServer(new ChooseClassPacket(CLASSES[i].getId()));
                Minecraft.getInstance().setScreen(null);
                return true;
            }
        }
        return true;
    }

    @Override public boolean isPauseScreen() { return false; }
}
