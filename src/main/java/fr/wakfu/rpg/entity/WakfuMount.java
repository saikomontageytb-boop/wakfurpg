package fr.wakfu.rpg.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class WakfuMount extends AbstractHorse {

    public enum MountType { WOLF, BEAR, GRIFFIN }

    private MountType mountType = MountType.WOLF;
    private java.util.UUID ownerUuid = null;
    private String customName = "";
    private boolean flying = false;
    private int mountArmorSlot = -1;

    public WakfuMount(EntityType<? extends AbstractHorse> type, Level level) {
        super(type, level);
        this.setTamed(true);
        this.setPersistenceRequired();
    }

    public void initMount(MountType mt, Player owner) {
        this.mountType = mt;
        this.ownerUuid = owner.getUUID();
        this.customName = switch (mt) {
            case WOLF -> "Loup Rapide";
            case BEAR -> "Ours Cuirass\u00e9";
            case GRIFFIN -> "Griffon C\u00e9leste";
        };
        var speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(switch (mt) {
                case WOLF -> 0.35;
                case BEAR -> 0.22;
                case GRIFFIN -> 0.28;
            });
        }
        var jump = this.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump != null) {
            jump.setBaseValue(switch (mt) {
                case WOLF -> 0.7;
                case BEAR -> 0.5;
                case GRIFFIN -> 0.8;
            });
        }
        var hp = this.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(switch (mt) {
                case WOLF -> 30;
                case BEAR -> 60;
                case GRIFFIN -> 40;
            });
        }
        this.setHealth(this.getMaxHealth());
        refreshName();
    }

    public MountType getMountType() { return mountType; }
    public java.util.UUID getOwnerUuid() { return ownerUuid; }
    public boolean isFlying() { return flying && mountType == MountType.GRIFFIN; }

    public void refreshName() {
        String color = switch (mountType) {
            case WOLF -> "\u00a7b";
            case BEAR -> "\u00a76";
            case GRIFFIN -> "\u00a7e";
        };
        String icon = switch (mountType) {
            case WOLF -> "\uD83D\uDC3A";
            case BEAR -> "\uD83D\uDC3B";
            case GRIFFIN -> "\uD83E\uDD85";
        };
        setCustomName(Component.literal(icon + " " + color + customName));
        setCustomNameVisible(true);
    }

    @Override
    public boolean isSaddleable() { return true; }


    public boolean canEquipMountArmor() { return mountType == MountType.BEAR; }

    public ItemStack getMountArmor() {
        if (mountArmorSlot < 0) return ItemStack.EMPTY;
        return this.getSlot(mountArmorSlot).get();
    }

    public void setMountArmor(ItemStack stack) {
        if (canEquipMountArmor() && mountArmorSlot >= 0) {
            this.getSlot(mountArmorSlot).set(stack);
        }
    }

    public int[] getMountArmorStats() {
        if (mountType != MountType.BEAR) return new int[4];
        ItemStack armor = getMountArmor();
        if (armor.isEmpty()) return new int[4];
        return fr.wakfu.rpg.item.MountArmorItem.getStats(armor);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.isBaby()) return super.mobInteract(player, hand);

        ItemStack held = player.getItemInHand(hand);

        if (mountType == MountType.BEAR && !held.isEmpty()
                && held.getItem() instanceof fr.wakfu.rpg.item.MountArmorItem) {
            if (!level().isClientSide()) {
                ItemStack old = getMountArmor();
                if (!old.isEmpty()) {
                    player.getInventory().add(old);
                }
                setMountArmor(held.copy());
                held.shrink(1);
                player.displayClientMessage(
                    Component.literal("\u00a7aArmure de monture \u00e9quip\u00e9e !"), true);
            }
            return InteractionResult.sidedSuccess(level().isClientSide());
        }

        return super.mobInteract(player, hand);
    }

    @Override
    public void tick() {
        super.tick();
    }

    public int[] getRiderBonus() {
        int[] armorBonus = getMountArmorStats();
        return switch (mountType) {
            case WOLF -> new int[]{0, 2, 0, 0};
            case BEAR -> new int[]{armorBonus[0] + 10, armorBonus[1], armorBonus[2], armorBonus[3] + 3};
            case GRIFFIN -> new int[]{0, 0, 5, 0};
        };
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("MountType", mountType.name());
        if (ownerUuid != null) tag.putString("MountOwner", ownerUuid.toString());
        tag.putString("MountName", customName);
        tag.putInt("MountArmorSlot", mountArmorSlot);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        try {
            this.mountType = MountType.valueOf(tag.getString("MountType"));
        } catch (Exception e) { this.mountType = MountType.WOLF; }
        String owner = tag.getString("MountOwner");
        if (!owner.isEmpty()) {
            try { this.ownerUuid = java.util.UUID.fromString(owner); } catch (Exception ignored) {}
        }
        this.customName = tag.getString("MountName");
        if (this.customName.isEmpty()) this.customName = mountType.name();
        this.mountArmorSlot = tag.getInt("MountArmorSlot");
        refreshName();
    }

    @Override
    public Component getName() {
        Component custom = getCustomName();
        return custom != null ? custom : Component.literal(customName);
    }

    @Override
    public boolean removeWhenFarAway(double d) { return false; }
}
