package fr.wakfu.rpg.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * v0.28 : variantes visuelles des monstres de terrain.
 * Chaque zone a ses apparences (textures distinctes) et sa fourchette de
 * niveaux — le niveau affiché est le NIVEAU JOUEUR CONSEILLÉ, pas un
 * niveau de monstre arbitraire.
 *
 * Chaque monstre a un skin unique (64x64 PNG) pour se distinguer.
 */
public class MobVariants {

    public static class Variant {
        public final String id;
        public final String base;
        public final String nameFr;
        public final String texture;
        public final int minLvl;
        public final int maxLvl;
        public final int weight;

        public Variant(String id, String base, String nameFr, String texture,
                       int minLvl, int maxLvl, int weight) {
            this.id = id;
            this.base = base;
            this.nameFr = nameFr;
            this.texture = texture;
            this.minLvl = minLvl;
            this.maxLvl = maxLvl;
            this.weight = weight;
        }
    }

    public static final List<Variant> ALL = List.of(
            // ============ PLAINE (< 96 m) : zombies ============
            new Variant("", "momic", "Momic", "wakfurpg:textures/entity/skin_momic.png", 1, 3, 30),
            new Variant("putride", "momic", "Zombie Putride", "wakfurpg:textures/entity/skin_zombie_putride.png", 1, 3, 25),
            new Variant("noye", "momic", "Zombie Noyé", "wakfurpg:textures/entity/skin_zombie_noye.png", 2, 4, 20),
            new Variant("infecte", "momic", "Zombie Infecté", "wakfurpg:textures/entity/skin_zombie_infecte.png", 3, 5, 15),
            new Variant("ancien", "momic", "Zombie Ancien", "wakfurpg:textures/entity/skin_zombie_ancien.png", 4, 6, 10),

            // ============ CARRIERES (96-200 m) : squelettes ============
            new Variant("", "skeleton", "Squelette", "wakfurpg:textures/entity/skin_skeleton.png", 4, 7, 25),
            new Variant("mineur", "skeleton", "Squelette Mineur", "wakfurpg:textures/entity/skin_skeleton_mineur.png", 4, 7, 25),
            new Variant("archer", "skeleton", "Squelette Archer", "wakfurpg:textures/entity/skin_skeleton_archer.png", 5, 8, 20),
            new Variant("rodeur", "skeleton", "Rôdeur des Carrières", "wakfurpg:textures/entity/skin_skeleton_rodeur.png", 6, 9, 20),
            new Variant("arme", "skeleton", "Squelette Armé", "wakfurpg:textures/entity/skin_skeleton_arme.png", 7, 10, 10),

            // ============ GRAND CHEMIN (> 200 m) : bandits ============
            new Variant("", "bandit", "Bandit", "wakfurpg:textures/entity/skin_bandit.png", 7, 10, 25),
            new Variant("voleur", "bandit", "Voleur", "wakfurpg:textures/entity/skin_bandit_voleur.png", 8, 11, 25),
            new Variant("maraudeur", "bandit", "Maraudeur", "wakfurpg:textures/entity/skin_bandit_maraudeur.png", 9, 12, 25),
            new Variant("assassin", "bandit", "Assassin", "wakfurpg:textures/entity/skin_bandit_assassin.png", 10, 14, 15),
            new Variant("brigan", "bandit", "Brigand", "wakfurpg:textures/entity/skin_bandit_brigan.png", 12, 16, 10),

            // ============ BOSS (donjon) ============
            new Variant("", "chef_bandit", "Chef des Bandits", "wakfurpg:textures/entity/skin_boss_chef.png", 10, 15, 40),
            new Variant("necro", "chef_bandit", "Nécromancien", "wakfurpg:textures/entity/skin_boss_necromancien.png", 12, 18, 30),
            new Variant("seigneur", "chef_bandit", "Seigneur des Ombres", "wakfurpg:textures/entity/skin_boss_seigneur.png", 15, 20, 30),

            // ============ INVOCATIONS (Iop) ============
            new Variant("", "guerrier", "Frère d'Armes", null, 1, 50, 60),
            new Variant("lourd", "guerrier", "Guerrier Lourd", "wakfurpg:textures/entity/skin_guerrier_lourd.png", 1, 50, 40),

            // ============ INVOCATIONS (Sadida) ============
            new Variant("", "sentinelle", "Sentinelle", "wakfurpg:textures/entity/summon_sentinelle.png", 1, 50, 60),
            new Variant("forest", "sentinelle", "Sentinelle Forestière", "wakfurpg:textures/entity/skin_sentinelle_forest.png", 1, 50, 40),
            new Variant("", "poupee", "Poupée Vaillante", null, 1, 50, 60),
            new Variant("meca", "poupee", "Poupée Mécanique", "wakfurpg:textures/entity/skin_poupee_mecanique.png", 1, 50, 40),
            new Variant("", "golem", "Golem de Bois", "wakfurpg:textures/entity/summon_golem.png", 1, 50, 60),
            new Variant("pierre", "golem", "Golem de Pierre", "wakfurpg:textures/entity/skin_golem_pierre.png", 1, 50, 40),
            new Variant("", "colosse", "Colosse de Ronces", "wakfurpg:textures/entity/summon_golem.png", 1, 50, 100)
    );

    public static List<Variant> forBase(String baseId) {
        List<Variant> out = new ArrayList<>();
        for (Variant v : ALL) if (v.base.equals(baseId)) out.add(v);
        return out;
    }

    public static Variant get(String baseId, String variantId) {
        Variant first = null;
        for (Variant v : ALL) {
            if (!v.base.equals(baseId)) continue;
            if (v.id.equals(variantId == null ? "" : variantId)) return v;
            if (v.id.isEmpty()) first = v;
        }
        return first;
    }

    public static Variant roll(String baseId, Random rng) {
        List<Variant> vs = forBase(baseId);
        if (vs.isEmpty()) return null;
        int total = 0;
        for (Variant v : vs) total += v.weight;
        int r = rng.nextInt(total);
        for (Variant v : vs) {
            r -= v.weight;
            if (r < 0) return v;
        }
        return vs.get(0);
    }

    public static int[] levelRange(String baseId) {
        List<Variant> vs = forBase(baseId);
        if (vs.isEmpty()) return null;
        int min = Integer.MAX_VALUE, max = 0;
        for (Variant v : vs) {
            min = Math.min(min, v.minLvl);
            max = Math.max(max, v.maxLvl);
        }
        return new int[]{min, max};
    }

    public static String namesFor(String baseId) {
        StringBuilder b = new StringBuilder();
        for (Variant v : forBase(baseId)) {
            if (b.length() > 0) b.append(", ");
            b.append(v.nameFr).append(" (").append(v.minLvl).append("-").append(v.maxLvl).append(")");
        }
        return b.toString();
    }

    private MobVariants() {}
}

