package fr.wakfu.rpg.core;

import fr.wakfu.rpg.network.CombatFxPacket;
import fr.wakfu.rpg.network.CombatStatePacket;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Point d'ancrage commun : le code commun ne reference JAMAIS de classe
 * client (Screen, Minecraft...). Le client branche ses implementations
 * dans FMLClientSetupEvent (voir client.ClientEvents).
 */
public class ClientProxyHook {
    /** (entityId, DialogueNode) -> ouvre l'ecran de dialogue cote client. */
    public static BiConsumer<Integer, DialogueNode> openDialogue = (id, node) -> {};

    /** Etat de combat recu -> stocke pour le HUD cote client. */
    public static Consumer<CombatStatePacket> onCombatState = (state) -> {};

    /** v0.12 : effets visuels de sort recus -> particules cote client. */
    public static Consumer<CombatFxPacket> onCombatFx = (fx) -> {};

    /** v0.25 : Hotel des Ventes — true = ouvrir l'ecran du marche cote client. */
    public static Consumer<Boolean> openMarket = (open) -> {};

    private ClientProxyHook() {}
}
