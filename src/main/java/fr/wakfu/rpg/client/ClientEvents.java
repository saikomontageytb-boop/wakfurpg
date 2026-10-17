package fr.wakfu.rpg.client;

import fr.wakfu.rpg.WakfuRPG;
import fr.wakfu.rpg.core.ClientProxyHook;
import fr.wakfu.rpg.core.ModCapabilities;
import fr.wakfu.rpg.registry.ModEntities;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Evenements client :
 * - bus MOD : overlays HUD, renderers d'entites, touches, hooks proxy
 * - bus FORGE : ouverture de l'ecran des competences (K), annulation des
 *   elements vanilla remplaces par le HUD custom
 */
@Mod.EventBusSubscriber(modid = WakfuRPG.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientEvents {

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("wakfu_hud", new WakfuHud());
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.CLASS_MASTER.get(), NpcRenderer::new);
        event.registerEntityRenderer(ModEntities.BLACKSMITH.get(), NpcRenderer::new);
        event.registerEntityRenderer(ModEntities.MERCHANT.get(), NpcRenderer::new);
        event.registerEntityRenderer(ModEntities.MARKET.get(), NpcRenderer::new);
        event.registerEntityRenderer(ModEntities.HERALD.get(), NpcRenderer::new);
        event.registerEntityRenderer(ModEntities.MONSTER.get(), MonsterRenderer::new);

        // v0.29 : Montures
        event.registerEntityRenderer(ModEntities.MOUNT_WOLF.get(), MountRenderer::new);
        event.registerEntityRenderer(ModEntities.MOUNT_BEAR.get(), MountRenderer::new);
        event.registerEntityRenderer(ModEntities.MOUNT_GRIFFIN.get(), MountRenderer::new);
        // v0.29 : Familiers
        event.registerEntityRenderer(ModEntities.FAMILIAR_CAT.get(), FamiliarRenderer::new);
        event.registerEntityRenderer(ModEntities.FAMILIAR_DRAGON.get(), FamiliarRenderer::new);
        event.registerEntityRenderer(ModEntities.FAMILIAR_GOLEM.get(), FamiliarRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(ClientKeyBindings.SKILLS);
        event.register(ClientKeyBindings.CODEX);
        event.register(ClientKeyBindings.EQUIP);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ClientProxyHook.openDialogue = (id, node) ->
                    Minecraft.getInstance().setScreen(new DialogueScreen(id, node));
            ClientProxyHook.onCombatState = ClientCombatHud::set;
            ClientProxyHook.onCombatFx = SpellFx::play;
            ClientProxyHook.openMarket = (open) -> {
                if (open) Minecraft.getInstance().setScreen(new MarketScreen());
            };
        });
    }

    @Mod.EventBusSubscriber(modid = WakfuRPG.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeEvents {

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) return;
            // v0.23 : trainee de guidage vers le lieu/PNJ suivi
            if (mc.player.tickCount % 8 == 0) fr.wakfu.rpg.client.Waypoints.tickTrail(mc);
            while (ClientKeyBindings.SKILLS.consumeClick()) {
                if (ClientCombatHud.get().inCombat) {
                    mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§7Grimoire indisponible pendant un combat."));
                } else {
                    mc.setScreen(new GrimoireScreen());
                }
            }
            while (ClientKeyBindings.EQUIP.consumeClick()) {
                if (mc.player != null && mc.screen == null
                        && !fr.wakfu.rpg.core.combat.CombatManager.inCombat(mc.player.getUUID())) {
                    mc.setScreen(new EquipmentScreen());
                }
            }
            while (ClientKeyBindings.CODEX.consumeClick()) {
                if (ClientCombatHud.get().inCombat) {
                    mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§7Grimoire indisponible pendant un combat."));
                } else {
                    mc.setScreen(new GrimoireScreen());
                }
            }
        }

        /** Le HUD custom remplace coeurs/armure/XP vanilla (si classe choisie). */
        @SubscribeEvent
        public static void onOverlayPre(RenderGuiOverlayEvent.Pre event) {
            // v0.21 : faim desactivee — la barre de nourriture disparait du HUD
            if (event.getOverlay() == VanillaGuiOverlay.FOOD_LEVEL.type()) {
                event.setCanceled(true);
                return;
            }
            // combat : vue Wakfu (pas de crosshair / hotbar / nom d'item vanilla)
            if (ClientCombatHud.get().inCombat
                    && (event.getOverlay() == VanillaGuiOverlay.CROSSHAIR.type()
                        || event.getOverlay() == VanillaGuiOverlay.HOTBAR.type()
                        || event.getOverlay() == VanillaGuiOverlay.ITEM_NAME.type())) {
                event.setCanceled(true);
                return;
            }
            if (event.getOverlay() != VanillaGuiOverlay.PLAYER_HEALTH.type()
                    && event.getOverlay() != VanillaGuiOverlay.ARMOR_LEVEL.type()
                    && event.getOverlay() != VanillaGuiOverlay.EXPERIENCE_BAR.type()) {
                return;
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            mc.player.getCapability(ModCapabilities.RPG).ifPresent(data -> {
                if (data.hasClass()) event.setCanceled(true);
            });
        }
    }
}

