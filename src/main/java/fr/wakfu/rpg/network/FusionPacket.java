package fr.wakfu.rpg.network;

import fr.wakfu.rpg.core.Fusion;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * v0.16 : Client -> serveur — demande de fusion dans l'Atelier sombre.
 * L'index pointe vers la liste deterministe Fusion.groups() (cote client
 * et serveur, meme inventaire synchronise => meme liste).
 */
public class FusionPacket {
    public final int index;

    public FusionPacket(int index) {
        this.index = index;
    }

    public static void encode(FusionPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.index);
    }

    public static FusionPacket decode(FriendlyByteBuf buf) {
        return new FusionPacket(buf.readInt());
    }

    public static void handle(FusionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var player = ctx.get().getSender();
            if (player == null) return;
            if (msg.index >= 100 && msg.index < 104) {
                int slot = msg.index - 100;
                int needed = 6;
                List<ItemStack> items = player.getInventory().items;
                for (ItemStack stack : items) {
                    if (stack.is(fr.wakfu.rpg.registry.ModItems.WAKFU_INGOT.get()))
                        needed -= Math.min(needed, stack.getCount());
                    if (needed <= 0) break;
                }
                if (needed > 0) {
                    player.displayClientMessage(Component.literal("§cIl faut 6 lingots de Wakfu pour forger cette pièce."), true);
                    return;
                }
                needed = 6;
                for (int i = 0; i < items.size() && needed > 0; i++) {
                    ItemStack stack = items.get(i);
                    if (stack.is(fr.wakfu.rpg.registry.ModItems.WAKFU_INGOT.get())) {
                        int take = Math.min(needed, stack.getCount());
                        stack.shrink(take);
                        needed -= take;
                        if (stack.isEmpty()) items.set(i, ItemStack.EMPTY);
                    }
                }
                String[] slots = {"head", "chest", "legs", "feet"};
                ItemStack out = fr.wakfu.rpg.item.WakfuArmorItem.makePiece(slots[slot], "commune", 1,
                        net.minecraft.util.RandomSource.create());
                String name = out.getHoverName().getString();
                if (!player.getInventory().add(out)) player.drop(out, false);
                player.playSound(net.minecraft.sounds.SoundEvents.SMITHING_TABLE_USE, 0.9f, 0.8f);
                player.displayClientMessage(Component.literal("§aPièce forgée : " + name), true);
                fr.wakfu.rpg.events.CommonEvents.sync(player);
                return;
            }
            List<Fusion.Group> groups = Fusion.groups(player);
            if (msg.index < 0 || msg.index >= groups.size()) return;
            Fusion.Group g = groups.get(msg.index);

            // consomme 4 exemplaires
            int need = 4;
            List<ItemStack> items = player.getInventory().items;
            for (int i = 0; i < items.size() && need > 0; i++) {
                ItemStack s = items.get(i);
                if (g.key.equals(Fusion.keyOf(s))) {
                    int take = Math.min(need, s.getCount());
                    s.shrink(take);
                    need -= take;
                    if (s.isEmpty()) items.set(i, ItemStack.EMPTY);
                }
            }
            if (need > 0) {
                player.displayClientMessage(Component.literal("§cFusion impossible : objets insuffisants."), true);
                return;
            }
            ItemStack out = Fusion.fused(g);
            String outName = out.getHoverName().getString();   // v0.24 : avant add()
            if (!player.getInventory().add(out)) player.drop(out, false);
            player.playSound(net.minecraft.sounds.SoundEvents.SMITHING_TABLE_USE, 0.9f, 0.8f);
            player.playSound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 0.5f, 1.4f);
            player.displayClientMessage(Component.literal("§5★ Fusion réussie : " + outName
                    + " §7(stats ×1.5)"), true);
            fr.wakfu.rpg.core.Quests.progress(player, "fusion", 1);
            fr.wakfu.rpg.events.CommonEvents.sync(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
