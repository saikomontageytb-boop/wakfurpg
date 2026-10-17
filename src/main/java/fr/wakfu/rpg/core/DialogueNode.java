package fr.wakfu.rpg.core;

import java.util.List;

/** Un noeud de dialogue envoye au client : qui parle, quoi, et les choix. */
public class DialogueNode {
    public final String npcName;
    public final String text;
    public final List<String> choices;

    public DialogueNode(String npcName, String text, List<String> choices) {
        this.npcName = npcName;
        this.text = text;
        this.choices = choices;
    }
}
