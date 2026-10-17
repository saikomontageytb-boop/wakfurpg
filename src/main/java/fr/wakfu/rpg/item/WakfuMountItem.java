package fr.wakfu.rpg.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * v0.31 : Item monture equipable — utilise les creatures Mythic Montures.
 * wolf  = Direwolf  (loup rapide, terrestre)
 * bear  = Nightmare (cheval noir, terrestre)
 * griffin = Griffon  (volant, celeste)
 * dragon  = Dragon   (volant, puissant)
 */
public class WakfuMountItem extends Item {

    public static final String TYPE_WOLF = "wolf";
    public static final String TYPE_BEAR = "bear";
    public static final String TYPE_GRIFFIN = "griffin";
    public static final String TYPE_DRAGON = "dragon";

    private final String mountType;

    public WakfuMountItem(String mountType, Properties p) {
        super(p.stacksTo(1));
        this.mountType = mountType;
    }

    public String getMountType() { return mountType; }

    public static String getMountType(ItemStack stack) {
        if (stack.getItem() instanceof WakfuMountItem mi) return mi.mountType;
        String t = stack.getOrCreateTag().getString("MountType");
        return t.isEmpty() ? TYPE_WOLF : t;
    }

    public static boolean isMountItem(ItemStack stack) {
        return stack.getItem() instanceof WakfuMountItem;
    }

    @Override
    public Component getName(ItemStack stack) {
        return switch (mountType) {
            case TYPE_WOLF -> Component.literal("\u00a7bDirewolf");
            case TYPE_BEAR -> Component.literal("\u00a78Nightmare");
            case TYPE_GRIFFIN -> Component.literal("\u00a7eGriffon");
            case TYPE_DRAGON -> Component.literal("\u00a7cDragon");
            default -> super.getName(stack);
        };
    }
}
