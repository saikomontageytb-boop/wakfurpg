package fr.wakfu.rpg.events;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.wakfu.rpg.WakfuRPG;
import fr.wakfu.rpg.core.ModCapabilities;
import fr.wakfu.rpg.core.PlayerRpgData;
import fr.wakfu.rpg.core.RpgCapProvider;
import fr.wakfu.rpg.core.RpgClass;
import fr.wakfu.rpg.network.Network;
import fr.wakfu.rpg.network.DamagePopupPacket;
import fr.wakfu.rpg.network.SyncRpgDataPacket;
import fr.wakfu.rpg.registry.ModItems;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class CommonEvents {

    @SubscribeEvent
    public void onAttachCaps(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(new ResourceLocation(WakfuRPG.MODID, "rpg"), new RpgCapProvider());
        }
    }

    @SubscribeEvent
    public void onClone(PlayerEvent.Clone event) {
        event.getOriginal().getCapability(ModCapabilities.RPG).ifPresent(oldData -> {
            event.getEntity().getCapability(ModCapabilities.RPG).ifPresent(newData -> {
                newData.deserializeNBT(oldData.serializeNBT());
            });
        });
        // resync si c'est un vrai joueur serveur
        if (event.getEntity() instanceof ServerPlayer sp) {
            sync(sp);
        }
    }

    @SubscribeEvent
    public void onLivingDamage(LivingDamageEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level) || event.getAmount() <= 0) return;
        int amount = Math.max(1, Math.round(event.getAmount()));
        sendNearbyPopup(level, event.getEntity(), amount, false);
    }

    @SubscribeEvent
    public void onLivingHeal(LivingHealEvent event) {
        if (!(event.getEntity().level() instanceof ServerLevel level) || event.getAmount() <= 0) return;
        float missingHealth = event.getEntity().getMaxHealth() - event.getEntity().getHealth();
        int amount = Math.min(Math.round(event.getAmount()), Math.max(0, Math.round(missingHealth)));
        sendNearbyPopup(level, event.getEntity(), amount, true);
    }

    private static void sendNearbyPopup(ServerLevel level, Entity entity, int amount, boolean healing) {
        if (amount <= 0) return;
        DamagePopupPacket packet = new DamagePopupPacket(entity.getId(), amount, healing);
        for (ServerPlayer viewer : level.players()) {
            if (viewer.distanceToSqr(entity) <= 64 * 64) Network.sendToPlayer(viewer, packet);
        }
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size(); i++) {
            inventory.items.set(i, fr.wakfu.rpg.item.WakfuArmorItem.rebindToSlot(inventory.items.get(i)));
        }
        for (int i = 0; i < inventory.armor.size(); i++) {
            inventory.armor.set(i, fr.wakfu.rpg.item.WakfuArmorItem.rebindToSlot(inventory.armor.get(i)));
        }
        if (player instanceof ServerPlayer sp) {
            sp.getCapability(ModCapabilities.RPG).ifPresent(data -> {
                if (!data.hasClass()) Network.sendToPlayer(sp, new fr.wakfu.rpg.network.OpenClassSelectPacket());
            });
        }
    }

    @SubscribeEvent
    public void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        fr.wakfu.rpg.core.ZoneData.reset();
        fr.wakfu.rpg.core.Money.save();
        fr.wakfu.rpg.core.Friends.save();
        fr.wakfu.rpg.core.Market.save();
    }

    @SubscribeEvent
    public void onServerStarted(net.minecraftforge.event.server.ServerStartedEvent event) {
        var server = event.getServer();
        var log = org.apache.logging.log4j.LogManager.getLogger("wakfurpg");
        var level = server.overworld();
        if (level != null) {
            fr.wakfu.rpg.core.combat.Arena.cleanupOld(level);
            fr.wakfu.rpg.core.DialogueManager.ensureClassMasterAtSpawn(level, true);
            fr.wakfu.rpg.core.DialogueManager.ensureBlacksmithAtSpawn(level, true);
            fr.wakfu.rpg.core.DialogueManager.ensureHeraldAtSpawn(level, true);
            fr.wakfu.rpg.core.DialogueManager.ensureMerchantAtSpawn(level, true);
            fr.wakfu.rpg.core.Bank.ensureBank(level);
            // v0.25 : argent / amis / marché (fichiers monde) + Hôtel des Ventes
            fr.wakfu.rpg.core.Money.load(server);
            fr.wakfu.rpg.core.Friends.load(server);
            fr.wakfu.rpg.core.Market.load(server);
            fr.wakfu.rpg.core.DialogueManager.ensureMarketAtSpawn(level, true);
            fr.wakfu.rpg.core.DialogueManager.ensureSailorAtSpawn(level, true);
            var zd = fr.wakfu.rpg.core.ZoneData.get(level);
            if (zd.byName("spawn") == null) {
                var z0 = new fr.wakfu.rpg.core.ZoneData.Zone();
                z0.name = "spawn";
                z0.owner = "SERVEUR";
                var sp0 = level.getSharedSpawnPos();
                z0.x = sp0.getX();
                z0.z = sp0.getZ();
                z0.r = 24;
                zd.zones.add(z0);
                zd.setDirty();
                log.info("Zone protegee 'spawn' creee (rayon 24).");
            }
        }
        ServerLevel archipelago = server.getLevel(fr.wakfu.rpg.core.IslandMap.DIMENSION);
        if (archipelago != null) {
            fr.wakfu.rpg.core.IslandMap.prepare(archipelago);
            fr.wakfu.rpg.core.DialogueManager.ensureSailorAtSpawn(archipelago, true);
            archipelago.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING)
                    .set(false, server);
        }
        fr.wakfu.rpg.core.DungeonManager.ensureDefaults(server);
        log.info("Arenes v0.8 : cocons overworld au-dessus du lieu de combat (anciennes plates-formes nettoyees).");
    }

    /** Deplacement vraiment bloque pendant un combat : ancre + delta horizontal a zero. */
    // v0.25 : suivi d'immobilité pour la régénération rapide (1 PV/s, 2 PV/s à l'arrêt)
    private static final java.util.Map<java.util.UUID, double[]> LAST_POS = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, Integer> LAST_MOVE = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, String> LAST_ZONE = new java.util.HashMap<>();

    @SubscribeEvent
    public void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.START) return;
        if (event.player.level().isClientSide()) return;
        if (!(event.player instanceof ServerPlayer sp)) return;
        if (sp.tickCount % 10 == 0) notifyZoneChange(sp);
        if (sp.tickCount % 10 == 0) {
            for (int i = 0; i < sp.getInventory().armor.size(); i++) {
                var equipped = sp.getInventory().getArmor(i);
                var rebound = fr.wakfu.rpg.item.WakfuArmorItem.rebindToSlot(equipped);
                if (equipped != rebound) sp.getInventory().armor.set(i, rebound);
            }
        }
        // resend periodique de l'etat de combat (anti-perte de paquet / resync client)
        if (sp.tickCount % 40 == 0) fr.wakfu.rpg.core.combat.CombatManager.resend(sp);
        // v0.15 : Codex — suivi des zones visitees et du donjon
        if (sp.tickCount % 40 == 0) fr.wakfu.rpg.core.Codex.trackPosition(sp);
        // v0.21 : la faim est desactivee — barre toujours pleine, jamais d'affamement
        var fd = sp.getFoodData();
        if (fd.getFoodLevel() < 20 || fd.getSaturationLevel() < 5F) {
            fd.setFoodLevel(20);
            fd.setSaturation(5F);
            fd.setExhaustion(0F);
        }
        // v0.25 : régénération RAPIDE hors combat — 1 PV/s en bougeant,
        // 2 PV/s après 2 s d'immobilité.
        {
            double xm = sp.getX(), ym = sp.getY(), zm = sp.getZ();
            double[] lp = LAST_POS.get(sp.getUUID());
            if (lp == null || Math.abs(lp[0] - xm) > 1.0E-4 || Math.abs(lp[1] - ym) > 1.0E-4
                    || Math.abs(lp[2] - zm) > 1.0E-4) {
                LAST_POS.put(sp.getUUID(), new double[]{xm, ym, zm});
                LAST_MOVE.put(sp.getUUID(), sp.tickCount);
            }
        }
        if (sp.tickCount % 10 == 0
                && !fr.wakfu.rpg.core.combat.CombatManager.inCombat(sp.getUUID())) {
            int idle = sp.tickCount - LAST_MOVE.getOrDefault(sp.getUUID(), sp.tickCount);
            boolean still = idle >= 40;              // immobile depuis 2+ s
            if (still || sp.tickCount % 20 == 0) {   // 2 PV/s immobile, 1 PV/s sinon
                sp.getCapability(ModCapabilities.RPG).ifPresent(d -> {
                    if (!d.hasClass()) return;
                    int max = d.maxHp() + fr.wakfu.rpg.core.combat.CombatManager.armorBonusHp(sp);
                    if (d.getHp() < max) {
                        d.setHp(Math.min(max, d.getHp() + 1));
                        sync(sp);
                    }
                });
            }
        }
        var combat = fr.wakfu.rpg.core.combat.CombatManager.get(sp.getUUID());
        if (combat == null) return;
        // resync explicite des blocs de l'arene juste apres l'entree
        // (v0.19 : base individuelle — les membres qui rejoignent en cours de
        // combat ont leur propre joinTick)
        long t = sp.getServer().getTickCount();
        long base = Math.max(combat.enterTick, combat.joinTicks.getOrDefault(sp.getUUID(), 0L));
        if (t - base == 5 || t - base == 40 || t - base == 120 || t - base == 240) {
            fr.wakfu.rpg.core.combat.CombatManager.broadcastArenaBlocks(sp, combat);
        }
        var dm = sp.getDeltaMovement();
        sp.setDeltaMovement(0, dm.y, 0);
        // v0.12 : grace apres un deplacement legitime (animation cote client)
        int cside = combat.sideOf(sp.getUUID());
        if (cside >= 0 && sp.level().getGameTime() < combat.moveGrace[cside]) return;
        double ax = combat.anchorXFor(sp.getUUID());
        double az = combat.anchorZFor(sp.getUUID());
        double dx = sp.getX() - ax;
        double dz = sp.getZ() - az;
        if (dx * dx + dz * dz > 0.36) {
            sp.teleportTo(ax, sp.getY(), az);
        }
    }

    @SubscribeEvent
    public void onItemPickup(net.minecraftforge.event.entity.player.EntityItemPickupEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            fr.wakfu.rpg.core.Codex.item(sp, event.getItem().getItem());
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            // v0.17 : rattrapage unique des points (4 statut + 1 sort / niveau atteint)
            sp.getCapability(ModCapabilities.RPG).ifPresent(d -> d.migrate17());
            // v0.20 : reset unique de toute la progression (nouvelle courbe de sorts)
            sp.getCapability(ModCapabilities.RPG).ifPresent(d -> d.wipe20());
            sync(sp);
            fr.wakfu.rpg.core.combat.CombatManager.recoverIfStuck(sp);
            fr.wakfu.rpg.core.combat.CombatManager.onLogout(sp);
            var level = sp.serverLevel();
            fr.wakfu.rpg.core.DialogueManager.ensureClassMasterAtSpawn(level, false);
            fr.wakfu.rpg.core.DialogueManager.ensureBlacksmithAtSpawn(level, false);
            fr.wakfu.rpg.core.DialogueManager.ensureHeraldAtSpawn(level, false);
            fr.wakfu.rpg.core.DialogueManager.ensureMerchantAtSpawn(level, false);
            fr.wakfu.rpg.core.DialogueManager.ensureMarketAtSpawn(level, false);
            // v0.25 : argent — conversion des anciens lingots + sync HUD + pseudo amis
            fr.wakfu.rpg.core.Money.convertIngots(sp);
            fr.wakfu.rpg.core.Money.sync(sp);
            fr.wakfu.rpg.core.Friends.registerName(sp.getUUID(), sp.getName().getString());
            fr.wakfu.rpg.core.DialogueManager.tutorialHint(sp);
        }
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            sync(sp);
        }
    }

    public static void sync(ServerPlayer player) {
        player.getCapability(fr.wakfu.rpg.core.ModCapabilities.RPG).ifPresent(d ->
                fr.wakfu.rpg.core.Quests.progressMax(player, "level", d.getLevel()));
        player.getCapability(ModCapabilities.RPG).ifPresent(data ->
                Network.sendToPlayer(player, new SyncRpgDataPacket(data.serializeNBT())));
    }

    @SubscribeEvent
    public void onRightClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        // v0.18 : coffre/banque du spawn -> inventaire personnel de 27 slots
        if (event.getEntity() instanceof ServerPlayer sp3
                && sp3.level() instanceof net.minecraft.server.level.ServerLevel sl3
                && event.getPos().equals(fr.wakfu.rpg.core.Bank.chestPos(sl3))
                && !fr.wakfu.rpg.core.combat.CombatManager.inCombat(sp3.getUUID())) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            fr.wakfu.rpg.core.Bank.open(sp3);
            return;
        }
        if (event.getEntity() instanceof ServerPlayer sp
                && fr.wakfu.rpg.core.combat.CombatManager.inCombat(sp.getUUID())) {
            // en arene : aucune interaction bloc (tout passe par les cases)
            event.setCanceled(true);
        }
    }

    /** v0.18 : fermeture du coffre bancaire -> sauvegarde dans les donnees joueur. */
    @SubscribeEvent
    public void onBankClose(net.minecraftforge.event.entity.player.PlayerContainerEvent.Close event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        net.minecraft.world.SimpleContainer c = fr.wakfu.rpg.core.Bank.OPEN.get(sp.getUUID());
        if (c != null && event.getContainer() instanceof net.minecraft.world.inventory.ChestMenu cm
                && cm.getContainer() == c) {
            fr.wakfu.rpg.core.Bank.save(sp, c);
        }
    }

    @SubscribeEvent
    public void onLeftClickBlock(net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer sp
                && fr.wakfu.rpg.core.combat.CombatManager.inCombat(sp.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onBlockBreak(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
        event.setCanceled(true);
    }

    /** En combat : aucun degat vanilla (les sorts passent par les PV RPG). */
    @SubscribeEvent
    public void onLivingAttack(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (event.getEntity() instanceof ServerPlayer) {
            event.setCanceled(true);
            return;
        }
        if (event.getSource().getEntity() instanceof Player) {
            event.setCanceled(true);
        }
    }

    private static void notifyZoneChange(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        String label = fr.wakfu.rpg.core.ZoneData.get(level)
                .notificationLabel(level, player.blockPosition());
        String state = level.dimension().location() + ":" + label;
        String previous = LAST_ZONE.put(player.getUUID(), state);
        if (state.equals(previous)) return;
        player.displayClientMessage(Component.literal("§eZone §8· §f" + label), true);
        fr.wakfu.rpg.core.ZoneData.Zone zone = fr.wakfu.rpg.core.ZoneData.get(level)
                .at(player.blockPosition());
        if (zone != null) fr.wakfu.rpg.core.MobSpawnerData.refreshZone(level, zone);
    }

    @SubscribeEvent
    public void onDungeonGateInteract(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide() || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getTarget() instanceof net.minecraft.world.entity.decoration.ArmorStand stand)) return;
        if (fr.wakfu.rpg.core.DungeonManager.interactGate(player, stand)) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public void onSailorDuplicateLoad(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()
                || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof fr.wakfu.rpg.entity.SailorNpc incoming)) return;
        BlockPos spawn = level.dimension().equals(fr.wakfu.rpg.core.IslandMap.DIMENSION)
                ? fr.wakfu.rpg.core.IslandMap.spawn(level) : level.getSharedSpawnPos();
        double dx = incoming.getX() - (spawn.getX() + 0.5);
        double dz = incoming.getZ() - (spawn.getZ() + 0.5);
        // Remove saved sailors left at the imported-map arrival point after the
        // owner changed the dimension's world spawn. They are obsolete copies.
        if (dx * dx + dz * dz > 128 * 128) {
            event.setCanceled(true);
            return;
        }
        for (fr.wakfu.rpg.entity.SailorNpc other : level.getEntitiesOfClass(
                fr.wakfu.rpg.entity.SailorNpc.class,
                new net.minecraft.world.phys.AABB(spawn).inflate(128))) {
            if (other == incoming) continue;
            double ox = other.getX() - (spawn.getX() + 0.5);
            double oz = other.getZ() - (spawn.getZ() + 0.5);
            double otherDistance = ox * ox + oz * oz;
            double incomingDistance = dx * dx + dz * dz;
            if (otherDistance < incomingDistance
                    || (Math.abs(otherDistance - incomingDistance) < 0.01 && other.getId() < incoming.getId())) {
                event.setCanceled(true);
                return;
            }
            other.discard();
        }
    }

    /** Old wild NPCs stored in distant chunks are retired as those chunks load. */
    @SubscribeEvent
    public void onLegacyMonsterChunkLoad(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel)) return;
        Entity entity = event.getEntity();
        if (entity.getTags().contains("wakfurpg_spawner_managed")) return;
        if (entity instanceof fr.wakfu.rpg.entity.MonsterNpc mob) {
            if (mob.getTags().contains("manual") || mob.isSummon() || mob.getTags().contains("arena_copy")) return;
            mob.discardWithVisual();
        } else if (entity.getTags().contains("wakfurpg_alex_model")
                || entity instanceof net.minecraft.world.entity.monster.Monster) {
            entity.discard();
        }
    }

    @SubscribeEvent
    public void onExplosion(net.minecraftforge.event.level.ExplosionEvent.Start event) {
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void onAlexModelInteract(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide() || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        Entity clicked = event.getTarget();
        if (!clicked.getTags().contains("wakfurpg_alex_model")) return;
        String parent = clicked.getTags().stream().filter(t -> t.startsWith("wakfurpg_model_of_")).findFirst().orElse(null);
        if (parent == null || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (fr.wakfu.rpg.core.combat.CombatManager.inCombat(player.getUUID())) return;
        ServerLevel level = player.serverLevel();
        fr.wakfu.rpg.entity.MonsterNpc monster = level.getEntitiesOfClass(fr.wakfu.rpg.entity.MonsterNpc.class,
                clicked.getBoundingBox().inflate(5.0D), m -> parent.equals("wakfurpg_model_of_" + m.getUUID()))
                .stream().findFirst().orElse(null);
        if (monster != null) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
            fr.wakfu.rpg.core.combat.CombatManager.startCombat(player, monster);
        }
    }

    @SubscribeEvent
    public void onItemToss(net.minecraftforge.event.entity.item.ItemTossEvent event) {
        var item = event.getEntity().getItem();
        if (item.getItem() instanceof fr.wakfu.rpg.item.SpellItem
                || item.getItem() instanceof fr.wakfu.rpg.item.CombatUtilityItem) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            if (fr.wakfu.rpg.core.combat.CombatManager.inCombat(sp.getUUID())) {
                fr.wakfu.rpg.core.combat.CombatManager.endCombat(sp, null);
            }
            // v0.19 : nettoie invitations/groupes
            fr.wakfu.rpg.core.PartyManager.onLogout(sp);
            // v0.25 : nettoie le suivi d'immobilité
            LAST_POS.remove(sp.getUUID());
            LAST_MOVE.remove(sp.getUUID());
            LAST_ZONE.remove(sp.getUUID());
        }
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent event) {
        fr.wakfu.rpg.core.RpgCommands.register(event.getDispatcher());
        var classCommand = Commands.literal("class")
                .executes(ctx -> openClassSelect(ctx.getSource().getPlayer()))
                .then(Commands.argument("classe", StringArgumentType.word())
                        .executes(ctx -> {
                            ServerPlayer sp = ctx.getSource().getPlayer();
                            if (sp == null) return 0;
                            RpgClass c = RpgClass.byId(StringArgumentType.getString(ctx, "classe").toLowerCase());
                            if (c == null) {
                                ctx.getSource().sendFailure(Component.translatable("wakfurpg.unknown_class"));
                                return 0;
                            }
                            sp.getCapability(ModCapabilities.RPG).ifPresent(data -> {
                                if (data.hasClass()) {
                                    if (!fr.wakfu.rpg.network.ChooseClassPacket.consumeClassChangePermission(sp.getUUID())) {
                                        sp.sendSystemMessage(Component.translatable("wakfurpg.already_class"));
                                        return;
                                    }
                                    if (data.getRpgClass() == c) {
                                        sp.sendSystemMessage(Component.literal("Tu es déjà " + c.getName().getString() + "."));
                                        return;
                                    }
                                    data.changeClass(c);
                                    sp.sendSystemMessage(Component.literal("Classe changée pour "
                                            + c.getName().getString() + " ; niveau conservé, sorts réinitialisés."));
                                } else {
                                    data.chooseClass(c);
                                    sp.sendSystemMessage(Component.translatable("wakfurpg.class_chosen", c.getName()));
                                }
                                sync(sp);
                            });
                            return Command.SINGLE_SUCCESS;
                        }));
        event.getDispatcher().register(Commands.literal("wakfu")
                .then(classCommand)
                .then(Commands.literal("classe")
                        .executes(ctx -> openClassSelect(ctx.getSource().getPlayer())))
                .then(Commands.literal("xp")
                        .then(Commands.argument("quantite", IntegerArgumentType.integer(1, 1000000))
                                .executes(ctx -> {
                                    ServerPlayer sp = ctx.getSource().getPlayer();
                                    if (sp == null) return 0;
                                    int amount = IntegerArgumentType.getInteger(ctx, "quantite");
                                    sp.getCapability(ModCapabilities.RPG).ifPresent(data -> {
                                        data.addXp(amount);
                                        sp.sendSystemMessage(Component.translatable("wakfurpg.xp_added", amount, data.getXp(), data.getXpToNext(), data.getLevel()));
                                        sync(sp);
                                    });
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("stats")
                        .executes(ctx -> {
                            ServerPlayer sp = ctx.getSource().getPlayer();
                            if (sp == null) return 0;
                            sp.getCapability(ModCapabilities.RPG).ifPresent(data -> {
                                if (!data.hasClass()) {
                                    sp.sendSystemMessage(Component.translatable("wakfurpg.no_class"));
                                    return;
                                }
                                sp.sendSystemMessage(Component.literal(
                                        "§6" + data.getRpgClass().getName().getString() +
                                                " §7Niv §f" + data.getLevel() +
                                                " §7| PV §c" + data.getHp() + "/" + data.maxHp() +
                                                " §7| PA §e" + data.maxAp() +
                                                " §7| PM §a" + data.maxMp() +
                                                " §7| XP §b" + data.getXp() + "/" + data.getXpToNext()));
                            });
                            return Command.SINGLE_SUCCESS;
                        })));

        event.getDispatcher().register(Commands.literal("wakfuspawn")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("type", StringArgumentType.word())
                        .executes(ctx -> {
                            var sp = ctx.getSource().getPlayer();
                            if (sp == null) return 0;
                            fr.wakfu.rpg.core.MonsterType t =
                                    fr.wakfu.rpg.core.MonsterType.byId(StringArgumentType.getString(ctx, "type").toLowerCase());
                            if (t == null) {
                                ctx.getSource().sendFailure(Component.translatable("wakfurpg.unknown_monster"));
                                return 0;
                            }
                            var level = sp.serverLevel();
                            var pos = sp.blockPosition();
                            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
                            fr.wakfu.rpg.entity.MonsterNpc m = new fr.wakfu.rpg.entity.MonsterNpc(fr.wakfu.rpg.registry.ModEntities.MONSTER.get(), level);
                            m.init(t);
                            m.moveTo(pos.getX() + 0.5, y, pos.getZ() + 0.5, 0, 0);
                            level.addFreshEntity(m);
                            ctx.getSource().sendSuccess(() -> Component.literal("Monstre invoqué : " + t.getName().getString()), true);
                            return Command.SINGLE_SUCCESS;
                        })));

        // v0.19 : groupes de joueurs
        event.getDispatcher().register(Commands.literal("groupe")
                .then(Commands.literal("invite")
                        .then(Commands.argument("joueur", StringArgumentType.word())
                                .executes(ctx -> {
                                    ServerPlayer sp = ctx.getSource().getPlayer();
                                    if (sp != null) fr.wakfu.rpg.core.PartyManager.invite(sp, StringArgumentType.getString(ctx, "joueur"));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("accepter")
                        .executes(ctx -> {
                            ServerPlayer sp = ctx.getSource().getPlayer();
                            if (sp != null) fr.wakfu.rpg.core.PartyManager.accept(sp);
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("refuser")
                        .executes(ctx -> {
                            ServerPlayer sp = ctx.getSource().getPlayer();
                            if (sp != null) fr.wakfu.rpg.core.PartyManager.decline(sp);
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("quitter")
                        .executes(ctx -> {
                            ServerPlayer sp = ctx.getSource().getPlayer();
                            if (sp != null) fr.wakfu.rpg.core.PartyManager.leave(sp);
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("exclure")
                        .then(Commands.argument("joueur", StringArgumentType.word())
                                .executes(ctx -> {
                                    ServerPlayer sp = ctx.getSource().getPlayer();
                                    if (sp != null) fr.wakfu.rpg.core.PartyManager.kick(sp, StringArgumentType.getString(ctx, "joueur"));
                                    return Command.SINGLE_SUCCESS;
                                })))
                .then(Commands.literal("info")
                        .executes(ctx -> {
                            ServerPlayer sp = ctx.getSource().getPlayer();
                            if (sp != null) fr.wakfu.rpg.core.PartyManager.info(sp);
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(Commands.literal("rejoindre")
                        .then(Commands.argument("cle", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    ServerPlayer sp = ctx.getSource().getPlayer();
                                    if (sp != null) fr.wakfu.rpg.core.combat.CombatManager.joinCombat(sp, IntegerArgumentType.getInteger(ctx, "cle"));
                                    return Command.SINGLE_SUCCESS;
                                }))));
        event.getDispatcher().register(Commands.literal("g")
                .then(Commands.argument("message", net.minecraft.commands.arguments.MessageArgument.message())
                        .executes(ctx -> {
                            ServerPlayer sp = ctx.getSource().getPlayer();
                            if (sp != null) {
                                fr.wakfu.rpg.core.PartyManager.chat(sp,
                                        net.minecraft.commands.arguments.MessageArgument.getMessage(ctx, "message").getString());
                            }
                            return Command.SINGLE_SUCCESS;
                        })));
        event.getDispatcher().register(Commands.literal("duel")
                .then(Commands.argument("joueur", EntityArgument.player())
                        .executes(ctx -> {
                            ServerPlayer a = ctx.getSource().getPlayer();
                            if (a == null) return 0;
                            try {
                                ServerPlayer b = EntityArgument.getPlayer(ctx, "joueur");
                                fr.wakfu.rpg.core.combat.CombatManager.requestDuel(a, b);
                            } catch (CommandSyntaxException e) {
                                ctx.getSource().sendFailure(Component.literal("Joueur introuvable."));
                            }
                            return Command.SINGLE_SUCCESS;
                        })));
    }

    private static int openClassSelect(ServerPlayer player) {
        if (player == null) return 0;
        player.getCapability(ModCapabilities.RPG).ifPresent(data -> {
            if (data.hasClass()) {
                fr.wakfu.rpg.network.ChooseClassPacket.allowClassChange(player.getUUID());
                player.sendSystemMessage(Component.literal(
                        "Changer de classe conserve le niveau mais réinitialise les sorts."));
            }
            Network.sendToPlayer(player, new fr.wakfu.rpg.network.OpenClassSelectPacket());
        });
        return Command.SINGLE_SUCCESS;
    }

    // ------------------------------------------------------------ zones protegees (tranche 3)
    @SubscribeEvent
    public void onBreakBlock(net.minecraftforge.event.level.BlockEvent.BreakEvent e) {
        if (e.getLevel() instanceof ServerLevel sl && e.getPlayer() instanceof ServerPlayer sp
                && fr.wakfu.rpg.core.ZoneData.isBlocked(sl, e.getPos(), sp)) {
            e.setCanceled(true);
            sp.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§cZone protégée : construction réservée aux membres."), true);
        }
    }

    @SubscribeEvent
    public void onPlaceBlock(net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent e) {
        if (e.getLevel() instanceof ServerLevel sl && e.getEntity() instanceof ServerPlayer sp
                && fr.wakfu.rpg.core.ZoneData.isBlocked(sl, e.getPos(), sp)) {
            e.setCanceled(true);
            sp.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§cZone protégée : construction réservée aux membres."), true);
        }
    }

    @SubscribeEvent
    public void onAttackPlayer(net.minecraftforge.event.entity.player.AttackEntityEvent e) {
        e.setCanceled(true);
    }

    @SubscribeEvent
    public void onServerStartedMobs(net.minecraftforge.event.server.ServerStartedEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING)
                    .set(false, event.getServer());
            if (!level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)
                    && !level.dimension().equals(fr.wakfu.rpg.core.IslandMap.DIMENSION)) continue;
            int cleared = fr.wakfu.rpg.core.MobSpawnerData.purgeLegacyMobs(level);
            fr.wakfu.rpg.core.MobSpawnerData.get(level).ensureDefaults(level);
            fr.wakfu.rpg.core.ZoneData.get(level).ensureZoneConfigs(level);
            org.apache.logging.log4j.LogManager.getLogger("wakfurpg").info(
                    "Spawners invisibles prêts dans {} : {} point(s), {} ancien(s) monstre(s) supprimé(s).",
                    level.dimension().location(), fr.wakfu.rpg.core.MobSpawnerData.get(level).all().size(), cleared);
        }
    }

    @SubscribeEvent
    public void onServerTickMobRespawn(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        for (ServerLevel level : event.getServer().getAllLevels()) {
            fr.wakfu.rpg.core.combat.CombatManager.tickMovement(level);
            if (level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)
                    || level.dimension().equals(fr.wakfu.rpg.core.IslandMap.DIMENSION))
                fr.wakfu.rpg.core.MobSpawnerData.tick(level);
        }
    }
}
