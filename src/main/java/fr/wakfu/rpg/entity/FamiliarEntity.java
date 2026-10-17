package fr.wakfu.rpg.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * v0.29 : Familier compagnon — 3 types :
 *  - Chat (buff vitesse +15%)
 *  - Dragonnet (buff dégâts +3)
 *  - Golem (buff résistance +5)
 *
 *  Le familier suit son propriétaire et lui donne des bonus passifs.
 *  Clic droit pour le ramasser / poser. Il reste près du joueur.
 */
public class FamiliarEntity extends PathfinderMob {

    public enum FamiliarType { CAT, DRAGON, GOLEM }

    private FamiliarType familiarType = FamiliarType.CAT;
    private java.util.UUID ownerUuid = null;
    private String customName = "";

    public FamiliarEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setInvulnerable(true);
        this.setPersistenceRequired();
    }

    public void initFamiliar(FamiliarType ft, Player owner) {
        this.familiarType = ft;
        this.ownerUuid = owner.getUUID();
        this.customName = switch (ft) {
            case CAT -> "Minou";
            case DRAGON -> "Drakô";
            case GOLEM -> "Gardieng";
        };
        // Small size
        var hp = this.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(20);
        this.setHealth(20);
        refreshName();
    }

    public FamiliarType getFamiliarType() { return familiarType; }
    public java.util.UUID getOwnerUuid() { return ownerUuid; }

    public void refreshName() {
        String color = switch (familiarType) {
            case CAT -> "§d";
            case DRAGON -> "§c";
            case GOLEM -> "§7";
        };
        String icon = switch (familiarType) {
            case CAT -> "🐱";
            case DRAGON -> "🐉";
            case GOLEM -> "🛡️";
        };
        setCustomName(Component.literal(icon + " " + color + customName));
        setCustomNameVisible(true);
    }

    /** Passive stat bonus for the owner. */
    public int[] getPassiveBonus() {
        return switch (familiarType) {
            case CAT -> new int[]{0, 0, 0, 0, 0, 0, 15}; // +15% speed
            case DRAGON -> new int[]{0, 3, 0, 0, 0, 0, 0}; // +3 damage
            case GOLEM -> new int[]{10, 0, 0, 5, 0, 0, 0}; // +10 PV, +5 resistance
        };
    }

    @Override
    protected void registerGoals() {
        // Follow owner goal - simplified
        this.goalSelector.addGoal(0, new net.minecraft.world.entity.ai.goal.FloatGoal(this));
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(2, new WaterAvoidingRandomStrollGoal(this, 0.6D, 30));
    }

    @Override
    public void tick() {
        super.tick();
        // Follow owner
        if (!level().isClientSide && ownerUuid != null) {
            Player owner = level().getServer().getPlayerList().getPlayer(ownerUuid);
            if (owner != null && this.distanceTo(owner) > 5) {
                this.getNavigation().moveTo(owner, 1.0);
            }
            if (owner != null && this.distanceTo(owner) > 30) {
                this.teleportTo(owner.getX() + 1, owner.getY(), owner.getZ() + 1);
            }
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide()) {
            if (player.isShiftKeyDown()) {
                // Pick up familiar
                ItemStack egg = new ItemStack(
                    switch (familiarType) {
                        case CAT -> fr.wakfu.rpg.registry.ModItems.EGG_FAMILIAR_CAT.get();
                        case DRAGON -> fr.wakfu.rpg.registry.ModItems.EGG_FAMILIAR_DRAGON.get();
                        case GOLEM -> fr.wakfu.rpg.registry.ModItems.EGG_FAMILIAR_GOLEM.get();
                    }
                );
                egg.getOrCreateTag().putString("FamiliarName", customName);
                player.getInventory().add(egg);
                this.discard();
                player.displayClientMessage(
                    Component.literal("§7Familier ramassé dans ton inventaire."), true);
                return InteractionResult.SUCCESS;
            }
            player.displayClientMessage(
                Component.literal("§a" + getCustomName().getString() + " §7te suit ! (Shift+clic pour ramasser)"), true);
        }
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    // NBT
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("FamiliarType", familiarType.name());
        if (ownerUuid != null) tag.putString("FamiliarOwner", ownerUuid.toString());
        tag.putString("FamiliarName", customName);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        try {
            this.familiarType = FamiliarType.valueOf(tag.getString("FamiliarType"));
        } catch (Exception e) { this.familiarType = FamiliarType.CAT; }
        String owner = tag.getString("FamiliarOwner");
        if (!owner.isEmpty()) {
            try { this.ownerUuid = java.util.UUID.fromString(owner); } catch (Exception ignored) {}
        }
        this.customName = tag.getString("FamiliarName");
        if (this.customName.isEmpty()) this.customName = familiarType.name();
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