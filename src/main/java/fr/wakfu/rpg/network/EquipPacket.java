package fr.wakfu.rpg.network;

import fr.wakfu.rpg.core.ModCapabilities;
import fr.wakfu.rpg.core.PlayerRpgData;
import fr.wakfu.rpg.core.combat.CombatManager;
import fr.wakfu.rpg.item.WakfuArmorItem;
import fr.wakfu.rpg.item.WakfuMountItem;
import fr.wakfu.rpg.item.WakfuWeaponItem;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

/**
 * v0.31 : Client -> serveur — equipement via l'ecran « Sac & equipement » (touche I).
 * code >= 0 : equipe l'objet a l'index `code` de l'inventaire principal
 * code -1..-4 : retire l'armure.
 * code -5 : retire l'arme.
 * code -6 : retire la monture.
 */
public class EquipPacket {
    public final int code;

    public EquipPacket(int code) {
        this.code = code;
    }

    public static void encode(EquipPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.code);
    }

    public static EquipPacket decode(FriendlyByteBuf buf) {
        return new EquipPacket(buf.readInt());
    }

    public static void handle(EquipPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = ctx.get().getSender();
            if (player == null) return;
            if (CombatManager.inCombat(player.getUUID())) {
                player.sendSystemMessage(Component.literal("\u00a7cImpossible de changer d'\u00e9quipement en plein combat."));
                return;
            }
            // --- desequiper
            if (msg.code < 0) {
                if (msg.code >= -4) {
                    int idx = switch (msg.code) {
                        case -1 -> WakfuArmorItem.SLOT_HEAD;
                        case -2 -> WakfuArmorItem.SLOT_CHEST;
                        case -3 -> WakfuArmorItem.SLOT_LEGS;
                        default -> WakfuArmorItem.SLOT_FEET;
                    };
                    ItemStack prev = player.getInventory().getArmor(idx);
                    if (prev.isEmpty()) return;
                    player.getInventory().armor.set(idx, ItemStack.EMPTY);
                    if (!player.getInventory().add(prev)) player.drop(prev, false);
                    player.playSound(SoundEvents.ARMOR_EQUIP_GENERIC, 0.5f, 0.8f);
                    player.sendSystemMessage(Component.literal("\u00a77Tu retires : " + prev.getHoverName().getString()));
                } else if (msg.code == -5) {
                    PlayerRpgData data = player.getCapability(ModCapabilities.RPG).resolve().orElse(null);
                    if (data == null) return;
                    ItemStack prev = data.getEquippedWeapon();
                    if (prev.isEmpty()) return;
                    data.setEquippedWeapon(ItemStack.EMPTY);
                    if (!player.getInventory().add(prev)) player.drop(prev, false);
                    player.playSound(SoundEvents.ARMOR_EQUIP_GENERIC, 0.5f, 0.8f);
                    player.sendSystemMessage(Component.literal("\u00a77Tu rengaines : " + prev.getHoverName().getString()));
                    fr.wakfu.rpg.events.CommonEvents.sync(player);
                } else if (msg.code == -6) {
                    PlayerRpgData data = player.getCapability(ModCapabilities.RPG).resolve().orElse(null);
                    if (data == null || data.getEquippedMount().isEmpty()) return;
                    String mountType = data.getEquippedMount();
                    despawnMount(player, data);
                    ItemStack mountItem = makeMountItem(mountType);
                    if (!player.getInventory().add(mountItem)) player.drop(mountItem, false);
                    data.setEquippedMount("");
                    player.playSound(SoundEvents.ARMOR_EQUIP_GENERIC, 0.5f, 0.8f);
                    player.sendSystemMessage(Component.literal("\u00a77Tu retires ta monture."));
                    fr.wakfu.rpg.events.CommonEvents.sync(player);
                }
                return;
            }
            // --- equiper depuis l'inventaire
            var items = player.getInventory().items;
            if (msg.code >= items.size()) return;
            ItemStack held = items.get(msg.code);
            if (held.isEmpty()) return;
            if (held.getItem() instanceof WakfuArmorItem) {
                int idx = WakfuArmorItem.slotIndex(held);
                ItemStack prev = player.getInventory().getArmor(idx);
                player.getInventory().armor.set(idx, held.copy());
                items.set(msg.code, prev.isEmpty() ? ItemStack.EMPTY : prev);
                player.playSound(SoundEvents.ARMOR_EQUIP_GENERIC, 0.6f, 1.0f);
                player.sendSystemMessage(Component.literal("\u00a77Tu \u00e9quipes : " + held.getHoverName().getString()));
            } else if (WakfuWeaponItem.isWeapon(held)) {
                PlayerRpgData data = player.getCapability(ModCapabilities.RPG).resolve().orElse(null);
                if (data == null) return;
                ItemStack prev = data.getEquippedWeapon();
                data.setEquippedWeapon(held.copy());
                items.set(msg.code, prev.isEmpty() ? ItemStack.EMPTY : prev);
                player.playSound(SoundEvents.ARMOR_EQUIP_GENERIC, 0.6f, 1.0f);
                player.sendSystemMessage(Component.literal("\u00a77Tu \u00e9quipes : " + held.getHoverName().getString()
                        + (prev.isEmpty() ? "" : " \u00a78(remplace " + prev.getHoverName().getString() + "\u00a78)")));
                fr.wakfu.rpg.events.CommonEvents.sync(player);
            } else if (WakfuMountItem.isMountItem(held)) {
                PlayerRpgData data = player.getCapability(ModCapabilities.RPG).resolve().orElse(null);
                if (data == null) return;
                String prevType = data.getEquippedMount();
                String newType = WakfuMountItem.getMountType(held);
                // Despawn previous mount
                despawnMount(player, data);
                // Swap items
                if (!prevType.isEmpty()) {
                    ItemStack prevItem = makeMountItem(prevType);
                    items.set(msg.code, prevItem);
                } else {
                    items.set(msg.code, ItemStack.EMPTY);
                }
                data.setEquippedMount(newType);
                // Spawn Mythic Mounts entity + ride it
                spawnMythicMount(player, data, newType);
                player.playSound(SoundEvents.ARMOR_EQUIP_GENERIC, 0.6f, 1.0f);
                player.sendSystemMessage(Component.literal("\u00a77Tu \u00e9quipes : " + held.getHoverName().getString()));
                fr.wakfu.rpg.events.CommonEvents.sync(player);
            } else {
                player.sendSystemMessage(Component.literal("\u00a7cCet objet ne peut pas \u00eatre \u00e9quip\u00e9."));
            }
        });
        ctx.get().setPacketHandled(true);
    }

    // ---- Mount helpers (using Mythic Mounts entities) ----

    private static final java.util.Map<String, String> MOUNT_ENTITY_MAP = java.util.Map.of(
        "wolf", "mythicmounts:direwolf",
        "bear", "mythicmounts:nightmare",
        "griffin", "mythicmounts:griffon",
        "dragon", "mythicmounts:dragon",
        "moth", "mythicmounts:moth",
        "firebird", "mythicmounts:firebird"
    );

    private static void despawnMount(net.minecraft.world.entity.player.Player player, PlayerRpgData data) {
        int eid = data.getMountEntityId();
        if (eid >= 0) {
            var entity = player.level().getEntity(eid);
            if (entity != null && entity.isAlive()) {
                entity.ejectPassengers();
                entity.discard();
            }
            data.setMountEntityId(-1);
        }
        // Disable flight when dismounting
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            var ab = sp.getAbilities();
            ab.mayfly = false;
            ab.flying = false;
            sp.onUpdateAbilities();
        }
    }

    private static void spawnMythicMount(net.minecraft.world.entity.player.Player player, PlayerRpgData data, String type) {
        var level = player.level();
        String entityId = MOUNT_ENTITY_MAP.getOrDefault(type, "mythicmounts:direwolf");
        ResourceLocation rl = new ResourceLocation(entityId);
        EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(rl);
        if (entityType == null) {
            player.sendSystemMessage(Component.literal("\u00a7cErreur: monture introuvable."));
            return;
        }
        Entity entity = entityType.create(level);
        if (entity == null) {
            player.sendSystemMessage(Component.literal("\u00a7cErreur: cr\u00e9ation impossible."));
            return;
        }
        // Position at player
        entity.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        // Configure as rideable mount
        if (entity instanceof net.minecraft.world.entity.animal.horse.AbstractHorse horse) {
            horse.setTamed(true);
            horse.setOwnerUUID(player.getUUID());
            // Add saddle so the player can control with WASD
            horse.getSlot(1).set(new ItemStack(net.minecraft.world.item.Items.SADDLE));
            // Make invulnerable — can't be hit
            horse.setInvulnerable(true);
            horse.setNoGravity(false);
            horse.finalizeSpawn((net.minecraft.server.level.ServerLevel) level,
                    level.getCurrentDifficultyAt(player.blockPosition()),
                    MobSpawnType.MOB_SUMMONED, null, null);
        }
        // Also set invulnerable on the entity level
        entity.setInvulnerable(true);
        // Spawn and mount
        level.addFreshEntity(entity);
        player.startRiding(entity, true);
        data.setMountEntityId(entity.getId());
        // For griffin/dragon: enable flight for the player
        if ("griffin".equals(type) || "dragon".equals(type)) {
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                var ab = sp.getAbilities();
                ab.mayfly = true;
                ab.flying = false;
                sp.onUpdateAbilities();
            }
        }
    }

    private static ItemStack makeMountItem(String type) {
        var item = switch (type) {
            case "bear" -> fr.wakfu.rpg.registry.ModItems.MOUNT_BEAR_ITEM.get();
            case "griffin" -> fr.wakfu.rpg.registry.ModItems.MOUNT_GRIFFIN_ITEM.get();
            case "dragon" -> fr.wakfu.rpg.registry.ModItems.MOUNT_DRAGON_ITEM.get();
            default -> fr.wakfu.rpg.registry.ModItems.MOUNT_WOLF_ITEM.get();
        };
        return new ItemStack(item);
    }
}
