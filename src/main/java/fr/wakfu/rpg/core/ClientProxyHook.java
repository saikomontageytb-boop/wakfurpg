package fr.wakfu.rpg.core;

import fr.wakfu.rpg.network.CombatFxPacket;
import fr.wakfu.rpg.network.CombatStatePacket;
import fr.wakfu.rpg.network.DamagePopupPacket;
import fr.wakfu.rpg.core.DungeonInfo;

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
    public static Runnable openClassSelect = () -> {};

    /** Etat de combat recu -> stocke pour le HUD cote client. */
    public static Consumer<CombatStatePacket> onCombatState = (state) -> {};

    /** v0.12 : effets visuels de sort recus -> particules cote client. */
    public static Consumer<CombatFxPacket> onCombatFx = (fx) -> {};
    public static Consumer<DamagePopupPacket> onDamagePopup = (popup) -> {};

    /** v0.25 : Hotel des Ventes — true = ouvrir l'ecran du marche cote client. */
    public static Consumer<Boolean> openMarket = (open) -> {};
    public static Consumer<DungeonInfo> openDungeon = info -> {};

    private ClientProxyHook() {}
}
