package fr.wakfu.rpg.core;

import net.minecraft.network.chat.Component;

public enum RpgClass {
    IOP("iop", 50, 10),
    CRA("cra", 40, 8),
    SADIDA("sadida", 45, 9);

    private final String id;
    private final int baseHp;
    private final int hpPerLevel;

    RpgClass(String id, int baseHp, int hpPerLevel) {
        this.id = id;
        this.baseHp = baseHp;
        this.hpPerLevel = hpPerLevel;
    }

    public String getId() {
        return id;
    }

    public Component getName() {
        return Component.translatable("class.wakfurpg." + id);
    }

    public int maxHp(int level) {
        return baseHp + hpPerLevel * (level - 1);
    }

    /** Points d'Action standards Wakfu */
    public int maxAp() {
        return 6;
    }

    /** Points de Mouvement standards Wakfu */
    public int maxMp() {
        return 3;
    }

    public static RpgClass byId(String id) {
        for (RpgClass c : values()) {
            if (c.id.equals(id)) return c;
        }
        return null;
    }
}
