package fr.wakfu.rpg.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.wakfu.rpg.WakfuRPG;
import fr.wakfu.rpg.core.ModCapabilities;
import fr.wakfu.rpg.core.RpgClass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

public class ClassSkinLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private final PlayerModel<AbstractClientPlayer> model;

    public ClassSkinLayer(PlayerRenderer parent, EntityModelSet models, boolean slim) {
        super(parent);
        this.model = new PlayerModel<>(models.bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (Minecraft.getInstance().player != player) {
            return;
        }

        ResourceLocation texture = player.getCapability(ModCapabilities.RPG)
                .map(data -> textureFor(data.getRpgClass()))
                .orElse(null);
        if (texture == null) {
            return;
        }

        model.setAllVisible(false);
        if (player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            model.head.visible = true;
            model.hat.visible = true;
        }
        if (player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
            model.body.visible = true;
            model.jacket.visible = true;
            model.rightArm.visible = true;
            model.rightSleeve.visible = true;
            model.leftArm.visible = true;
            model.leftSleeve.visible = true;
        }
        if (player.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                && player.getItemBySlot(EquipmentSlot.FEET).isEmpty()) {
            model.rightLeg.visible = true;
            model.rightPants.visible = true;
            model.leftLeg.visible = true;
            model.leftPants.visible = true;
        }

        model.setupAnim(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
        model.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY,
                1.0F, 1.0F, 1.0F, 1.0F);
        model.setAllVisible(true);
    }

    private static ResourceLocation textureFor(RpgClass rpgClass) {
        if (rpgClass == null) {
            return null;
        }
        return ResourceLocation.fromNamespaceAndPath(WakfuRPG.MODID,
                "textures/entity/skin_player_" + rpgClass.getId() + ".png");
    }
}
