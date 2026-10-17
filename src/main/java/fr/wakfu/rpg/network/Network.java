package fr.wakfu.rpg.network;

import fr.wakfu.rpg.WakfuRPG;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class Network {
    private static final String PROTOCOL = "7";
    public static SimpleChannel CHANNEL;

    public static void init() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(WakfuRPG.MODID, "main"),
                () -> PROTOCOL,
                PROTOCOL::equals,
                PROTOCOL::equals);

        CHANNEL.registerMessage(0, SyncRpgDataPacket.class,
                SyncRpgDataPacket::encode, SyncRpgDataPacket::decode, SyncRpgDataPacket::handle);
        CHANNEL.registerMessage(1, ChooseClassPacket.class,
                ChooseClassPacket::encode, ChooseClassPacket::decode, ChooseClassPacket::handle);
        CHANNEL.registerMessage(2, DialoguePacket.class,
                DialoguePacket::encode, DialoguePacket::decode, DialoguePacket::handle);
        CHANNEL.registerMessage(3, NpcChoicePacket.class,
                NpcChoicePacket::encode, NpcChoicePacket::decode, NpcChoicePacket::handle);
        CHANNEL.registerMessage(4, CombatStatePacket.class,
                CombatStatePacket::encode, CombatStatePacket::decode, CombatStatePacket::handle);
        CHANNEL.registerMessage(5, SpendPacket.class,
                SpendPacket::encode, SpendPacket::decode, SpendPacket::handle);
        CHANNEL.registerMessage(6, ActionPacket.class,
                ActionPacket::encode, ActionPacket::decode, ActionPacket::handle);
        CHANNEL.registerMessage(7, ClientHelloPacket.class,
                ClientHelloPacket::encode, ClientHelloPacket::decode, ClientHelloPacket::handle);
        CHANNEL.registerMessage(8, CombatFxPacket.class,
                CombatFxPacket::encode, CombatFxPacket::decode, CombatFxPacket::handle);
        CHANNEL.registerMessage(9, FusionPacket.class,
                FusionPacket::encode, FusionPacket::decode, FusionPacket::handle);
        CHANNEL.registerMessage(10, EquipPacket.class,
                EquipPacket::encode, EquipPacket::decode, EquipPacket::handle);
        CHANNEL.registerMessage(11, CombatInvitePacket.class,
                CombatInvitePacket::encode, CombatInvitePacket::decode, CombatInvitePacket::handle);
        CHANNEL.registerMessage(12, JoinCombatPacket.class,
                JoinCombatPacket::encode, JoinCombatPacket::decode, JoinCombatPacket::handle);
        CHANNEL.registerMessage(13, MoneyPacket.class,
                MoneyPacket::encode, MoneyPacket::decode, MoneyPacket::handle);
        CHANNEL.registerMessage(14, SocialRequestPacket.class,
                SocialRequestPacket::encode, SocialRequestPacket::decode, SocialRequestPacket::handle);
        CHANNEL.registerMessage(15, SocialDataPacket.class,
                SocialDataPacket::encode, SocialDataPacket::decode, SocialDataPacket::handle);
        CHANNEL.registerMessage(16, SocialActionPacket.class,
                SocialActionPacket::encode, SocialActionPacket::decode, SocialActionPacket::handle);
        CHANNEL.registerMessage(17, MarketDataPacket.class,
                MarketDataPacket::encode, MarketDataPacket::decode, MarketDataPacket::handle);
        CHANNEL.registerMessage(18, MarketActionPacket.class,
                MarketActionPacket::encode, MarketActionPacket::decode, MarketActionPacket::handle);
        CHANNEL.registerMessage(19, VictoryPacket.class,
                VictoryPacket::encode, VictoryPacket::decode, VictoryPacket::handle);
        CHANNEL.registerMessage(20, ZoomPacket.class,
                ZoomPacket::encode, ZoomPacket::decode, ZoomPacket::handle);
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        if (CHANNEL != null) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }

    public static void sendToServer(Object packet) {
        if (CHANNEL != null) {
            CHANNEL.sendToServer(packet);
        }
    }
}

