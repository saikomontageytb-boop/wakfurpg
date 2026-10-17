package fr.wakfu.rpg.client;

import fr.wakfu.rpg.core.RpgClass;
import fr.wakfu.rpg.network.ChooseClassPacket;
import fr.wakfu.rpg.network.Network;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ClassSelectScreen extends Screen {

    public ClassSelectScreen() {
        super(Component.translatable("wakfurpg.select_title"));
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2;
        addRenderableWidget(Button.builder(Component.translatable("class.wakfurpg.iop"), b -> choose(RpgClass.IOP))
                .bounds(cx - 160, cy - 10, 150, 40).build());
        addRenderableWidget(Button.builder(Component.translatable("class.wakfurpg.cra"), b -> choose(RpgClass.CRA))
                .bounds(cx + 10, cy - 10, 150, 40).build());
    }

    private void choose(RpgClass c) {
        Network.sendToServer(new ChooseClassPacket(c.getId()));
        onClose();
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        super.render(g, mx, my, pt);
        g.drawCenteredString(font, title, width / 2, height / 2 - 50, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("wakfurpg.select_hint"), width / 2, height / 2 + 45, 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
