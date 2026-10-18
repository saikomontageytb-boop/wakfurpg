package fr.wakfu.rpg.client;

import fr.wakfu.rpg.WakfuRPG;
import fr.wakfu.rpg.core.DungeonInfo;
import fr.wakfu.rpg.network.JoinDungeonPacket;
import fr.wakfu.rpg.network.Network;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Dark stone and jade dungeon entrance panel, arranged like the supplied reference. */
public final class DungeonEntryScreen extends Screen {
    private static final ResourceLocation PORTAL = new ResourceLocation(WakfuRPG.MODID, "textures/gui/codex_dungeon.png");
    private static final int BASE_W = 720, BASE_H = 420;
    private final DungeonInfo info;
    private float scale = 1;
    private int left, top;
    private int buttonX, buttonY, buttonW, buttonH;

    public DungeonEntryScreen(DungeonInfo info) {
        super(Component.literal("Donjon"));
        this.info = info;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        scale = Math.min(1.0f, Math.min((width - 24f) / BASE_W, (height - 24f) / BASE_H));
        left = (int) ((width - BASE_W * scale) / 2);
        top = (int) ((height - BASE_H * scale) / 2);
        g.pose().pushPose(); g.pose().translate(left, top, 0); g.pose().scale(scale, scale, 1);

        panel(g, 0, 0, BASE_W, BASE_H, 0xF0181A16, 0xFF9A8055);
        panel(g, 18, 14, BASE_W - 36, 54, 0xF02A2B23, 0xFFB3925B);
        g.drawCenteredString(font, "DONJON", BASE_W / 2, 22, 0xFFF0D99C);
        g.drawCenteredString(font, info.name(), BASE_W / 2, 42, 0xFFFFFFFF);

        panel(g, 20, 82, 270, 272, 0xF0262924, 0xFF856D48);
        g.blit(PORTAL, 36, 96, 238, 168, 0, 0, 64, 64, 64, 64);
        g.drawCenteredString(font, info.name(), 155, 278, 0xFFF0D99C);
        g.drawCenteredString(font, "Une expédition vous attend.", 155, 298, 0xFFCDC5AE);
        g.drawString(font, "NIVEAU CONSEILLÉ", 42, 326, 0xFFB7AA8D, false);
        g.drawString(font, info.minLevel() + " – " + info.maxLevel(), 189, 326, 0xFF77D6C5, false);

        panel(g, 304, 82, 396, 272, 0xF0262924, 0xFF856D48);
        panel(g, 393, 94, 218, 28, 0xFF294544, 0xFF9C8658);
        g.drawCenteredString(font, "RÉCOMPENSES POSSIBLES", 502, 103, 0xFFF4E5BE);
        ItemStack[] rewards = { new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.AMETHYST_SHARD),
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.EMERALD), new ItemStack(Items.DIAMOND),
                new ItemStack(Items.BLAZE_POWDER), new ItemStack(Items.GOLDEN_APPLE), new ItemStack(Items.NETHER_STAR) };
        String[] labels = { "Pièces", "Cristaux", "Minerai", "Composants", "Équipement", "Essences", "Consommables", "Butin rare" };
        for (int i = 0; i < rewards.length; i++) {
            int col = i % 4, row = i / 4, x = 322 + col * 91, y = 137 + row * 94;
            panel(g, x, y, 82, 82, 0xFF20211C, 0xFF8E7449);
            g.renderItem(rewards[i], x + 33, y + 13);
            g.drawCenteredString(font, labels[i], x + 41, y + 58, 0xFFDBC99D);
        }
        g.drawCenteredString(font, "Le butin est attribué à la fin du combat.", 502, 330, 0xFFBEB39A);

        buttonX = 194; buttonY = 367; buttonW = 332; buttonH = 38;
        boolean hover = mouseX >= left + buttonX * scale && mouseX < left + (buttonX + buttonW) * scale
                && mouseY >= top + buttonY * scale && mouseY < top + (buttonY + buttonH) * scale;
        panel(g, buttonX, buttonY, buttonW, buttonH, hover ? 0xFF517D38 : 0xFF3D642F, 0xFFD8C37F);
        g.drawCenteredString(font, "REJOINDRE LE DONJON", BASE_W / 2, buttonY + 13, 0xFFFFF4D6);
        g.drawCenteredString(font, "Échap : fermer", BASE_W / 2, 410, 0xFFBDB29B);
        g.pose().popPose();
        super.render(g, mouseX, mouseY, partialTick);
    }

    private static void panel(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w, y + 2, border); g.fill(x, y + h - 2, x + w, y + h, border);
        g.fill(x, y, x + 2, y + h, border); g.fill(x + w - 2, y, x + w, y + h, border);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= left + buttonX * scale && mouseX < left + (buttonX + buttonW) * scale
                && mouseY >= top + buttonY * scale && mouseY < top + (buttonY + buttonH) * scale) {
            Network.sendToServer(new JoinDungeonPacket(info.id()));
            onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
