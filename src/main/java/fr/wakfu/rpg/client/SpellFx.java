package fr.wakfu.rpg.client;

import fr.wakfu.rpg.network.CombatFxPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

import java.util.Random;

/**
 * Animations visuelles des sorts (v0.12) : chaque sort a son profil de
 * particules, joue sur les cellules touchees a la reception du paquet FX.
 */
public class SpellFx {

    private static final Random R = new Random();

    public static void play(CombatFxPacket fx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double y = fx.floorY + 0.6;
        switch (fx.spellId) {
            case "iop_basic":
                for (BlockPos c : fx.cells) burst(c, y, ParticleTypes.SWEEP_ATTACK, 10, 0.4, 0.15);
                break;
            case "iop_heavy":
                for (BlockPos c : fx.cells) {
                    burst(c, y, ParticleTypes.CRIT, 18, 0.5, 0.25);
                    burst(c, y, ParticleTypes.FLAME, 8, 0.4, 0.2);
                    puff(c, y, ParticleTypes.EXPLOSION, 1);
                }
                break;
            case "iop_tempete": {
                BlockPos c = fx.cells.isEmpty()
                        ? new BlockPos(fx.casterX, fx.floorY, fx.casterZ) : fx.cells.get(0);
                double cx = c.getX() + 0.5, cz = c.getZ() + 0.5;
                for (int i = 0; i < 28; i++) {
                    double a = i / 28.0 * Math.PI * 2;
                    double rr = 1.3 + R.nextDouble() * 0.6;
                    mc.level.addParticle(ParticleTypes.CLOUD,
                            cx + Math.cos(a) * rr, y, cz + Math.sin(a) * rr,
                            -Math.cos(a) * 0.12, 0.05, -Math.sin(a) * 0.12);
                }
                burst(c, y, ParticleTypes.SWEEP_ATTACK, 10, 0.6, 0.2);
                break;
            }
            case "iop_pourfendre":
                for (BlockPos c : fx.cells) burst(c, y, ParticleTypes.FLAME, 10, 0.35, 0.15);
                if (!fx.cells.isEmpty()) puff(fx.cells.get(fx.cells.size() / 2), y, ParticleTypes.SWEEP_ATTACK, 3);
                break;
            case "iop_colere":
                for (BlockPos c : fx.cells) {
                    burst(c, y, ParticleTypes.LAVA, 6, 0.35, 0.1);
                    burst(c, y, ParticleTypes.FLAME, 8, 0.4, 0.2);
                }
                if (!fx.cells.isEmpty()) puff(fx.cells.get(0), y, ParticleTypes.EXPLOSION, 1);
                break;
            case "iop_uppercut":
                for (BlockPos c : fx.cells) column(c, fx.floorY, ParticleTypes.CRIT, 18);
                break;
            case "cra_basic":
                for (BlockPos c : fx.cells) burst(c, y, ParticleTypes.ENCHANTED_HIT, 12, 0.4, 0.18);
                break;
            case "cra_snipe":
                for (BlockPos c : fx.cells) {
                    burst(c, y, ParticleTypes.ELECTRIC_SPARK, 8, 0.3, 0.12);
                    burst(c, y, ParticleTypes.CRIT, 4, 0.3, 0.1);
                }
                break;
            case "cra_explosive":
                for (BlockPos c : fx.cells) {
                    burst(c, y, ParticleTypes.FLAME, 8, 0.4, 0.2);
                    burst(c, y, ParticleTypes.LARGE_SMOKE, 4, 0.4, 0.08);
                }
                if (!fx.cells.isEmpty()) puff(fx.cells.get(fx.cells.size() / 2), y, ParticleTypes.EXPLOSION, 1);
                break;
            case "cra_pluie":
                for (BlockPos c : fx.cells) rain(c, fx.floorY);
                break;
            case "cra_chercheuse":
                for (BlockPos c : fx.cells) {
                    converge(c, y);
                    burst(c, y, ParticleTypes.ENCHANTED_HIT, 10, 0.35, 0.15);
                }
                break;
            case "cra_tirrapide":
                for (BlockPos c : fx.cells) burst(c, y, ParticleTypes.ENCHANTED_HIT, 8, 0.3, 0.15);
                break;
            case "iop_allie":
            case "cra_sentinelle":
            case "sadida_poupee":
            case "sadida_golem":
                for (BlockPos c : fx.cells) {
                    burst(c, y, ParticleTypes.CLOUD, 12, 0.4, 0.1);
                    burst(c, y, ParticleTypes.PORTAL, 8, 0.4, 0.2);
                }
                break;
            case "sadida_ronce":
                for (BlockPos c : fx.cells) burst(c, y, ParticleTypes.COMPOSTER, 12, 0.4, 0.15);
                break;
            case "sadida_graine":
                for (BlockPos c : fx.cells) {
                    burst(c, y, ParticleTypes.COMPOSTER, 8, 0.35, 0.12);
                    burst(c, y, ParticleTypes.HAPPY_VILLAGER, 5, 0.35, 0.1);
                }
                break;
            case "sadida_sylvestre":
                for (BlockPos c : fx.cells) {
                    burst(c, y, ParticleTypes.COMPOSTER, 10, 0.4, 0.15);
                    burst(c, y, ParticleTypes.CLOUD, 4, 0.4, 0.08);
                }
                break;
            case "sadida_soin":
                for (BlockPos c : fx.cells) {
                    burst(c, y, ParticleTypes.HEART, 8, 0.4, 0.2);
                    burst(c, y, ParticleTypes.HAPPY_VILLAGER, 8, 0.4, 0.15);
                }
                break;
            case "summon_hit":
                for (BlockPos c : fx.cells) burst(c, y, ParticleTypes.SWEEP_ATTACK, 5, 0.3, 0.12);
                break;
            case "iop_bond":
            case "sadida_liane":
                for (BlockPos c : fx.cells) {
                    burst(c, y, ParticleTypes.PORTAL, 12, 0.4, 0.2);
                    burst(c, y, ParticleTypes.CRIT, 8, 0.4, 0.15);
                }
                break;
            case "iop_sanguinaire":
                for (BlockPos c : fx.cells) {
                    burst(c, y, ParticleTypes.DAMAGE_INDICATOR, 8, 0.35, 0.12);
                    burst(c, y, ParticleTypes.HEART, 4, 0.35, 0.15);
                }
                break;
            case "vol_heal":
                for (BlockPos c : fx.cells) {
                    burst(c, y, ParticleTypes.HEART, 8, 0.4, 0.2);
                }
                break;
            case "cra_piege":
            case "cra_piege_explosif":
            case "sadida_roncier":
            case "trap_place":
                for (BlockPos c : fx.cells) {
                    burst(c, y, ParticleTypes.CRIT, 6, 0.3, 0.1);
                    burst(c, y, ParticleTypes.SMOKE, 6, 0.3, 0.05);
                }
                break;
            case "trap_trigger":
                for (BlockPos c : fx.cells) {
                    burst(c, y, ParticleTypes.FLAME, 10, 0.4, 0.15);
                    burst(c, y, ParticleTypes.LARGE_SMOKE, 6, 0.45, 0.08);
                    burst(c, y, ParticleTypes.CRIT, 8, 0.45, 0.2);
                }
                break;
            case "iop_couperet":
            case "iop_brise":
            case "iop_rage":
                for (BlockPos c : fx.cells) {
                    burst(c, y, ParticleTypes.SWEEP_ATTACK, 6, 0.4, 0.12);
                    burst(c, y, ParticleTypes.CRIT, 8, 0.4, 0.15);
                }
                break;
            case "iop_wakfu":
            case "iop_apoca":
                for (BlockPos c : fx.cells) {
                    burst(c, y, ParticleTypes.CRIT, 10, 0.45, 0.18);
                    burst(c, y, ParticleTypes.FLAME, 6, 0.4, 0.1);
                    burst(c, y, ParticleTypes.CLOUD, 5, 0.45, 0.08);
                }
                break;
            case "cra_perforante":
            case "cra_faucon":
            case "cra_solaire":
            case "cra_jugement":
                for (BlockPos c : fx.cells) {
                    burst(c, y, ParticleTypes.CRIT, 10, 0.4, 0.2);
                    burst(c, y, ParticleTypes.END_ROD, 4, 0.35, 0.08);
                }
                break;
            case "cra_tornade":
                for (BlockPos c : fx.cells) {
                    burst(c, y, ParticleTypes.CLOUD, 8, 0.45, 0.12);
                    burst(c, y, ParticleTypes.CRIT, 6, 0.4, 0.15);
                }
                break;
            case "sadida_etrangleuse":
            case "sadida_multiples":
            case "sadida_bombardier":
                for (BlockPos c : fx.cells) {
                    burst(c, y, ParticleTypes.COMPOSTER, 10, 0.4, 0.15);
                    burst(c, y, ParticleTypes.SPORE_BLOSSOM_AIR, 4, 0.4, 0.05);
                }
                break;
            case "sadida_vie":
                for (BlockPos c : fx.cells) {
                    burst(c, y, ParticleTypes.HEART, 10, 0.45, 0.2);
                    burst(c, y, ParticleTypes.HAPPY_VILLAGER, 10, 0.45, 0.15);
                }
                break;
            case "sadida_colosse":
                for (BlockPos c : fx.cells) {
                    burst(c, y, ParticleTypes.CLOUD, 14, 0.5, 0.1);
                    burst(c, y, ParticleTypes.PORTAL, 10, 0.45, 0.2);
                }
                break;
            default:
                for (BlockPos c : fx.cells) burst(c, y, ParticleTypes.CRIT, 8, 0.4, 0.15);
        }
    }

    private static void burst(BlockPos c, double y, ParticleOptions t, int n, double spread, double up) {
        var lvl = Minecraft.getInstance().level;
        for (int i = 0; i < n; i++) {
            lvl.addParticle(t,
                    c.getX() + 0.5 + (R.nextDouble() - 0.5) * spread * 2,
                    y + R.nextDouble() * 0.8,
                    c.getZ() + 0.5 + (R.nextDouble() - 0.5) * spread * 2,
                    (R.nextDouble() - 0.5) * 0.15, up * R.nextDouble(), (R.nextDouble() - 0.5) * 0.15);
        }
    }

    private static void puff(BlockPos c, double y, ParticleOptions t, int n) {
        var lvl = Minecraft.getInstance().level;
        for (int i = 0; i < n; i++) {
            lvl.addParticle(t, c.getX() + 0.5, y + 0.3, c.getZ() + 0.5, 0, 0, 0);
        }
    }

    private static void column(BlockPos c, int floorY, ParticleOptions t, int n) {
        var lvl = Minecraft.getInstance().level;
        for (int i = 0; i < n; i++) {
            lvl.addParticle(t,
                    c.getX() + 0.5 + (R.nextDouble() - 0.5) * 0.5,
                    floorY + 0.2 + R.nextDouble() * 2.2,
                    c.getZ() + 0.5 + (R.nextDouble() - 0.5) * 0.5,
                    0, 0.25 + R.nextDouble() * 0.2, 0);
        }
    }

    private static void rain(BlockPos c, int floorY) {
        var lvl = Minecraft.getInstance().level;
        for (int i = 0; i < 10; i++) {
            lvl.addParticle(ParticleTypes.DRAGON_BREATH,
                    c.getX() + 0.5 + (R.nextDouble() - 0.5) * 0.9,
                    floorY + 3.5 + R.nextDouble(),
                    c.getZ() + 0.5 + (R.nextDouble() - 0.5) * 0.9,
                    0, -0.35, 0);
        }
        for (int i = 0; i < 3; i++) {
            lvl.addParticle(ParticleTypes.WITCH,
                    c.getX() + 0.5, floorY + 1.5, c.getZ() + 0.5, 0, -0.1, 0);
        }
    }

    private static void converge(BlockPos c, double y) {
        var lvl = Minecraft.getInstance().level;
        double cx = c.getX() + 0.5, cz = c.getZ() + 0.5;
        for (int i = 0; i < 20; i++) {
            double a = R.nextDouble() * Math.PI * 2;
            double rr = 1.4 + R.nextDouble() * 0.8;
            lvl.addParticle(ParticleTypes.PORTAL,
                    cx + Math.cos(a) * rr, y + R.nextDouble(), cz + Math.sin(a) * rr,
                    -Math.cos(a) * 0.5, 0, -Math.sin(a) * 0.5);
        }
    }

    private SpellFx() {}
}
