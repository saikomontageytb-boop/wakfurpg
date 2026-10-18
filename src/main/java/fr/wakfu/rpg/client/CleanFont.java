package fr.wakfu.rpg.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.ResourceLocation;

/** A clean, non-pixel UI face from Minecraft's built-in uniform font. */
final class CleanFont {
    private static final ResourceLocation ID = new ResourceLocation("minecraft", "uniform");
    private static Font cached;

    static Font get() {
        if (cached != null) return cached;
        Minecraft mc = Minecraft.getInstance();
        try {
            for (java.lang.reflect.Field field : Minecraft.class.getFields()) {
                if (field.getType().getSimpleName().equals("FontManager")) {
                    cached = read(field, mc);
                    if (cached != null) return cached;
                }
            }
            for (java.lang.reflect.Field field : Minecraft.class.getDeclaredFields()) {
                if (field.getType().getSimpleName().equals("FontManager")) {
                    field.setAccessible(true);
                    cached = read(field, mc);
                    if (cached != null) return cached;
                }
            }
        } catch (Exception ignored) { }
        return mc.font;
    }

    private static Font read(java.lang.reflect.Field field, Minecraft mc) throws Exception {
        Object manager = field.get(mc);
        if (manager == null) return null;
        Object result = manager.getClass().getMethod("getFont", ResourceLocation.class).invoke(manager, ID);
        return result instanceof Font font ? font : null;
    }

    private CleanFont() { }
}
