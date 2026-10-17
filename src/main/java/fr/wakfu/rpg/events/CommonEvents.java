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
import fr.wakfu.rpg.network.SyncRpgDataPacket;
import fr.wakfu.rpg.registry.ModItems;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
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
        log.info("Arenes v0.8 : cocons overworld au-dessus du lieu de combat (anciennes plates-formes nettoyees).");
    }

    /** Deplacement vraiment bloque pendant un combat : ancre + delta horizontal a zero. */
    // v0.25 : suivi d'immobilité pour la régénération rapide (1 PV/s, 2 PV/s à l'arrêt)
    private static final java.util.Map<java.util.UUID, double[]> LAST_POS = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, Integer> LAST_MOVE = new java.util.HashMap<>();

    @SubscribeEvent
    public void onPlayerTick(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.START) return;
        if (event.player.level().isClientSide()) return;
        if (!(event.player instanceof ServerPlayer sp)) return;
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
        if (event.getPlayer() instanceof ServerPlayer sp
                && fr.wakfu.rpg.core.combat.CombatManager.inCombat(sp.getUUID())) {
            event.setCanceled(true);
        }
    }

    /** En combat : aucun degat vanilla (les sorts passent par les PV RPG). */
    @SubscribeEvent
    public void onLivingAttack(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (event.getEntity() instanceof ServerPlayer sp
                && fr.wakfu.rpg.core.combat.CombatManager.inCombat(sp.getUUID())) {
            event.setCanceled(true);
            return;
        }
        if (event.getSource().getEntity() instanceof ServerPlayer atk
                && fr.wakfu.rpg.core.combat.CombatManager.inCombat(atk.getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onBlockPlace(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        // rien : la pose de bloc passe par RightClickBlock annule en arene
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
        }
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent event) {
        fr.wakfu.rpg.core.RpgCommands.register(event.getDispatcher());
        event.getDispatcher().register(Commands.literal("wakfu")
                .then(Commands.literal("class")
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
                                            sp.sendSystemMessage(Component.translatable("wakfurpg.already_class"));
                                            return;
                                        }
                                        data.chooseClass(c);
                                        sp.sendSystemMessage(Component.translatable("wakfurpg.class_chosen", c.getName()));
                                        sync(sp);
                                    });
                                    return Command.SINGLE_SUCCESS;
                                })))
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
        // v0.12 fix : plus AUCUNE attaque vanilla pendant un combat (le tour par
        // tour passe uniquement par la barre de sorts / les cases)
        if (e.getEntity() instanceof ServerPlayer atkSelf
                && fr.wakfu.rpg.core.combat.CombatManager.inCombat(atkSelf.getUUID())) {
            e.setCanceled(true);
            return;
        }
        if (e.getTarget() instanceof ServerPlayer target && e.getEntity() instanceof ServerPlayer atk
                && fr.wakfu.rpg.core.ZoneData.isBlocked(target.serverLevel(), target.blockPosition(), atk)) {
            e.setCanceled(true);
            atk.displayClientMessage(net.minecraft.network.chat.Component.literal(
                    "§cPvP interdit dans cette zone protégée."), true);
        }
    }

    /** v0.13 : monstres PAR ZONES — grille 3x3 de secteurs de 64 blocs autour du
     *  spawn. Cible stable par secteur : 2 ou 3 monstres. Jamais 0 (respawn
     *  garanti), respawn graduel (1 max par secteur et par cycle), parfois en
     *  groupes de 3 (serres les uns contre les autres, combat en vagues). */
    private static final int SECTOR = 64;
    private static final java.util.Random MOB_RNG = new java.util.Random();

    @SubscribeEvent
    public void onServerStartedMobs(net.minecraftforge.event.server.ServerStartedEvent event) {
        var level = event.getServer().overworld();
        if (level == null) return;
        var spawn = level.getSharedSpawnPos();
        for (int cx = (spawn.getX() - 112) >> 4; cx <= (spawn.getX() + 112) >> 4; cx++) {
            for (int cz = (spawn.getZ() - 112) >> 4; cz <= (spawn.getZ() + 112) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        // anciens monstres : rafraichit le nom avec niveau
        for (var m : level.getEntitiesOfClass(fr.wakfu.rpg.entity.MonsterNpc.class,
                new net.minecraft.world.phys.AABB(spawn).inflate(128))) {
            m.refreshName();
        }
        // v0.21 : plus de spawn naturel vanilla (le systeme gere tout) +
        //         nettoyage des mobs sur les toits et des mobs vanilla residuels
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING)
                .set(false, level.getServer());
        cleanOffStreet(level, spawn);
        for (int sx = -1; sx <= 1; sx++) {
            for (int sz = -1; sz <= 1; sz++) {
                topUpSector(level, spawn, sx, sz, true);
            }
        }
        // v0.14 : donjon a l'est du village
        fr.wakfu.rpg.core.Dungeon.ensure(level, true);
    }

    private static int sectorTarget(int sx, int sz) {
        int h = Math.abs(sx * 31 + sz * 17);
        return 3 + h % 2;   // v0.21 : 3 ou 4 selon la zone (stable)
    }

    private static int countSector(ServerLevel level, int ox, int oz, int sx, int sz) {
        int x0 = ox + sx * SECTOR, z0 = oz + sz * SECTOR;
        var box = new net.minecraft.world.phys.AABB(x0, level.getMinBuildHeight(), z0,
                x0 + SECTOR, level.getMaxBuildHeight(), z0 + SECTOR);
        int n = 0;
        for (var m : level.getEntitiesOfClass(fr.wakfu.rpg.entity.MonsterNpc.class, box)) {
            if (!m.getTags().contains("arena_copy") && !m.getTags().contains("summon")) n++;
        }
        return n;
    }

    private static void topUpSector(ServerLevel level, net.minecraft.core.BlockPos spawn,
                                    int sx, int sz, boolean initial) {
        int ox = spawn.getX() & ~63, oz = spawn.getZ() & ~63;
        int ground = streetLevel(level, spawn);   // v0.24 : plafond absolu anti-toits
        int count = countSector(level, ox, oz, sx, sz);
        int target = sectorTarget(sx, sz);
        if (count == 0) {
            // jamais 0 : un spawn immediat, 35% de chances que ce soit un groupe
            if (MOB_RNG.nextInt(100) < 35) spawnGroup(level, ox + sx * SECTOR, oz + sz * SECTOR, ground);
            else spawnOne(level, ox + sx * SECTOR, oz + sz * SECTOR, ground);
            return;
        }
        if (count < target) {
            if (initial) {
                while (count < target) {
                    spawnOne(level, ox + sx * SECTOR, oz + sz * SECTOR, ground);
                    count++;
                }
            } else if (MOB_RNG.nextInt(100) < 65) {
                spawnOne(level, ox + sx * SECTOR, oz + sz * SECTOR, ground);  // graduel : 1 max/cycle
            }
        }
    }

    private static boolean validSpawn(ServerLevel level, int x, int y, int z, int ground) {
        var pos = new net.minecraft.core.BlockPos(x, y, z);
        if (!level.isLoaded(pos)) return false;
        if (!level.getBlockState(pos).getFluidState().isEmpty()) return false; // pas sous l'eau
        if (!level.canSeeSky(pos)) return false;                               // rue a ciel ouvert
        if (y > ground + 6 || y < ground - 10) return false;   // v0.24 : plafond de la rue
        // ni toit ni trou : le sol doit rester proche du niveau le plus bas du
        // voisinage (la rue). Un toit depasse largement ses voisins.
        int m = y;
        for (int dx = -6; dx <= 6; dx += 6) {
            for (int dz = -6; dz <= 6; dz += 6) {
                if (dx == 0 && dz == 0) continue;
                m = Math.min(m, level.getHeight(
                        net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x + dx, z + dz));
            }
        }
        return y <= m + 3;
    }

    /** v0.21 : retire les monstres hors rue (toits, balcons) et tout monstre
     *  vanilla residuel. Copies d'arene et invocations non touchees. */
    /** v0.24 : niveau de la rue = PLUS BAS des hauteurs autour d'un point
     *  (insensible aux structures/toits au point de spawn). */
    private static int streetLevel(ServerLevel level, net.minecraft.core.BlockPos c) {
        int g = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types
                .MOTION_BLOCKING, c.getX(), c.getZ());
        for (int dx = -10; dx <= 10; dx += 5) {
            for (int dz = -10; dz <= 10; dz += 5) {
                g = Math.min(g, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types
                        .MOTION_BLOCKING, c.getX() + dx, c.getZ() + dz));
            }
        }
        return g;
    }

    private static void cleanOffStreet(ServerLevel level, net.minecraft.core.BlockPos spawn) {
        int ground = streetLevel(level, spawn);
        var box = new net.minecraft.world.phys.AABB(spawn).inflate(224);
        for (var m : level.getEntitiesOfClass(fr.wakfu.rpg.entity.MonsterNpc.class, box)) {
            if (m.getTags().contains("arena_copy") || m.getTags().contains("summon")) continue;
            if (m.getY() > ground + 6.5) m.discard();
        }
        for (var mo : level.getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, box)) {
            mo.discard();
        }
    }

    private static void spawnOne(ServerLevel level, int x0, int z0, int ground) {
        for (int tries = 0; tries < 16; tries++) {
            int x = x0 + 4 + MOB_RNG.nextInt(SECTOR - 8);
            int z = z0 + 4 + MOB_RNG.nextInt(SECTOR - 8);
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
            if (!validSpawn(level, x, y, z, ground)) continue;
            spawnMobAt(level, x, y, z);
            return;
        }
    }

    /** Groupe de 3 monstres serres (combat en vagues ensuite). */
    private static void spawnGroup(ServerLevel level, int x0, int z0, int ground) {
        for (int tries = 0; tries < 12; tries++) {
            int gx = x0 + 8 + MOB_RNG.nextInt(SECTOR - 16);
            int gz = z0 + 8 + MOB_RNG.nextInt(SECTOR - 16);
            int gy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, gx, gz);
            if (!validSpawn(level, gx, gy, gz, ground)) continue;
            spawnMobAt(level, gx, gy, gz);
            int placed = 1;
            for (int i = 0; i < 6 && placed < 3; i++) {
                int mx = gx + MOB_RNG.nextInt(7) - 3;
                int mz = gz + MOB_RNG.nextInt(7) - 3;
                int my = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, mx, mz);
                if (!validSpawn(level, mx, my, mz, ground)) continue;
                spawnMobAt(level, mx, my, mz);
                placed++;
            }
            return;
        }
        spawnOne(level, x0, z0, ground);
    }

    private static void spawnMobAt(ServerLevel level, int x, int y, int z) {
        // v0.23 : zones par distance — plaine (zombies Nv.1-4), carrieres
        // (squelettes Nv.4-9), grand chemin (bandits Nv.7-13). Le niveau
        // affiche = niveau JOUEUR conseille. Variante visuelle ponderee.
        var sp0 = level.getSharedSpawnPos();
        double dx = x - sp0.getX(), dz = z - sp0.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        String baseId = dist < 96 ? "momic" : (dist < 200 ? "skeleton" : "bandit");
        if (MOB_RNG.nextInt(100) < 12) {
            baseId = new String[]{"momic", "skeleton", "bandit"}[MOB_RNG.nextInt(3)];
        }
        var type = fr.wakfu.rpg.core.MonsterType.byId(baseId);
        var v = fr.wakfu.rpg.core.MobVariants.roll(baseId, MOB_RNG);
        int lvl = v.minLvl + MOB_RNG.nextInt(v.maxLvl - v.minLvl + 1);
        var m = new fr.wakfu.rpg.entity.MonsterNpc(fr.wakfu.rpg.registry.ModEntities.MONSTER.get(), level);
        m.init(type, lvl, v.id);
        m.moveTo(x + 0.5, y, z + 0.5, MOB_RNG.nextFloat() * 360, 0);
        m.setHome(x, y, z);
        level.addFreshEntity(m);
    }

    /** v0.21 : respawn periodique graduel — toutes les 60 s, 1 max par zone
     *  sous-peuplee, + nettoyage des toits a chaque cycle. */
    @SubscribeEvent
    public void onServerTickMobRespawn(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        var server = event.getServer();
        if (server.getTickCount() % 1200 != 0) return;
        var level = server.overworld();
        if (level == null) return;
        var spawn = level.getSharedSpawnPos();
        cleanOffStreet(level, spawn);   // v0.21 : jamais de mobs sur les toits
        // v0.14 : entretien du donjon (boss/gardes) — independant des joueurs proches
        fr.wakfu.rpg.core.Dungeon.ensure(level, false);
        boolean near = false;
        for (var pl : server.getPlayerList().getPlayers()) {
            if (pl.level() == level
                    && pl.distanceToSqr(spawn.getX(), spawn.getY(), spawn.getZ()) < 160 * 160) {
                near = true;
                break;
            }
        }
        if (!near) return;
        for (int sx = -1; sx <= 1; sx++) {
            for (int sz = -1; sz <= 1; sz++) {
                topUpSector(level, spawn, sx, sz, false);
            }
        }
    }
}
