package fr.wakfu.rpg.client;

import fr.wakfu.rpg.WakfuRPG;
import fr.wakfu.rpg.entity.ClassMasterNpc;
import fr.wakfu.rpg.registry.ModEntities;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * v0.13 : PNJ rendus comme des HUMAINS (modele joueur + skin pixel-art
 * genere par PNJ), fini le modele villageois.
 */
public class NpcRenderer extends MobRenderer<ClassMasterNpc, PlayerModel<ClassMasterNpc>> {

    private static final ResourceLocation MASTER =
            new ResourceLocation(WakfuRPG.MODID, "textures/entity/skin_master.png");
    private static final ResourceLocation BLACKSMITH =
            new ResourceLocation(WakfuRPG.MODID, "textures/entity/skin_blacksmith.png");
    private static final ResourceLocation HERALD =
            new ResourceLocation(WakfuRPG.MODID, "textures/entity/skin_herald.png");
    private static final ResourceLocation MERCHANT =
            new ResourceLocation(WakfuRPG.MODID, "textures/entity/skin_merchant.png");
    private static final ResourceLocation MARKET =
            new ResourceLocation(WakfuRPG.MODID, "textures/entity/skin_market.png");

    public NpcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(ClassMasterNpc entity) {
        if (entity.getType() == ModEntities.BLACKSMITH.get()) return BLACKSMITH;
        if (entity.getType() == ModEntities.HERALD.get()) return HERALD;
        if (entity.getType() == ModEntities.MERCHANT.get()) return MERCHANT;
        if (entity.getType() == ModEntities.MARKET.get()) return MARKET;
        return MASTER;
    }
}
