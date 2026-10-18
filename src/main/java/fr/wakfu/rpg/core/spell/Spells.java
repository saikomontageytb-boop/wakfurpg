package fr.wakfu.rpg.core.spell;

import fr.wakfu.rpg.core.RpgClass;

import java.util.List;

/**
 * Definitions des sorts par classe.
 * v0.14 : 3e classe SADIDA + sorts d'invocation. damage < 0 = soin.
 * Les cinq classes principales commencent avec trois sorts et en debloquent
 * ensuite un tous les cinq niveaux, jusqu'au niveau 50.
 */
public class Spells {

    public static final int MAX_EQUIPPED = 4;

    public static List<Spell> forClass(RpgClass c) {
        c = c.spellArchetype();
        if (c == RpgClass.SYKAR) return List.of(
                new Spell("sykar_lame", 2, 1, 1, 6, ZoneType.SINGLE),
                new Spell("sykar_pas", 2, 1, 5, 0, ZoneType.SINGLE, null, 1, "teleport"),
                new Spell("sykar_echo", 4, 0, 0, 0, ZoneType.SELF, "guerrier", 1),
                new Spell("sykar_sang", 3, 1, 1, 5, ZoneType.SINGLE, null, 5, "vol"),
                new Spell("sykar_rafale", 3, 1, 4, 8, ZoneType.CROSS, null, 10),
                new Spell("sykar_ombre", 4, 0, 0, 0, ZoneType.SELF, "sentinelle", 15),
                new Spell("sykar_lames", 4, 1, 3, 10, ZoneType.SQUARE2, null, 20),
                new Spell("sykar_marque", 3, 1, 5, 7, ZoneType.SINGLE, null, 25, "pa_debuff"),
                new Spell("sykar_faille", 4, 0, 0, 12, ZoneType.SELF_RING, null, 30),
                new Spell("sykar_reflet", 3, 0, 0, 0, ZoneType.SELF, null, 35, "invulnerable"),
                new Spell("sykar_saignement", 4, 1, 4, 8, ZoneType.SINGLE, null, 40, "dot"),
                new Spell("sykar_spectral", 5, 0, 0, 0, ZoneType.SELF, "archer_os", 45),
                new Spell("sykar_avatar", 6, 0, 0, 0, ZoneType.SELF, "colosse", 50));
        if (c == RpgClass.TOR) return List.of(
                new Spell("tor_marteau", 2, 1, 1, 6, ZoneType.SINGLE),
                new Spell("tor_garde", 2, 0, 0, 0, ZoneType.SELF, null, 1, "armor"),
                new Spell("tor_ecuyer", 4, 0, 0, 0, ZoneType.SELF, "guerrier_lourd", 1),
                new Spell("tor_choc", 3, 1, 1, 5, ZoneType.CROSS, null, 5, "slow"),
                new Spell("tor_tremblement", 4, 0, 0, 8, ZoneType.SELF_RING, null, 10),
                new Spell("tor_crochet", 4, 0, 0, 0, ZoneType.SELF, "golem", 15),
                new Spell("tor_rempart", 4, 0, 0, 0, ZoneType.SELF, null, 20, "armor"),
                new Spell("tor_provocation", 3, 1, 4, 6, ZoneType.SQUARE2, null, 25, "pa_debuff"),
                new Spell("tor_pilier", 5, 0, 0, 12, ZoneType.SELF_RING, null, 30),
                new Spell("tor_implacable", 4, 0, 0, 0, ZoneType.SELF, null, 35, "invulnerable"),
                new Spell("tor_seisme", 5, 1, 3, 13, ZoneType.CROSS, null, 40),
                new Spell("tor_ancien", 5, 0, 0, 0, ZoneType.SELF, "golem_siege", 45),
                new Spell("tor_colosse", 6, 0, 0, 0, ZoneType.SELF, "colosse", 50));
        if (c == RpgClass.SPLIAN) return List.of(
                new Spell("splian_dard", 2, 1, 5, 5, ZoneType.SINGLE),
                new Spell("splian_lenteur", 2, 1, 4, 2, ZoneType.SINGLE, null, 1, "slow"),
                new Spell("splian_sentinelle", 4, 0, 0, 0, ZoneType.SELF, "sentinelle", 1),
                new Spell("splian_ronce", 3, 1, 3, 5, ZoneType.SINGLE, null, 5, "piege_slow"),
                new Spell("splian_venin", 3, 1, 5, 5, ZoneType.SINGLE, null, 10, "dot"),
                new Spell("splian_toile", 4, 0, 0, 0, ZoneType.SELF, "poupee", 15),
                new Spell("splian_epines", 4, 1, 5, 8, ZoneType.SQUARE2, null, 20),
                new Spell("splian_piege", 3, 1, 6, 10, ZoneType.SINGLE, null, 25, "piege"),
                new Spell("splian_miasme", 4, 1, 5, 8, ZoneType.SQUARE2, null, 30, "dot"),
                new Spell("splian_racines", 4, 1, 5, 6, ZoneType.SINGLE, null, 35, "piege_pa"),
                new Spell("splian_floraison", 5, 1, 5, 12, ZoneType.CROSS, null, 40),
                new Spell("splian_poupee", 4, 0, 0, 0, ZoneType.SELF, "poupee", 45),
                new Spell("splian_colosse", 6, 0, 0, 0, ZoneType.SELF, "colosse", 50));
        if (c == RpgClass.GEMA) return List.of(
                new Spell("gema_lumiere", 2, 1, 4, 5, ZoneType.SINGLE),
                new Spell("gema_soin", 2, 0, 0, -8, ZoneType.SELF),
                new Spell("gema_graine", 4, 0, 0, 0, ZoneType.SELF, "poupee", 1),
                new Spell("gema_bouclier", 3, 0, 0, 0, ZoneType.SELF, null, 5, "armor"),
                new Spell("gema_averse", 3, 1, 5, -10, ZoneType.CROSS, null, 10, "heal_zone"),
                new Spell("gema_renouveau", 4, 0, 0, 0, ZoneType.SELF, "sentinelle", 15),
                new Spell("gema_entrave", 3, 1, 5, 5, ZoneType.SINGLE, null, 20, "slow"),
                new Spell("gema_roncier", 4, 1, 4, 9, ZoneType.SQUARE2, null, 25),
                new Spell("gema_cocon", 4, 0, 0, 0, ZoneType.SELF, null, 30, "armor"),
                new Spell("gema_invincible", 5, 0, 0, 0, ZoneType.SELF, null, 35, "invulnerable"),
                new Spell("gema_sylve", 5, 1, 5, 12, ZoneType.CROSS, null, 40, "dot"),
                new Spell("gema_golem", 5, 0, 0, 0, ZoneType.SELF, "golem", 45),
                new Spell("gema_colosse", 6, 0, 0, 0, ZoneType.SELF, "colosse", 50));
        if (c == RpgClass.ENESTRAS) return List.of(
                new Spell("enestras_ombre", 2, 1, 5, 5, ZoneType.SINGLE),
                new Spell("enestras_malefice", 2, 1, 4, 3, ZoneType.SINGLE, null, 1, "dot"),
                new Spell("enestras_serviteur", 4, 0, 0, 0, ZoneType.SELF, "skeleton", 1),
                new Spell("enestras_froid", 3, 1, 4, 3, ZoneType.SINGLE, null, 5, "slow"),
                new Spell("enestras_sceau", 3, 1, 5, 4, ZoneType.SINGLE, null, 10, "pa_debuff"),
                new Spell("enestras_essaim", 4, 0, 0, 0, ZoneType.SELF, "archer_os", 15),
                new Spell("enestras_silence", 3, 1, 5, 5, ZoneType.SINGLE, null, 20, "pa_debuff"),
                new Spell("enestras_piege", 3, 1, 6, 8, ZoneType.SINGLE, null, 25, "piege_dot"),
                new Spell("enestras_tourment", 4, 1, 5, 9, ZoneType.CROSS, null, 30, "dot"),
                new Spell("enestras_reflet", 4, 0, 0, 0, ZoneType.SELF, null, 35, "invulnerable"),
                new Spell("enestras_fleau", 5, 1, 6, 11, ZoneType.SQUARE2, null, 40, "pa_debuff"),
                new Spell("enestras_revenant", 5, 0, 0, 0, ZoneType.SELF, "momic", 45),
                new Spell("enestras_neant", 6, 0, 0, 0, ZoneType.SELF, "colosse", 50));
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
