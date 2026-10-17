package fr.wakfu.rpg.registry;

import fr.wakfu.rpg.WakfuRPG;
import fr.wakfu.rpg.core.spell.Spell;
import fr.wakfu.rpg.core.spell.Spells;
import fr.wakfu.rpg.core.RpgClass;
import fr.wakfu.rpg.item.*;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, WakfuRPG.MODID);

    // Core items
    public static final RegistryObject<Item> WAKFU_ARMOR = ITEMS.register("wakfu_armor",
            () -> new WakfuArmorItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> WAKFU_WEAPON = ITEMS.register("wakfu_weapon",
            () -> new WakfuWeaponItem(new Item.Properties().stacksTo(1), "sword"));
    public static final RegistryObject<Item> WAKFU_GEAR = ITEMS.register("wakfu_gear",
            () -> new WakfuGearItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> WAKFU_TROPHY = ITEMS.register("wakfu_trophy",
            () -> new WakfuTrophyItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> WAKFU_GRIMOIRE = ITEMS.register("wakfu_grimoire",
            () -> new WakfuGrimoireItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> WAKFU_INGOT = ITEMS.register("wakfu_ingot",
            () -> new Item(new Item.Properties()));

    // Spell items
    private static final List<Spell> IOP_SPELLS = Spells.forClass(RpgClass.IOP);
    private static final List<Spell> CRA_SPELLS = Spells.forClass(RpgClass.CRA);

    public static final RegistryObject<Item> SPELL_IOP_BASIC = ITEMS.register("spell_iop_basic",
            () -> new SpellItem(new Item.Properties().stacksTo(1), IOP_SPELLS.get(0)));
    public static final RegistryObject<Item> SPELL_IOP_HEAVY = ITEMS.register("spell_iop_heavy",
            () -> new SpellItem(new Item.Properties().stacksTo(1), IOP_SPELLS.get(1)));
    public static final RegistryObject<Item> SPELL_CRA_BASIC = ITEMS.register("spell_cra_basic",
            () -> new SpellItem(new Item.Properties().stacksTo(1), CRA_SPELLS.get(0)));
    public static final RegistryObject<Item> SPELL_CRA_SNIPE = ITEMS.register("spell_cra_snipe",
            () -> new SpellItem(new Item.Properties().stacksTo(1), CRA_SPELLS.get(1)));

    // Combat utilities
    public static final RegistryObject<Item> COMBAT_MOVE = ITEMS.register("combat_move",
            () -> new CombatUtilityItem(new Item.Properties().stacksTo(1), CombatUtilityItem.Kind.MOVE));
    public static final RegistryObject<Item> COMBAT_END = ITEMS.register("combat_end",
            () -> new CombatUtilityItem(new Item.Properties().stacksTo(1), CombatUtilityItem.Kind.END));
    public static final RegistryObject<Item> COMBAT_FLEE = ITEMS.register("combat_flee",
            () -> new CombatUtilityItem(new Item.Properties().stacksTo(1), CombatUtilityItem.Kind.FLEE));

    // NPC spawn eggs
    public static final RegistryObject<Item> EGG_CLASS_MASTER = ITEMS.register("egg_class_master",
            () -> new ForgeSpawnEggItem(ModEntities.CLASS_MASTER, 0x5A3A8A, 0xFFDD55, new Item.Properties()));
    public static final RegistryObject<Item> EGG_HERALD = ITEMS.register("egg_herald",
            () -> new ForgeSpawnEggItem(ModEntities.HERALD, 0x2E6B2E, 0xFFCC77, new Item.Properties()));
    public static final RegistryObject<Item> EGG_BLACKSMITH = ITEMS.register("egg_blacksmith",
            () -> new ForgeSpawnEggItem(ModEntities.BLACKSMITH, 0x3A3A44, 0xFF7733, new Item.Properties()));
    public static final RegistryObject<Item> EGG_MERCHANT = ITEMS.register("egg_merchant",
            () -> new ForgeSpawnEggItem(ModEntities.MERCHANT, 0x8A5A2A, 0xFFDD99, new Item.Properties()));
    public static final RegistryObject<Item> EGG_MARKET = ITEMS.register("egg_market",
            () -> new ForgeSpawnEggItem(ModEntities.MARKET, 0x2A5A8A, 0xFFDD55, new Item.Properties()));
    public static final RegistryObject<Item> EGG_MONSTER = ITEMS.register("egg_monster",
            () -> new ForgeSpawnEggItem(ModEntities.MONSTER, 0x4A6B2A, 0x99AA55, new Item.Properties()));

    // v0.29 : Mount spawn eggs
    public static final RegistryObject<Item> EGG_MOUNT_WOLF = ITEMS.register("egg_mount_wolf",
            () -> new ForgeSpawnEggItem(ModEntities.MOUNT_WOLF, 0x6666AA, 0x8888CC, new Item.Properties()));
    public static final RegistryObject<Item> EGG_MOUNT_BEAR = ITEMS.register("egg_mount_bear",
            () -> new ForgeSpawnEggItem(ModEntities.MOUNT_BEAR, 0x8B6914, 0xD4A950, new Item.Properties()));
    public static final RegistryObject<Item> EGG_MOUNT_GRIFFIN = ITEMS.register("egg_mount_griffin",
            () -> new ForgeSpawnEggItem(ModEntities.MOUNT_GRIFFIN, 0xC8A820, 0xFFE070, new Item.Properties()));

    // v0.29 : Familiar spawn eggs
    public static final RegistryObject<Item> EGG_FAMILIAR_CAT = ITEMS.register("egg_familiar_cat",
            () -> new ForgeSpawnEggItem(ModEntities.FAMILIAR_CAT, 0x888899, 0x66FF66, new Item.Properties()));
    public static final RegistryObject<Item> EGG_FAMILIAR_DRAGON = ITEMS.register("egg_familiar_dragon",
            () -> new ForgeSpawnEggItem(ModEntities.FAMILIAR_DRAGON, 0xCC3322, 0xFFFF00, new Item.Properties()));
    public static final RegistryObject<Item> EGG_FAMILIAR_GOLEM = ITEMS.register("egg_familiar_golem",
            () -> new ForgeSpawnEggItem(ModEntities.FAMILIAR_GOLEM, 0x888899, 0x55BBFF, new Item.Properties()));

    // v0.29 : Mount armor (bear only)
    public static final RegistryObject<Item> MOUNT_ARMOR_IRON = ITEMS.register("mount_armor_iron",
            () -> new MountArmorItem("iron", new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> MOUNT_ARMOR_GOLD = ITEMS.register("mount_armor_gold",
            () -> new MountArmorItem("gold", new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> MOUNT_ARMOR_DIAMOND = ITEMS.register("mount_armor_diamond",
            () -> new MountArmorItem("diamond", new Item.Properties().stacksTo(1)));

    // v0.31 : Montures equipables (slot monture)
    public static final RegistryObject<Item> MOUNT_WOLF_ITEM = ITEMS.register("mount_wolf",
            () -> new fr.wakfu.rpg.item.WakfuMountItem(fr.wakfu.rpg.item.WakfuMountItem.TYPE_WOLF,
                    new Item.Properties()));
    public static final RegistryObject<Item> MOUNT_BEAR_ITEM = ITEMS.register("mount_bear",
            () -> new fr.wakfu.rpg.item.WakfuMountItem(fr.wakfu.rpg.item.WakfuMountItem.TYPE_BEAR,
                    new Item.Properties()));
    public static final RegistryObject<Item> MOUNT_GRIFFIN_ITEM = ITEMS.register("mount_griffin",
            () -> new fr.wakfu.rpg.item.WakfuMountItem(fr.wakfu.rpg.item.WakfuMountItem.TYPE_GRIFFIN,
                    new Item.Properties()));
    public static final RegistryObject<Item> MOUNT_DRAGON_ITEM = ITEMS.register("mount_dragon",
            () -> new fr.wakfu.rpg.item.WakfuMountItem(fr.wakfu.rpg.item.WakfuMountItem.TYPE_DRAGON,
                    new Item.Properties()));
    public static final RegistryObject<Item> MOUNT_ARMOR_NETHERITE = ITEMS.register("mount_armor_netherite",
            () -> new MountArmorItem("netherite", new Item.Properties().stacksTo(1)));
}

