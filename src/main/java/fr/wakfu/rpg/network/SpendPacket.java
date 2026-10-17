package fr.wakfu.rpg.network;

import fr.wakfu.rpg.core.ModCapabilities;
import fr.wakfu.rpg.core.spell.Spell;
import fr.wakfu.rpg.core.spell.Spells;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> serveur : depense de points.
 * v0.16 : 5 points de STATUT par niveau (vie/force/sagesse/resistance, 1 pt chacun)
 * et 1 point de SORT par niveau (amelioration : cout 1 puis 2 puis 3 puis 5).
 */
public class SpendPacket {
    public static final int[] SPELL_COSTS = {1, 2, 3, 5, 5};
    public static final int MAX_SPELL_LEVEL = 5;

    public final String kind; // "hp" | "str" | "wis" | "res" | "spell" | "equip" | (legacy "pa"/"pm")
    public final String id;   // id du sort si kind=spell/equip

    public SpendPacket(String kind, String id) {
        this.kind = kind;
        this.id = id;
    }

    public static void encode(SpendPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.kind, 16);
        buf.writeUtf(msg.id, 32);
    }

    public static SpendPacket decode(FriendlyByteBuf buf) {
        return new SpendPacket(buf.readUtf(16), buf.readUtf(32));
    }

    public static void handle(SpendPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = ctx.get().getSender();
            if (player == null) return;
            player.getCapability(ModCapabilities.RPG).ifPresent(data -> {
                switch (msg.kind) {
                    case "hp" -> {
                        if (data.spendSkillPoints(1)) {
                            data.addBonusHp(5);
                            data.setHp(data.getHp() + 5);
                            player.displayClientMessage(Component.literal("§c+5 PV max."), true);
                        }
                    }
                    case "str" -> {
                        if (data.spendSkillPoints(1)) {
                            data.addBonusStr(1);
                            player.displayClientMessage(Component.literal("§e+1 Force (§6+1 dégât§e)."), true);
                        }
                    }
                    case "wis" -> {
                        if (data.spendSkillPoints(1)) {
                            data.addBonusWis(1);
                            player.displayClientMessage(Component.literal("§b+1 Sagesse (XP accélérée tous les 10 pts)."), true);
                        }
                    }
                    case "res" -> {
                        if (data.spendSkillPoints(1)) {
                            data.addBonusRes(1);
                            player.displayClientMessage(Component.literal("§a+1 Résistance (-2% dégâts subis)."), true);
                        }
                    }
                    case "spell" -> {
                        int lvl = data.spellLevel(msg.id);
                        if (lvl >= MAX_SPELL_LEVEL) {
                            player.sendSystemMessage(Component.literal("§7Sort déjà au niveau maximum."));
                            return;
                        }
                        Spell sp = Spells.byId(msg.id);
                        if (sp != null && data.getLevel() < sp.unlockLevel) {
                            player.sendSystemMessage(Component.literal("§7Sort verrouillé : niveau " + sp.unlockLevel + " requis."));
                            return;
                        }
                        int cost = SPELL_COSTS[Math.min(lvl, SPELL_COSTS.length - 1)];
                        if (data.spendSpellPts(cost)) {
                            data.addSpellLevel(msg.id);
                            player.sendSystemMessage(Component.literal(
                                    "§eSort amélioré : " + (sp != null ? sp.getName().getString() : msg.id)
                                            + " niv " + data.spellLevel(msg.id) + " §8(-" + cost + " pt" + (cost > 1 ? "s" : "") + ")"));
                        } else {
                            player.sendSystemMessage(Component.literal(
                                    "§cIl te faut " + cost + " points de sort (1 gagné par niveau). Tu en as " + data.getSpellPts() + "."));
                        }
                    }
                    case "equip" -> {
                        Spell sp = Spells.byId(msg.id);
                        if (sp != null && data.getLevel() < sp.unlockLevel) {
                            player.sendSystemMessage(Component.literal("§7Sort verrouillé : niveau " + sp.unlockLevel + " requis."));
                            return;
                        }
                        if (data.isEquipped(msg.id)) {
                            data.toggleEquip(msg.id);
                            player.sendSystemMessage(Component.literal("§7Sort retiré de la barre de combat."));
                        } else if (data.getEquipped().size() >= Spells.MAX_EQUIPPED) {
                            player.sendSystemMessage(Component.literal("§cBarre pleine (" + Spells.MAX_EQUIPPED + " sorts max) : retire-en un d'abord."));
                        } else {
                            data.toggleEquip(msg.id);
                            player.sendSystemMessage(Component.literal("§aSort équipé dans la barre de combat."));
                        }
                    }
                    default -> { }
                }
                fr.wakfu.rpg.events.CommonEvents.sync(player);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
