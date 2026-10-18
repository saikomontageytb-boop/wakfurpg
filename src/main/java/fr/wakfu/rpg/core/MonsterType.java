package fr.wakfu.rpg.core;

import net.minecraft.network.chat.Component;

/** Types de monstres affrontables en tour par tour. */
public enum MonsterType {
    ZOMBIE("momic", "Momic", 40, 6, 2, 1, 60, "wakfurpg:textures/entity/skin_momic.png"),
    SKELETON("skeleton", "Squelette", 30, 7, 2, 3, 70, "wakfurpg:textures/entity/skin_skeleton.png"),
    BANDIT("bandit", "Bandit", 55, 9, 3, 1, 110, "wakfurpg:textures/entity/skin_bandit.png"),
    // v0.14 : invocables (xp 0, textures generees) + boss du donjon
    GUERRIER("guerrier", "Frère d'Armes", 32, 6, 2, 1, 0, "wakfurpg:textures/entity/summon_guerrier.png"),
    SENTINELLE("sentinelle", "Sentinelle", 18, 5, 1, 4, 0, "wakfurpg:textures/entity/summon_sentinelle.png"),
    POUPEE("poupee", "Poupée Vaillante", 24, 5, 2, 1, 0, "wakfurpg:textures/entity/summon_poupee.png"),
    GOLEM("golem", "Golem de Bois", 42, 7, 1, 1, 0, "wakfurpg:textures/entity/summon_golem.png"),
    COLOSSE("colosse", "Colosse de Ronces", 70, 9, 2, 1, 0, "wakfurpg:textures/entity/summon_golem.png"),
    // Bestiaire original du mod : ces archetypes utilisent des skins Wakfu, sans entite externe.
    ROOT_WARDEN("gardien_racines", "Gardien des racines", 80, 10, 2, 1, 180, "wakfurpg:textures/entity/skin_golem_pierre.png"),
    ASH_SCOUT("eclaireur_cendres", "Éclaireur des cendres", 25, 5, 4, 2, 90, "wakfurpg:textures/entity/skin_bandit_voleur.png"),
    TOXIC_MOMIC("momic_venimeux", "Momic venimeux", 35, 8, 2, 2, 100, "wakfurpg:textures/entity/skin_zombie_infecte.png"),
    SIEGE_GOLEM("golem_siege", "Golem de siège", 120, 8, 1, 1, 250, "wakfurpg:textures/entity/skin_golem_pierre.png"),
    HEAVY_WARRIOR("guerrier_lourd", "Guerrier lourd", 90, 11, 2, 1, 200, "wakfurpg:textures/entity/skin_guerrier_lourd.png"),
    BONE_ARCHER("archer_os", "Archer des os", 30, 9, 3, 6, 120, "wakfurpg:textures/entity/skin_skeleton_archer.png"),
    FOREST_SENTINEL("sentinelle_forest", "Sentinelle forestière", 50, 7, 2, 1, 130, "wakfurpg:textures/entity/skin_sentinelle_forest.png"),
    ROAD_BRIGAND("brigand_grand_chemin", "Brigand du grand chemin", 70, 9, 3, 1, 160, "wakfurpg:textures/entity/skin_bandit_brigan.png"),
    ROAD_ASSASSIN("assassin_grand_chemin", "Assassin du grand chemin", 60, 12, 3, 1, 170, "wakfurpg:textures/entity/skin_bandit_assassin.png"),
    MECHANICAL_DOLL("poupee_mecanique", "Poupée mécanique", 20, 6, 3, 3, 80, "wakfurpg:textures/entity/skin_poupee_mecanique.png"),

    BOSS("chef_bandit", "Chef des Bandits", 130, 12, 3, 1, 400, "wakfurpg:textures/entity/skin_boss_chef.png");

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
        if ("zombie".equalsIgnoreCase(id)) return ZOMBIE;
        for (MonsterType t : values()) {
            if (t.id.equalsIgnoreCase(id)) return t;
        }
        return null;
    }
}
