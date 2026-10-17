package fr.wakfu.rpg.entity;

import fr.wakfu.rpg.core.MobVariants;
import fr.wakfu.rpg.core.MonsterType;
import fr.wakfu.rpg.core.combat.CombatManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.ResourceLocation;

/**
 * Monstre affrontable : PV propres, niveau = NIVEAU JOUEUR CONSEILLÉ affiché
 * dans le nom. v0.23 : variantes visuelles par zone (textures distinctes),
 * errance douce dans son secteur (jamais hors zone), et retour automatique
 * au sol si jamais il se retrouve sur un toit. Clic droit -> combat.
 */
public class MonsterNpc extends PathfinderMob {

    // v0.27 : types Alex's Mobs pour spawn aleatoire
    private static final MonsterType[] ALEX_TYPES = {
        MonsterType.GRIZZLY, MonsterType.GAZELLE, MonsterType.RATTLESNAKE,
        MonsterType.ELEPHANT, MonsterType.GORILLA, MonsterType.FALCON,
        MonsterType.KOMODO_DRAGON, MonsterType.MOOSE, MonsterType.TIGER, MonsterType.CROW
    };
    // v0.27 : visual map for Alex Mobs rendering
    private static final java.util.Map<String, String> ALEX_VISUAL_MAP = new java.util.HashMap<>();
    static {
        ALEX_VISUAL_MAP.put("grizzly", "alexsmobs:grizzly_bear");
        ALEX_VISUAL_MAP.put("gazelle", "alexsmobs:gazelle");
        ALEX_VISUAL_MAP.put("rattlesnake", "alexsmobs:rattlesnake");
        ALEX_VISUAL_MAP.put("elephant", "alexsmobs:elephant");
        ALEX_VISUAL_MAP.put("gorilla", "alexsmobs:gorilla");
        ALEX_VISUAL_MAP.put("falcon", "alexsmobs:roadrunner");
        ALEX_VISUAL_MAP.put("komodo_dragon", "alexsmobs:komodo_dragon");
        ALEX_VISUAL_MAP.put("moose", "alexsmobs:moose");
        ALEX_VISUAL_MAP.put("tiger", "alexsmobs:tiger");
        ALEX_VISUAL_MAP.put("crow", "alexsmobs:crow");
    }
    private int visualEntityId = -1;
    private MonsterType type = MonsterType.ZOMBIE;
    private String variant = "";              // v0.23 : "" = apparence de base
    private int currentHp;
    private int level = 1;
    private java.util.UUID summoner = null;   // v0.14 : non-null si invocation
    // v0.23 : territoire d'errance (les monstres de terrain seulement)
    private boolean hasHome = false;
    private double homeX, homeY, homeZ;

    public MonsterNpc(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setInvulnerable(true);
        setPersistenceRequired();
        this.currentHp = this.type.maxHp;
    }

    /** v0.25 : œuf de spawn → monstre de zone initialisé, avec foyer d'errance. */
    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(
            net.minecraft.world.level.ServerLevelAccessor acc,
            net.minecraft.world.DifficultyInstance diff,
            net.minecraft.world.entity.MobSpawnType mtype,
            net.minecraft.world.entity.SpawnGroupData data,
            net.minecraft.nbt.CompoundTag tag) {
        if (mtype == net.minecraft.world.entity.MobSpawnType.SPAWN_EGG
                || mtype == net.minecraft.world.entity.MobSpawnType.COMMAND) {
            // v0.27 : spawn aleatoire parmi les Alex's Mobs
            MonsterType pick = this.type;
            if (pick == null || pick == MonsterType.ZOMBIE) {
                pick = ALEX_TYPES[getRandom().nextInt(ALEX_TYPES.length)];
            }
            init(pick, 1 + getRandom().nextInt(3), "");
            addTag("manual");
            if (!hasHome) setHome(getX(), getY(), getZ());
            spawnVisual();
        }
        return super.finalizeSpawn(acc, diff, mtype, data, tag);
    }

    public void init(MonsterType t) {
        init(t, 1, "");
    }

    public void init(MonsterType t, int lvl) {
        init(t, lvl, "");
    }

    public void init(MonsterType t, int lvl, String variant) {
        this.type = t;
        this.variant = variant == null ? "" : variant;
        this.level = Math.max(1, Math.min(50, lvl));
        this.currentHp = maxHpFor();
        refreshName();
    }

    /** Nom lisible : variante française + niveau conseillé coloré. */
    public void refreshName() {
        String col = level < 4 ? "§a" : (level < 7 ? "§e" : (level < 10 ? "§c" : "§d"));
        setCustomName(Component.literal(displayName() + " " + col + "Nv." + level));
        setCustomNameVisible(true);
    }

    public String displayName() {
        MobVariants.Variant v = MobVariants.get(type.getId(), variant);
        return v != null ? v.nameFr : type.getName().getString();
    }

    public String getTexturePath() {
        MobVariants.Variant v = MobVariants.get(type.getId(), variant);
        if (v != null && v.texture != null) return v.texture;
        return type.texture;
    }

    public MonsterType getMonsterType() {
        return type;
    }

    public String getVariant() {
        return variant;
    }

    public int getLevel() {
        return level;
    }

    public java.util.UUID getSummoner() {
        return summoner;
    }

    public void setSummoner(java.util.UUID u) {
        this.summoner = u;
    }

    public boolean isSummon() {
        return summoner != null || getTags().contains("summon");
    }

    /** v0.23 : attache le monstre à un territoire d'errance (spawn de zone). */
    public void setHome(double x, double y, double z) {
        this.homeX = x;
        this.homeY = y;
        this.homeZ = z;
        this.hasHome = true;
        restrictTo(new BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)), 26);
    }

    public int maxHpFor() {
        return (int) Math.round(type.maxHp * (1 + 0.40 * (level - 1)));
    }

    public int getDamage() {
        return (int) Math.round(type.damage * (1 + 0.30 * (level - 1)));
    }

    public int getXpReward() {
        return (int) Math.round(type.xpReward * (1 + 0.25 * (level - 1)));
    }

    public int getCurrentHp() {
        return currentHp;
    }

    public void hurtCombat(int dmg) {
        this.currentHp = Math.max(0, this.currentHp - dmg);
    }

    public void setCurrentHp(int hp) {
        this.currentHp = Math.max(0, Math.min(hp, maxHpFor()));
    }

    public void dieCombat() {
        discardVisual();
        this.discard();
    }

    // ---------------------------------------------------------------- IA
    /** Errance douce : uniquement pour les monstres de terrain (pas les
     *  invocations ni les copies d'arène qui n'ont pas de territoire). */
    private class ZoneStrollGoal extends WaterAvoidingRandomStrollGoal {
        ZoneStrollGoal() {
            super(MonsterNpc.this, 0.6D, 30);
        }

        @Override
        public boolean canUse() {
            return hasHome && !isSummon() && !getTags().contains("arena_copy") && super.canUse();
        }
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new ZoneStrollGoal());
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F));
    }


    /** v0.27 : spawn Alex's Mobs entity for visual rendering. */
    public void spawnVisual() {
        if (level().isClientSide || visualEntityId >= 0) return;
        String alexId = ALEX_VISUAL_MAP.get(type.getId());
        if (alexId == null) return;
        EntityType<?> et = ForgeRegistries.ENTITY_TYPES.getValue(
            new net.minecraft.resources.ResourceLocation(alexId));
        if (et == null) return;
        Entity visual = et.create(level());
        if (visual == null) return;
        visual.moveTo(getX(), getY(), getZ(), getYRot(), getXRot());
        visual.setInvulnerable(true);
        if (visual instanceof net.minecraft.world.entity.Mob mob) {
            mob.setNoAi(true);
            mob.setPersistenceRequired();
        }
        visual.addTag("alex_visual");
        visual.addTag("visual_of_" + getId());
        level().addFreshEntity(visual);
        visualEntityId = visual.getId();
        setInvisible(true);
    }

    /** v0.27 : sync visual entity position. */
    public void syncVisual() {
        if (level().isClientSide || visualEntityId < 0) return;
        Entity visual = level().getEntity(visualEntityId);
        if (visual != null && isAlive()) {
            visual.moveTo(getX(), getY(), getZ(), getYRot(), getXRot());
            visual.setInvulnerable(true);
        } else if (visual != null) {
            visual.discard();
            visualEntityId = -1;
        }
    }

    public void discardVisual() {
        if (visualEntityId < 0) return;
        Entity visual = level().getEntity(visualEntityId);
        if (visual != null) visual.discard();
        visualEntityId = -1;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        // v0.27 : sync visual entity
        syncVisual();
        if (!hasHome || isSummon()) return;
        // reste dans sa zone : au-delà de 28 blocs du foyer, on rentre
        double dx = getX() - homeX, dz = getZ() - homeZ;
        if (dx * dx + dz * dz > 28 * 28) {
            getNavigation().moveTo(homeX, homeY, homeZ, 0.8);
        }
        // anti-toit : si le monstre se retrouve perché, il revient au sol
        if (getY() > homeY + 6) {
            int gy = level().getHeight(Heightmap.Types.MOTION_BLOCKING,
                    (int) Math.floor(homeX), (int) Math.floor(homeZ));
            teleportTo(homeX + 0.5, gy, homeZ + 0.5);
            getNavigation().stop();
        }
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return false;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (!level().isClientSide() && player instanceof ServerPlayer sp) {
            if (CombatManager.inCombat(sp.getUUID())) {
                return InteractionResult.SUCCESS; // le combat se joue via cases / barre de sorts
            }
            CombatManager.startCombat(sp, this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide());
    }

    // ---------------------------------------------------------------- NBT
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("mtype", type.getId());
        tag.putString("mvariant", variant);
        tag.putInt("mhp", currentHp);
        tag.putInt("visualId", visualEntityId);
        tag.putInt("mlevel", level);
        tag.putBoolean("mhome", hasHome);
        if (hasHome) {
            tag.putDouble("homex", homeX);
            tag.putDouble("homey", homeY);
            tag.putDouble("homez", homeZ);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        MonsterType t = MonsterType.byId(tag.getString("mtype"));
        // v0.27 : ancien ZOMBIE -> random Alex's Mobs
        if (t == null || (t == MonsterType.ZOMBIE && tag.getString("mtype").isEmpty())) {
            t = ALEX_TYPES[getRandom().nextInt(ALEX_TYPES.length)];
        }
        this.type = t;
        this.variant = tag.getString("mvariant");
        this.level = Math.max(1, tag.getInt("mlevel"));
        this.currentHp = tag.getInt("mhp");
        this.visualEntityId = tag.getInt("visualId");
        if (this.currentHp <= 0) this.currentHp = maxHpFor();
        this.hasHome = tag.getBoolean("mhome");
        if (hasHome) {
            this.homeX = tag.getDouble("homex");
            this.homeY = tag.getDouble("homey");
            this.homeZ = tag.getDouble("homez");
            restrictTo(new BlockPos((int) Math.floor(homeX), (int) Math.floor(homeY),
                    (int) Math.floor(homeZ)), 26);
        } else if (!isSummon() && type.xpReward > 0 && !getTags().contains("arena_copy")) {
            // v0.24 : les monstres deja en monde (charges sans foyer) se mettent
            // eux aussi a errer autour de leur position actuelle
            setHome(getX(), getY(), getZ());
        }
        refreshName();
    }

    @Override
    public Component getName() {
        Component custom = getCustomName();
        return custom != null ? custom : Component.literal(displayName());
    }
}
