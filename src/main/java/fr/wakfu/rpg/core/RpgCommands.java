package fr.wakfu.rpg.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Commandes /rpg (tranche 3) : zones protegees + outils admin.
 * Admin = permission >= 2 ; une zone appartient a son createur.
 */
public class RpgCommands {
    public static void register(CommandDispatcher<CommandSourceStack> d) {
        DungeonManager.registerCommands(d);
        d.register(Commands.literal("rpg")
                .then(Commands.literal("zone")
                        .then(Commands.literal("create")
                                .then(Commands.argument("nom", StringArgumentType.word())
                                        .then(Commands.argument("rayon", IntegerArgumentType.integer(4, 200))
                                                .executes(c -> zoneCreate(c.getSource(),
                                                        StringArgumentType.getString(c, "nom"),
                                                        IntegerArgumentType.getInteger(c, "rayon"))))))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("nom", StringArgumentType.word())
                                        .executes(c -> zoneDelete(c.getSource(), StringArgumentType.getString(c, "nom")))))
                        .then(Commands.literal("add")
                                .then(Commands.argument("nom", StringArgumentType.word())
                                        .then(Commands.argument("joueur", StringArgumentType.word())
                                                .executes(c -> zoneAdd(c.getSource(),
                                                        StringArgumentType.getString(c, "nom"),
                                                        StringArgumentType.getString(c, "joueur"))))))
                        .then(zoneMonsterCommand())
                        .then(Commands.literal("types")
                                .executes(c -> listMonsterTypes(c.getSource())))
                        .then(Commands.literal("list")
                                .executes(c -> zoneList(c.getSource()))))
                .then(Commands.literal("tp")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("cible", EntityArgument.player())
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                                        .executes(c -> tp(c.getSource(),
                                                                EntityArgument.getPlayer(c, "cible"),
                                                                IntegerArgumentType.getInteger(c, "x"),
                                                                IntegerArgumentType.getInteger(c, "y"),
                                                                IntegerArgumentType.getInteger(c, "z"))))))))
                .then(Commands.literal("give")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("cible", EntityArgument.player())
                                .then(Commands.argument("quoi", StringArgumentType.word())
                                        .then(Commands.argument("n", IntegerArgumentType.integer(1, 64))
                                                .executes(c -> give(c.getSource(),
                                                        EntityArgument.getPlayer(c, "cible"),
                                                        StringArgumentType.getString(c, "quoi"),
                                                        IntegerArgumentType.getInteger(c, "n")))))))
                .then(Commands.literal("level")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("cible", EntityArgument.player())
                                .then(Commands.argument("niveau", IntegerArgumentType.integer(1, 100))
                                        .executes(c -> level(c.getSource(),
                                                EntityArgument.getPlayer(c, "cible"),
                                                IntegerArgumentType.getInteger(c, "niveau")))))));
        registerSpawnerCommands(d);
    }

    private static int openClassSelect(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        player.getCapability(ModCapabilities.RPG).ifPresent(data -> {
            if (data.hasClass()) {
                fr.wakfu.rpg.network.ChooseClassPacket.allowClassChange(player.getUUID());
            }
            fr.wakfu.rpg.network.Network.sendToPlayer(player,
                    new fr.wakfu.rpg.network.OpenClassSelectPacket());
        });
        return 1;
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> zoneMonsterCommand() {
        var types = Commands.argument("types", StringArgumentType.greedyString())
                .executes(c -> configureZoneMonsters(
                        c.getSource(),
                        StringArgumentType.getString(c, "nom"),
                        IntegerArgumentType.getInteger(c, "niveauMin"),
                        IntegerArgumentType.getInteger(c, "niveauMax"),
                        IntegerArgumentType.getInteger(c, "maximum"),
                        StringArgumentType.getString(c, "types")));
        var maximum = Commands.argument("maximum", IntegerArgumentType.integer(1, 12)).then(types);
        var maxLevel = Commands.argument("niveauMax", IntegerArgumentType.integer(1, 50)).then(maximum);
        var minLevel = Commands.argument("niveauMin", IntegerArgumentType.integer(1, 50)).then(maxLevel);
        var name = Commands.argument("nom", StringArgumentType.word()).then(minLevel);
        return Commands.literal("monsters").then(name);
    }

    private static void registerSpawnerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        var spawners = Commands.literal("spawner").requires(s -> s.hasPermission(2))
                .then(Commands.literal("add")
                        .then(Commands.argument("nom", StringArgumentType.word())
                                .then(Commands.argument("rayon", IntegerArgumentType.integer(8, 128))
                                        .then(Commands.argument("niveauMin", IntegerArgumentType.integer(1, 50))
                                                .then(Commands.argument("niveauMax", IntegerArgumentType.integer(1, 50))
                                                        .then(Commands.argument("maximum", IntegerArgumentType.integer(1, 12))
                                                                .executes(c -> addSpawner(c.getSource(),
                                                                        StringArgumentType.getString(c, "nom"),
                                                                        IntegerArgumentType.getInteger(c, "rayon"),
                                                                        IntegerArgumentType.getInteger(c, "niveauMin"),
                                                                        IntegerArgumentType.getInteger(c, "niveauMax"),
                                                                        IntegerArgumentType.getInteger(c, "maximum")))))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("nom", StringArgumentType.word())
                                .executes(c -> removeSpawner(c.getSource(), StringArgumentType.getString(c, "nom")))))
                .then(Commands.literal("list").executes(c -> listSpawners(c.getSource())))
                .then(Commands.literal("clear").executes(c -> clearSpawners(c.getSource(), false)))
                .then(Commands.literal("wipe").executes(c -> clearSpawners(c.getSource(), true)));
        var zones = Commands.literal("zone")
                .then(zoneMonsterCommand().requires(s -> s.hasPermission(2)))
                .then(Commands.literal("types").requires(s -> s.hasPermission(2))
                        .executes(c -> listMonsterTypes(c.getSource())));
        dispatcher.register(Commands.literal("wakfu").then(spawners).then(zones));
    }

    private static int configureZoneMonsters(CommandSourceStack source, String name, int minLevel,
                                             int maxLevel, int cap, String ids) {
        ServerPlayer player = source.getEntity() instanceof ServerPlayer p ? p : null;
        if (player == null && !source.hasPermission(2)) {
            source.sendFailure(Component.literal("Cette commande est réservée aux administrateurs depuis la console."));
            return 0;
        }
        if (minLevel > maxLevel) {
            source.sendFailure(Component.literal("Le niveau minimum doit être inférieur au niveau maximum."));
            return 0;
        }
        ServerLevel level = source.getLevel();
        ZoneData data = ZoneData.get(level);
        ZoneData.Zone zone = data.byName(name);
        if (zone == null) {
            source.sendFailure(Component.literal("Zone introuvable dans cette dimension."));
            return 0;
        }
        if (player != null && !source.hasPermission(2)
                && !player.getName().getString().equalsIgnoreCase(zone.owner)) {
            source.sendFailure(Component.literal("Seul le propriétaire de la zone ou un administrateur peut la configurer."));
            return 0;
        }
        if (zone.name.equalsIgnoreCase("spawn")) {
            source.sendFailure(Component.literal("La zone spawn reste sans monstres."));
            return 0;
        }
        List<String> monsters = new java.util.ArrayList<>();
        for (String id : ids.split(",")) {
            String clean = id.trim().toLowerCase(java.util.Locale.ROOT);
            if (clean.isEmpty()) continue;
            if (fr.wakfu.rpg.core.MonsterType.byId(clean) == null) {
                source.sendFailure(Component.literal("Monstre inconnu : " + clean + ". Sépare plusieurs IDs par des virgules."));
                return 0;
            }
            if (!monsters.contains(clean)) monsters.add(clean);
        }
        if (monsters.isEmpty()) {
            source.sendFailure(Component.literal("Indique au moins un ID de monstre."));
            return 0;
        }
        zone.monsterIds.clear();
        zone.monsterIds.addAll(monsters);
        zone.minLevel = minLevel;
        zone.maxLevel = maxLevel;
        zone.maxMobs = cap;
        zone.nextSpawnTick = 0;
        data.setDirty();
        MobSpawnerData.refreshZone(level, zone);
        source.sendSuccess(() -> Component.literal("Zone « " + zone.name + " » : monstres "
                + String.join(", ", zone.monsterIds) + " · niveaux " + minLevel + "–" + maxLevel
                + " · population 1–" + cap + "."), false);
        return 1;
    }

    private static int addSpawner(CommandSourceStack source, String name, int radius, int minLevel,
                                  int maxLevel, int cap) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Utilise cette commande en jeu, à l'emplacement du spawner."));
            return 0;
        }
        if (name.length() > 32 || minLevel > maxLevel) {
            source.sendFailure(Component.literal("Nom trop long ou niveaux inversés."));
            return 0;
        }
        MobSpawnerData data = MobSpawnerData.get(player.serverLevel());
        if (data.get(name) != null) {
            source.sendFailure(Component.literal("Un spawner porte déjà ce nom dans cette dimension."));
            return 0;
        }
        var site = data.add(name, player.blockPosition(), radius, minLevel, maxLevel, cap);
        source.sendSuccess(() -> Component.literal("Spawner invisible « " + site.name + " » créé en "
                + player.serverLevel().dimension().location() + " · rayon " + radius + " · niveaux "
                + minLevel + "–" + maxLevel + " · maximum " + cap + " monstres."), false);
        return 1;
    }

    private static int removeSpawner(CommandSourceStack source, String name) {
        ServerLevel level = source.getLevel();
        if (!MobSpawnerData.get(level).remove(name)) {
            source.sendFailure(Component.literal("Spawner introuvable dans cette dimension."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Spawner « " + name + " » supprimé."), false);
        return 1;
    }

    private static int listSpawners(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        var sites = MobSpawnerData.get(level).all();
        if (sites.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Aucun spawner dans "
                    + level.dimension().location() + "."), false);
            return 1;
        }
        source.sendSuccess(() -> Component.literal("Spawners de " + level.dimension().location() + " :"), false);
        for (var site : sites) source.sendSuccess(() -> Component.literal("§6" + site.name + " §7: "
                + site.x + " " + site.y + " " + site.z + " · r=" + site.radius + " · niv. "
                + site.minLevel + "–" + site.maxLevel + " · max " + site.cap), false);
        return sites.size();
    }

    private static int clearSpawners(CommandSourceStack source, boolean purge) {
        ServerLevel level = source.getLevel();
        MobSpawnerData data = MobSpawnerData.get(level);
        int count = data.all().size();
        data.clear();
        int removed = purge ? MobSpawnerData.purgeLegacyMobs(level) : 0;
        source.sendSuccess(() -> Component.literal("§a" + count + " spawner(s) supprimé(s)"
                + (purge ? " et " + removed + " monstre(s) de terrain effacé(s)." : ".")), false);
        return count + removed;
    }

    private static int listMonsterTypes(CommandSourceStack source) {
        for (fr.wakfu.rpg.core.MonsterType type : fr.wakfu.rpg.core.MonsterType.values()) {
            source.sendSuccess(() -> Component.literal(type.getId() + " — " + type.nameFr), false);
        }
        return fr.wakfu.rpg.core.MonsterType.values().length;
    }

    // ------------------------------------------------------------ zones
    private static int zoneCreate(CommandSourceStack src, String name, int r) {
        if (!(src.getEntity() instanceof ServerPlayer sp)) {
            src.sendFailure(Component.literal("Réservé aux joueurs."));
            return 0;
        }
        ServerLevel level = sp.serverLevel();
        ZoneData zd = ZoneData.get(level);
        if (zd.byName(name) != null) {
            src.sendFailure(Component.literal("Une zone porte déjà ce nom."));
            return 0;
        }
        ZoneData.Zone z = new ZoneData.Zone();
        z.name = name;
        z.owner = sp.getName().getString();
        z.x = sp.blockPosition().getX();
        z.z = sp.blockPosition().getZ();
        z.r = r;
        ZoneData.configureDefaults(z, level);
        zd.zones.add(z);
        zd.setDirty();
        MobSpawnerData.refreshZone(level, z);
        src.sendSuccess(() -> Component.literal("§aZone protégée §6" + name + " §acréée (rayon " + r
                + ", centre " + z.x + "/" + z.z + "). Membres : toi."), false);
        return 1;
    }

    private static int zoneDelete(CommandSourceStack src, String name) {
        if (!(src.getEntity() instanceof ServerPlayer sp)) {
            src.sendFailure(Component.literal("Réservé aux joueurs."));
            return 0;
        }
        ZoneData zd = ZoneData.get(sp.serverLevel());
        ZoneData.Zone z = zd.byName(name);
        if (z == null) {
            src.sendFailure(Component.literal("Zone introuvable : " + name));
            return 0;
        }
        if (!z.isMember(sp) && !sp.hasPermissions(2)) {
            src.sendFailure(Component.literal("Tu n'es pas membre de cette zone."));
            return 0;
        }
        zd.zones.remove(z);
        zd.setDirty();
        src.sendSuccess(() -> Component.literal("§cZone §6" + name + " §csupprimée."), false);
        return 1;
    }

    private static int zoneAdd(CommandSourceStack src, String name, String who) {
        if (!(src.getEntity() instanceof ServerPlayer sp)) {
            src.sendFailure(Component.literal("Réservé aux joueurs."));
            return 0;
        }
        ZoneData zd = ZoneData.get(sp.serverLevel());
        ZoneData.Zone z = zd.byName(name);
        if (z == null) {
            src.sendFailure(Component.literal("Zone introuvable : " + name));
            return 0;
        }
        if (!z.isMember(sp) && !sp.hasPermissions(2)) {
            src.sendFailure(Component.literal("Tu n'es pas membre de cette zone."));
            return 0;
        }
        z.members.add(who);
        zd.setDirty();
        src.sendSuccess(() -> Component.literal("§a§6" + who + " §aajouté à la zone §6" + name + "."), false);
        return 1;
    }

    private static int zoneList(CommandSourceStack src) {
        ZoneData zd = ZoneData.get(src.getLevel());
        if (zd.zones.isEmpty()) {
            src.sendSuccess(() -> Component.literal("§7Aucune zone protégée."), false);
            return 1;
        }
        for (ZoneData.Zone z : zd.zones) {
            src.sendSuccess(() -> Component.literal("§6" + z.name + " §8: centre " + z.x + "/" + z.z
                    + ", rayon " + z.r + ", owner " + z.owner + ", membres " + z.members), false);
        }
        return zd.zones.size();
    }

    // ------------------------------------------------------------ admin
    private static int tp(CommandSourceStack src, ServerPlayer target, int x, int y, int z) {
        target.teleportTo(x + 0.5, y, z + 0.5);
        src.sendSuccess(() -> Component.literal("§a" + target.getName().getString() + " téléporté en "
                + x + " " + y + " " + z + "."), false);
        return 1;
    }

    private static int give(CommandSourceStack src, ServerPlayer target, String what, int n) {
        switch (what.toLowerCase()) {
            case "argent", "money", "lingot", "ingot" -> {
                fr.wakfu.rpg.core.Money.add(target.getUUID(), n);
                fr.wakfu.rpg.core.Money.sync(target);
            }
            case "armure", "armor" -> target.getInventory().add(
                    fr.wakfu.rpg.item.WakfuArmorItem.randomPiece(target.getRandom()));
            case "arme", "weapon" -> target.getInventory().add(
                    fr.wakfu.rpg.item.WakfuWeaponItem.randomWeapon(target.getRandom()));
            case "xp" -> target.getCapability(ModCapabilities.RPG).ifPresent(data -> {
                data.addXp(n);
                fr.wakfu.rpg.events.CommonEvents.sync(target);
            });
            default -> {
                src.sendFailure(Component.literal("Types : argent, armure, arme, xp."));
                return 0;
            }
        }
        src.sendSuccess(() -> Component.literal("§aDonné " + n + " × " + what + " à "
                + target.getName().getString() + "."), false);
        return 1;
    }

    private static int level(CommandSourceStack src, ServerPlayer target, int lvl) {
        target.getCapability(ModCapabilities.RPG).ifPresent(data -> {
            data.setLevel(lvl);
            fr.wakfu.rpg.events.CommonEvents.sync(target);
        });
        src.sendSuccess(() -> Component.literal("§a" + target.getName().getString() + " → niveau " + lvl + "."), false);
        return 1;
    }
}
