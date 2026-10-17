package fr.wakfu.rpg.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class ClientKeyBindings {
    public static final KeyMapping SKILLS = new KeyMapping(
            "key.wakfurpg.skills",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.wakfurpg.category");

    /** v0.15 : Codex (bestiaire / zones / donjon / butin). */
    public static final KeyMapping CODEX = new KeyMapping(
            "key.wakfurpg.codex",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "key.wakfurpg.category");

    /** v0.18 : Sac & equipement RPG. */
    public static final KeyMapping EQUIP = new KeyMapping(
            "key.wakfurpg.equip",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_I,
            "key.wakfurpg.category");

    private ClientKeyBindings() {
    }
}
