package fr.wakfu.rpg.core;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * v0.18 : coffre/banque du spawn — un coffre dore visible sur une estrade,
 * ouvrant un inventaire de 27 slots SAUVEGARDE PAR JOUEUR (chaque joueur
 * a son propre contenu, partage nulle part).
 */
public final class Bank {

    public static final int SIZE = 27;

    /** Containers ouverts : joueur -> son container bancaire. */
    public static final Map<UUID, SimpleContainer> OPEN = new HashMap<>();

    private Bank() {
    }

    /** Position exacte du coffre (deterministe, proche du spawn). */
    public static BlockPos chestPos(ServerLevel level) {
        BlockPos s = level.getSharedSpawnPos();
        return new BlockPos(s.getX() - 7, 0, s.getZ() - 2);
    }

    /** Construit l'estrade + le coffre si absents (au demarrage serveur). */
    public static void ensureBank(ServerLevel level) {
        BlockPos p = chestPos(level);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, p.getX(), p.getZ());
        if (y < level.getMinBuildHeight() + 1) return;
        level.getChunk(p.getX() >> 4, p.getZ() >> 4);
        BlockPos base = new BlockPos(p.getX(), y - 1, p.getZ());
        if (level.getBlockState(p).getBlock() != Blocks.CHEST) {
            // 3x3 socle d'or + coffre au centre
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.setBlock(base.offset(dx, 0, dz), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
                }
            }
            level.setBlock(base.offset(0, 1, 0), Blocks.CHEST.defaultBlockState(), 3);
        }
    }

    /** Ouvre la banque personnelle du joueur. */
    public static void open(ServerPlayer player) {
        PlayerRpgData data = player.getCapability(ModCapabilities.RPG).resolve().orElse(null);
        if (data == null) return;
        ListTag tag = data.getBankTag();
        SimpleContainer c = new SimpleContainer(SIZE);
        for (int i = 0; i < tag.size() && i < SIZE; i++) {
            c.setItem(i, ItemStack.of(tag.getCompound(i)));
        }
        OPEN.put(player.getUUID(), c);
        player.openMenu(new SimpleMenuProvider(
                (id, inv, pl) -> ChestMenu.threeRows(id, inv, c),
                Component.translatable("screen.wakfurpg.bank")));
    }

    /** Sauvegarde le container bancaire dans les donnees du joueur (a la fermeture). */
    public static void save(ServerPlayer player, SimpleContainer c) {
        PlayerRpgData data = player.getCapability(ModCapabilities.RPG).resolve().orElse(null);
        if (data == null) return;
        ListTag tag = new ListTag();
        for (int i = 0; i < c.getContainerSize(); i++) {
            tag.add(c.getItem(i).save(new net.minecraft.nbt.CompoundTag()));
        }
        data.setBankTag(tag);
        OPEN.remove(player.getUUID());
    }
}
