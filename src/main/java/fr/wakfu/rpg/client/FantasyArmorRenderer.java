package fr.wakfu.rpg.client;

import fr.wakfu.rpg.item.WakfuArmorItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

/** Renders Wakfu gear with the genuine GeckoLib armor models from Fantasy Armor. */
public final class FantasyArmorRenderer extends GeoArmorRenderer<WakfuArmorItem> {
    public FantasyArmorRenderer(String modelSet) {
        super(new FantasyArmorModel(modelSet));
    }

    private static final class FantasyArmorModel extends GeoModel<WakfuArmorItem> {
        private final String modelSet;

        private FantasyArmorModel(String modelSet) {
            this.modelSet = modelSet;
        }

        @Override
        public ResourceLocation getModelResource(WakfuArmorItem item) {
            return new ResourceLocation(fr.wakfu.rpg.WakfuRPG.MODID, "geo/armor/" + modelSet + "_armor.geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(WakfuArmorItem item) {
            return new ResourceLocation(fr.wakfu.rpg.WakfuRPG.MODID, "textures/armor/fantasy_" + modelSet + ".png");
        }

        @Override
        public ResourceLocation getAnimationResource(WakfuArmorItem item) {
            return null;
        }
    }
}
