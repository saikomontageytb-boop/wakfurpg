package fr.wakfu.rpg.registry;

import fr.wakfu.rpg.WakfuRPG;
import fr.wakfu.rpg.entity.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, WakfuRPG.MODID);

    // NPCs
    public static final RegistryObject<EntityType<ClassMasterNpc>> CLASS_MASTER =
            ENTITIES.register("class_master", () -> EntityType.Builder.of(ClassMasterNpc::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F).clientTrackingRange(10).build("class_master"));
    public static final RegistryObject<EntityType<HeraldNpc>> HERALD =
            ENTITIES.register("herald", () -> EntityType.Builder.of(HeraldNpc::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F).clientTrackingRange(10).build("herald"));
    public static final RegistryObject<EntityType<BlacksmithNpc>> BLACKSMITH =
            ENTITIES.register("blacksmith", () -> EntityType.Builder.of(BlacksmithNpc::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F).clientTrackingRange(10).build("blacksmith"));
    public static final RegistryObject<EntityType<MerchantNpc>> MERCHANT =
            ENTITIES.register("merchant", () -> EntityType.Builder.of(MerchantNpc::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F).clientTrackingRange(10).build("merchant"));
    public static final RegistryObject<EntityType<MarketNpc>> MARKET =
            ENTITIES.register("market", () -> EntityType.Builder.of(MarketNpc::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F).clientTrackingRange(10).build("market"));
    public static final RegistryObject<EntityType<MonsterNpc>> MONSTER =
            ENTITIES.register("monster", () -> EntityType.Builder.of(MonsterNpc::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F).clientTrackingRange(10).build("monster"));

    // v0.29 : Montures (rideable)
    public static final RegistryObject<EntityType<WakfuMount>> MOUNT_WOLF =
            ENTITIES.register("mount_wolf", () -> EntityType.Builder.of(WakfuMount::new, MobCategory.CREATURE)
                    .sized(1.2F, 1.6F).clientTrackingRange(16).build("mount_wolf"));
    public static final RegistryObject<EntityType<WakfuMount>> MOUNT_BEAR =
            ENTITIES.register("mount_bear", () -> EntityType.Builder.of(WakfuMount::new, MobCategory.CREATURE)
                    .sized(1.4F, 1.8F).clientTrackingRange(16).build("mount_bear"));
    public static final RegistryObject<EntityType<WakfuMount>> MOUNT_GRIFFIN =
            ENTITIES.register("mount_griffin", () -> EntityType.Builder.of(WakfuMount::new, MobCategory.CREATURE)
                    .sized(1.4F, 1.8F).clientTrackingRange(16).build("mount_griffin"));

    // v0.29 : Familiers (companions)
    public static final RegistryObject<EntityType<FamiliarEntity>> FAMILIAR_CAT =
            ENTITIES.register("familiar_cat", () -> EntityType.Builder.of(FamiliarEntity::new, MobCategory.CREATURE)
                    .sized(0.5F, 0.5F).clientTrackingRange(12).build("familiar_cat"));
    public static final RegistryObject<EntityType<FamiliarEntity>> FAMILIAR_DRAGON =
            ENTITIES.register("familiar_dragon", () -> EntityType.Builder.of(FamiliarEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 0.6F).clientTrackingRange(12).build("familiar_dragon"));
    public static final RegistryObject<EntityType<FamiliarEntity>> FAMILIAR_GOLEM =
            ENTITIES.register("familiar_golem", () -> EntityType.Builder.of(FamiliarEntity::new, MobCategory.CREATURE)
                    .sized(0.5F, 0.7F).clientTrackingRange(12).build("familiar_golem"));
}

