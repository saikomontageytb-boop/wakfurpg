package fr.wakfu.rpg.core;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

/**
 * Données RPG persistantes d'un joueur : classe, niveau, XP, PV.
 * Synchronisées vers le client via paquet (voir network.SyncRpgDataPacket).
 */
public class PlayerRpgData implements INBTSerializable<CompoundTag> {

    private RpgClass rpgClass = null;
    private int level = 1;
    private int xp = 0;
    private int hp = 20;

    // --- progression / competences
    private int skillPoints = 0;
    private int bonusHp = 0;
    private int bonusPa = 0;
    private int bonusPm = 0;
    // v0.16 : nouvelles stats (force / sagesse / resistance) + points de sort
    private int bonusStr = 0;
    private int bonusWis = 0;
    private int bonusRes = 0;
    private int spellPts = 0;
    // v0.16 : arme equipee (clic droit) — stockee en NBT d'ItemStack
    private net.minecraft.nbt.CompoundTag eqWeapon = null;
    // v0.31 : monture equipee
    private String equippedMount = "";
    private int mountEntityId = -1;
    // v0.17 : rattrapage unique des points pour les personnages existants
    private boolean migrated17 = false;
    private boolean wiped20 = false;

    /** Accorde 4 pts de statut + 1 pt de sort par niveau deja atteint (une seule fois). */
    public void migrate17() {
        if (migrated17) return;
        migrated17 = true;
        int n = Math.max(0, level - 1);
        skillPoints += n * 4;
        spellPts += n;
    }

    public boolean isMigrated17() { return migrated17; }

    /** v0.20 : reset complet de la progression (une seule fois, au login). */
    public void wipe20() {
        if (wiped20) return;
        wiped20 = true;
        rpgClass = null;
        level = 1;
        xp = 0;
        skillPoints = 0;
        spellPts = 0;
        bonusHp = 0;
        bonusPa = 0;
        bonusPm = 0;
        bonusStr = 0;
        bonusWis = 0;
        bonusRes = 0;
        spellLevels.clear();
        equipped.clear();
        eqWeapon = null;
        questIndex = 0;
        questProgress = 0;
        equippedMount = "";
        mountEntityId = -1;
        migrated17 = true;   // pas de rattrapage apres wipe
        hp = maxHp();
    }
    private final java.util.HashMap<String, Integer> spellLevels = new java.util.HashMap<>();

    private int questIndex = 0;
    private int questProgress = 0;

    // --- v0.15 : Codex (bestiaire / exploration / butin) ---
    private final java.util.HashMap<String, Integer> codexKills = new java.util.HashMap<>();
    private final java.util.HashSet<String> codexSeen = new java.util.HashSet<>();
    private final java.util.HashSet<String> codexZones = new java.util.HashSet<>();
    private boolean codexDungeon = false;
    private final java.util.HashMap<String, Integer> codexItems = new java.util.HashMap<>();

    public java.util.Map<String, Integer> getCodexKills() { return codexKills; }
    public java.util.Set<String> getCodexSeen() { return codexSeen; }
    public java.util.Set<String> getCodexZones() { return codexZones; }
    public boolean isCodexDungeon() { return codexDungeon; }
    public java.util.Map<String, Integer> getCodexItems() { return codexItems; }

    /** @return true si la decouverte est nouvelle. */
    public boolean codexSee(String monsterId) { return codexSeen.add(monsterId); }

    public void codexKill(String monsterId) {
        codexSeen.add(monsterId);
        codexKills.merge(monsterId, 1, Integer::sum);
    }

    /** @return true si la zone est nouvellement exploree. */
    public boolean codexVisitZone(int sx, int sz) { return codexZones.add(sx + "," + sz); }

    /** @return true si le donjon vient d'etre decouvert. */
    public boolean codexFindDungeon() {
        if (codexDungeon) return false;
        codexDungeon = true;
        return true;
    }

    public void codexReceive(String itemKey, int count) {
        codexItems.merge(itemKey, Math.max(1, count), Integer::sum);
    }

    public int getQuestIndex() { return questIndex; }
    public int getQuestProgress() { return questProgress; }
    public void addQuestProgress(int n) { this.questProgress += n; }
    public void setQuestProgress(int n) { this.questProgress = n; }
    public void nextQuest() { this.questIndex++; this.questProgress = 0; }

    public void setLevel(int l) { this.level = Math.max(1, l); }

    public int getSkillPoints() {
        return skillPoints;
    }

    public void addSkillPoints(int n) {
        this.skillPoints += n;
    }

    public boolean spendSkillPoints(int cost) {
        if (skillPoints < cost) return false;
        skillPoints -= cost;
        return true;
    }

    public int getBonusHp() { return bonusHp; }
    public int getBonusPa() { return bonusPa; }
    public int getBonusPm() { return bonusPm; }
    public void addBonusHp(int n) { this.bonusHp += n; }
    public void addBonusPa(int n) { this.bonusPa += n; }
    public void addBonusPm(int n) { this.bonusPm += n; }

    public int getBonusStr() { return bonusStr; }
    public int getBonusWis() { return bonusWis; }
    public int getBonusRes() { return bonusRes; }
    public void addBonusStr(int n) { this.bonusStr += n; }
    public void addBonusWis(int n) { this.bonusWis += n; }
    public void addBonusRes(int n) { this.bonusRes += n; }

    public int getSpellPts() { return spellPts; }
    public void addSpellPts(int n) { this.spellPts += n; }
    public boolean spendSpellPts(int cost) {
        if (spellPts < cost) return false;
        spellPts -= cost;
        return true;
    }

    // --- arme equipee (v0.16)
    public void setEquippedWeapon(net.minecraft.world.item.ItemStack s) {
        if (s == null || s.isEmpty()) {
            eqWeapon = null;
        } else {
            eqWeapon = s.save(new net.minecraft.nbt.CompoundTag());
        }
    }

    public net.minecraft.world.item.ItemStack getEquippedWeapon() {
        return eqWeapon == null ? net.minecraft.world.item.ItemStack.EMPTY
                : net.minecraft.world.item.ItemStack.of(eqWeapon);
    }

    public int spellLevel(String spellId) {
        return spellLevels.getOrDefault(spellId, 0);
    }

    public void addSpellLevel(String spellId) {
        spellLevels.merge(spellId, 1, Integer::sum);
    }

    // --- sorts equipes (barre de combat, 4 max)
    private final java.util.List<String> equipped = new java.util.ArrayList<>();

    public java.util.List<String> getEquipped() {
        if (equipped.isEmpty() && rpgClass != null) {
            for (var s : fr.wakfu.rpg.core.spell.Spells.forClass(rpgClass)) {
                if (equipped.size() >= fr.wakfu.rpg.core.spell.Spells.MAX_EQUIPPED) break;
                equipped.add(s.id);
            }
        }
        return equipped;
    }

    public boolean isEquipped(String id) {
        return equipped.contains(id);
    }

    /** @return true si refuse (barre pleine). */
    public boolean toggleEquip(String id) {
        if (equipped.contains(id)) {
            equipped.remove(id);
            return false;
        }
        if (equipped.size() >= fr.wakfu.rpg.core.spell.Spells.MAX_EQUIPPED) return true;
        equipped.add(id);
        return false;
    }

    // --- état d'arène de combat (persisté pour survivre déconnexions/crashes)
    private boolean inArena = false;
    private int retX, retY, retZ;
    private net.minecraft.nbt.ListTag savedInv = null;

    // v0.18 : contenu de la banque personnelle (27 slots, ListTag de CompoundTag d'items)
    private net.minecraft.nbt.ListTag bankTag = new net.minecraft.nbt.ListTag();

    public net.minecraft.nbt.ListTag getBankTag() {
        return bankTag;
    }

    public void setBankTag(net.minecraft.nbt.ListTag t) {
        bankTag = t;
    }

    public boolean isInArena() {
        return inArena;
    }

    public void setInArena(boolean v) {
        this.inArena = v;
    }

    public void setArenaReturn(int x, int y, int z) {
        this.retX = x;
        this.retY = y;
        this.retZ = z;
    }

    public int getRetX() { return retX; }
    public int getRetY() { return retY; }
    public int getRetZ() { return retZ; }

    public net.minecraft.nbt.ListTag getSavedInv() {
        return savedInv;
    }

    public void setSavedInv(net.minecraft.nbt.ListTag tag) {
        this.savedInv = tag;
    }

    public RpgClass getRpgClass() {
        return rpgClass;
    }

    public boolean hasClass() {
        return rpgClass != null;
    }

    public void chooseClass(RpgClass c) {
        if (rpgClass != null) return;
        this.rpgClass = c;
        this.level = 1;
        this.xp = 0;
        this.hp = maxHp();
    }

    /** v0.12 : changement de classe. Niveau/XP/bonus conserves, sorts remis a zero. */
    public void changeClass(RpgClass c) {
        this.rpgClass = c;
        this.spellLevels.clear();
        this.equipped.clear();
        this.hp = maxHp();
    }

    public int getLevel() {
        return level;
    }

    public int getXp() {
        return xp;
    }

    public int xpToNext() {
        return 100 * level * level;
    }

    public int getXpToNext() {
        return xpToNext();
    }

    /** v0.20 : niveau maximum — deblocage des sorts etale jusque-la. */
    public static final int MAX_LEVEL = 50;

    public void addXp(int amount) {
        if (rpgClass == null) return;
        if (this.level >= MAX_LEVEL) {
            this.xp = 0;
            return;
        }
        this.xp += amount;
        while (this.xp >= xpToNext() && this.level < MAX_LEVEL) {
            this.xp -= xpToNext();
            this.level++;
            this.skillPoints += 5; // v0.16 : 5 points de statut par niveau
            this.spellPts += 1;    // v0.16 : 1 point de sort par niveau
            this.hp = maxHp(); // full heal au level up
        }
        if (this.level >= MAX_LEVEL) this.xp = 0;
    }

    public int maxHp() {
        return (rpgClass == null ? 20 : rpgClass.maxHp(level)) + bonusHp;
    }

    public int maxAp() {
        return (rpgClass == null ? 0 : rpgClass.maxAp()) + bonusPa;
    }

    public int maxMp() {
        return (rpgClass == null ? 0 : rpgClass.maxMp()) + bonusPm;
    }

    public int getHp() {
        return hp;
    }

    public void setHp(int hp) {
        this.hp = Math.max(0, Math.min(hp, maxHp()));
    }

    // --- monture equipee (v0.31)
    public String getEquippedMount() { return equippedMount; }
    public void setEquippedMount(String type) { this.equippedMount = type == null ? "" : type; }
    public int getMountEntityId() { return mountEntityId; }
    public void setMountEntityId(int id) { this.mountEntityId = id; }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("class", rpgClass == null ? "" : rpgClass.getId());
        tag.putInt("level", level);
        tag.putInt("xp", xp);
        tag.putInt("hp", hp);
        tag.putInt("skillPoints", skillPoints);
        tag.putInt("bonusHp", bonusHp);
        tag.putInt("bonusPa", bonusPa);
        tag.putInt("bonusPm", bonusPm);
        tag.putInt("bonusStr", bonusStr);
        tag.putInt("bonusWis", bonusWis);
        tag.putInt("bonusRes", bonusRes);
        tag.putInt("spellPts", spellPts);
        tag.putBoolean("migrated17", migrated17);
        tag.putBoolean("wiped20", wiped20);
        tag.put("bank", bankTag);
        if (eqWeapon != null) tag.put("eqWeapon", eqWeapon);
        CompoundTag sl = new CompoundTag();
        for (var e : spellLevels.entrySet()) sl.putInt(e.getKey(), e.getValue());
        tag.put("spellLevels", sl);
        net.minecraft.nbt.ListTag eq = new net.minecraft.nbt.ListTag();
        for (String id : equipped) eq.add(net.minecraft.nbt.StringTag.valueOf(id));
        tag.put("equipped", eq);
        tag.putInt("questIndex", questIndex);
        tag.putInt("questProgress", questProgress);
        CompoundTag ck = new CompoundTag();
        for (var e : codexKills.entrySet()) ck.putInt(e.getKey(), e.getValue());
        tag.put("codexKills", ck);
        CompoundTag ci = new CompoundTag();
        for (var e : codexItems.entrySet()) ci.putInt(e.getKey(), e.getValue());
        tag.put("codexItems", ci);
        net.minecraft.nbt.ListTag cs = new net.minecraft.nbt.ListTag();
        for (String s : codexSeen) cs.add(net.minecraft.nbt.StringTag.valueOf(s));
        tag.put("codexSeen", cs);
        net.minecraft.nbt.ListTag cz = new net.minecraft.nbt.ListTag();
        for (String s : codexZones) cz.add(net.minecraft.nbt.StringTag.valueOf(s));
        tag.put("codexZones", cz);
        tag.putBoolean("codexDungeon", codexDungeon);
        tag.putBoolean("inArena", inArena);
        tag.putInt("retX", retX);
        tag.putInt("retY", retY);
        tag.putInt("retZ", retZ);
        if (savedInv != null) {
            tag.put("savedInv", savedInv);
        }
        tag.putString("equippedMount", equippedMount);
        tag.putInt("mountEntityId", mountEntityId);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        RpgClass c = RpgClass.byId(tag.getString("class"));
        this.rpgClass = c;
        this.level = Math.max(1, tag.getInt("level"));
        this.xp = tag.getInt("xp");
        this.hp = tag.getInt("hp");
        this.skillPoints = tag.getInt("skillPoints");
        this.bonusHp = tag.getInt("bonusHp");
        this.bonusPa = tag.getInt("bonusPa");
        this.bonusPm = tag.getInt("bonusPm");
        this.bonusStr = tag.getInt("bonusStr");
        this.bonusWis = tag.getInt("bonusWis");
        this.bonusRes = tag.getInt("bonusRes");
        this.spellPts = tag.getInt("spellPts");
        this.migrated17 = tag.getBoolean("migrated17");
        this.wiped20 = tag.getBoolean("wiped20");
        this.eqWeapon = tag.contains("eqWeapon") ? tag.getCompound("eqWeapon") : null;
        this.questIndex = tag.getInt("questIndex");
        this.questProgress = tag.getInt("questProgress");
        codexKills.clear();
        CompoundTag ck = tag.getCompound("codexKills");
        for (String k : ck.getAllKeys()) codexKills.put(k, ck.getInt(k));
        codexItems.clear();
        CompoundTag ci = tag.getCompound("codexItems");
        for (String k : ci.getAllKeys()) codexItems.put(k, ci.getInt(k));
        codexSeen.clear();
        net.minecraft.nbt.ListTag cs = tag.getList("codexSeen", 8);
        for (int i = 0; i < cs.size(); i++) codexSeen.add(cs.getString(i));
        codexZones.clear();
        net.minecraft.nbt.ListTag cz = tag.getList("codexZones", 8);
        for (int i = 0; i < cz.size(); i++) codexZones.add(cz.getString(i));
        this.codexDungeon = tag.getBoolean("codexDungeon");
        spellLevels.clear();
        CompoundTag sl = tag.getCompound("spellLevels");
        for (String k : sl.getAllKeys()) spellLevels.put(k, sl.getInt(k));
        equipped.clear();
        net.minecraft.nbt.ListTag eq = tag.getList("equipped", 8);
        for (int i = 0; i < eq.size(); i++) equipped.add(eq.getString(i));
        this.inArena = tag.getBoolean("inArena");
        this.retX = tag.getInt("retX");
        this.retY = tag.getInt("retY");
        this.retZ = tag.getInt("retZ");
        this.savedInv = tag.contains("savedInv") ? tag.getList("savedInv", 10) : null;
        this.bankTag = tag.contains("bank") ? tag.getList("bank", 10) : new net.minecraft.nbt.ListTag();
        this.equippedMount = tag.getString("equippedMount");
        this.mountEntityId = tag.getInt("mountEntityId");
    }
}
