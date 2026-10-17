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

/**
 * Commandes /rpg (tranche 3) : zones protegees + outils admin.
 * Admin = permission >= 2 ; une zone appartient a son createur.
 */
public class RpgCommands {

    public static void register(CommandDispatcher<CommandSourceStack> d) {
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
        zd.zones.add(z);
        zd.setDirty();
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
        if (!(src.getEntity() instanceof ServerPlayer sp)) {
            src.sendFailure(Component.literal("Réservé aux joueurs."));
            return 0;
        }
        ZoneData zd = ZoneData.get(sp.serverLevel());
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
