package fr.wakfu.rpg.registry;

import fr.wakfu.rpg.WakfuRPG;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreative {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, WakfuRPG.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.literal("Wakfu RPG"))
                    .icon(() -> new ItemStack(ModItems.WAKFU_GRIMOIRE.get()))
                    .displayItems((params, output) -> {
                        // Core items
                        output.accept(ModItems.WAKFU_GRIMOIRE.get());
                        output.accept(ModItems.WAKFU_ARMOR.get());
                        output.accept(ModItems.WAKFU_WEAPON.get());
                        output.accept(ModItems.WAKFU_GEAR.get());
                        output.accept(ModItems.WAKFU_TROPHY.get());
                        output.accept(ModItems.WAKFU_INGOT.get());
                        // Spells
                        output.accept(ModItems.SPELL_IOP_BASIC.get());
                        output.accept(ModItems.SPELL_IOP_HEAVY.get());
                        output.accept(ModItems.SPELL_CRA_BASIC.get());
                        output.accept(ModItems.SPELL_CRA_SNIPE.get());
                        // Combat
                        output.accept(ModItems.COMBAT_MOVE.get());
                        output.accept(ModItems.COMBAT_END.get());
                        output.accept(ModItems.COMBAT_FLEE.get());
                        // NPC eggs
                        output.accept(ModItems.EGG_CLASS_MASTER.get());
                        output.accept(ModItems.EGG_HERALD.get());
                        output.accept(ModItems.EGG_BLACKSMITH.get());
                        output.accept(ModItems.EGG_MERCHANT.get());
                        output.accept(ModItems.EGG_MARKET.get());
                        output.accept(ModItems.EGG_MONSTER.get());
                        // v0.29 : Mount eggs
                        output.accept(ModItems.MOUNT_WOLF_ITEM.get());
                        output.accept(ModItems.MOUNT_BEAR_ITEM.get());
                        output.accept(ModItems.MOUNT_GRIFFIN_ITEM.get());
                        output.accept(ModItems.MOUNT_DRAGON_ITEM.get());
                        output.accept(ModItems.EGG_MOUNT_WOLF.get());
                        output.accept(ModItems.EGG_MOUNT_BEAR.get());
                        output.accept(ModItems.EGG_MOUNT_GRIFFIN.get());
                        // v0.29 : Familiar eggs
                        output.accept(ModItems.EGG_FAMILIAR_CAT.get());
                        output.accept(ModItems.EGG_FAMILIAR_DRAGON.get());
                        output.accept(ModItems.EGG_FAMILIAR_GOLEM.get());
                        // v0.29 : Mount armor
                        output.accept(ModItems.MOUNT_ARMOR_IRON.get());
                        output.accept(ModItems.MOUNT_ARMOR_GOLD.get());
                        output.accept(ModItems.MOUNT_ARMOR_DIAMOND.get());
                        output.accept(ModItems.MOUNT_ARMOR_NETHERITE.get());
                    })
                    .build());
}

