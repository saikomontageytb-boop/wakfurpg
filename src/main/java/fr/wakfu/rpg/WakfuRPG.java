package fr.wakfu.rpg;

import fr.wakfu.rpg.core.PlayerRpgData;
import fr.wakfu.rpg.events.CommonEvents;
import fr.wakfu.rpg.network.Network;
import fr.wakfu.rpg.registry.ModCreative;
import fr.wakfu.rpg.registry.ModEntities;
import fr.wakfu.rpg.registry.ModItems;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(WakfuRPG.MODID)
public class WakfuRPG {
    public static final String MODID = "wakfurpg";

    public WakfuRPG() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.ITEMS.register(bus);
        ModCreative.TABS.register(bus);
        ModEntities.ENTITIES.register(bus);
        bus.addListener(this::commonSetup);
        bus.addListener(this::registerCaps);
        bus.addListener(this::entityAttributes);
        MinecraftForge.EVENT_BUS.register(new CommonEvents());
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(Network::init);
    }

    private void registerCaps(final RegisterCapabilitiesEvent event) {
        event.register(PlayerRpgData.class);
    }

    private void entityAttributes(final EntityAttributeCreationEvent event) {
        event.put(ModEntities.CLASS_MASTER.get(), Mob.createMobAttributes().build());
        event.put(ModEntities.BLACKSMITH.get(), Mob.createMobAttributes().build());
        event.put(ModEntities.MERCHANT.get(), Mob.createMobAttributes().build());
        event.put(ModEntities.HERALD.get(), Mob.createMobAttributes().build());
        event.put(ModEntities.MARKET.get(), Mob.createMobAttributes().build());
        event.put(ModEntities.SAILOR.get(), Mob.createMobAttributes().build());
        event.put(ModEntities.DUNGEON_NPC.get(), Mob.createMobAttributes().build());
        event.put(ModEntities.MONSTER.get(), Mob.createMobAttributes()
                .add(Attributes.MOVEMENT_SPEED, 0.28D).build());

        // v0.29 : Montures
        event.put(ModEntities.MOUNT_WOLF.get(), Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30D)
                .add(Attributes.MOVEMENT_SPEED, 0.35D)
                .add(Attributes.JUMP_STRENGTH, 0.7D).build());
        event.put(ModEntities.MOUNT_BEAR.get(), Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 60D)
                .add(Attributes.MOVEMENT_SPEED, 0.22D)
                .add(Attributes.JUMP_STRENGTH, 0.5D).build());
        event.put(ModEntities.MOUNT_GRIFFIN.get(), Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.JUMP_STRENGTH, 0.8D).build());

        // v0.29 : Familiers
        event.put(ModEntities.FAMILIAR_CAT.get(), Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D).build());
        event.put(ModEntities.FAMILIAR_DRAGON.get(), Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D).build());
        event.put(ModEntities.FAMILIAR_GOLEM.get(), Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D).build());
    }
}

