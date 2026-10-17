package fr.wakfu.rpg.core;

import net.minecraft.network.chat.Component;

/** Types de monstres affrontables en tour par tour. */
public enum MonsterType {
    ZOMBIE("momic", "Momic", 40, 6, 2, 1, 60, "wakfurpg:textures/entity/alexsmobs/zombie.png"),
    SKELETON("skeleton", "Squelette", 30, 7, 2, 3, 70, "wakfurpg:textures/entity/alexsmobs/skeleton.png"),
    BANDIT("bandit", "Bandit", 55, 9, 3, 1, 110, "wakfurpg:textures/entity/alexsmobs/bandit.png"),
    // v0.14 : invocables (xp 0, textures generees) + boss du donjon
    GUERRIER("guerrier", "Frère d'Armes", 32, 6, 2, 1, 0, "textures/entity/summon_guerrier.png"),
    SENTINELLE("sentinelle", "Sentinelle", 18, 5, 1, 4, 0, "textures/entity/summon_sentinelle.png"),
    POUPEE("poupee", "Poupée Vaillante", 24, 5, 2, 1, 0, "textures/entity/summon_poupee.png"),
    GOLEM("golem", "Golem de Bois", 42, 7, 1, 1, 0, "textures/entity/summon_golem.png"),
    COLOSSE("colosse", "Colosse de Ronces", 70, 9, 2, 1, 0, "textures/entity/summon_golem.png"),
    // ===== Alex's Mobs : creatures animees =====
    GRIZZLY("grizzly", "Grizzly", 80, 10, 2, 1, 180, "wakfurpg:textures/entity/alexsmobs/grizzly.png"),
    GAZELLE("gazelle", "Gazelle", 25, 5, 4, 2, 90, "wakfurpg:textures/entity/alexsmobs/gazelle.png"),
    RATTLESNAKE("rattlesnake", "Crotale", 35, 8, 2, 2, 100, "wakfurpg:textures/entity/alexsmobs/rattlesnake.png"),
    ELEPHANT("elephant", "Elephant", 120, 8, 1, 1, 250, "wakfurpg:textures/entity/alexsmobs/elephant.png"),
    GORILLA("gorilla", "Gorille", 90, 11, 2, 1, 200, "wakfurpg:textures/entity/alexsmobs/gorilla.png"),
    FALCON("falcon", "Faucon", 30, 9, 3, 6, 120, "wakfurpg:textures/entity/alexsmobs/falcon.png"),
    KOMODO_DRAGON("komodo_dragon", "Dragon de Komodo", 50, 7, 2, 1, 130, "wakfurpg:textures/entity/alexsmobs/komodo_dragon.png"),
    MOOSE("moose", "Orignal", 70, 9, 3, 1, 160, "wakfurpg:textures/entity/alexsmobs/moose.png"),
    TIGER("tiger", "Tigre", 60, 12, 3, 1, 170, "wakfurpg:textures/entity/alexsmobs/tiger.png"),
    CROW("crow", "Corbeau", 20, 6, 3, 3, 80, "wakfurpg:textures/entity/alexsmobs/crow.png"),

    BOSS("chef_bandit", "Chef des Bandits", 130, 12, 3, 1, 400, "wakfurpg:textures/entity/alexsmobs/boss.png");

    private final String id;
    /** v0.23 : nom francais LITTERAL — plus jamais de clé de traduction brute. */
    public final String nameFr;
    public final int maxHp;
    public final int damage;
    public final int pm;
    public final int range;
    public final int xpReward;
    public final String texture;

    MonsterType(String id, String nameFr, int maxHp, int damage, int pm, int range, int xpReward, String texture) {
        this.id = id;
        this.nameFr = nameFr;
        this.maxHp = maxHp;
        this.damage = damage;
        this.pm = pm;
        this.range = range;
        this.xpReward = xpReward;
        this.texture = texture;
    }

    public String getId() {
        return id;
    }

    public Component getName() {
        return Component.literal(nameFr);
    }

    public static MonsterType byId(String id) {
        for (MonsterType t : values()) {
            if (t.id.equals(id)) return t;
        }
        return null;
    }
}
