package fr.wakfu.rpg.network;

import fr.wakfu.rpg.core.Market;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * v0.25 : Serveur -> client : annonces de l'Hôtel des Ventes.
 * open = true -> ouvre l'écran du marché (clic sur le PNJ) ;
 * open = false -> simple rafraîchissement des données.
 */
public class MarketDataPacket {
    private final List<Market.Listing> listings;
    private final boolean open;

    public MarketDataPacket(List<Market.Listing> listings, boolean open) {
        this.listings = listings;
        this.open = open;
    }

    public static void encode(MarketDataPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.open);
        buf.writeVarInt(msg.listings.size());
        for (Market.Listing l : msg.listings) {
            buf.writeVarInt(l.id());
            buf.writeUUID(l.seller());
            buf.writeUtf(l.sellerName());
            buf.writeItem(l.stack());
            buf.writeVarInt(l.price());
        }
    }

    public static MarketDataPacket decode(FriendlyByteBuf buf) {
        boolean open = buf.readBoolean();
        int n = Math.min(buf.readVarInt(), 200);
        List<Market.Listing> l = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int id = buf.readVarInt();
            UUID seller = buf.readUUID();
            String name = buf.readUtf();
            ItemStack stack = buf.readItem();
            int price = buf.readVarInt();
            l.add(new Market.Listing(id, seller, name, stack, price));
        }
        return new MarketDataPacket(l, open);
    }

    public static void handle(MarketDataPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            fr.wakfu.rpg.client.ClientMarket.set(msg.listings);
            if (msg.open) fr.wakfu.rpg.core.ClientProxyHook.openMarket.accept(true);
        });
        ctx.get().setPacketHandled(true);
    }
}
