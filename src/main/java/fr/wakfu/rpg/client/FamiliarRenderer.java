package fr.wakfu.rpg.client;

import fr.wakfu.rpg.WakfuRPG;
import fr.wakfu.rpg.entity.FamiliarEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.model.geom.ModelLayers;

/**
 * v0.29 : Rendu simple des familiers.
 */
public class FamiliarRenderer extends MobRenderer<FamiliarEntity, EntityModel<FamiliarEntity>> {

    public FamiliarRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new SimpleFamiliarModel(ctx.bakeLayer(ModelLayers.CAT)), 0.3F);
    }

    @Override
    public ResourceLocation getTextureLocation(FamiliarEntity entity) {
        return switch (entity.getFamiliarType()) {
            case CAT -> new ResourceLocation(WakfuRPG.MODID, "textures/entity/familiar_cat.png");
            case DRAGON -> new ResourceLocation(WakfuRPG.MODID, "textures/entity/familiar_dragon.png");
            case GOLEM -> new ResourceLocation(WakfuRPG.MODID, "textures/entity/familiar_golem.png");
        };
    }

    /** Simple box model for familiars. */
    static class SimpleFamiliarModel extends EntityModel<FamiliarEntity> {
        private final ModelPart body;
        public SimpleFamiliarModel(ModelPart root) {
            this.body = root.getChild("body");
        }
        @Override
        public void setupAnim(FamiliarEntity entity, float limbSwing, float limbSwingAmount,
                              float ageInTicks, float netHeadYaw, float headPitch) {}
        @Override
        public void renderToBuffer(com.mojang.blaze3d.vertex.PoseStack pose,
                com.mojang.blaze3d.vertex.VertexConsumer buffer,
                int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
            body.render(pose, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        }
    }
}

