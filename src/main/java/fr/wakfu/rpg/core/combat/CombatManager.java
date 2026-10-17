package fr.wakfu.rpg.core.combat;

import fr.wakfu.rpg.core.ModCapabilities;
import fr.wakfu.rpg.core.MonsterType;
import fr.wakfu.rpg.core.combat.MobSkill;
import fr.wakfu.rpg.core.PlayerRpgData;
import fr.wakfu.rpg.core.spell.Spell;
import fr.wakfu.rpg.core.spell.Spells;
import fr.wakfu.rpg.core.spell.ZoneType;
import fr.wakfu.rpg.entity.MonsterNpc;
import fr.wakfu.rpg.item.CombatUtilityItem;
import fr.wakfu.rpg.network.CombatStatePacket;
import fr.wakfu.rpg.network.Network;
import fr.wakfu.rpg.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Moteur du combat tour par tour façon Wakfu (serveur) :
 * arene instanciee dans une dimension dediee (vide = chargement instantane,
 * toujours jour, jamais de pluie), deplacement par cases (PM), sorts a
 * zones (PA), camera vue de haut, duels PvP 1v1.
 * Le monstre du monde n'est PAS deplace : on en cree une copie dans
 * l'arene (ses PV sont reportes, puis recopies a la fin).
 */
public class CombatManager {
    // v0.27 : map MonsterType -> alexsmobs entity registry name
    private static final java.util.Map<MonsterType, String> ALEX_ENTITY_MAP = new java.util.HashMap<>();
    static {
        ALEX_ENTITY_MAP.put(MonsterType.GRIZZLY, "alexsmobs:grizzly_bear");
        ALEX_ENTITY_MAP.put(MonsterType.GAZELLE, "alexsmobs:gazelle");
        ALEX_ENTITY_MAP.put(MonsterType.RATTLESNAKE, "alexsmobs:rattlesnake");
        ALEX_ENTITY_MAP.put(MonsterType.ELEPHANT, "alexsmobs:elephant");
        ALEX_ENTITY_MAP.put(MonsterType.GORILLA, "alexsmobs:gorilla");
        ALEX_ENTITY_MAP.put(MonsterType.FALCON, "alexsmobs:roadrunner");
        ALEX_ENTITY_MAP.put(MonsterType.KOMODO_DRAGON, "alexsmobs:komodo_dragon");
        ALEX_ENTITY_MAP.put(MonsterType.MOOSE, "alexsmobs:moose");
        ALEX_ENTITY_MAP.put(MonsterType.TIGER, "alexsmobs:tiger");
        ALEX_ENTITY_MAP.put(MonsterType.CROW, "alexsmobs:crow");
    }



    /** v0.27 : get combat by player UUID. */
    public static Combat getCombat(UUID playerId) { return COMBATS.get(playerId); }

    private static final ConcurrentHashMap<UUID, Combat> COMBATS = new ConcurrentHashMap<>();
    /** v0.19 : combats par cle unique (invitations de groupe). */
    private static final ConcurrentHashMap<Integer, Combat> BY_KEY = new ConcurrentHashMap<>();
    private static final AtomicInteger KEY_GEN = new AtomicInteger(1);
    private static final Random RANDOM = new Random();

    public static Combat combatByKey(int key) {
        return BY_KEY.get(key);
    }

    private static ServerPlayer member(Combat combat, UUID u) {
        return combat.level.getServer().getPlayerList().getPlayer(u);
    }

    private static ServerPlayer activePlayer(Combat combat) {
        UUID u = combat.activeMember();
        return u == null ? null : member(combat, u);
    }

    public static boolean inCombat(UUID player) {
        return COMBATS.containsKey(player);
    }

    public static Combat get(UUID player) {
        return COMBATS.get(player);
    }

    // ------------------------------------------------------------ slot d'arene libre
    private static int nextFreeSlot() {
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (Combat c : COMBATS.values()) used.add(c.arenaSlot);
        for (int i = 0; i < 64; i++) {
            if (!used.contains(i)) return i;
        }
        return 0;
    }

    // ------------------------------------------------------------ entree PvE
    public static void startCombat(ServerPlayer player, MonsterNpc monster) {
        if (inCombat(player.getUUID())) return;
        if (monsterIsBusy(monster)) {
            hint(player, "§7Ce monstre est déjà occupé dans un combat.");
            return;
        }
        fr.wakfu.rpg.core.Codex.encounter(player, monster.getMonsterType().getId());
        player.getCapability(ModCapabilities.RPG).ifPresent(data -> {
            if (!data.hasClass()) return;
            int slot = nextFreeSlot();
            // v0.19 : theme/taille d'arene selon la zone + groupe de joueurs
            int theme = Arena.themeFor(player.serverLevel(),
                    player.blockPosition().getX(), player.blockPosition().getZ());
            Combat combat = buildCombat(player, null, slot, theme);
            if (combat == null) return;
            fr.wakfu.rpg.core.PartyManager.Party party =
                    fr.wakfu.rpg.core.PartyManager.partyOf(player.getUUID());
            int partySize = party == null ? 1 : party.members.size();

            // v0.19 : les allies proches (10 blocs) sont affrontes TOUS EN MEME TEMPS
            java.util.List<MonsterNpc> grp = new java.util.ArrayList<>();
            grp.add(monster);
            for (MonsterNpc other : monster.level().getEntitiesOfClass(MonsterNpc.class,
                    new AABB(monster.blockPosition()).inflate(10))) {
                if (other == monster || other.getTags().contains("arena_copy")) continue;
                if (monsterIsBusy(other) || grp.size() >= 6) continue;
                grp.add(other);
            }
            int target = partySize >= 2
                    ? Math.min(6, partySize + 1)
                    : Math.min(2, Math.max(1, grp.size()));
            spawnMobs(combat, grp, target, partySize >= 2);

            COMBATS.put(player.getUUID(), combat);
            BY_KEY.put(combat.key, combat);
            org.apache.logging.log4j.LogManager.getLogger("wakfurpg").info("Combat PvE demarre : slot={} theme={} taille={} mobs={}",
                    slot, combat.theme, combat.size, combat.mobs.size());
            enterSide(player, combat, 0);
            msg(player, "§c⚔ Le combat commence ! Cases §avertes§c = portée, §e1-4§c : sorts, §eR§c = fin du tour.");
            if (combat.mobs.size() > 1) {
                msg(player, "§6⚔ §c" + combat.mobs.size() + " adversaires§6 simultanés !");
            }
            // v0.19 : invite le groupe a rejoindre (jusqu'au premier mouvement)
            if (party != null && partySize > 1) inviteParty(combat, player, party);
            sendStateAll(combat);
        });
    }

    private static boolean monsterIsBusy(MonsterNpc monster) {
        for (Combat c : COMBATS.values()) {
            for (int id : c.mobOrigins) {
                if (id == monster.getId()) return true;
            }
        }
        return false;
    }

    /** v0.19 : spawn PLUSIEURS copies de monstres en meme temps (cote B).
     *  origins : monstres du monde collectes ; clones = renforts du meme type
     *  quand le groupe de joueurs depasse les monstres presents. */
    private static void spawnMobs(Combat combat, List<MonsterNpc> origins, int target, boolean allowClones) {
        List<MonsterNpc> types = new ArrayList<>();
        List<Integer> origIds = new ArrayList<>();
        for (MonsterNpc o : origins) {
            if (types.size() >= target) break;
            types.add(o);
            origIds.add(o.getId());
        }
        int guard = 0;
        while (allowClones && types.size() < target && !origins.isEmpty() && guard++ < 8) {
            types.add(origins.get(RANDOM.nextInt(origins.size())));
            origIds.add(-1);
        }
        int size = combat.size;
        for (int i = 0; i < types.size(); i++) {
            MonsterNpc origin = types.get(i);
            MonsterNpc copy = new MonsterNpc(ModEntities.MONSTER.get(), combat.level);
            copy.init(origin.getMonsterType(), origin.getLevel(), origin.getVariant());
            copy.addTag("arena_copy");
            if (origIds.get(i) >= 0) {
                int missing = origin.maxHpFor() - origin.getCurrentHp();
                if (missing > 0) copy.hurtCombat(missing);
            }
            // repartition sur les deux dernieres colonnes jouables
            BlockPos cell = null;
            for (int tries = 0; tries < 32 && cell == null; tries++) {
                int rx = size - 3 - (tries % 2);
                int rz = 1 + (i * 3 + tries) % (size - 2);
                BlockPos c = new BlockPos(combat.baseX + rx, combat.floorY, combat.baseZ + rz);
                if (combat.isPlayable(c) && !occupiedByUnit(combat, c, null)) cell = c;
            }
            if (cell == null) {
                cell = new BlockPos(combat.baseX + size - 3, combat.floorY, combat.baseZ + size / 2);
            }
            copy.moveTo(cell.getX() + 0.5, combat.floorY, cell.getZ() + 0.5, 0, 0);
            copy.setInvisible(true);
            combat.level.addFreshEntity(copy);
            combat.mobs.add(copy.getId());

            // v0.27 : spawn real Alex's Mobs entity as visual
            int visualId = -1;
            String alexId = ALEX_ENTITY_MAP.get(copy.getMonsterType());
            if (alexId != null) {
                EntityType<?> alexType = ForgeRegistries.ENTITY_TYPES.getValue(
                    new net.minecraft.resources.ResourceLocation(alexId));
                if (alexType != null) {
                    Entity visual = alexType.create(combat.level);
                    if (visual != null) {
                        visual.moveTo(cell.getX() + 0.5, combat.floorY, cell.getZ() + 0.5, 0, 0);
                        visual.setInvulnerable(true);
                        visual.setNoGravity(true);
                        if (visual instanceof Mob mob) {
                            mob.setNoAi(true);
                            mob.setPersistenceRequired();
                        }
                        visual.addTag("alex_visual");
                        combat.level.addFreshEntity(visual);
                        visualId = visual.getId();
                    }
                }
            }
            combat.visualEntityIds.add(visualId);
            combat.mobOrigins.add(origIds.get(i));
        }
        combat.monsterId = combat.mobs.isEmpty() ? -1 : combat.mobs.get(0);
        combat.monsterOriginId = combat.mobOrigins.isEmpty() ? -1 : combat.mobOrigins.get(0);
        combat.cellB = combat.firstMobCell();
    }

    // ------------------------------------------------------------ v0.19 : groupe — invitations
    /** Premiere action reelle : la fenetre d'invitation se ferme et les notifs sautent. */
    public static void lockJoin(Combat combat) {
        if (!combat.joinable) return;
        combat.joinable = false;
        for (UUID u : combat.invited) {
            ServerPlayer ip = combat.level.getServer().getPlayerList().getPlayer(u);
            if (ip != null) {
                Network.sendToPlayer(ip, new fr.wakfu.rpg.network.CombatInvitePacket(combat.key, true, "", 0));
            }
        }
        combat.invited.clear();
        if (combat.alive()) {
            broadcast(combat, "§8Le combat a commencé — il n'est plus possible de le rejoindre.");
        }
    }

    private static void inviteParty(Combat combat, ServerPlayer starter, fr.wakfu.rpg.core.PartyManager.Party party) {
        for (UUID u : party.members) {
            if (u.equals(starter.getUUID())) continue;
            ServerPlayer mp2 = combat.level.getServer().getPlayerList().getPlayer(u);
            if (mp2 == null || inCombat(u) || !hasClass(mp2)) continue;
            combat.invited.add(u);
            Network.sendToPlayer(mp2, new fr.wakfu.rpg.network.CombatInvitePacket(
                    combat.key, false, starter.getName().getString(), combat.mobs.size()));
            mp2.sendSystemMessage(Component.literal("§6⚔ " + starter.getName().getString()
                            + " engage un combat (" + combat.mobs.size() + " monstres) ! ")
                    .append(Component.literal("§a[Rejoindre]")
                            .withStyle(st2 -> st2
                                    .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                            net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                            "/groupe rejoindre " + combat.key))
                                    .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                            net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                            Component.literal("Rejoindre le combat de " + starter.getName().getString())))))
                    .append(Component.literal(" §7— l'invitation expire au premier mouvement.")));
        }
    }

    /** Un membre invite rejoint le combat AVANT la premiere action. */
    public static void joinCombat(ServerPlayer p, int key) {
        Combat combat = BY_KEY.get(key);
        if (combat == null || !combat.joinable) {
            msg(p, "§cCe combat a déjà commencé ou est terminé.");
            return;
        }
        if (inCombat(p.getUUID())) {
            msg(p, "§cTu es déjà en combat.");
            return;
        }
        if (!hasClass(p)) {
            msg(p, "§cTu n'as pas encore choisi de classe.");
            return;
        }
        if (combat.pvp()) {
            msg(p, "§cImpossible de rejoindre un duel.");
            return;
        }
        if (combat.members.size() >= 4) {
            msg(p, "§cLe groupe de combat est plein (4 maximum).");
            return;
        }
        if (!combat.invited.contains(p.getUUID())) {
            msg(p, "§cTu n'as pas été invité à ce combat.");
            return;
        }
        combat.invited.remove(p.getUUID());
        combat.members.add(p.getUUID());
        COMBATS.put(p.getUUID(), combat);
        combat.joinTicks.put(p.getUUID(), (long) p.getServer().getTickCount());
        enterSide(p, combat, 0);
        broadcastArenaBlocks(p, combat);
        broadcast(combat, "§a✚ " + p.getName().getString() + " rejoint le combat !");
        msg(p, "§aTu rejoins le combat ! Attends ton tour (les alliés jouent dans l'ordre).");
        sendStateAll(combat);
    }

    public static void declineInvite(ServerPlayer p, int key) {
        Combat combat = BY_KEY.get(key);
        if (combat != null) combat.invited.remove(p.getUUID());
    }

    // ------------------------------------------------------------ duel PvP
    public static void requestDuel(ServerPlayer a, ServerPlayer b) {
        if (a == null || b == null) return;
        if (a == b) {
            msg(a, "§7Tu ne peux pas te défier toi-même.");
            return;
        }
        if (inCombat(a.getUUID()) || inCombat(b.getUUID())) {
            msg(a, "§cUn des deux joueurs est déjà en combat.");
            return;
        }
        if (!hasClass(a)) {
            msg(a, "§cTu n'as pas encore choisi de classe (parle au Maître des classes).");
            return;
        }
        if (!hasClass(b)) {
            msg(a, "§c" + b.getName().getString() + " n'a pas encore choisi de classe.");
            return;
        }
        startDuel(a, b);
    }

    public static void startDuel(ServerPlayer a, ServerPlayer b) {
        int slot = nextFreeSlot();
        int theme = Arena.themeFor(a.serverLevel(), a.blockPosition().getX(), a.blockPosition().getZ());
        Combat combat = buildCombat(a, b, slot, theme);
        if (combat == null) return;
        combat.cellB = new BlockPos(combat.baseX + combat.size - 4, combat.floorY, combat.baseZ + combat.size / 2);
        combat.joinable = false;   // un duel ne se rejoint pas
        COMBATS.put(a.getUUID(), combat);
        COMBATS.put(b.getUUID(), combat);
        BY_KEY.put(combat.key, combat);
        enterSide(a, combat, 0);
        enterSide(b, combat, 1);
        msg(a, "§c⚔ Duel contre §f" + b.getName().getString() + " §c! À toi de jouer.");
        msg(b, "§c⚔ §f" + a.getName().getString() + " §cte défie en duel !");
        sendStateAll(combat);
    }

    // ------------------------------------------------------------ preparation commune
    private static Combat buildCombat(ServerPlayer a, ServerPlayer b, int slot, int theme) {
        // v0.8 : cocon overworld AU-DESSUS du lieu du combat (memes chunks,
        // aucune sync de dimension/chunks lointains a envoyer au client)
        ServerLevel ow = a.serverLevel();
        int cx = net.minecraft.util.Mth.floor(a.getX());
        int cz = net.minecraft.util.Mth.floor(a.getZ());
        int surf = ow.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, cx, cz);
        int floorY = Math.min(190, Math.max(surf + 24, net.minecraft.util.Mth.floor(a.getY()) + 20));
        // evite la superposition avec un combat deja en cours a proximite
        int size = Arena.sizeFor(theme);
        int half = size / 2;
        for (int off : new int[]{0, 32, -32, 64, -64, 96, -96, 128, -128}) {
            int tx = cx + off;
            boolean free = true;
            for (Combat c : COMBATS.values()) {
                if (Math.abs(c.baseX + c.size / 2 - tx) < c.size / 2 + half + 10
                        && Math.abs(c.baseZ + c.size / 2 - cz) < c.size / 2 + half + 10) {
                    free = false;
                    break;
                }
            }
            if (free) {
                cx = tx;
                break;
            }
        }
        int baseX = cx - half;
        int baseZ = cz - half;
        long seed = (long) baseX * 31L + (long) baseZ * 17L + floorY;
        java.util.Set<BlockPos> walls = Arena.ensureAt(ow, baseX, floorY, baseZ, theme, seed);

        // purge d'eventuels restes d'un combat interrompu (crash/redemarrage)
        AABB box = new AABB(cx - half - 2, floorY - 2, cz - half - 2, cx + half + 2, floorY + 9, cz + half + 2);
        for (MonsterNpc m : ow.getEntitiesOfClass(MonsterNpc.class, box)) m.discard();
        for (ArmorStand s : ow.getEntitiesOfClass(ArmorStand.class, box)) {
            if (s.isInvisible()) s.discard();
        }

        // camera vue de haut : armor stand invisible sous le toit
        // v0.27 : camera Y adapte a la taille de l'arene
        int camHeight = Math.max(16, size + 8);
        ArmorStand stand = new ArmorStand(ow, cx + 0.5, floorY + camHeight, cz + 0.5);
        stand.setInvisible(true);
        stand.setNoGravity(true);
        stand.setInvulnerable(true);
        stand.setNoBasePlate(true);
        stand.setYRot(180);
        ow.addFreshEntity(stand);

        Combat combat = new Combat(ow, a.getUUID(), b == null ? null : b.getUUID(),
                slot, baseX, baseZ, floorY, size, stand.getId());
        combat.enterTick = a.getServer().getTickCount();
        combat.theme = theme;
        combat.walls.addAll(walls);
        combat.key = KEY_GEN.getAndIncrement();
        combat.cells.put(a.getUUID(), new BlockPos(baseX + 2, combat.floorY, baseZ + size / 2));
        combat.apmp.put(a.getUUID(), new int[]{maxApFor(a), maxMpFor(a)});
        if (b != null) {
            combat.apmp.put(b.getUUID(), new int[]{maxApFor(b), maxMpFor(b)});
        }
        return combat;
    }

    private static int maxApFor(ServerPlayer p) {
        return p.getCapability(ModCapabilities.RPG).map(d -> d.maxAp() + armorBonusAp(p)).orElse(0);
    }

    private static int maxMpFor(ServerPlayer p) {
        return p.getCapability(ModCapabilities.RPG).map(d -> d.maxMp() + armorBonusMp(p)).orElse(0);
    }

    private static void enterSide(ServerPlayer p, Combat combat, int side) {
        p.getCapability(ModCapabilities.RPG).ifPresent(data -> {
            BlockPos ret = p.blockPosition();
            data.setArenaReturn(ret.getX(), ret.getY(), ret.getZ());
            data.setInArena(true);
            ListTag saved = new ListTag();
            p.getInventory().save(saved);
            data.setSavedInv(saved);
            // armure + arme en main conservees, le reste masque
            List<ItemStack> armor = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++) armor.add(p.getInventory().getArmor(i).copy());
            ItemStack weapon = p.getMainHandItem().copy();
            boolean hasWeapon = fr.wakfu.rpg.item.WakfuWeaponItem.isWeapon(weapon);
            p.getInventory().clearContent();
            for (int i = 0; i < 4; i++) p.getInventory().armor.set(i, armor.get(i));
            if (hasWeapon) {
                p.getInventory().items.set(p.getInventory().selected, weapon);
            }
            // garder le chunk de retour chaud pendant le combat (retour plus rapide)
            ServerLevel ow = p.getServer().overworld();
            ow.setChunkForced(ret.getX() >> 4, ret.getZ() >> 4, true);
            // jamais de pluie pendant un combat (donnee meteo partagee avec l'overworld)
            if (combat.level.isRaining()) {
                combat.level.setWeatherParameters(6000, 0, false, false);
            }
            // v0.19 : cellule par joueur (rejoint un groupe en cours = spawn libre)
            BlockPos cell = combat.cells.get(p.getUUID());
            if (cell == null) {
                cell = side == 1 ? combat.cellB : freeMemberSpawn(combat);
            }
            combat.cells.put(p.getUUID(), cell);
            combat.apmp.put(p.getUUID(), new int[]{maxApFor(p), maxMpFor(p)});
            p.teleportTo(combat.level, cell.getX() + 0.5, combat.floorY, cell.getZ() + 0.5, p.getYRot(), p.getXRot());
            combat.setAnchorFor(p.getUUID(), cell.getX() + 0.5, cell.getZ() + 0.5);
            org.apache.logging.log4j.LogManager.getLogger("wakfurpg").info("TP {} apres entree : dim={} pos={} solSousPieds={}",
                    p.getName().getString(), p.level().dimension().location(), p.blockPosition(),
                    combat.level.getBlockState(cell.below()).getBlock());
        });
    }

    /** v0.19 : cellule de spawn libre pour un membre du groupe (colonne du camp A). */
    private static BlockPos freeMemberSpawn(Combat combat) {
        int halfZ = combat.size / 2;
        for (int k = 0; k < combat.size * 2; k++) {
            int zz = halfZ + (k % 2 == 0 ? k / 2 : -(k + 1) / 2);
            int xx = 2 + (k / (combat.size * 2));
            BlockPos c = new BlockPos(combat.baseX + Math.min(xx, 3), combat.floorY, combat.baseZ + zz);
            if (combat.isPlayable(c) && !occupiedByUnit(combat, c, null)) return c;
        }
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z < combat.size - 1; z++) {
                BlockPos c = new BlockPos(combat.baseX + x, combat.floorY, combat.baseZ + z);
                if (combat.isPlayable(c) && !occupiedByUnit(combat, c, null)) return c;
            }
        }
        return new BlockPos(combat.baseX + 2, combat.floorY, combat.baseZ + halfZ);
    }

    // ------------------------------------------------------------ actions (paquets client)
    public static void castAtCell(ServerPlayer p, String spellId, int cellX, int cellZ) {
        Combat combat = COMBATS.get(p.getUUID());
        if (combat == null) return;
        int side = combat.sideOf(p.getUUID());
        if (side < 0) return;
        if (!combat.isTurn(side)) {
            hint(p, "§7Attends ton tour.");
            return;
        }
        // v0.19 : en groupe, un seul membre agit a la fois
        if (side == 0 && !p.getUUID().equals(combat.activeMember())) {
            hint(p, "§7Patience — c'est au tour d'un allié.");
            return;
        }
        PlayerRpgData data = cap(p);
        if (data == null || !data.hasClass()) return;
        if (!data.getEquipped().contains(spellId)) {
            hint(p, "§cSort non équipé (touche K pour choisir tes 4 sorts).");
            return;
        }
        Spell s = Spells.byId(spellId);
        if (s == null) return;
        if (s.apCost > combat.apOf(side)) {
            hint(p, "§cPas assez de PA (" + s.apCost + " requis).");
            return;
        }
        BlockPos self = combat.cells.get(p.getUUID());
        if (self == null) return;
        BlockPos target = new BlockPos(cellX, combat.floorY, cellZ);
        if (!s.validTarget(self, target)) {
            hint(p, "§cCible hors portée.");
            return;
        }
        // v0.19 : murs bloquants — ligne de vue requise sauf sorts percants
        if (s.needsLos() && !hasLos(combat, self, target)) {
            hint(p, "§cUn mur bloque la trajectoire !");
            return;
        }
        // v0.16 : sort verrouille tant que le niveau requis n'est pas atteint
        if (data.getLevel() < s.unlockLevel) {
            hint(p, "§7Sort verrouillé : niveau " + s.unlockLevel + " requis.");
            return;
        }
        // v0.14 : verifs des invocations AVANT de depenser les PA
        if (s.summon != null) {
            purgeSummons(combat, side);
            if (combat.summonsOf(side).size() >= 2) {
                hint(p, "§cTu as déjà 2 invocations actives.");
                return;
            }
            if (freeAdjacentCell(combat, self) == null) {
                hint(p, "§cPas de case libre autour de toi pour invoquer.");
                return;
            }
        }
        // v0.18 : mecaniques speciales (bond / piege / vol de vie)
        if (s.mechanic != null) {
            int mdmg = s.damage + 2 * data.spellLevel(s.id) + RANDOM.nextInt(3) + armorBonusDamage(p);
            if (s.mechanic.equals("piege")) {
                if (!combat.isPlayable(target) || occupiedByUnit(combat, target, null)) {
                    hint(p, "§7Le piège doit être posé sur une case libre.");
                    return;
                }
                int mine = 0;
                for (int[] t : combat.traps) if (t[2] == side) mine++;
                if (mine >= 3) {
                    hint(p, "§cTu as déjà 3 pièges actifs.");
                    return;
                }
                lockJoin(combat);
                combat.setAp(side, combat.apOf(side) - s.apCost);
                combat.traps.add(new int[]{target.getX(), target.getZ(), side, mdmg});
                broadcast(combat, "§e" + p.getName().getString() + " pose §f" + s.getName().getString() + "§e !");
                combat.level.playSound(null, target, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.PLAYERS, 0.9f, 1.3f);
                sendFxId(combat, "trap_place", self, List.of(target));
                sendStateAll(combat);
                return;
            }
            // bond / vol : cible une unite ennemie
            Object foe = enemyUnitAt(combat, side, target);
            if (foe == null) {
                hint(p, "§7Vise une case occupée par un ennemi.");
                return;
            }
            BlockPos land = null;
            if (s.mechanic.equals("bond")) {
                land = freeAdjacentCell(combat, target);
                if (land == null || land.equals(self)) {
                    hint(p, "§cPas de case d'atterrissage libre autour de la cible.");
                    return;
                }
            }
            lockJoin(combat);
            combat.setAp(side, combat.apOf(side) - s.apCost);
            if (land != null) {
                p.teleportTo(land.getX() + 0.5, combat.floorY, land.getZ() + 0.5);
                combat.setCell(side, land);
                combat.setAnchor(side, land.getX() + 0.5, land.getZ() + 0.5);
                combat.moveGrace[side] = p.level().getGameTime() + 12;
                combat.level.playSound(null, land, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7f, 1.2f);
            }
            sendFx(combat, s, self, List.of(target));
            if (foe instanceof MonsterNpc fm) {
                fm.hurtCombat(mdmg);
                combat.level.playSound(null, fm.blockPosition(), hurtSound(fm.getMonsterType()), SoundSource.HOSTILE, 1f, 1f);
                broadcast(combat, "§e" + s.getName().getString() + " §rinflige §c" + mdmg + " §rdégâts à " + fm.getName().getString() + ".");
                if (s.mechanic.equals("vol")) healCaster(combat, p, mdmg / 2);
                if (fm.getCurrentHp() <= 0) {
                    if (fm.isSummon()) {
                        combat.summonsOf(1 - side).remove((Integer) fm.getId());
                        fm.discard();
                        broadcast(combat, "§7L'invocation est détruite !");
                    } else {
                        sendStateAll(combat);
                        mobDefeated(p, fm, combat);
                        return;
                    }
                }
            } else if (foe instanceof ServerPlayer fp) {
                int dealt = resistReduce(fp, mdmg);
                PlayerRpgData fd = cap(fp);
                if (fd != null) {
                    fd.setHp(fd.getHp() - dealt);
                    fr.wakfu.rpg.events.CommonEvents.sync(fp);
                }
                fp.level().playSound(null, fp.blockPosition(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1f, 1f);
                broadcast(combat, "§e" + s.getName().getString() + " §rinflige §c" + dealt + " §rdégâts à " + fp.getName().getString() + ".");
                if (s.mechanic.equals("vol")) healCaster(combat, p, dealt / 2);
                if (fd != null && fd.getHp() <= 0) {
                    sendStateAll(combat);
                    duelEnd(combat, side);
                    return;
                }
            }
            sendStateAll(combat);
            return;
        }
        lockJoin(combat);
        combat.setAp(side, combat.apOf(side) - s.apCost);
        // v0.12 : combo = sorts enchaines dans le meme tour
        combat.casts[side]++;
        combat.spellsOf(side).add(spellId);
        int combo = combat.casts[side];
        playSpellSound(combat, s, target);
        // v0.14 : soin (degats negatifs)
        if (s.damage < 0) {
            int heal = Math.max(1, -s.damage + 2 * data.spellLevel(s.id) + RANDOM.nextInt(3));
            data.setHp(Math.min(data.maxHp() + armorBonusHp(p), data.getHp() + heal));
            fr.wakfu.rpg.events.CommonEvents.sync(p);
            broadcast(combat, "§a" + s.getName().getString() + " §2rend §a" + heal + " PV§2.");
            if (combat.spellsOf(side).size() == 3) {
                combat.setAp(side, combat.apOf(side) + 1);
                broadcast(combat, "§6Enchaînement parfait ! §e+1 PA");
            }
            sendFx(combat, s, self, List.of(self));
            sendStateAll(combat);
            return;
        }
        // v0.14 : invocation
        if (s.summon != null) {
            BlockPos cell = freeAdjacentCell(combat, self);
            MonsterNpc sum = new MonsterNpc(ModEntities.MONSTER.get(), combat.level);
            sum.init(MonsterType.byId(s.summon), Math.max(1, data.getLevel()));
            sum.addTag("summon");
            sum.setSummoner(p.getUUID());
            sum.moveTo(cell.getX() + 0.5, combat.floorY, cell.getZ() + 0.5, side == 0 ? 90 : 270, 0);
            combat.level.addFreshEntity(sum);
            combat.summonsOf(side).add(sum.getId());
            if (side == 0) fr.wakfu.rpg.core.Codex.encounter(p, s.summon);
            broadcast(combat, "§2" + p.getName().getString() + " invoque §f" + sum.getName().getString() + "§2 !");
            if (combo >= 2) broadcast(combat, "§dCombo x" + combo + " §!");
            if (combat.spellsOf(side).size() == 3) {
                combat.setAp(side, combat.apOf(side) + 1);
                broadcast(combat, "§6Enchaînement parfait ! §e+1 PA");
            }
            sendFx(combat, s, self, List.of(cell));
            sendStateAll(combat);
            return;
        }
        List<BlockPos> zone = truncateLine(combat, s, self, s.zoneCells(self, target));
        int dmg = s.damage + 2 * data.spellLevel(s.id) + RANDOM.nextInt(3) + armorBonusDamage(p);
        if (combo >= 2) {
            int pct = Math.min(45, 15 * (combo - 1));
            dmg = dmg * (100 + pct) / 100;
            broadcast(combat, "§dCombo x" + combo + " §r: +" + pct + "% dégâts !");
        }
        if (combat.spellsOf(side).size() == 3) {
            combat.setAp(side, combat.apOf(side) + 1);
            broadcast(combat, "§6Enchaînement parfait ! §e+1 PA");
        }
        applyZone(combat, side, zone, dmg, s);
        sendFx(combat, s, self, zone);
        if (COMBATS.containsKey(p.getUUID())) {
            sendStateAll(combat);
        }
    }

    private static void applyZone(Combat combat, int casterSide, List<BlockPos> zone, int dmg, Spell s) {
        // v0.19 : PvE — TOUS les monstres simultanes dans la zone sont touches
        if (casterSide == 0 && !combat.pvp()) {
            ServerPlayer caster = activePlayer(combat);
            for (int id : new ArrayList<>(combat.mobs)) {
                Entity e = combat.level.getEntity(id);
                if (!(e instanceof MonsterNpc m) || !m.isAlive()) continue;
                if (!zone.contains(m.blockPosition())) continue;
                m.hurtCombat(dmg);
                combat.level.playSound(null, m.blockPosition(), hurtSound(m.getMonsterType()), SoundSource.HOSTILE, 1f, 1f);
                broadcast(combat, "§e" + s.getName().getString() + " §rinflige §c" + dmg + " §rdégâts à " + m.getName().getString() + ".");
                if (m.getCurrentHp() <= 0 && caster != null) {
                    mobDefeated(caster, m, combat);
                    if (!combat.alive() || combat.mobs.isEmpty()) return;
                }
            }
        }
        // v0.14 : invocations adverses touchees par la zone
        List<Integer> enemySum = combat.summonsOf(1 - casterSide);
        for (int id : new java.util.ArrayList<>(enemySum)) {
            Entity se = combat.level.getEntity(id);
            if (!(se instanceof MonsterNpc sm)) {
                enemySum.remove((Integer) id);
                continue;
            }
            if (!zone.contains(sm.blockPosition())) continue;
            sm.hurtCombat(dmg);
            combat.level.playSound(null, sm.blockPosition(), SoundEvents.WOOD_HIT, SoundSource.HOSTILE, 0.9f, 1f);
            broadcast(combat, "§e" + s.getName().getString() + " §rinflige §c" + dmg + " §rà " + sm.getName().getString() + ".");
            if (sm.getCurrentHp() <= 0) {
                sm.discard();
                enemySum.remove((Integer) id);
                broadcast(combat, "§7L'invocation est détruite !");
            }
        }
        // PvP : joueur adverse
        if (combat.pvp()) {
            UUID oppUuid = casterSide == 0 ? combat.playerB : combat.playerA;
            BlockPos oppCell = combat.cellOf(1 - casterSide);
            if (zone.contains(oppCell)) {
                ServerPlayer opp = combat.level.getServer().getPlayerList().getPlayer(oppUuid);
                if (opp != null) {
                    PlayerRpgData od = cap(opp);
                    if (od != null) {
                        dmg = resistReduce(opp, dmg);
                        od.setHp(od.getHp() - dmg);
                        fr.wakfu.rpg.events.CommonEvents.sync(opp);
                        opp.level().playSound(null, opp.blockPosition(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1f, 1f);
                        broadcast(combat, "§e" + s.getName().getString() + " §rinflige §c" + dmg + " §rdégâts à " + opp.getName().getString() + ".");
                        if (od.getHp() <= 0) {
                            duelEnd(combat, casterSide);
                        }
                    }
                }
            }
        }
    }

    public static void moveToCell(ServerPlayer p, int cellX, int cellZ) {
        Combat combat = COMBATS.get(p.getUUID());
        if (combat == null) return;
        int side = combat.sideOf(p.getUUID());
        if (side < 0) return;
        if (!combat.isTurn(side)) {
            hint(p, "§7Attends ton tour.");
            return;
        }
        // (le gate membre actif est plus bas)
        if (side == 0 && !p.getUUID().equals(combat.activeMember())) {
            hint(p, "§7Patience — c'est au tour d'un allié.");
            return;
        }
        BlockPos target = new BlockPos(cellX, combat.floorY, cellZ);
        BlockPos self = combat.cells.get(p.getUUID());
        if (self == null) return;
        if (target.equals(self)) return;
        if (!combat.isPlayable(target)) return;
        if (occupiedByUnit(combat, target, p)) {
            hint(p, "§7Case occupée.");
            return;
        }
        // v0.19 : distance reelle du chemin (les murs bloquent)
        int dist = bfsDist(combat, self, target, combat.mpOf(side), p);
        if (dist < 0) {
            hint(p, "§cTrop loin ou inaccessible (" + combat.mpOf(side) + " PM restants).");
            return;
        }
        lockJoin(combat);
        combat.setMp(side, combat.mpOf(side) - dist);
        combat.cells.put(p.getUUID(), target);
        p.teleportTo(target.getX() + 0.5, combat.floorY, target.getZ() + 0.5);
        combat.setAnchorFor(p.getUUID(), target.getX() + 0.5, target.getZ() + 0.5);
        combat.moveGrace[side] = p.level().getGameTime() + 12; // animation client du deplacement
        p.level().playSound(null, target, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.4f, 1.4f);
        // v0.18 : piege ennemi sur la case d'arrivee ?
        int trapDmg = triggerTrap(combat, side, target);
        if (trapDmg > 0) {
            trapDmg = resistReduce(p, trapDmg);
            combat.level.playSound(null, target, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7f, 1.3f);
            sendFxId(combat, "trap_trigger", target, List.of(target));
            PlayerRpgData pd = cap(p);
            if (pd != null) {
                pd.setHp(pd.getHp() - trapDmg);
                fr.wakfu.rpg.events.CommonEvents.sync(p);
                msg(p, "§c💥 Piège ! Tu subis " + trapDmg + " dégâts.");
                if (pd.getHp() <= 0) {
                    knockout(p);
                    return;
                }
            }
        }
        sendStateAll(combat);
    }

    public static void endTurn(ServerPlayer p) {
        Combat combat = COMBATS.get(p.getUUID());
        if (combat == null) return;
        int side = combat.sideOf(p.getUUID());
        if (side < 0 || !combat.isTurn(side)) return;
        if (side == 0 && !p.getUUID().equals(combat.activeMember())) {
            hint(p, "§7Ce n'est pas ton tour.");
            return;
        }
        lockJoin(combat);
        if (!combat.pvp()) {
            // v0.19 : rotation des membres du groupe avant la phase des monstres
            if (combat.activeIdx < combat.members.size() - 1) {
                combat.activeIdx++;
                combat.resetTurn(0);
                UUID nu = combat.activeMember();
                ServerPlayer np = nu == null ? null : member(combat, nu);
                if (np != null) {
                    combat.apmp.put(nu, new int[]{maxApFor(np), maxMpFor(np)});
                    msg(np, "§6À ton tour !");
                }
                broadcast(combat, "§7Au tour de §f" + (np != null ? np.getName().getString() : "?") + "§7.");
                sendStateAll(combat);
                return;
            }
            combat.phase = Combat.Phase.SIDE_B;
            summonsAct(combat, 0);
            if (!combat.alive()) return;                       // fini pendant les invocations
            sendStateAll(combat);
            monsterPhase(combat);
        } else {
            summonsAct(combat, side);
            if (!COMBATS.containsKey(p.getUUID())) return;
            combat.phase = side == 0 ? Combat.Phase.SIDE_B : Combat.Phase.SIDE_A;
            if (combat.phase == Combat.Phase.SIDE_A) combat.round++;
            int next = 1 - side;
            ServerPlayer np = online(combat, next);
            if (np != null) {
                combat.setAp(next, maxApFor(np));
                combat.setMp(next, maxMpFor(np));
                combat.resetTurn(next);
                msg(np, "§6À ton tour !");
            }
            sendStateAll(combat);
        }
    }

    public static void flee(ServerPlayer p) {
        Combat combat = COMBATS.get(p.getUUID());
        if (combat == null) return;
        if (combat.pvp()) {
            int side = combat.sideOf(p.getUUID());
            if (side < 0) return;
            msg(p, "§7🏃 Tu abandonnes le duel.");
            duelEnd(combat, 1 - side);
        } else if (combat.members.size() > 1) {
            COMBATS.remove(p.getUUID());
            broadcast(combat, "§7🏃 " + p.getName().getString() + " fuit le combat !");
            leaveMember(combat, p, false);
            msg(p, "§7🏃 Tu fuis le combat. Tes alliés continuent sans toi.");
        } else {
            endCombat(p, "§7🏃 Tu fuis le combat. Retour à ta position d'origine.");
        }
    }

    // ------------------------------------------------------------ v0.19 : phase des monstres (tous, chacun leur tour)
    private static void monsterPhase(Combat combat) {
        combat.purgeMobs();
        for (int id : new ArrayList<>(combat.mobs)) {
            if (!combat.alive()) return;
            Entity e = combat.level.getEntity(id);
            if (!(e instanceof MonsterNpc m) || !m.isAlive()) continue;
            monsterAct(combat, m);
            if (!combat.alive()) return;
        }
        combat.purgeMobs();
        if (!combat.alive() || combat.mobs.isEmpty()) return;   // victoire geree ailleurs
        combat.round++;
        combat.phase = Combat.Phase.SIDE_A;
        combat.activeIdx = 0;
        combat.resetTurn(0);
        for (UUID u : combat.members) {
            ServerPlayer mp2 = member(combat, u);
            if (mp2 != null) combat.apmp.put(u, new int[]{maxApFor(mp2), maxMpFor(mp2)});
        }
        ServerPlayer act = activePlayer(combat);
        if (act != null) msg(act, "§6À ton tour.");
        if (combat.members.size() > 1 && act != null) {
            broadcast(combat, "§7Round §e" + combat.round + " §7— à §f" + act.getName().getString() + "§7 de jouer.");
        }
        sendStateAll(combat);
    }

    private static void monsterAct(Combat combat, MonsterNpc m) {
        MonsterType t = m.getMonsterType();
        BlockPos cur = m.blockPosition();
        // v0.27 : tick cooldowns + tentative de skill special
        MobSkill.tickCooldowns(m.getId());
        MobSkill skill = MobSkill.pickUsable(t, m.getId());
        // cible la plus proche : membre du groupe ou invocation
        purgeSummons(combat, 0);
        ServerPlayer bestP = null;
        MonsterNpc bestS = null;
        BlockPos target = null;
        int bestD = Integer.MAX_VALUE;
        for (UUID u : combat.members) {
            ServerPlayer mp2 = member(combat, u);
            if (mp2 == null) continue;
            BlockPos c = combat.cells.get(u);
            if (c == null) continue;
            int d = ZoneType.chebyshev(cur, c);
            if (d < bestD) {
                bestD = d;
                bestP = mp2;
                bestS = null;
                target = c;
            }
        }
        for (int sid : combat.summonsOf(0)) {
            Entity se = combat.level.getEntity(sid);
            if (se instanceof MonsterNpc sm && sm.isAlive()) {
                int d = ZoneType.chebyshev(cur, sm.blockPosition());
                if (d < bestD) {
                    bestD = d;
                    bestS = sm;
                    bestP = null;
                    target = sm.blockPosition();
                }
            }
        }
        if (target == null) return;

        // rapprochement BFS (les murs bloquent)
        int steps = t.pm;
        while (steps > 0 && ZoneType.chebyshev(cur, target) > t.range) {
            BlockPos next = bfsNext(combat, cur, target, m);
            if (next == null || next.equals(cur) || next.equals(target)) break;
            cur = next;
            steps--;
        }
        if (!cur.equals(m.blockPosition())) {
            m.teleportTo(cur.getX() + 0.5, combat.floorY, cur.getZ() + 0.5);
        }
        // v0.18 : piege pose par le joueur ?
        int trapDmg = triggerTrap(combat, 1, cur);
        if (trapDmg > 0) {
            m.hurtCombat(trapDmg);
            combat.level.playSound(null, cur, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.8f, 1.2f);
            sendFxId(combat, "trap_trigger", cur, List.of(cur));
            broadcast(combat, "§e💥 " + m.getName().getString() + " déclenche un piège : §c-" + trapDmg + "§e !");
            if (m.getCurrentHp() <= 0) {
                ServerPlayer trapper = activePlayer(combat);
                if (trapper == null && !combat.members.isEmpty()) trapper = member(combat, combat.members.get(0));
                if (trapper != null) mobDefeated(trapper, m, combat);
                return;
            }
        }

        int dist = ZoneType.chebyshev(cur, target);

        // v0.27 : executer le skill special si disponible et applicable
        boolean usedSkill = false;
        if (skill != null && dist >= skill.minRange && dist <= skill.maxRange) {
            usedSkill = executeMobSkill(combat, m, skill, cur, target, bestP, bestS, t);
            if (usedSkill) skill.use(m.getId());
        }

        // Attaque normale si pas de skill special utilise
        if (!usedSkill && dist <= t.range && (t.range <= 1 || hasLos(combat, cur, target))) {
            int raw = m.getDamage() + RANDOM.nextInt(3);
            combat.level.playSound(null, cur, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 0.6f, 0.9f);
            if (bestS != null) {
                bestS.hurtCombat(raw);
                combat.level.playSound(null, bestS.blockPosition(), SoundEvents.WOOD_HIT, SoundSource.HOSTILE, 0.9f, 1f);
                broadcast(combat, "§c" + m.getName().getString() + " frappe §7" + bestS.getName().getString()
                        + " §c(§7-" + raw + "§c).");
                sendFxId(combat, "summon_hit", cur, List.of(bestS.blockPosition()));
                if (bestS.getCurrentHp() <= 0) {
                    combat.summonsOf(0).remove((Integer) bestS.getId());
                    bestS.discard();
                    broadcast(combat, "§7Une invocation est détruite !");
                }
            } else if (bestP != null) {
                int dmg = resistReduce(bestP, raw);
                PlayerRpgData data = cap(bestP);
                if (data != null) {
                    data.setHp(data.getHp() - dmg);
                    fr.wakfu.rpg.events.CommonEvents.sync(bestP);
                    bestP.level().playSound(null, bestP.blockPosition(), SoundEvents.PLAYER_HURT, SoundSource.HOSTILE, 1f, 1f);
                    msg(bestP, "§c" + m.getName().getString() + " t'inflige " + dmg + " dégâts"
                            + (dmg < raw ? " (armure : -" + (raw - dmg) + ")" : "") + ".");
                    if (data.getHp() <= 0) {
                        broadcast(combat, "§c💀 " + bestP.getName().getString() + " est mis à terre !");
                        knockout(bestP);
                        return;
                    }
                }
            }
        } else if (dist > t.range) {
            if (combat.members.size() == 1) {
                ServerPlayer solo = member(combat, combat.members.get(0));
                if (solo != null) msg(solo, "§7" + m.getName().getString() + " se rapproche...");
            }
        }
    }



    // v0.27 : sync Alex's Mobs visual entities with MonsterNpc positions
    private static void syncVisuals(Combat combat) {
        for (int i = 0; i < combat.mobs.size(); i++) {
            if (i >= combat.visualEntityIds.size()) break;
            int vid = combat.visualEntityIds.get(i);
            if (vid < 0) continue;
            Entity visual = combat.level.getEntity(vid);
            Entity mob = combat.level.getEntity(combat.mobs.get(i));
            if (visual != null && mob != null && mob.isAlive()) {
                visual.moveTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), mob.getXRot());
            } else if (visual != null && (mob == null || !mob.isAlive())) {
                visual.discard();
                combat.visualEntityIds.set(i, -1);
            }
        }
    }

    private static void cleanupVisuals(Combat combat) {
        for (int vid : combat.visualEntityIds) {
            if (vid < 0) continue;
            Entity e = combat.level.getEntity(vid);
            if (e != null) e.discard();
        }
        combat.visualEntityIds.clear();
    }

    // ------------------------------------------------------------ v0.27 : skills speciaux des monstres (Alex's Mobs)
    private static boolean executeMobSkill(Combat combat, MonsterNpc m, MobSkill skill,
                                           BlockPos cur, BlockPos target, ServerPlayer bestP,
                                           MonsterNpc bestS, MonsterType t) {
        int entityId = m.getId();
        String mobName = m.getName().getString();

        switch (skill.type) {
            case HEAL -> {
                int heal = 5 + m.getLevel() * 2;
                m.setCurrentHp(Math.min(m.maxHpFor(), m.getCurrentHp() + heal));
                combat.level.playSound(null, cur, SoundEvents.PLAYER_LEVELUP, SoundSource.HOSTILE, 0.7f, 1.2f);
                sendFxId(combat, "mob_heal", cur, List.of(cur));
                broadcast(combat, "\u00a7d\u2726 " + mobName + " utilise \u00a7l" + skill.nameFr + "\u00a7r\u00a7d et recupere \u00a7a+" + heal + " PV\u00a7d !");
                return true;
            }
            case BUFF -> {
                if (!m.getTags().contains("buffed")) {
                    m.addTag("buffed");
                    combat.level.playSound(null, cur, SoundEvents.TOTEM_USE, SoundSource.HOSTILE, 0.5f, 1.5f);
                    sendFxId(combat, "mob_buff", cur, List.of(cur));
                    broadcast(combat, "\u00a7e\u2b06 " + mobName + " utilise \u00a7l" + skill.nameFr + "\u00a7r\u00a7e ! Degats augmentes !");
                }
                return true;
            }
            case DEBUFF -> {
                ServerPlayer targetP = bestP;
                if (targetP == null) return false;
                PlayerRpgData data = cap(targetP);
                if (data == null) return false;
                if ("venom".equals(skill.id) || "toxic_bite".equals(skill.id)) {
                    if (!targetP.getTags().contains("poisoned")) {
                        targetP.addTag("poisoned");
                        targetP.addTag("poison_turns:3");
                        combat.level.playSound(null, targetP.blockPosition(), SoundEvents.PLAYER_HURT, SoundSource.HOSTILE, 0.8f, 0.5f);
                        broadcast(combat, "\u00a7a\u2620 " + mobName + " utilise \u00a7l" + skill.nameFr + "\u00a7r\u00a7a sur " + targetP.getName().getString() + " ! Poison 3 tours !");
                    }
                } else if ("momic_wail".equals(skill.id) || "screech".equals(skill.id)) {
                    if (!targetP.getTags().contains("debuff_pa")) {
                        targetP.addTag("debuff_pa");
                        int[] apmp = combat.apmp.get(targetP.getUUID());
                        if (apmp != null && apmp[0] > 1) apmp[0]--;
                        combat.level.playSound(null, targetP.blockPosition(), SoundEvents.WITHER_HURT, SoundSource.HOSTILE, 0.6f, 1.8f);
                        broadcast(combat, "\u00a75\u2726 " + mobName + " utilise \u00a7l" + skill.nameFr + "\u00a7r\u00a75 ! " + targetP.getName().getString() + " perd 1 PA !");
                    }
                } else {
                    broadcast(combat, "\u00a75\u2726 " + mobName + " utilise \u00a7l" + skill.nameFr + "\u00a7r\u00a75 !");
                }
                return true;
            }
            case MOVE -> {
                if ("pounce".equals(skill.id)) {
                    BlockPos bestPounce = null;
                    int bestDistP = Integer.MAX_VALUE;
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dz == 0) continue;
                            BlockPos candidate = target.offset(dx, 0, dz);
                            if (combat.isPlayable(candidate)) {
                                int d = ZoneType.chebyshev(candidate, target);
                                if (d < bestDistP) { bestDistP = d; bestPounce = candidate; }
                            }
                        }
                    }
                    if (bestPounce != null) {
                        m.teleportTo(bestPounce.getX() + 0.5, combat.floorY, bestPounce.getZ() + 0.5);
                        int raw = m.getDamage() + 3 + RANDOM.nextInt(3);
                        if (bestP != null) {
                            int dmg = resistReduce(bestP, raw);
                            PlayerRpgData data = cap(bestP);
                            if (data != null) {
                                data.setHp(data.getHp() - dmg);
                                fr.wakfu.rpg.events.CommonEvents.sync(bestP);
                                bestP.level().playSound(null, bestP.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1f, 0.8f);
                                broadcast(combat, "\u00a7c " + mobName + " bondit sur " + bestP.getName().getString() + " \u00a7c(-" + dmg + ") !");
                                if (data.getHp() <= 0) { broadcast(combat, "\u00a7c " + bestP.getName().getString() + " est mis a terre !"); knockout(bestP); }
                            }
                        }
                    }
                } else {
                    // Sprint / fuite : deplacement bonus
                    BlockPos sprintCur = cur;
                    int extra = 2;
                    while (extra > 0 && ZoneType.chebyshev(sprintCur, target) > 1) {
                        BlockPos next = bfsNext(combat, sprintCur, target, m);
                        if (next == null || next.equals(sprintCur) || next.equals(target)) break;
                        sprintCur = next;
                        extra--;
                    }
                    if (!sprintCur.equals(cur)) {
                        m.teleportTo(sprintCur.getX() + 0.5, combat.floorY, sprintCur.getZ() + 0.5);
                    }
                    combat.level.playSound(null, cur, SoundEvents.HORSE_GALLOP, SoundSource.HOSTILE, 0.8f, 1.2f);
                    broadcast(combat, "\u00a7b " + mobName + " utilise \u00a7l" + skill.nameFr + "\u00a7r\u00a7b et se deplace vite !");
                }
                return true;
            }
            case AOE -> {
                int aoeDamage = m.getDamage() + RANDOM.nextInt(4);
                List<BlockPos> aoeCells = new ArrayList<>();
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        aoeCells.add(cur.offset(dx, 0, dz));
                    }
                }
                combat.level.playSound(null, cur, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1f, 0.8f);
                sendFxId(combat, "mob_aoe", cur, aoeCells);
                boolean hit = false;
                for (UUID u : combat.members) {
                    ServerPlayer mp = member(combat, u);
                    if (mp == null) continue;
                    BlockPos pc = combat.cells.get(u);
                    if (pc == null) continue;
                    for (BlockPos ac : aoeCells) {
                        if (ac.getX() == pc.getX() && ac.getZ() == pc.getZ()) {
                            int dmg = resistReduce(mp, aoeDamage);
                            PlayerRpgData data = cap(mp);
                            if (data != null) {
                                data.setHp(data.getHp() - dmg);
                                fr.wakfu.rpg.events.CommonEvents.sync(mp);
                                msg(mp, "\u00a7c " + mobName + " utilise \u00a7l" + skill.nameFr + "\u00a7r\u00a7c ! -" + dmg + " PV !");
                                hit = true;
                                if (data.getHp() <= 0) { broadcast(combat, "\u00a7c " + mp.getName().getString() + " est mis a terre !"); knockout(mp); }
                            }
                            break;
                        }
                    }
                }
                for (int sid : combat.summonsOf(0)) {
                    Entity se = combat.level.getEntity(sid);
                    if (se instanceof MonsterNpc sm && sm.isAlive()) {
                        for (BlockPos ac : aoeCells) {
                            if (ac.getX() == sm.blockPosition().getX() && ac.getZ() == sm.blockPosition().getZ()) {
                                sm.hurtCombat(aoeDamage); hit = true;
                                if (sm.getCurrentHp() <= 0) { combat.summonsOf(0).remove((Integer) sm.getId()); sm.discard(); }
                                break;
                            }
                        }
                    }
                }
                broadcast(combat, "\u00a7c " + mobName + " utilise \u00a7l" + skill.nameFr + "\u00a7r\u00a7c !" + (hit ? " Zone devastatrice !" : " Aucune cible touchee."));
                return true;
            }
            case DAMAGE -> {
                int raw = m.getDamage() + RANDOM.nextInt(4) + 2;
                if ("maul".equals(skill.id)) raw = (int)(raw * 1.5);
                if (bestP != null) {
                    int dmg = resistReduce(bestP, raw);
                    PlayerRpgData data = cap(bestP);
                    if (data != null) {
                        data.setHp(data.getHp() - dmg);
                        fr.wakfu.rpg.events.CommonEvents.sync(bestP);
                        bestP.level().playSound(null, bestP.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1f, 1f);
                        broadcast(combat, "\u00a7c\u2694 " + mobName + " utilise \u00a7l" + skill.nameFr + "\u00a7r\u00a7c sur " + bestP.getName().getString() + " \u00a7c(-" + dmg + ") !");
                        if ("life_steal".equals(skill.id)) {
                            int heal = dmg / 2;
                            m.setCurrentHp(Math.min(m.maxHpFor(), m.getCurrentHp() + heal));
                            broadcast(combat, "\u00a7d " + mobName + " vole \u00a7a+" + heal + " PV\u00a7d !");
                        }
                        if (data.getHp() <= 0) { broadcast(combat, "\u00a7c " + bestP.getName().getString() + " est mis a terre !"); knockout(bestP); }
                    }
                } else if (bestS != null) {
                    bestS.hurtCombat(raw);
                    broadcast(combat, "\u00a7c\u2694 " + mobName + " utilise \u00a7l" + skill.nameFr + "\u00a7r\u00a7c sur " + bestS.getName().getString() + " \u00a7c(-" + raw + ") !");
                    if (bestS.getCurrentHp() <= 0) { combat.summonsOf(0).remove((Integer) bestS.getId()); bestS.discard(); }
                }
                return true;
            }
        }
        return false;
    }


    // ------------------------------------------------------------ fins de combat
    /** v0.19 : un monstre tombe — recompenses au tueur, le combat continue
     *  tant qu'il reste des adversaires. */
    private static void mobDefeated(ServerPlayer player, MonsterNpc copy, Combat combat) {
        PlayerRpgData data = cap(player);
        MonsterType t = copy.getMonsterType();
        int xp = copy.getXpReward();
        int wis = armorBonusWis(player) + (data != null ? data.getBonusWis() : 0);
        int xpMult = 1 + wis / 10;
        if (xpMult > 1) xp = xp * xpMult;
        if (data != null) {
            data.addXp(xp);
            fr.wakfu.rpg.events.CommonEvents.sync(player);
        }
        fr.wakfu.rpg.core.Quests.progress(player, "kill:" + t.getId(), 1);
        fr.wakfu.rpg.core.Codex.kill(player, t.getId());
        // v0.22 : loot propre a chaque monstre de zone (panoplie + trophee) ;
        // les invocations ne droppent rien
        ItemStack drop = (t.xpReward > 0 && RANDOM.nextInt(100) < 90) ? rollLoot(t) : null;
        int idx = combat.mobs.indexOf(copy.getId());
        int origin = (idx >= 0 && idx < combat.mobOrigins.size()) ? combat.mobOrigins.get(idx) : -1;
        copy.dieCombat();
        if (idx >= 0) {
            combat.mobs.remove(idx);
            if (idx < combat.mobOrigins.size()) combat.mobOrigins.remove(idx);
        }
        ServerLevel ow = combat.level.getServer().overworld();
        if (origin >= 0) {
            Entity orig = ow.getEntity(origin);
            if (orig instanceof MonsterNpc mo) mo.dieCombat();
        }
        // v0.24 : noms et quantites captures AVANT l'ajout en inventaire
        // (Inventory.add() vide le stack fusionne -> "air" / "0 lingots") ;
        // une seule ligne en barre d'action, plus rien dans le chat.
        StringBuilder lootLine = new StringBuilder();
        if (drop != null && !drop.isEmpty()) {
            ItemStack rec = drop.copy();
            fr.wakfu.rpg.core.Codex.item(player, rec);
            give(player, drop);
            lootLine.append(rec.getHoverName().getString());
        }
        if (t == MonsterType.BOSS) {
            ItemStack tro = fr.wakfu.rpg.item.WakfuTrophyItem.make("chef");
            ItemStack rec = tro.copy();
            fr.wakfu.rpg.core.Codex.item(player, rec);
            give(player, tro);
            if (lootLine.length() > 0) lootLine.append(" §7· ");
            lootLine.append(rec.getHoverName().getString());
        }
        if (t.xpReward > 0) {
            // v0.25 : argent ($) versé dans la bourse — visible HUD + grimoire
            long coins = 10 + RANDOM.nextInt(3) * 10L;
            if (t == MonsterType.BOSS) coins += 100 + RANDOM.nextInt(5) * 10L;
            fr.wakfu.rpg.core.Money.add(player.getUUID(), coins);
            fr.wakfu.rpg.core.Money.sync(player);
            if (lootLine.length() > 0) lootLine.append(" §7· ");
            lootLine.append("§6+").append(coins).append(" $");
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.55f, 1.4f);
        if (!combat.mobs.isEmpty()) {
            combat.monsterId = combat.mobs.get(0);
            combat.monsterOriginId = combat.mobOrigins.isEmpty() ? -1 : combat.mobOrigins.get(0);
            msg(player, "§a✔ +" + xp + " XP · §cIl reste " + combat.mobs.size() + " adversaire(s)"
                    + (lootLine.length() > 0 ? " §7· " + lootLine : ""));
            return;
        }
        groupVictory(combat, player, lootLine.toString());
    }

    /** v0.19 : dernier monstre tombe — victoire de tout le groupe. */
    private static void groupVictory(Combat combat, ServerPlayer killer, String lootLine) {
        // v0.27 : collecte des donnees de victoire
        java.util.List<String> mobNames = new java.util.ArrayList<>();
        for (int mid : new java.util.ArrayList<>(combat.mobs)) {
            Entity e = combat.level.getEntity(mid);
            if (e instanceof MonsterNpc mn) mobNames.add(mn.getName().getString());
        }

        long duration = combat.level.getServer().getTickCount() - combat.enterTick;

        for (UUID u : new ArrayList<>(combat.members)) {
            ServerPlayer mp2 = member(combat, u);
            if (mp2 == null) continue;
            mp2.level().playSound(null, mp2.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1f);
            if (mp2 != killer) {
                PlayerRpgData d = cap(mp2);
                if (d != null) {
                    d.addXp(25);
                    fr.wakfu.rpg.events.CommonEvents.sync(mp2);
                }
            }
            // v0.27 : envoyer ecran de victoire a chaque joueur
            PlayerRpgData vd = cap(mp2);
            int totalXp = vd != null ? 50 : 25;  // estimation
            long totalMoney = fr.wakfu.rpg.core.Money.balance(mp2.getUUID());
            java.util.List<String> lootList = new java.util.ArrayList<>();
            if (lootLine != null && !lootLine.isEmpty()) {
                for (String part : lootLine.split(" §7· ")) {
                    lootList.add(part.trim());
                }
            }
            fr.wakfu.rpg.network.VictoryPacket vp = new fr.wakfu.rpg.network.VictoryPacket(
                "§6Victoire !", totalXp, totalMoney,
                mobNames, lootList, combat.round, duration);
            fr.wakfu.rpg.network.Network.sendToPlayer(mp2, vp);
        }
        if (killer != null) {
            killer.level().playSound(null, killer.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 0.45f, 1f);
        }
        endCombatAll(combat, "§a✔ Victoire !"
                + (combat.members.size() > 1 ? " §6Groupe éliminé — bravo à toute l'équipe !" : "")
                + (lootLine == null || lootLine.isEmpty() ? "" : "  " + lootLine), false);
    }

    /** v0.19 : un membre tombe — il quitte l'arene, le groupe continue sans lui. */
    private static void knockout(ServerPlayer player) {
        Combat combat = COMBATS.get(player.getUUID());
        player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.8f, 0.5f);
        PlayerRpgData data = cap(player);
        if (data != null) {
            data.setHp(Math.max(1, data.maxHp() / 2));
            fr.wakfu.rpg.events.CommonEvents.sync(player);
        }
        if (combat == null) return;
        if (combat.pvp()) {
            duelEnd(combat, 1 - combat.sideOf(player.getUUID()));
            return;
        }
        leaveMember(combat, player, true);
        msg(player, "§c💀 Tu es mis à terre... Tu te réveilles au spawn, amoindri mais vivant.");
    }

    /** v0.19 : retire un membre du combat (fuite, K.O., deco, fin). */
    private static void leaveMember(Combat combat, ServerPlayer p, boolean toSpawn) {
        int idx = combat.members.indexOf(p.getUUID());
        boolean wasActive = idx >= 0 && idx == combat.activeIdx;
        COMBATS.remove(p.getUUID());
        if (idx >= 0) combat.members.remove(idx);
        combat.cells.remove(p.getUUID());
        combat.apmp.remove(p.getUUID());
        combat.anchors.remove(p.getUUID());
        if (idx >= 0 && combat.activeIdx >= idx && combat.activeIdx > 0) combat.activeIdx--;
        exitSide(p, toSpawn);
        sendState(p, null);
        if (!combat.alive()) {
            cleanupAll(combat);
            return;
        }
        if (wasActive && combat.phase == Combat.Phase.SIDE_A) {
            // le membre actif est parti : au suivant de jouer
            combat.resetTurn(0);
            UUID nu = combat.activeMember();
            ServerPlayer np = nu == null ? null : member(combat, nu);
            if (np != null) {
                combat.apmp.put(nu, new int[]{maxApFor(np), maxMpFor(np)});
                msg(np, "§6Un allié est tombé — à ton tour !");
            }
        }
        sendStateAll(combat);
    }

    /** Fin de duel : le gagnant retourne d'ou il vient, le perdant va au spawn. */
    private static void duelEnd(Combat combat, int winnerSide) {
        ServerPlayer winner = online(combat, winnerSide);
        ServerPlayer loser = online(combat, 1 - winnerSide);
        COMBATS.remove(combat.playerA);
        COMBATS.remove(combat.playerB);
        BY_KEY.remove(combat.key);
        if (winner != null) {
            PlayerRpgData wd = cap(winner);
            if (wd != null) {
                wd.addXp(30);
                fr.wakfu.rpg.events.CommonEvents.sync(winner);
            }
            fr.wakfu.rpg.core.Quests.progress(winner, "duel", 1);
            exitSide(winner, false);
            sendState(winner, null);
            msg(winner, "§a✔ Victoire en duel ! +30 XP.");
        }
        if (loser != null) {
            PlayerRpgData ld = cap(loser);
            if (ld != null) {
                ld.setHp(Math.max(1, ld.maxHp() / 2));
                fr.wakfu.rpg.events.CommonEvents.sync(loser);
            }
            exitSide(loser, true);
            sendState(loser, null);
            msg(loser, "§c💀 Défaite en duel... Tu te réveilles au spawn.");
        }
        cleanupVisuals(combat);
        removeCamera(combat);
        cleanupSummons(combat);
        Arena.clearAt(combat.level, combat.baseX, combat.floorY, combat.baseZ, combat.size);
    }

    /** v0.19 : fin du combat pour TOUT le groupe. */
    private static void endCombatAll(Combat combat, String message, boolean toSpawn) {
        // v0.27 : collect data before cleanup
        java.util.List<String> vicMobs = new java.util.ArrayList<>();
        int vicRound = combat.round;
        long vicDur = combat.level.getServer().getTickCount() - combat.enterTick;
        for (int mid : new java.util.ArrayList<>(combat.mobs)) {
            Entity e = combat.level.getEntity(mid);
            if (e instanceof MonsterNpc mn) vicMobs.add(mn.getName().getString());
        }
        boolean isVictory = message != null && message.contains("Victoire");

        for (UUID u : new ArrayList<>(combat.members)) {
            ServerPlayer mp2 = member(combat, u);
            COMBATS.remove(u);
            if (mp2 != null) {
                exitSide(mp2, toSpawn);
                sendState(mp2, null);
                if (message != null) msg(mp2, message);
                // v0.27 : send victory screen AFTER teleport
                if (isVictory) {
                    fr.wakfu.rpg.network.VictoryPacket vp =
                        new fr.wakfu.rpg.network.VictoryPacket(
                            "Victoire !", 50, 0, vicMobs,
                            new java.util.ArrayList<>(), vicRound, vicDur);
                    fr.wakfu.rpg.network.Network.sendToPlayer(mp2, vp);
                }
            }
        }
        cleanupAll(combat);
    }

    /** v0.19 : nettoyage complet — monstres restants (PV reportes), invocations,
     *  camera, arene (taille variable), registre. */
    private static void cleanupAll(Combat combat) {
        combat.members.clear();
        combat.cells.clear();
        ServerLevel ow = combat.level.getServer().overworld();
        for (int i = 0; i < combat.mobs.size(); i++) {
            Entity copy = combat.level.getEntity(combat.mobs.get(i));
            int origin = i < combat.mobOrigins.size() ? combat.mobOrigins.get(i) : -1;
            if (copy instanceof MonsterNpc mc2) {
                if (origin >= 0) {
                    Entity orig = ow.getEntity(origin);
                    if (orig instanceof MonsterNpc mo) mo.setCurrentHp(mc2.getCurrentHp());
                }
                mc2.discard();
            }
        }
        combat.mobs.clear();
        combat.mobOrigins.clear();
        combat.traps.clear();
        cleanupSummons(combat);
        cleanupVisuals(combat);
        removeCamera(combat);
        Arena.clearAt(combat.level, combat.baseX, combat.floorY, combat.baseZ, combat.size);
        BY_KEY.remove(combat.key);
    }

    public static void endCombat(ServerPlayer player, String message) {
        endCombat(player, message, false);
    }

    /** Fin propre : restauration complete, retour dans l'overworld. */
    public static void endCombat(ServerPlayer player, String message, boolean toSpawn) {
        Combat combat = COMBATS.remove(player.getUUID());
        if (combat == null) return;
        if (combat.pvp()) {
            COMBATS.remove(combat.playerA);
            COMBATS.remove(combat.playerB);
            BY_KEY.remove(combat.key);
            cleanupSummons(combat);
            ServerPlayer other = online(combat, 1 - combat.sideOf(player.getUUID()));
            exitSide(player, toSpawn);
            sendState(player, null);
            if (other != null) {
                exitSide(other, false);
                sendState(other, null);
                msg(other, "§7Le duel est interrompu.");
            }
            cleanupVisuals(combat);
        removeCamera(combat);
            Arena.clearAt(combat.level, combat.baseX, combat.floorY, combat.baseZ, combat.size);
        } else {
            // v0.19 : le membre part ; s'il etait seul, tout est nettoye
            leaveMember(combat, player, toSpawn);
        }
        if (message != null) msg(player, message);
    }

    private static void exitSide(ServerPlayer p, boolean toSpawn) {
        if (p == null) return;
        p.getCapability(ModCapabilities.RPG).ifPresent(data -> {
            ServerLevel ow = p.getServer().overworld();
            if (data.getSavedInv() != null) {
                p.getInventory().clearContent();
                p.getInventory().load(data.getSavedInv());
                data.setSavedInv(null);
            }
            if (toSpawn) {
                var spawn = ow.getSharedSpawnPos();
                p.teleportTo(ow, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0, 0);
            } else if (data.isInArena()) {
                p.teleportTo(ow, data.getRetX() + 0.5, data.getRetY(), data.getRetZ() + 0.5, 0, 0);
            }
            if (data.isInArena()) {
                ow.setChunkForced(data.getRetX() >> 4, data.getRetZ() >> 4, false);
            }
            data.setInArena(false);
        });
    }

    private static void removeCamera(Combat combat) {
        Entity e = combat.level.getEntity(combat.cameraId);
        if (e instanceof ArmorStand s) {
            s.discard();
        }
    }

    /** Appele au login : si le joueur etait reste en arene (deco/crash), on le recupere. */
    public static void recoverIfStuck(ServerPlayer player) {
        player.getCapability(ModCapabilities.RPG).ifPresent(data -> {
            if (!data.isInArena()) return;
            if (inCombat(player.getUUID())) {
                endCombat(player, "§7Combat interrompu : retour à ta position.");
            } else {
                ServerLevel ow = player.getServer().overworld();
                if (data.getSavedInv() != null) {
                    player.getInventory().clearContent();
                    player.getInventory().load(data.getSavedInv());
                    data.setSavedInv(null);
                }
                player.teleportTo(ow, data.getRetX() + 0.5, data.getRetY(), data.getRetZ() + 0.5, 0, 0);
                data.setInArena(false);
                player.sendSystemMessage(Component.literal("§7Retour à ta position d'origine."));
            }
        });
    }

    // ------------------------------------------------------------ compat objets v0.5 (non distribues)
    public static void onSpellItemUse(ServerPlayer player, Spell spell) {
        if (inCombat(player.getUUID())) {
            hint(player, "§7Utilise la barre de sorts en bas de l'écran (touches 1-4).");
        } else {
            hint(player, "§7Ce sort ne s'utilise qu'en combat.");
        }
    }

    public static void onUtilityUse(ServerPlayer player, CombatUtilityItem.Kind kind) {
        Combat combat = COMBATS.get(player.getUUID());
        if (combat == null) {
            hint(player, "§7Hors combat, cet objet ne sert à rien.");
            return;
        }
        switch (kind) {
            case FLEE -> flee(player);
            case END -> endTurn(player);
            default -> hint(player, "§7Clique directement sur une case pour te déplacer.");
        }
    }

    public static void menuChoice(ServerPlayer player, MonsterNpc monster, int choice) {
        if (choice == 0) startCombat(player, monster);
    }

    // ------------------------------------------------------------ drops / bonus (tranche 2)
    /** v0.23 : donne un objet sans jamais le perdre (inventaire plein -> sol). */
    private static void give(ServerPlayer player, ItemStack s) {
        if (!player.getInventory().add(s)) player.drop(s, false);
    }

    /** v0.22 : butin par type de monstre — piece de la panoplie de sa zone
     *  (30 %), trophee unique (25 %), equipement runique classique (15 %),
     *  sinon lingots. Le boss donne toujours une piece de sa panoplie. */
    private static ItemStack rollLoot(MonsterType t) {
        net.minecraft.util.RandomSource rng = net.minecraft.util.RandomSource.create();
        if (t == MonsterType.BOSS) {
            int br = RANDOM.nextInt(100);
            if (br < 50) return fr.wakfu.rpg.item.WakfuArmorItem.randomSetPiece("chef", rng);
            return fr.wakfu.rpg.item.WakfuArmorItem.randomFantasyPiece(rng);
        }
        String set = fr.wakfu.rpg.item.WakfuArmorItem.setForMonster(t.getId());
        String trophy = fr.wakfu.rpg.item.WakfuTrophyItem.forMonster(t.getId());
        int r = RANDOM.nextInt(100);
        if (r < 20 && set != null) return fr.wakfu.rpg.item.WakfuArmorItem.randomSetPiece(set, rng);
        if (r < 35 && trophy != null) return fr.wakfu.rpg.item.WakfuTrophyItem.make(trophy);
        if (r < 60) {
            // Momic drops fantasy armor more often
            return fr.wakfu.rpg.item.WakfuArmorItem.randomFantasyPiece(rng);
        }
        if (r < 85) {
            String rarity = RANDOM.nextInt(100) < 60 ? "commune" : (RANDOM.nextInt(100) < 80 ? "rare" : "epique");
            String[] slots = {"head", "chest", "legs", "feet"};
            String slot = slots[RANDOM.nextInt(4)];
            return fr.wakfu.rpg.item.WakfuArmorItem.makePiece(slot, rarity, 1, rng);
        }
        return ItemStack.EMPTY;
    }

    @SuppressWarnings("unused")
    private static ItemStack rollGear() {
        ItemStack stack = new ItemStack(fr.wakfu.rpg.registry.ModItems.WAKFU_GEAR.get());
        CompoundTag tag = new CompoundTag();
        int roll = RANDOM.nextInt(100);
        String rarity;
        int bonus;
        if (roll < 60) { rarity = "commune"; bonus = 1 + RANDOM.nextInt(2); }
        else if (roll < 90) { rarity = "rare"; bonus = 3 + RANDOM.nextInt(2); }
        else { rarity = "epique"; bonus = 5 + RANDOM.nextInt(3); }
        tag.putString("rarity", rarity);
        tag.putInt("bonusDamage", bonus);
        stack.setTag(tag);
        return stack;
    }

    /** v0.16 : bonus {vie, force, sagesse, resistance} de l'equipement porte. */
    private static int[] gearFixed(ServerPlayer player) {
        return fr.wakfu.rpg.item.WakfuArmorItem.equippedFixed(player);
    }

    /** v0.22 : les bonus de panoplie complete (4 pieces identiques equipees)
     *  s'ajoutent aux stats des pieces. */
    public static int armorBonusHp(ServerPlayer player) {
        return gearFixed(player)[0] + fr.wakfu.rpg.item.WakfuArmorItem.activeSetBonus(player)[0];
    }
    public static int armorBonusAp(ServerPlayer player) {
        return fr.wakfu.rpg.item.WakfuArmorItem.activeSetBonus(player)[4];
    }
    public static int armorBonusMp(ServerPlayer player) {
        return fr.wakfu.rpg.item.WakfuArmorItem.activeSetBonus(player)[5];
    }

    /** Force totale = points alloues + equipement : +1 degat par point. */
    public static int armorBonusDamage(ServerPlayer player) {
        PlayerRpgData d = cap(player);
        return gearFixed(player)[1] + (d != null ? d.getBonusStr() : 0)
                + fr.wakfu.rpg.item.WakfuArmorItem.activeSetBonus(player)[1];
    }

    /** Sagesse totale (equipement + points) : +10% XP par tranche de 10. */
    public static int armorBonusWis(ServerPlayer player) {
        return gearFixed(player)[2] + fr.wakfu.rpg.item.WakfuArmorItem.activeSetBonus(player)[2];
    }

    /** Resistance totale (equipement + points). */
    public static int resistTotal(ServerPlayer player) {
        PlayerRpgData d = cap(player);
        return gearFixed(player)[3] + (d != null ? d.getBonusRes() : 0)
                + fr.wakfu.rpg.item.WakfuArmorItem.activeSetBonus(player)[3];
    }

    /** Reduit les degats subis : -2% par point de resistance (max -50%). */
    public static int resistReduce(ServerPlayer player, int raw) {
        int pct = Math.min(50, resistTotal(player) * 2);
        return Math.max(1, raw - raw * pct / 100);
    }

    /** Renvoie explicitement tous les blocs de l'arene + la lumiere au client
     (contourne le bug de sync des chunks de dimension custom : les entites
     arrivaient, les blocs non). */
    public static void broadcastArenaBlocks(ServerPlayer p, Combat combat) {
        ServerLevel lvl = combat.level;
        int half = combat.size / 2;
        int cx = combat.baseX + half;
        int cz = combat.baseZ + half;
        var conn = p.connection;
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                boolean edge = Math.abs(x) == half || Math.abs(z) == half;
                BlockPos fp = new BlockPos(cx + x, combat.floorY - 1, cz + z);
                conn.send(new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(
                        fp, lvl.getBlockState(fp)));
                if (edge) {
                    for (int h = 0; h < 8; h++) {
                        BlockPos wp = new BlockPos(cx + x, combat.floorY + h, cz + z);
                        conn.send(new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(
                                wp, lvl.getBlockState(wp)));
                    }
                }
            }
        }
        // v0.19 : murs internes
        for (BlockPos w : combat.walls) {
            conn.send(new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(w, lvl.getBlockState(w)));
            conn.send(new net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket(w.above(), lvl.getBlockState(w.above())));
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos((cx >> 4) + dx, (cz >> 4) + dz);
                conn.send(new net.minecraft.network.protocol.game.ClientboundLightUpdatePacket(
                        cp, lvl.getLightEngine(), null, null));
            }
        }
    }

    /** Renvoie l'etat au joueur (resend periodique anti-perte de paquet). */
    public static void resend(ServerPlayer p) {
        Combat combat = COMBATS.get(p.getUUID());
        if (combat != null) sendState(p, combat);
    }

    /** Deconnexion pendant un combat : nettoie cote serveur (l'inventaire sera rendu au login). */
    public static void onLogout(ServerPlayer p) {
        Combat c = COMBATS.get(p.getUUID());
        if (c == null) return;
        if (c.pvp()) {
            int side = c.sideOf(p.getUUID());
            if (side >= 0) duelEnd(c, 1 - side);
        } else {
            // v0.19 : le membre deconnecte sort du combat ; le groupe continue
            COMBATS.remove(p.getUUID());
            int idx = c.members.indexOf(p.getUUID());
            boolean wasActive = idx >= 0 && idx == c.activeIdx;
            if (idx >= 0) c.members.remove(idx);
            c.cells.remove(p.getUUID());
            c.apmp.remove(p.getUUID());
            c.anchors.remove(p.getUUID());
            if (idx >= 0 && c.activeIdx >= idx && c.activeIdx > 0) c.activeIdx--;
            if (!c.alive()) {
                cleanupAll(c);
            } else {
                if (wasActive && c.phase == Combat.Phase.SIDE_A) {
                    c.resetTurn(0);
                    UUID nu = c.activeMember();
                    ServerPlayer np = nu == null ? null : member(c, nu);
                    if (np != null) c.apmp.put(nu, new int[]{maxApFor(np), maxMpFor(np)});
                }
                broadcast(c, "§7" + p.getName().getString() + " est déconnecté du combat.");
                sendStateAll(c);
            }
        }
    }

    // ------------------------------------------------------------ etat -> clients
    private static void sendStateAll(Combat combat) {
        for (UUID u : new ArrayList<>(combat.members)) {
            ServerPlayer mp2 = member(combat, u);
            if (mp2 != null) sendState(mp2, combat);
        }
        if (combat.pvp()) {
            ServerPlayer b = online(combat, 1);
            if (b != null) sendState(b, combat);
        }
    }

    private static void sendState(ServerPlayer viewer, Combat combat) {
        if (combat == null) {
            Network.sendToPlayer(viewer, CombatStatePacket.empty());
            return;
        }
        int side = combat.sideOf(viewer.getUUID());
        if (side < 0) return;
        PlayerRpgData data = cap(viewer);
        int selfHp = data == null ? 0 : data.getHp();
        int selfMax = data == null ? 1 : data.maxHp() + armorBonusHp(viewer);
        int oppHp, oppMax;
        String oppName;
        BlockPos oppCell;
        if (combat.pvp()) {
            ServerPlayer opp = online(combat, 1 - side);
            PlayerRpgData od = opp == null ? null : cap(opp);
            oppHp = od == null ? 0 : od.getHp();
            oppMax = od == null ? 1 : od.maxHp();
            oppName = opp == null ? "Adversaire" : opp.getName().getString();
            oppCell = combat.cellOf(1 - side);
        } else {
            // v0.19 : premier mob vivant = adversaire "principal" (barre du haut)
            oppHp = 0;
            oppMax = 1;
            oppName = "";
            oppCell = combat.firstMobCell();
            for (int id : combat.mobs) {
                Entity e = combat.level.getEntity(id);
                if (e instanceof MonsterNpc m && m.isAlive()) {
                    oppHp = m.getCurrentHp();
                    oppMax = m.maxHpFor();
                    oppName = m.getName().getString();
                    oppCell = m.blockPosition();
                    break;
                }
            }
        }
        if (oppCell == null) oppCell = new BlockPos(combat.baseX + combat.size - 3, combat.floorY, combat.baseZ + combat.size / 2);
        BlockPos selfCell = combat.cells.get(viewer.getUUID());
        if (selfCell == null) selfCell = combat.cellOf(side);
        if (selfCell == null) selfCell = new BlockPos(combat.baseX + 2, combat.floorY, combat.baseZ + combat.size / 2);
        int[] myApmp = combat.apmp.get(viewer.getUUID());
        int myAp = myApmp != null ? myApmp[0] : combat.apOf(side);
        int myMp = myApmp != null ? myApmp[1] : combat.mpOf(side);
        boolean yourTurn = combat.pvp()
                ? combat.isTurn(side)
                : combat.phase == Combat.Phase.SIDE_A && viewer.getUUID().equals(combat.activeMember());

        // invocations (liste legacy)
        List<int[]> sums = new ArrayList<>();
        for (int sd = 0; sd < 2; sd++) {
            for (int sid : combat.summonsOf(sd)) {
                Entity se = combat.level.getEntity(sid);
                if (se instanceof MonsterNpc sm) {
                    sums.add(new int[]{sm.getId(), sm.blockPosition().getX(), sm.blockPosition().getZ(),
                            sm.getCurrentHp(), sm.maxHpFor(), sd == side ? 0 : 1});
                }
            }
        }
        List<int[]> trapList = new ArrayList<>();
        for (int[] t : combat.traps) trapList.add(new int[]{t[0], t[1], t[2] == side ? 0 : 1});

        // v0.19 : unites (joueurs + mobs) {sideRel, kind(0 joueur/1 mob), x, z, hp, maxHp, active}
        List<int[]> units = new ArrayList<>();
        List<String> unitNames = new ArrayList<>();
        for (UUID u : combat.members) {
            BlockPos c = combat.cells.get(u);
            if (c == null) continue;
            ServerPlayer mp2 = member(combat, u);
            PlayerRpgData d2 = mp2 == null ? null : cap(mp2);
            int hp = d2 == null ? 0 : d2.getHp();
            int mx = d2 == null ? 1 : d2.maxHp() + (mp2 == null ? 0 : armorBonusHp(mp2));
            int active = (combat.phase == Combat.Phase.SIDE_A && u.equals(combat.activeMember())) ? 1 : 0;
            units.add(new int[]{0, 0, c.getX(), c.getZ(), hp, mx, active});
            unitNames.add(mp2 == null ? "Allié" : mp2.getName().getString());
        }
        if (combat.pvp()) {
            ServerPlayer opp = online(combat, 1 - side);
            BlockPos oc = combat.cellOf(1 - side);
            PlayerRpgData od = opp == null ? null : cap(opp);
            if (oc != null) {
                units.add(new int[]{1, 0, oc.getX(), oc.getZ(),
                        od == null ? 0 : od.getHp(), od == null ? 1 : od.maxHp(),
                        combat.phase == Combat.Phase.SIDE_B ? 1 : 0});
                unitNames.add(opp == null ? "Adversaire" : opp.getName().getString());
            }
        } else {
            for (int id : combat.mobs) {
                Entity e = combat.level.getEntity(id);
                if (e instanceof MonsterNpc m && m.isAlive()) {
                    units.add(new int[]{1, 1, m.blockPosition().getX(), m.blockPosition().getZ(),
                            m.getCurrentHp(), m.maxHpFor(), combat.phase == Combat.Phase.SIDE_B ? 1 : 0});
                    unitNames.add(m.getName().getString());
                }
            }
        }
        // murs internes (offsets compacts)
        List<Integer> wallList = new ArrayList<>();
        for (BlockPos w : combat.walls) {
            wallList.add((w.getX() - combat.baseX) * 64 + (w.getZ() - combat.baseZ));
        }
        Network.sendToPlayer(viewer, new CombatStatePacket(true, combat.pvp(), yourTurn,
                combat.round, myAp, myMp, "",
                selfHp, selfMax, selfCell.getX(), selfCell.getZ(),
                oppHp, oppMax, oppName, oppCell.getX(), oppCell.getZ(),
                combat.baseX, combat.baseZ, combat.floorY, combat.size, combat.cameraId,
                sums, trapList, units, unitNames, wallList, combat.theme));
    }

    private static ServerPlayer online(Combat combat, int side) {
        UUID u = side == 0 ? combat.playerA : combat.playerB;
        if (u == null) return null;
        return combat.level.getServer().getPlayerList().getPlayer(u);
    }

    // ------------------------------------------------------------ utilitaires
    private static PlayerRpgData cap(ServerPlayer p) {
        return p.getCapability(ModCapabilities.RPG).resolve().orElse(null);
    }

    private static boolean hasClass(ServerPlayer p) {
        PlayerRpgData d = cap(p);
        return d != null && d.hasClass();
    }

    /** v0.24 : les infos de jeu vont dans la barre d'action — PLUS dans le chat. */
    private static void msg(ServerPlayer p, String text) {
        p.displayClientMessage(Component.literal(text), true);
    }

    private static void hint(ServerPlayer p, String text) {
        p.displayClientMessage(Component.literal(text), true);
    }

    // ------------------------------------------------------------ v0.14 : invocations
    private static void purgeSummons(Combat combat, int side) {
        List<Integer> list = combat.summonsOf(side);
        list.removeIf(id -> !(combat.level.getEntity(id) instanceof MonsterNpc mm) || !mm.isAlive());
    }

    /** Case occupee par une unite (joueurs, mobs, invocations), sauf `except`. */
    private static boolean occupiedByUnit(Combat combat, BlockPos cell, Entity except) {
        for (Map.Entry<UUID, BlockPos> e : combat.cells.entrySet()) {
            if (cell.equals(e.getValue())) {
                ServerPlayer sp2 = member(combat, e.getKey());
                if (sp2 != except) return true;
            }
        }
        for (int id : combat.mobs) {
            Entity e = combat.level.getEntity(id);
            if (e != null && e != except && e.isAlive() && e.blockPosition().equals(cell)) return true;
        }
        for (int side = 0; side < 2; side++) {
            for (int id : combat.summonsOf(side)) {
                Entity e = combat.level.getEntity(id);
                if (e == null || e == except) continue;
                if (e.blockPosition().equals(cell)) return true;
            }
        }
        return false;
    }

    private static BlockPos freeAdjacentCell(Combat combat, BlockPos self) {
        int[][] offs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int[] o : offs) {
            BlockPos c = new BlockPos(self.getX() + o[0], combat.floorY, self.getZ() + o[1]);
            if (combat.isPlayable(c) && !occupiedByUnit(combat, c, null)) return c;
        }
        return null;
    }

    // ------------------------------------------------------------ v0.19 : chemin / murs
    /** Distance BFS (8 directions) en evitant murs et unites ; -1 si > limit ou inaccessible. */
    private static int bfsDist(Combat combat, BlockPos from, BlockPos to, int limit, Entity mover) {
        Map<BlockPos, Integer> dist = new HashMap<>();
        List<BlockPos> queue = new ArrayList<>();
        dist.put(from, 0);
        queue.add(from);
        while (!queue.isEmpty()) {
            BlockPos c = queue.remove(0);
            int d = dist.get(c);
            if (c.equals(to)) return d;
            if (d >= limit) continue;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos n = new BlockPos(c.getX() + dx, combat.floorY, c.getZ() + dz);
                    if (dist.containsKey(n)) continue;
                    if (!n.equals(to)) {
                        if (!combat.isPlayable(n) || occupiedByUnit(combat, n, mover)) continue;
                    }
                    dist.put(n, d + 1);
                    queue.add(n);
                }
            }
        }
        return -1;
    }

    /** Prochaine case du plus court chemin BFS (ou `from` si bloque). */
    private static BlockPos bfsNext(Combat combat, BlockPos from, BlockPos to, Entity mover) {
        Map<BlockPos, BlockPos> prev = new HashMap<>();
        List<BlockPos> queue = new ArrayList<>();
        prev.put(from, from);
        queue.add(from);
        while (!queue.isEmpty()) {
            BlockPos c = queue.remove(0);
            if (c.equals(to)) break;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos n = new BlockPos(c.getX() + dx, combat.floorY, c.getZ() + dz);
                    if (prev.containsKey(n)) continue;
                    if (!n.equals(to)) {
                        if (!combat.isPlayable(n) || occupiedByUnit(combat, n, mover)) continue;
                    }
                    prev.put(n, c);
                    queue.add(n);
                }
            }
        }
        if (!prev.containsKey(to)) return from;
        BlockPos c = to;
        for (int guard = 0; guard < 512; guard++) {
            BlockPos pr = prev.get(c);
            if (pr == null || pr.equals(from)) return c;
            c = pr;
        }
        return from;
    }

    /** Ligne de vue (Bresenham) : les murs internes bloquent les cases intermediaires. */
    public static boolean hasLos(Combat combat, BlockPos a, BlockPos b) {
        int x = a.getX(), z = a.getZ();
        int x1 = b.getX(), z1 = b.getZ();
        int dx = Math.abs(x1 - x), dz = Math.abs(z1 - z);
        int sx = x < x1 ? 1 : -1, sz = z < z1 ? 1 : -1;
        int err = dx - dz;
        for (int guard = 0; guard < 64; guard++) {
            if (x == x1 && z == z1) return true;
            int e2 = 2 * err;
            if (e2 > -dz) { err -= dz; x += sx; }
            if (e2 < dx) { err += dx; z += sz; }
            if (x == x1 && z == z1) return true;
            if (combat.walls.contains(new BlockPos(x, combat.floorY, z))) return false;
        }
        return true;
    }

    /** La zone LINE s'arrete au premier mur (sorts non percants). */
    private static List<BlockPos> truncateLine(Combat combat, Spell s, BlockPos self, List<BlockPos> zone) {
        if (s.zone != ZoneType.LINE || s.pierce) return zone;
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos c : zone) {
            if (combat.walls.contains(c)) break;
            out.add(c);
        }
        return out;
    }

    /** Les invocations d'un cote jouent (fin du tour de leur maitre). */
    private static void summonsAct(Combat combat, int side) {
        purgeSummons(combat, side);
        for (int id : new java.util.ArrayList<>(combat.summonsOf(side))) {
            // v0.19 : garde — le combat peut s'etre termine entre-temps
            if (side == 0 ? !combat.alive() : !COMBATS.containsKey(combat.playerB)) return;
            Entity e = combat.level.getEntity(id);
            if (!(e instanceof MonsterNpc s)) continue;
            MonsterType mt = s.getMonsterType();
            BlockPos cur = s.blockPosition();

            // cible ennemie la plus proche
            Entity best = null;
            BlockPos tgt = null;
            int bestD = Integer.MAX_VALUE;
            List<Entity> units = new java.util.ArrayList<>();
            if (!combat.pvp()) {
                if (side == 0) {
                    // v0.19 : tous les monstres simultanes sont des cibles
                    for (int mid : combat.mobs) {
                        Entity mo = combat.level.getEntity(mid);
                        if (mo != null && mo.isAlive()) units.add(mo);
                    }
                } else {
                    for (UUID mu : combat.members) {
                        ServerPlayer pa = member(combat, mu);
                        if (pa != null) units.add(pa);
                    }
                }
            } else {
                ServerPlayer opp = online(combat, 1 - side);
                if (opp != null) units.add(opp);
                purgeSummons(combat, 1 - side);
                for (int oid : combat.summonsOf(1 - side)) {
                    Entity oe = combat.level.getEntity(oid);
                    if (oe != null) units.add(oe);
                }
            }
            for (Entity u : units) {
                BlockPos up = u instanceof ServerPlayer sp2
                        ? combat.cells.get(sp2.getUUID()) : u.blockPosition();
                if (up == null) continue;
                int d = ZoneType.chebyshev(cur, up);
                if (d < bestD) {
                    bestD = d;
                    best = u;
                    tgt = up;
                }
            }
            if (best == null) continue;

            // approche case par case
            int pm = mt.pm;
            while (pm > 0 && ZoneType.chebyshev(cur, tgt) > mt.range) {
                int dx = tgt.getX() - cur.getX();
                int dz = tgt.getZ() - cur.getZ();
                int sx = Integer.signum(dx);
                int sz = Integer.signum(dz);
                BlockPos next = new BlockPos(cur.getX() + sx, combat.floorY, cur.getZ() + sz);
                if (!combat.isPlayable(next) || occupiedByUnit(combat, next, s) || next.equals(tgt)) {
                    next = Math.abs(dx) >= Math.abs(dz)
                            ? new BlockPos(cur.getX() + sx, combat.floorY, cur.getZ())
                            : new BlockPos(cur.getX(), combat.floorY, cur.getZ() + sz);
                    if (!combat.isPlayable(next) || occupiedByUnit(combat, next, s) || next.equals(tgt)) break;
                }
                cur = next;
                pm--;
            }
            if (!cur.equals(s.blockPosition())) {
                s.teleportTo(cur.getX() + 0.5, combat.floorY, cur.getZ() + 0.5);
            }
            // v0.18 : piege ennemi ?
            int stDmg = triggerTrap(combat, side, cur);
            if (stDmg > 0) {
                s.hurtCombat(stDmg);
                combat.level.playSound(null, cur, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.6f, 1.3f);
                sendFxId(combat, "trap_trigger", cur, List.of(cur));
                broadcast(combat, "§e💥 " + s.getName().getString() + " déclenche un piège : §c-" + stDmg + "§e.");
                if (s.getCurrentHp() <= 0) {
                    combat.summonsOf(side).remove((Integer) s.getId());
                    s.discard();
                    broadcast(combat, "§7L'invocation est détruite par le piège !");
                    sendStateAll(combat);
                    continue;
                }
            }

            // attaque
            if (ZoneType.chebyshev(cur, tgt) <= mt.range) {
                int dmg = s.getDamage() + RANDOM.nextInt(3);
                combat.level.playSound(null, cur, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 0.5f, 1.2f);
                if (best instanceof ServerPlayer tp) {
                    dmg = resistReduce(tp, dmg);
                    PlayerRpgData td = cap(tp);
                    if (td != null) {
                        td.setHp(td.getHp() - dmg);
                        fr.wakfu.rpg.events.CommonEvents.sync(tp);
                        tp.level().playSound(null, tp.blockPosition(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1f, 1f);
                        broadcast(combat, "§2" + s.getName().getString() + " §rfrappe §c" + tp.getName().getString()
                                + " §7(§c-" + dmg + "§7).");
                        sendFxId(combat, "summon_hit", cur, List.of(combat.cellOf(1 - side)));
                        if (td.getHp() <= 0) {
                            duelEnd(combat, side);
                            return;
                        }
                    }
                } else if (best instanceof MonsterNpc tm) {
                    tm.hurtCombat(dmg);
                    combat.level.playSound(null, tm.blockPosition(), hurtSound(tm.getMonsterType()), SoundSource.HOSTILE, 0.9f, 1.1f);
                    broadcast(combat, "§2" + s.getName().getString() + " §rfrappe §c" + tm.getName().getString()
                            + " §7(§c-" + dmg + "§7).");
                    sendFxId(combat, "summon_hit", cur, List.of(tm.blockPosition()));
                    if (tm.getCurrentHp() <= 0) {
                        if (combat.mobs.contains(tm.getId())) {
                            UUID sumUuid = s.getSummoner();
                            ServerPlayer owner = sumUuid == null ? null : member(combat, sumUuid);
                            if (owner == null) owner = activePlayer(combat);
                            if (owner == null && !combat.members.isEmpty()) owner = member(combat, combat.members.get(0));
                            if (owner != null) {
                                mobDefeated(owner, tm, combat);
                                if (!combat.alive() || combat.mobs.isEmpty()) return;
                            }
                        } else {
                            combat.summonsOf(1 - side).remove((Integer) tm.getId());
                            tm.discard();
                            broadcast(combat, "§7Une invocation adverse est détruite !");
                        }
                    }
                }
            }
        }
    }

    private static void cleanupSummons(Combat combat) {
        for (int side = 0; side < 2; side++) {
            for (int id : combat.summonsOf(side)) {
                Entity e = combat.level.getEntity(id);
                if (e != null) e.discard();
            }
            combat.summonsOf(side).clear();
        }
    }

    // ------------------------------------------------------------ v0.14 : audio
    private static void playSpellSound(Combat combat, Spell s, BlockPos at) {
        SoundEvent ev;
        float vol = 0.9f, pitch = 1f;
        switch (s.id) {
            case "iop_basic": ev = SoundEvents.PLAYER_ATTACK_SWEEP; break;
            case "iop_heavy": ev = SoundEvents.PLAYER_ATTACK_CRIT; pitch = 0.8f; break;
            case "iop_tempete": ev = SoundEvents.EVOKER_CAST_SPELL; break;
            case "iop_pourfendre": ev = SoundEvents.PLAYER_ATTACK_SWEEP; pitch = 0.7f; break;
            case "iop_colere": ev = SoundEvents.GENERIC_EXPLODE; vol = 0.6f; break;
            case "iop_uppercut": ev = SoundEvents.PLAYER_ATTACK_KNOCKBACK; break;
            case "iop_allie": ev = SoundEvents.EVOKER_CAST_SPELL; pitch = 1.2f; break;
            case "cra_basic": ev = SoundEvents.ARROW_SHOOT; break;
            case "cra_snipe": ev = SoundEvents.ARROW_SHOOT; pitch = 0.8f; break;
            case "cra_explosive": ev = SoundEvents.GENERIC_EXPLODE; pitch = 1.4f; vol = 0.6f; break;
            case "cra_pluie": ev = SoundEvents.GENERIC_SPLASH; break;
            case "cra_chercheuse": ev = SoundEvents.EVOKER_PREPARE_SUMMON; break;
            case "iop_bond":
            case "sadida_liane": ev = SoundEvents.ENDERMAN_TELEPORT; break;
            case "iop_sanguinaire": ev = SoundEvents.PLAYER_ATTACK_SWEEP; break;
            case "cra_piege":
            case "cra_piege_explosif":
            case "sadida_roncier": ev = SoundEvents.ROOTED_DIRT_PLACE; break;
            case "cra_tirrapide": ev = SoundEvents.ARROW_SHOOT; pitch = 1.5f; break;
            case "cra_sentinelle": ev = SoundEvents.EVOKER_CAST_SPELL; pitch = 1.3f; break;
            case "sadida_ronce": ev = SoundEvents.VINE_HIT; break;
            case "sadida_poupee": ev = SoundEvents.WOOD_PLACE; pitch = 1.2f; break;
            case "sadida_graine": ev = SoundEvents.ROOTED_DIRT_PLACE; break;
            case "sadida_golem": ev = SoundEvents.WOOD_PLACE; pitch = 0.6f; break;
            case "sadida_sylvestre": ev = SoundEvents.GRASS_BREAK; break;
            case "sadida_soin": ev = SoundEvents.AMETHYST_BLOCK_CHIME; break;
            default: ev = SoundEvents.PLAYER_ATTACK_CRIT;
        }
        combat.level.playSound(null, at, ev, SoundSource.PLAYERS, vol, pitch);
    }

    private static SoundEvent hurtSound(MonsterType t) {
        switch (t) {
            case ZOMBIE: return SoundEvents.ZOMBIE_HURT;
            case SKELETON: return SoundEvents.SKELETON_HURT;
            case BANDIT: case BOSS: return SoundEvents.PILLAGER_HURT;
            default: return SoundEvents.WOOD_HIT;
        }
    }

    /** v0.18/19 : unite ennemie (monstre/joueur/invocation) presente sur la case, ou null. */
    private static Object enemyUnitAt(Combat combat, int side, BlockPos cell) {
        if (!combat.pvp()) {
            if (side == 0) {
                for (int id : combat.mobs) {
                    Entity e = combat.level.getEntity(id);
                    if (e instanceof MonsterNpc m && m.isAlive() && m.blockPosition().equals(cell)) return m;
                }
            }
        } else {
            BlockPos oc = combat.cellOf(1 - side);
            if (oc != null && oc.equals(cell)) {
                ServerPlayer opp = online(combat, 1 - side);
                if (opp != null) return opp;
            }
        }
        if (side == 1) {
            for (UUID u : combat.members) {
                BlockPos c = combat.cells.get(u);
                if (c != null && c.equals(cell)) {
                    ServerPlayer mp2 = member(combat, u);
                    if (mp2 != null) return mp2;
                }
            }
        }
        for (int sid : combat.summonsOf(1 - side)) {
            Entity se = combat.level.getEntity(sid);
            if (se instanceof MonsterNpc sm && sm.isAlive() && sm.blockPosition().equals(cell)) return sm;
        }
        return null;
    }

    /** v0.18 : declenche le piege ennemi pose sur la case ; renvoie ses degats bruts (0 = aucun). */
    private static int triggerTrap(Combat combat, int moverSide, BlockPos cell) {
        for (var it = combat.traps.iterator(); it.hasNext(); ) {
            int[] t = it.next();
            if (t[0] == cell.getX() && t[1] == cell.getZ() && t[2] != moverSide) {
                it.remove();
                return t[3];
            }
        }
        return 0;
    }

    /** v0.18 : vol de vie — soigne le lanceur. */
    private static void healCaster(Combat combat, ServerPlayer p, int amount) {
        if (amount <= 0) return;
        PlayerRpgData d = cap(p);
        if (d == null) return;
        d.setHp(Math.min(d.maxHp() + armorBonusHp(p), d.getHp() + amount));
        fr.wakfu.rpg.events.CommonEvents.sync(p);
        sendFxId(combat, "vol_heal", p.blockPosition(),
                List.of(combat.cells.getOrDefault(p.getUUID(), p.blockPosition())));
        msg(p, "§dLe sort te rend " + amount + " PV.");
    }

    private static void sendFxId(Combat combat, String fxId, BlockPos caster, List<BlockPos> cells) {
        var fx = new fr.wakfu.rpg.network.CombatFxPacket(
                fxId, caster.getX(), caster.getZ(), combat.floorY, cells);
        for (int side = 0; side < 2; side++) {
            ServerPlayer pl = online(combat, side);
            if (pl != null) Network.sendToPlayer(pl, fx);
        }
    }

    /** v0.12 : envoie les effets visuels du sort aux deux camps. */
    private static void sendFx(Combat combat, Spell s, BlockPos caster, List<BlockPos> zone) {
        var fx = new fr.wakfu.rpg.network.CombatFxPacket(
                s.id, caster.getX(), caster.getZ(), combat.floorY, zone);
        for (int side = 0; side < 2; side++) {
            ServerPlayer p = online(combat, side);
            if (p != null) Network.sendToPlayer(p, fx);
        }
    }

    private static void broadcast(Combat combat, String text) {
        for (int side = 0; side < 2; side++) {
            ServerPlayer p = online(combat, side);
            if (p != null) p.displayClientMessage(Component.literal(text), true);
        }
    }
}


