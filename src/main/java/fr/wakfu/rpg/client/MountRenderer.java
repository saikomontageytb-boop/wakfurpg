package fr.wakfu.rpg.client;

import fr.wakfu.rpg.WakfuRPG;
import fr.wakfu.rpg.entity.WakfuMount;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class MountRenderer extends MobRenderer<WakfuMount, EntityModel<WakfuMount>> {

    public MountRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new MountModel(ctx.bakeLayer(ModelLayers.HORSE)), 0.7F);
    }

    @Override
    public ResourceLocation getTextureLocation(WakfuMount entity) {
        return switch (entity.getMountType()) {
            case WOLF -> new ResourceLocation(WakfuRPG.MODID, "textures/entity/mount_loup.png");
            case BEAR -> new ResourceLocation(WakfuRPG.MODID, "textures/entity/mount_ours.png");
            case GRIFFIN -> new ResourceLocation(WakfuRPG.MODID, "textures/entity/mount_griffin.png");
        };
    }

    static class MountModel extends EntityModel<WakfuMount> {
        private final ModelPart body;
        MountModel(ModelPart root) { this.body = root.getChild("body"); }
        @Override
        public void setupAnim(WakfuMount e, float a, float b, float c, float d, float e2) {}
        @Override
        public void renderToBuffer(com.mojang.blaze3d.vertex.PoseStack pose,
                com.mojang.blaze3d.vertex.VertexConsumer buffer,
                int light, int overlay, float r, float g, float b, float a) {
            body.render(pose, buffer, light, overlay, r, g, b, a);
        }
    }
}

