package fr.wakfu.rpg.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import fr.wakfu.rpg.entity.MonsterNpc;
import fr.wakfu.rpg.entity.DungeonNpc;
import fr.wakfu.rpg.core.combat.CombatManager;
import fr.wakfu.rpg.network.Network;
import fr.wakfu.rpg.network.OpenDungeonPacket;
import fr.wakfu.rpg.registry.ModEntities;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

/** Dungeon entrances, their floating labels and the encounter start flow. */
public final class DungeonManager {
    private static final String GATE_TAG = "wakfurpg_dungeon_gate";
    private DungeonManager() { }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        var set = Commands.literal("set").then(Commands.argument("id", StringArgumentType.word())
                .then(Commands.argument("niveauMin", IntegerArgumentType.integer(1, 50))
                        .then(Commands.argument("niveauMax", IntegerArgumentType.integer(1, 50))
                                .then(Commands.argument("nom", StringArgumentType.greedyString())
                                        .executes(c -> setGate(c.getSource(), StringArgumentType.getString(c, "id"),
                                                IntegerArgumentType.getInteger(c, "niveauMin"),
                                                IntegerArgumentType.getInteger(c, "niveauMax"),
                                                StringArgumentType.getString(c, "nom")))))));
        var delete = Commands.literal("delete").then(Commands.argument("id", StringArgumentType.word())
                .executes(c -> delete(c.getSource(), StringArgumentType.getString(c, "id"))));
        dispatcher.register(Commands.literal("wakfudungeon").requires(s -> s.hasPermission(2))
                .then(set)
                .then(Commands.literal("list").executes(c -> list(c.getSource())))
                .then(delete));
    }

    private static int setGate(CommandSourceStack source, String id, int min, int max, String name) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        if (min > max) { source.sendFailure(Component.literal("Le niveau minimum doit être inférieur au maximum.")); return 0; }
        String cleanId = id.toLowerCase().replaceAll("[^a-z0-9_-]", "");
        if (cleanId.isBlank()) { source.sendFailure(Component.literal("Identifiant invalide.")); return 0; }
        String cleanName = name.trim();
        if (cleanName.isBlank() || cleanName.length() > 48) {
            source.sendFailure(Component.literal("Le nom doit contenir entre 1 et 48 caractères.")); return 0;
        }
        DungeonRegistry registry = DungeonRegistry.get(player.getServer());
        DungeonRegistry.Gate previous = registry.get(cleanId);
        if (previous != null) removeEntity(player.getServer(), previous);
        DungeonRegistry.Gate gate = new DungeonRegistry.Gate();
        gate.id = cleanId; gate.name = cleanName;
        gate.dimension = player.serverLevel().dimension().location().toString();
        BlockPos pos = player.blockPosition();
        gate.x = pos.getX(); gate.y = pos.getY(); gate.z = pos.getZ();
        gate.minLevel = min; gate.maxLevel = max;
        registry.put(gate);
        spawnGate(player.serverLevel(), gate);
        source.sendSuccess(() -> Component.literal("Entrée de donjon « " + cleanName + " » posée ici (niv. " + min + "–" + max + ")."), true);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        DungeonRegistry registry = DungeonRegistry.get(source.getServer());
        if (registry.all().isEmpty()) { source.sendSuccess(() -> Component.literal("Aucun donjon enregistré."), false); return 0; }
        for (DungeonRegistry.Gate g : registry.all()) {
            source.sendSuccess(() -> Component.literal("§6" + g.id + " §f— " + g.name + " §7(niv. "
                    + g.minLevel + "–" + g.maxLevel + ", " + g.dimension + " @ " + g.x + " " + g.y + " " + g.z + ")"), false);
        }
        return registry.all().size();
    }

    private static int delete(CommandSourceStack source, String id) {
        DungeonRegistry registry = DungeonRegistry.get(source.getServer());
        DungeonRegistry.Gate gate = registry.remove(id);
        if (gate == null) { source.sendFailure(Component.literal("Donjon introuvable : " + id)); return 0; }
        removeEntity(source.getServer(), gate);
        source.sendSuccess(() -> Component.literal("Entrée du donjon « " + gate.name + " » supprimée."), true);
        return 1;
    }

    public static void ensureDefaults(MinecraftServer server) {
        ServerLevel tutorial = server.overworld();
        ServerLevel grecos = server.getLevel(IslandMap.DIMENSION);
        if (tutorial == null || grecos == null) return;
        DungeonRegistry registry = DungeonRegistry.get(server);
        if (registry.get("repaire_bandits") == null) {
            BlockPos c = Dungeon.center(tutorial);
            int x = c.getX() - 10, z = c.getZ();
            createDefault(server, tutorial, "repaire_bandits", "Repaire des Bandits", 5, 12,
                    new BlockPos(x, tutorial.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z));
        }
        if (registry.get("crypte_grecos") == null) {
            BlockPos p = safeSite(grecos, IslandMap.spawn(grecos).offset(96, 0, 48));
            createDefault(server, grecos, "crypte_grecos", "Crypte des Épaves", 5, 25, p);
        }
        if (registry.get("abime_grecos") == null) {
            BlockPos p = safeSite(grecos, IslandMap.spawn(grecos).offset(420, 0, 300));
            createDefault(server, grecos, "abime_grecos", "Abîme de Grecos", 25, 50, p);
        }
        // Migrate legacy armor-stand signs to a visible NPC for reliable interaction.
        for (DungeonRegistry.Gate gate : registry.all()) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION,
                    new ResourceLocation(gate.dimension)));
            if (level != null) spawnGate(level, gate);
        }
    }

    private static BlockPos safeSite(ServerLevel level, BlockPos desired) {
        for (int r = 0; r <= 96; r += 4) {
            for (int dx = -r; dx <= r; dx += 4) for (int dz = -r; dz <= r; dz += 4) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                int x = desired.getX() + dx, z = desired.getZ() + dz;
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                BlockPos feet = new BlockPos(x, y, z);
                if (level.getBlockState(feet.below()).isSolid() && level.getBlockState(feet).canBeReplaced()
                        && level.getBlockState(feet.above()).canBeReplaced()
                        && level.getFluidState(feet).isEmpty()) return feet;
            }
        }
        return IslandMap.safeArrival(level);
    }

    private static void createDefault(MinecraftServer server, ServerLevel level, String id, String name,
                                      int min, int max, BlockPos pos) {
        DungeonRegistry.Gate gate = new DungeonRegistry.Gate();
        gate.id = id; gate.name = name; gate.dimension = level.dimension().location().toString();
        gate.x = pos.getX(); gate.y = pos.getY(); gate.z = pos.getZ(); gate.minLevel = min; gate.maxLevel = max;
        DungeonRegistry.get(server).put(gate);
        spawnGate(level, gate);
    }

    private static void spawnGate(ServerLevel level, DungeonRegistry.Gate gate) {
        BlockPos pos = new BlockPos(gate.x, gate.y, gate.z);
        level.getChunkAt(pos);
        for (ArmorStand old : level.getEntitiesOfClass(ArmorStand.class, new AABB(pos).inflate(3), a ->
                a.getTags().contains(GATE_TAG) && a.getPersistentData().getString("dungeonId").equals(gate.id))) old.discard();
        for (DungeonNpc old : level.getEntitiesOfClass(DungeonNpc.class, new AABB(pos).inflate(3), n -> n.gateId().equals(gate.id))) old.discard();
        DungeonNpc npc = new DungeonNpc(ModEntities.DUNGEON_NPC.get(), level);
        npc.setGateId(gate.id);
        npc.setCustomName(Component.literal("Gardien · " + gate.name));
        npc.setCustomNameVisible(true);
        npc.moveTo(gate.x + 0.5, gate.y, gate.z + 0.5, 180, 0);
        npc.addTag(GATE_TAG);
        level.addFreshEntity(npc);
        ArmorStand sign = new ArmorStand(level, gate.x + 0.5, gate.y + 2.05, gate.z + 0.5);
        sign.addTag(GATE_TAG);
        sign.setInvisible(true);
        sign.setNoGravity(true);
        sign.setInvulnerable(true);
        sign.setCustomName(Component.literal("§6✦ " + gate.name + " §e| Clic droit au gardien"));
        sign.setCustomNameVisible(true);
        sign.getPersistentData().putString("dungeonId", gate.id);
        level.addFreshEntity(sign);
    }

    private static void removeEntity(MinecraftServer server, DungeonRegistry.Gate gate) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(gate.dimension));
        ServerLevel level = server.getLevel(key);
        if (level == null) return;
        BlockPos pos = new BlockPos(gate.x, gate.y, gate.z);
        for (ArmorStand a : level.getEntitiesOfClass(ArmorStand.class, new AABB(pos).inflate(3), e ->
                e.getTags().contains(GATE_TAG) && e.getPersistentData().getString("dungeonId").equals(gate.id))) a.discard();
        for (DungeonNpc npc : level.getEntitiesOfClass(DungeonNpc.class, new AABB(pos).inflate(3), e -> e.gateId().equals(gate.id))) npc.discard();
    }

    public static void openPanel(ServerPlayer player, String id) {
        DungeonRegistry.Gate gate = DungeonRegistry.get(player.getServer()).get(id);
        if (gate == null) {
            player.sendSystemMessage(Component.literal("Cette entrée de donjon n'est plus disponible."));
            return;
        }
        Network.sendToPlayer(player, new OpenDungeonPacket(gate.id, gate.name, gate.minLevel, gate.maxLevel));
    }

    public static boolean interactGate(ServerPlayer player, ArmorStand stand) {
        if (!stand.getTags().contains(GATE_TAG)) return false;
        String id = stand.getPersistentData().getString("dungeonId");
        DungeonRegistry.Gate gate = DungeonRegistry.get(player.getServer()).get(id);
        if (gate == null) { stand.discard(); return true; }
        if (player.distanceToSqr(stand) > 25) return true;
        Network.sendToPlayer(player, new OpenDungeonPacket(gate.id, gate.name, gate.minLevel, gate.maxLevel));
        return true;
    }

    public static void join(ServerPlayer player, String id) {
        DungeonRegistry.Gate gate = DungeonRegistry.get(player.getServer()).get(id);
        if (gate == null) { player.sendSystemMessage(Component.literal("Cette entrée de donjon n'est plus disponible.")); return; }
        if (!player.serverLevel().dimension().location().toString().equals(gate.dimension)
                || player.distanceToSqr(gate.x + 0.5, gate.y, gate.z + 0.5) > 64) {
            player.sendSystemMessage(Component.literal("Approche-toi de l'entrée pour rejoindre ce donjon.")); return;
        }
        if (CombatManager.inCombat(player.getUUID())) { player.sendSystemMessage(Component.literal("Termine d'abord ton combat.")); return; }
        if (PartyManager.partyOf(player.getUUID()) != null && PartyManager.partyOf(player.getUUID()).members.size() > 4) {
            player.sendSystemMessage(Component.literal("Un groupe de quatre aventuriers maximum peut entrer.")); return;
        }
        PlayerRpgData data = player.getCapability(ModCapabilities.RPG).orElse(null);
        if (data == null || !data.hasClass()) { player.sendSystemMessage(Component.literal("Choisis d'abord ta classe.")); return; }
        if (data.getLevel() < gate.minLevel) {
            player.sendSystemMessage(Component.literal("Niveau minimum requis : " + gate.minLevel + ".")); return;
        }

        ServerLevel level = player.serverLevel();
        BlockPos p = new BlockPos(gate.x, gate.y, gate.z);
        int base = Math.max(gate.minLevel, Math.min(gate.maxLevel, data.getLevel()));
        MonsterType[] types = base < 15
                ? new MonsterType[]{MonsterType.BANDIT, MonsterType.ASH_SCOUT, MonsterType.ROAD_BRIGAND}
                : base < 30 ? new MonsterType[]{MonsterType.ROAD_ASSASSIN, MonsterType.HEAVY_WARRIOR, MonsterType.FOREST_SENTINEL}
                : base < 42 ? new MonsterType[]{MonsterType.ROOT_WARDEN, MonsterType.BONE_ARCHER, MonsterType.SIEGE_GOLEM}
                : new MonsterType[]{MonsterType.SIEGE_GOLEM, MonsterType.ROAD_ASSASSIN, MonsterType.BOSS};
        MonsterNpc first = null;
        for (int i = 0; i < types.length; i++) {
            int x = p.getX() + 3 + (i % 2) * 2, z = p.getZ() + 3 + (i / 2) * 2;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            MonsterNpc mob = new MonsterNpc(ModEntities.MONSTER.get(), level);
            int mobLevel = Math.max(gate.minLevel, Math.min(gate.maxLevel, base + (i - 1) * Math.max(1, (gate.maxLevel - gate.minLevel) / 8)));
            mob.init(types[i], mobLevel);
            mob.addTag("dungeon_encounter_" + gate.id);
            mob.setHome(x, y, z);
            mob.moveTo(x + 0.5, y, z + 0.5, 0, 0);
            level.addFreshEntity(mob);
            if (first == null) first = mob;
        }
        if (first != null) CombatManager.startCombat(player, first);
    }
}
