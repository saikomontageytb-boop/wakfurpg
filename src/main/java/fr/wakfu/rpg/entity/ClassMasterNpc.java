package fr.wakfu.rpg.entity;

import fr.wakfu.rpg.core.DialogueManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

/**
 * PNJ Maître des Classes : immobile, invulnerable, persistant.
 * Clic droit -> ouvre le dialogue de choix de classe (didacticiel de spawn).
 */
public class ClassMasterNpc extends PathfinderMob {

    public ClassMasterNpc(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setInvulnerable(true);
        setPersistenceRequired();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0F));
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide() && player instanceof ServerPlayer sp) {
            DialogueManager.openRoot(this, sp);
        }
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    /** v0.25 : nom par défaut (œuf de spawn / commande). */
    protected Component defaultName() {
        return Component.literal("Maître des Classes");
    }

    /** v0.25 : placé par œuf/commande → tag "manual" (jamais supprimé au redémarrage). */
    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(
            net.minecraft.world.level.ServerLevelAccessor acc,
            net.minecraft.world.DifficultyInstance diff,
            net.minecraft.world.entity.MobSpawnType mtype,
            net.minecraft.world.entity.SpawnGroupData data,
            net.minecraft.nbt.CompoundTag tag) {
        if (mtype == net.minecraft.world.entity.MobSpawnType.SPAWN_EGG
                || mtype == net.minecraft.world.entity.MobSpawnType.COMMAND) {
            addTag("manual");
            if (getCustomName() == null) {
                setCustomName(defaultName());
                setCustomNameVisible(true);
            }
        }
        return super.finalizeSpawn(acc, diff, mtype, data, tag);
    }

    @Override
    public Component getName() {
        Component custom = getCustomName();
        return custom != null ? custom : Component.translatable("entity.wakfurpg.class_master");
    }
}
