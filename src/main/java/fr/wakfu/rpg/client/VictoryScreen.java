package fr.wakfu.rpg.client;

import fr.wakfu.rpg.WakfuRPG;
import fr.wakfu.rpg.network.VictoryPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Ecran de fin de combat utilisant le visuel fourni et les recompenses reelles. */
public class VictoryScreen extends Screen {

    private static final int IMAGE_W = 850;
    private static final int IMAGE_H = 670;
    private static final ResourceLocation PANEL = new ResourceLocation(WakfuRPG.MODID,
            "textures/gui/victory_screen.png");

    private final VictoryPacket data;
    private int ticksOpen;

    private VictoryScreen(VictoryPacket data) {
        super(Component.literal("Victoire"));
        this.data = data;
    }

    public static void show(VictoryPacket pkt) {
        Minecraft.getInstance().setScreen(new VictoryScreen(pkt));
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void tick() {
        ticksOpen++;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        float scale = Math.min((width - 24f) / IMAGE_W, (height - 24f) / IMAGE_H);
        scale = Math.max(0.35f, scale);
        int drawW = Math.round(IMAGE_W * scale);
        int drawH = Math.round(IMAGE_H * scale);
        int left = (width - drawW) / 2;
        int top = (height - drawH) / 2;

        g.pose().pushPose();
        g.pose().translate(left, top, 0);
        g.pose().scale(scale, scale, 1f);
        g.blit(PANEL, 0, 0, IMAGE_W, IMAGE_H, 0, 0, IMAGE_W, IMAGE_H, IMAGE_W, IMAGE_H);

        // Values positionnees dans les emplacements qui remplacent les variables du visuel.
        centered(g, "+" + data.totalXp + " XP", 223, 328, 0xFF74D878);
        g.fill(400, 276, 562, 305, 0xFF283531);
        centered(g, "PIÈCES GAGNÉES", 481, 286, 0xFFFFD04A);
        centered(g, "+" + data.totalMoney + " pièces", 498, 333, 0xFFFFD04A);
        int seconds = (int) (data.duration / 20L);
        centered(g, String.format("%d:%02d", seconds / 60, seconds % 60), 700, 333, 0xFF9FE4E4);
        int level = Minecraft.getInstance().player == null ? 1
                : Minecraft.getInstance().player.getCapability(fr.wakfu.rpg.core.ModCapabilities.RPG)
                    .map(fr.wakfu.rpg.core.PlayerRpgData::getLevel).orElse(1);
        centered(g, "NIV. " + level, 275, 386, 0xFF83D5C5);

        int[] centers = {152, 231, 309, 387, 466, 544, 622, 702};
        int shown = Math.min(centers.length, data.lootItems.size());
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = 0; i < shown; i++) {
            ItemStack stack = data.lootItems.get(i);
            int ix = centers[i] - 8;
            int iy = 484;
            g.renderItem(stack, ix, iy);
            g.renderItemDecorations(font, stack, ix, iy);
            String label = stack.getHoverName().getString();
            if (label.length() > 9) label = label.substring(0, 8) + "…";
            centered(g, label, centers[i], 519, 0xFFE5D7B8);
            if (mouseX >= left + (centers[i] - 34) * scale && mouseX <= left + (centers[i] + 34) * scale
                    && mouseY >= top + 468 * scale && mouseY <= top + 537 * scale) hovered = stack;
        }

        String extra = data.lootItems.size() > centers.length
                ? "+" + (data.lootItems.size() - centers.length) + " autres objets"
                : data.lootItems.isEmpty() ? "Aucun objet bonus" : "Butin du combat recupere";
        g.fill(340, 602, 570, 632, 0xFF293732);
        g.drawCenteredString(font, extra, 455, 611, 0xFFB7DBD2);
        g.pose().popPose();

        if (!hovered.isEmpty()) g.renderTooltip(font, hovered, mouseX, mouseY);

        String hint = "Cliquez ou appuyez sur Echap pour continuer";
        g.drawCenteredString(font, hint, width / 2, top + drawH + 2, 0xFFDDDDDD);
        super.render(g, mouseX, mouseY, partial);
    }

    private void centered(GuiGraphics g, String value, int x, int y, int color) {
        g.drawString(font, value, x - font.width(value) / 2, y, color, false);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        Minecraft.getInstance().setScreen(null);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        Minecraft.getInstance().setScreen(null);
        return true;
    }
}
