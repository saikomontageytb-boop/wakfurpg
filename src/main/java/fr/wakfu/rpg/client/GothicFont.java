package fr.wakfu.rpg.client;

import fr.wakfu.rpg.WakfuRPG;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;

/** Police gothique du mod (assets/wakfurpg/font/gothic.json), repli sur la police vanilla. */
public class GothicFont {
    private static final ResourceLocation ID = new ResourceLocation(WakfuRPG.MODID, "gothic");
    private static Font cached = null;

    public static Font get() {
        if (cached != null) return cached;
        Minecraft mc = Minecraft.getInstance();
        try {
            java.lang.reflect.Field f = null;
            for (java.lang.reflect.Field cand : Minecraft.class.getFields()) {
                if (cand.getType().getSimpleName().equals("FontManager")) {
                    f = cand;
                    break;
                }
            }
            if (f == null) {
                for (java.lang.reflect.Field cand : Minecraft.class.getDeclaredFields()) {
                    if (cand.getType().getSimpleName().equals("FontManager")) {
                        cand.setAccessible(true);
                        f = cand;
                        break;
                    }
                }
            }
            if (f != null) {
                Object fm = f.get(mc);
                if (fm != null) {
                    java.lang.reflect.Method m = fm.getClass().getMethod("getFont", ResourceLocation.class);
                    Font got = (Font) m.invoke(fm, ID);
                    if (got != null) {
                        cached = got;
                        return cached;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return mc.font;
    }

    private GothicFont() {}
}
