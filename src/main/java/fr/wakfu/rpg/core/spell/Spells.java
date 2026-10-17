package fr.wakfu.rpg.core.spell;

import fr.wakfu.rpg.core.RpgClass;

import java.util.List;

/**
 * Definitions des sorts par classe.
 * v0.14 : 3e classe SADIDA + sorts d'invocation. damage < 0 = soin.
 * v0.20 : PROGRESSION — 1 seul sort au niveau 1, deblocages etales
 * regulierement jusqu'au niveau 50 (niveau maximum).
 */
public class Spells {

    public static final int MAX_EQUIPPED = 4;

    public static List<Spell> forClass(RpgClass c) {
        if (c == RpgClass.IOP) {
            return List.of(
                    new Spell("iop_basic", 2, 1, 1, 6, ZoneType.SINGLE),                                  // 1
                    new Spell("iop_heavy", 4, 1, 1, 12, ZoneType.SINGLE, null, 3),                       // 3
                    new Spell("iop_tempete", 3, 0, 0, 6, ZoneType.SELF_RING, null, 6),                   // 6
                    new Spell("iop_uppercut", 3, 1, 2, 8, ZoneType.SINGLE, null, 10),                    // 10
                    new Spell("iop_pourfendre", 4, 1, 2, 9, ZoneType.CROSS, null, 14),                   // 14
                    new Spell("iop_couperet", 3, 1, 1, 11, ZoneType.SINGLE, null, 18),                   // 18
                    new Spell("iop_colere", 5, 0, 0, 11, ZoneType.CROSS, null, 22),                      // 22
                    P(new Spell("iop_bond", 4, 2, 5, 12, ZoneType.SINGLE, null, 26, "bond")),           // 26
                    new Spell("iop_brise", 4, 1, 2, 14, ZoneType.LINE, null, 30),                        // 30
                    new Spell("iop_rage", 5, 0, 0, 16, ZoneType.SELF_RING, null, 34),                    // 34
                    P(new Spell("iop_sanguinaire", 5, 1, 1, 14, ZoneType.SINGLE, null, 38, "vol")),     // 38
                    new Spell("iop_wakfu", 6, 1, 2, 20, ZoneType.CROSS, null, 42),                       // 42
                    new Spell("iop_apoca", 6, 0, 0, 24, ZoneType.SQUARE2, null, 46),                     // 46
                    new Spell("iop_allie", 4, 0, 0, 0, ZoneType.SELF, "guerrier", 50));                  // 50
        }
        if (c == RpgClass.CRA) {
            return List.of(
                    new Spell("cra_basic", 2, 1, 8, 6, ZoneType.SINGLE),                                 // 1
                    new Spell("cra_tirrapide", 1, 1, 5, 3, ZoneType.SINGLE, null, 3),                    // 3
                    new Spell("cra_explosive", 4, 1, 7, 8, ZoneType.SQUARE2, null, 6),                   // 6
                    new Spell("cra_snipe", 4, 3, 10, 10, ZoneType.LINE, null, 10),                       // 10
                    P(new Spell("cra_pluie", 5, 1, 6, 6, ZoneType.CROSS, null, 14)),                     // 14
                    P(new Spell("cra_perforante", 3, 2, 9, 12, ZoneType.SINGLE, null, 18)),             // 18
                    P(new Spell("cra_chercheuse", 5, 1, 12, 9, ZoneType.SINGLE, null, 22)),             // 22
                    new Spell("cra_piege", 3, 1, 6, 12, ZoneType.SINGLE, null, 26, "piege"),             // 26
                    new Spell("cra_faucon", 4, 3, 11, 15, ZoneType.LINE, null, 30),                      // 30
                    P(new Spell("cra_tornade", 5, 1, 7, 13, ZoneType.SQUARE2, null, 34)),                // 34
                    new Spell("cra_piege_explosif", 4, 1, 6, 17, ZoneType.SINGLE, null, 38, "piege"),    // 38
                    P(new Spell("cra_solaire", 6, 2, 10, 19, ZoneType.CROSS, null, 42)),                 // 42
                    P(new Spell("cra_jugement", 6, 1, 12, 22, ZoneType.SQUARE2, null, 46)),              // 46
                    new Spell("cra_sentinelle", 4, 0, 0, 0, ZoneType.SELF, "sentinelle", 50));           // 50
        }
        if (c == RpgClass.SADIDA) {
            return List.of(
                    P(new Spell("sadida_ronce", 2, 1, 6, 5, ZoneType.SINGLE)),                           // 1
                    P(new Spell("sadida_graine", 3, 1, 4, 7, ZoneType.SINGLE, null, 3)),                // 3
                    new Spell("sadida_soin", 4, 0, 0, -10, ZoneType.SELF, null, 6),                      // 6
                    P(new Spell("sadida_sylvestre", 4, 1, 3, 7, ZoneType.CROSS, null, 10)),             // 10
                    P(new Spell("sadida_etrangleuse", 3, 1, 5, 10, ZoneType.SINGLE, null, 14)),         // 14
                    P(new Spell("sadida_multiples", 4, 1, 4, 9, ZoneType.CROSS, null, 18)),             // 18
                    P(new Spell("sadida_liane", 4, 2, 6, 10, ZoneType.SINGLE, null, 22, "bond")),       // 22
                    P(new Spell("sadida_bombardier", 5, 1, 6, 15, ZoneType.SQUARE2, null, 26)),         // 26
                    P(new Spell("sadida_roncier", 4, 1, 5, 14, ZoneType.SINGLE, null, 30, "piege")),    // 30
                    new Spell("sadida_vie", 5, 0, 0, -18, ZoneType.SELF, null, 34),                      // 34
                    new Spell("sadida_poupee", 3, 0, 0, 0, ZoneType.SELF, "poupee", 38),                 // 38
                    new Spell("sadida_golem", 5, 0, 0, 0, ZoneType.SELF, "golem", 42),                   // 42
                    new Spell("sadida_colosse", 6, 0, 0, 0, ZoneType.SELF, "colosse", 50));              // 50
        }
        return List.of();
    }

    /** v0.19 : marque un sort comme traversant les murs. */
    private static Spell P(Spell s) {
        s.pierce = true;
        return s;
    }

    public static Spell byId(String id) {
        for (RpgClass c : RpgClass.values()) {
            for (Spell s : forClass(c)) {
                if (s.id.equals(id)) return s;
            }
        }
        return null;
    }
}
