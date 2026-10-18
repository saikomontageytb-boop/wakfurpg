package fr.wakfu.rpg.core.combat;

import fr.wakfu.rpg.core.MonsterType;
import fr.wakfu.rpg.entity.MonsterNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.*;

/**
 * Capacites uniques des archetypes du bestiaire Wakfu.
 * Chaque monstre a 1-2 competences speciales qui se declenchent
 * aleatoirement pendant la phase des monstres, avec un cooldown.
 */
public class MobSkill {

    public final String id;
    public final String nameFr;
    public final String description;
    public final int cooldown;      // tours entre utilisations
    public final int minRange;      // portee minimale
    public final int maxRange;      // portee maximale
    public final SkillType type;

    public enum SkillType {
        DAMAGE,     // inflige des degats
        HEAL,       // soigne le monstre
        BUFF,       // ameliore les stats du monstre
        DEBUFF,     // affaiblit le joueur
        MOVE,       // deplacement special
        AOE         // degats de zone
    }

    // Etat interne par monstre (cooldown restant)
    private static final Map<Integer, Map<String, Integer>> COOLDOWNS = new HashMap<>();

    public MobSkill(String id, String nameFr, String description, SkillType type,
                    int cooldown, int minRange, int maxRange) {
        this.id = id;
        this.nameFr = nameFr;
        this.description = description;
        this.type = type;
        this.cooldown = cooldown;
        this.minRange = minRange;
        this.maxRange = maxRange;
    }

    /** Reinitialise les cooldowns d\'un monstre (nouveau combat). */
    public static void resetCooldowns(int entityId) {
        COOLDOWNS.remove(entityId);
    }

    /** Retourne le cooldown restant pour ce skill sur ce monstre. */
    public int remainingCd(int entityId) {
        Map<String, Integer> cd = COOLDOWNS.get(entityId);
        if (cd == null) return 0;
        return cd.getOrDefault(id, 0);
    }

    /** Consomme le cooldown. */
    public void use(int entityId) {
        COOLDOWNS.computeIfAbsent(entityId, k -> new HashMap<>()).put(id, cooldown);
    }

    /** Decremente tous les cooldowns d\'un monstre (debut de tour). */
    public static void tickCooldowns(int entityId) {
        Map<String, Integer> cd = COOLDOWNS.get(entityId);
        if (cd == null) return;
        cd.replaceAll((k, v) -> Math.max(0, v - 1));
        if (cd.values().stream().allMatch(v -> v <= 0)) COOLDOWNS.remove(entityId);
    }

    // =========================================================
    //  DEFINITION DES SKILLS PAR TYPE DE MONSTRE
    // =========================================================

    private static final Map<MonsterType, List<MobSkill>> SKILLS = new HashMap<>();

    static {
        // --- Gardien des racines : frappe tellurique + sève vive ---
        register(MonsterType.ROOT_WARDEN,
            new MobSkill("root_slam", "Frappe tellurique", "Frappe dévastatrice", SkillType.DAMAGE, 3, 1, 1),
            new MobSkill("root_regen", "Sève vive", "Le gardien récupère des PV", SkillType.HEAL, 5, 0, 0)
        );
        // --- Éclaireur des cendres : ruée + fumigène ---
        register(MonsterType.ASH_SCOUT,
            new MobSkill("ash_dash", "Ruée", "Déplacement rapide et attaque furtive", SkillType.MOVE, 3, 0, 0),
            new MobSkill("smoke", "Fumigène", "Se dissimule dans la cendre", SkillType.BUFF, 4, 0, 0)
        );
        // --- Momic venimeux : venin + enroulement ---
        register(MonsterType.TOXIC_MOMIC,
            new MobSkill("venom", "Morsure venimeuse", "Poison qui inflige des degats sur 3 tours", SkillType.DEBUFF, 4, 1, 2),
            new MobSkill("coil", "Enroulement", "Se protege et regenere", SkillType.HEAL, 5, 0, 0)
        );
        // --- Golem de siège : piétinement + charge ---
        register(MonsterType.SIEGE_GOLEM,
            new MobSkill("stomp", "Pietinement", "Ecrase toutes les cases adjacentes", SkillType.AOE, 4, 0, 0),
            new MobSkill("charge", "Charge", "Fonce sur la cible et la repousse", SkillType.DAMAGE, 3, 2, 5)
        );
        // --- Guerrier lourd : jet de pierre + frénésie ---
        register(MonsterType.HEAVY_WARRIOR,
            new MobSkill("boulder_throw", "Lancer de rocher", "Lance un rocher a distance", SkillType.DAMAGE, 3, 2, 6),
            new MobSkill("rage", "Frénésie", "Augmente les dégâts pendant 2 tours", SkillType.BUFF, 5, 0, 0)
        );
        // --- Archer des os : tir tendu + cri ---
        register(MonsterType.BONE_ARCHER,
            new MobSkill("dive", "Plongeon", "Attaque depuis les airs", SkillType.DAMAGE, 2, 1, 8),
            new MobSkill("screech", "Cri perçant", "Reduit la precision du joueur", SkillType.DEBUFF, 4, 0, 6)
        );
        // --- Sentinelle forestière : ronces + épines ---
        register(MonsterType.FOREST_SENTINEL,
            new MobSkill("toxic_bite", "Morsure toxique", "Inflige poison et reduit les PM", SkillType.DEBUFF, 3, 1, 1),
            new MobSkill("sepsis", "Septicemie", "Degats supplementaires si deja empoisonne", SkillType.DAMAGE, 4, 1, 2)
        );
        // --- Brigand du grand chemin : charge + coup de crosse ---
        register(MonsterType.ROAD_BRIGAND,
            new MobSkill("brigand_charge", "Charge massive", "Fonce sur la cible", SkillType.DAMAGE, 3, 2, 5),
            new MobSkill("rear_up", "Coup de crosse", "Repousse tous les ennemis proches", SkillType.AOE, 4, 0, 2)
        );
        // --- Assassin du grand chemin : bond + lame cachée ---
        register(MonsterType.ROAD_ASSASSIN,
            new MobSkill("pounce", "Bond", "Saute sur la cible eloignee", SkillType.MOVE, 3, 3, 8),
            new MobSkill("hidden_blade", "Lame cachée", "Attaque critique avec bonus de dégâts", SkillType.DAMAGE, 2, 1, 1)
        );
        // --- Poupée mécanique : drain + rafale ---
        register(MonsterType.MECHANICAL_DOLL,
            new MobSkill("life_steal", "Vol de vie", "Draine les PV du joueur", SkillType.DAMAGE, 3, 1, 4),
            new MobSkill("burst", "Rafale", "Enchaîne plusieurs frappes", SkillType.AOE, 4, 0, 3)
        );
        // --- MOMIC (existant) : Morsure + Hurlement ---
        register(MonsterType.ZOMBIE,
            new MobSkill("momic_bite", "Morsure du Momic", "Morsure qui soigne le Momic", SkillType.DAMAGE, 3, 1, 1),
            new MobSkill("momic_wail", "Hurlement", "Effraie le joueur, reduit PA", SkillType.DEBUFF, 5, 0, 4)
        );
        // --- SKELETON : Fleche os + Armure ---
        register(MonsterType.SKELETON,
            new MobSkill("bone_shot", "Fleche d\'os", "Tir a distance en os", SkillType.DAMAGE, 2, 2, 6),
            new MobSkill("bone_armor", "Armure d\'os", "Augmente la defense", SkillType.BUFF, 5, 0, 0)
        );
        // --- BANDIT : Coup bas + Fuite ---
        register(MonsterType.BANDIT,
            new MobSkill("sucker_punch", "Coup bas", "Attaque surprise qui etourdit", SkillType.DAMAGE, 3, 1, 1),
            new MobSkill("bandit_flee", "Fuite", "Se deplace rapidement", SkillType.MOVE, 4, 0, 0)
        );
        // --- BOSS : Colere + Invocation ---
        register(MonsterType.BOSS,
            new MobSkill("boss_rage", "Colere du Chef", "Attaque devastatrice", SkillType.AOE, 3, 0, 0),
            new MobSkill("boss_summon", "Appel des bandits", "Invoque un bandit allie", SkillType.BUFF, 6, 0, 0)
        );
    }

    @SafeVarargs
    private static void register(MonsterType type, MobSkill... skills) {
        SKILLS.put(type, List.of(skills));
    }

    /** Retourne les skills disponibles pour un type de monstre. */
    public static List<MobSkill> skillsFor(MonsterType type) {
        return SKILLS.getOrDefault(type, List.of());
    }

    /** Choisit un skill utilisable aleatoirement (ou null si aucun dispo). */
    public static MobSkill pickUsable(MonsterType type, int entityId) {
        return pickUsable(type, entityId, s -> true, false);
    }

    /**
     * Choisit parmi les compétences prêtes (hors cooldown) et applicables.
     * Si preferHeal est vrai, un soin disponible passe en premier.
     */
    public static MobSkill pickUsable(MonsterType type, int entityId,
                                      java.util.function.Predicate<MobSkill> applicable, boolean preferHeal) {
        List<MobSkill> usable = new ArrayList<>();
        for (MobSkill s : skillsFor(type)) {
            if (s.remainingCd(entityId) > 0 || !applicable.test(s)) continue;
            if (preferHeal && s.type == SkillType.HEAL) return s;
            usable.add(s);
        }
        if (usable.isEmpty()) return null;
        return usable.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(usable.size()));
    }
}
