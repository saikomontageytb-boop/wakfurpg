package fr.wakfu.rpg.entity;

import fr.wakfu.rpg.core.DungeonManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Visible, clickable dungeon keeper. */
public final class DungeonNpc extends ClassMasterNpc {
    private String gateId = "";

    public DungeonNpc(EntityType<? extends net.minecraft.world.entity.PathfinderMob> type, Level level) {
        super(type, level);
    }

    public void setGateId(String id) { gateId = id == null ? "" : id; }
    public String gateId() { return gateId; }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide() && player instanceof ServerPlayer serverPlayer)
            DungeonManager.openPanel(serverPlayer, gateId);
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    @Override protected Component defaultName() { return Component.literal("Gardien de donjon"); }
    @Override public Component getName() {
        Component custom = getCustomName();
        return custom != null ? custom : defaultName();
    }

    @Override public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("dungeonGate", gateId);
    }

    @Override public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        gateId = tag.getString("dungeonGate");
    }
}
