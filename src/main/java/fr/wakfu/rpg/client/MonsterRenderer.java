package fr.wakfu.rpg.client;

import fr.wakfu.rpg.entity.MonsterNpc;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Rendu humanoide pour les monstres ; la texture depend du type. */
public class MonsterRenderer extends MobRenderer<MonsterNpc, HumanoidModel<MonsterNpc>> {

    public MonsterRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(MonsterNpc entity) {
        return new ResourceLocation(entity.getTexturePath());
    }
}
