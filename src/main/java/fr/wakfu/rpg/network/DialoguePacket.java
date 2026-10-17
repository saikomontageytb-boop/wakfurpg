package fr.wakfu.rpg.network;

import fr.wakfu.rpg.core.ClientProxyHook;
import fr.wakfu.rpg.core.DialogueNode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Serveur -> client : ouvre (ou met a jour) l'ecran de dialogue. */
public class DialoguePacket {
    private final int entityId;
    private final DialogueNode node;

    public DialoguePacket(int entityId, DialogueNode node) {
        this.entityId = entityId;
        this.node = node;
    }

    public static void encode(DialoguePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeUtf(msg.node.npcName, 128);
        buf.writeUtf(msg.node.text, 4096);
        buf.writeVarInt(msg.node.choices.size());
        for (String c : msg.node.choices) {
            buf.writeUtf(c, 256);
        }
    }

    public static DialoguePacket decode(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        String name = buf.readUtf(128);
        String text = buf.readUtf(4096);
        int n = buf.readVarInt();
        List<String> choices = new ArrayList<>();
        for (int i = 0; i < n; i++) choices.add(buf.readUtf(256));
        return new DialoguePacket(id, new DialogueNode(name, text, choices));
    }

    public static void handle(DialoguePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientProxyHook.openDialogue.accept(msg.entityId, msg.node));
        ctx.get().setPacketHandled(true);
    }
}
