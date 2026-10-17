package fr.wakfu.rpg.client;

import fr.wakfu.rpg.core.MonsterType;
import fr.wakfu.rpg.core.PlayerRpgData;
import fr.wakfu.rpg.item.WakfuArmorItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * v0.17 : notifications de decouverte — a chaque synchronisation, on
 * compare le Codex local au precedent ; toute nouveaute (monstre, zone,
 * donjon, butin) affiche un petit parchemin anime en haut a droite.
 */
public class DiscoveryToasts {

    private static final Set<String> seen = new HashSet<>();
    private static final Set<String> zones = new HashSet<>();
    private static final Set<String> items = new HashSet<>();
    private static boolean dungeon = false;
    private static boolean initialized = false;

    private record Toast(String text, long born) {}
    private static final Deque<Toast> queue = new ArrayDeque<>();
    private static final long LIFE_MS = 4200;

    /** Appele apres chaque deserializeNBT cote client (SyncRpgDataPacket). */
    public static synchronized void check(PlayerRpgData data) {
        if (!initialized) {
            seen.addAll(data.getCodexSeen());
            zones.addAll(data.getCodexZones());
            items.addAll(data.getCodexItems().keySet());
            dungeon = data.isCodexDungeon();
            initialized = true;
            return;
        }
        for (String id : data.getCodexSeen()) {
            if (seen.add(id)) {
                MonsterType t = MonsterType.byId(id);
                String name = t != null ? t.getName().getString() : id;
                push("§6✦ Monstre découvert : §f" + name);
            }
        }
        for (String z : data.getCodexZones()) {
            if (zones.add(z)) {
                push("§6✦ Zone explorée : §f" + zoneLabel(z));
            }
        }
        if (data.isCodexDungeon() && !dungeon) {
            dungeon = true;
            push("§6✦ Donjon découvert : §fRepaire du Chef des Bandits");
        }
        for (String k : data.getCodexItems().keySet()) {
            if (items.add(k)) {
                push("§6✦ Butin au Codex : §f" + itemLabel(k));
            }
        }
    }

    private static void push(String text) {
        queue.addLast(new Toast(text, System.currentTimeMillis()));
        while (queue.size() > 4) queue.removeFirst();
    }

    private static String zoneLabel(String key) {
        try {
            String[] p = key.split(",");
            int sx = Integer.parseInt(p[0]), sz = Integer.parseInt(p[1]);
            if (sx == 0 && sz == 0) return "Village";
            String v = sz < 0 ? "Nord" : (sz > 0 ? "Sud" : "");
            String h = sx < 0 ? "Ouest" : (sx > 0 ? "Est" : "");
            return (!v.isEmpty() && !h.isEmpty()) ? v + "-" + h : v + h;
        } catch (Exception e) {
            return key;
        }
    }

    private static String itemLabel(String key) {
        if (key.startsWith("armor_")) {
            String[] p = key.split("_");   // armor_<slot>_<rarity>
            if (p.length == 3) {
                return WakfuArmorItem.slotNameFr(p[1]) + " Runique [" + p[2] + "]";
            }
        }
        if (key.startsWith("sword_")) return "Lame Runique [" + key.substring(6) + "]";
        if (key.startsWith("bow_")) return "Arc Runique [" + key.substring(4) + "]";
        return switch (key) {
            case "ingot" -> "Lingot de Wakfu";
            case "grimoire" -> "Grimoire du Wakfu";
            case "golden_apple" -> "Pomme dorée";
            default -> key;
        };
    }

    /** Rendu des toasts actifs (coin haut-droit, glissement + fondu). */
    public static synchronized void render(GuiGraphics g, int screenWidth) {
        if (queue.isEmpty()) return;
        long now = System.currentTimeMillis();
        queue.removeIf(t -> now - t.born() > LIFE_MS);
        var font = Minecraft.getInstance().font;
        int i = 0;
        for (Toast t : queue) {
            long age = now - t.born();
            double in = Math.min(1.0, age / 250.0);                       // glissement
            double out = age > LIFE_MS - 600 ? (LIFE_MS - age) / 600.0 : 1.0;  // fondu
            int alpha = (int) (230 * Math.min(in, out));
            if (alpha <= 8) { i++; continue; }
            int w = font.width(t.text()) + 16;
            int x = (int) (screenWidth - w - 8 + (1 - in) * (w + 12));
            int y = 8 + i * 26;
            int bg = (alpha << 24) | 0x2A2018;
            g.fill(x, y, x + w, y + 22, bg);
            g.fill(x, y, x + w, y + 1, (alpha << 24) | 0xC8A84A);
            g.fill(x, y + 21, x + w, y + 22, (alpha << 24) | 0xC8A84A);
            g.fill(x, y, x + 1, y + 22, (alpha << 24) | 0xC8A84A);
            g.fill(x + w - 1, y, x + w, y + 22, (alpha << 24) | 0xC8A84A);
            g.drawString(font, t.text(), x + 8, y + 7, 0xE8DCC0 | (alpha << 24));
            i++;
            if (i >= 4) break;
        }
    }

    private DiscoveryToasts() {}
}
