package fr.wakfu.rpg.core.combat;

import fr.wakfu.rpg.core.MonsterType;
import fr.wakfu.rpg.entity.MonsterNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.*;

/**
 * v0.27 : Capacites uniques par type de monstre (Alex's Mobs).
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
    }

    // =========================================================
    //  DEFINITION DES SKILLS PAR TYPE DE MONSTRE
    // =========================================================

    private static final Map<MonsterType, List<MobSkill>> SKILLS = new HashMap<>();

    static {
        // --- GRIZZLY (Ours) : Maul + Regeneration ---
        register(MonsterType.GRIZZLY,
            new MobSkill("maul", "Maulure", "Griffe devastatrice qui saigne", SkillType.DAMAGE, 3, 1, 1),
            new MobSkill("bear_regen", "Regeneration", "L\'ours recupere des PV", SkillType.HEAL, 5, 0, 0)
        );
        // --- GAZELLE : Sprint + Esquive ---
        register(MonsterType.GAZELLE,
            new MobSkill("sprint", "Sprint", "Deplacement rapide + attaque furtive", SkillType.MOVE, 3, 0, 0),
            new MobSkill("dodge", "Esquive", "Evite la prochaine attaque", SkillType.BUFF, 4, 0, 0)
        );
        // --- RATTLESNAKE (Serpent) : Venin + Enroulement ---
        register(MonsterType.RATTLESNAKE,
            new MobSkill("venom", "Morsure venimeuse", "Poison qui inflige des degats sur 3 tours", SkillType.DEBUFF, 4, 1, 2),
            new MobSkill("coil", "Enroulement", "Se protege et regenere", SkillType.HEAL, 5, 0, 0)
        );
        // --- ELEPHANT : Stomp + Charge ---
        register(MonsterType.ELEPHANT,
            new MobSkill("stomp", "Pietinement", "Ecrase toutes les cases adjacentes", SkillType.AOE, 4, 0, 0),
            new MobSkill("charge", "Charge", "Fonce sur la cible et la repousse", SkillType.DAMAGE, 3, 2, 5)
        );
        // --- GORILLA : Lancer de rocher + Rage ---
        register(MonsterType.GORILLA,
            new MobSkill("boulder_throw", "Lancer de rocher", "Lance un rocher a distance", SkillType.DAMAGE, 3, 2, 6),
            new MobSkill("rage", "Rage du gorille", "Augmente les degats pendant 2 tours", SkillType.BUFF, 5, 0, 0)
        );
        // --- FALCON (Faucon) : Plongeon + Cri ---
        register(MonsterType.FALCON,
            new MobSkill("dive", "Plongeon", "Attaque depuis les airs", SkillType.DAMAGE, 2, 1, 8),
            new MobSkill("screech", "Cri perçant", "Reduit la precision du joueur", SkillType.DEBUFF, 4, 0, 6)
        );
        // --- KOMODO_DRAGON : Morsure toxique + Salive ---
        register(MonsterType.KOMODO_DRAGON,
            new MobSkill("toxic_bite", "Morsure toxique", "Inflige poison et reduit les PM", SkillType.DEBUFF, 3, 1, 1),
            new MobSkill("sepsis", "Septicemie", "Degats supplementaires si deja empoisonne", SkillType.DAMAGE, 4, 1, 2)
        );
        // --- MOOSE (Orignal) : Charge + Cabrage ---
        register(MonsterType.MOOSE,
            new MobSkill("moose_charge", "Charge massive", "Fonce sur la cible", SkillType.DAMAGE, 3, 2, 5),
            new MobSkill("rear_up", "Cabragement", "Repousse tous les ennemis proches", SkillType.AOE, 4, 0, 2)
        );
        // --- TIGER : Bond + Griffe ---
        register(MonsterType.TIGER,
            new MobSkill("pounce", "Bond", "Saute sur la cible eloignee", SkillType.MOVE, 3, 3, 8),
            new MobSkill("claw_slash", "Griffe feroce", "Attaque critique avec bonus de degats", SkillType.DAMAGE, 2, 1, 1)
        );
        // --- CROW (Corbeau) : Vol de PV + Essaim ---
        register(MonsterType.CROW,
            new MobSkill("life_steal", "Vol de vie", "Draine les PV du joueur", SkillType.DAMAGE, 3, 1, 4),
            new MobSkill("swarm", "Essaim", "Attaque multiple rapide", SkillType.AOE, 4, 0, 3)
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
        List<MobSkill> skills = skillsFor(type);
        if (skills.isEmpty()) return null;
        List<MobSkill> usable = new ArrayList<>();
        for (MobSkill s : skills) {
            if (s.remainingCd(entityId) <= 0) usable.add(s);
        }
        if (usable.isEmpty()) return null;
        return usable.get(new Random().nextInt(usable.size()));
    }
}
