package com.curiats.returncapsule.entity.humanoid;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.compat.tacz.TaczRifles;
import com.curiats.returncapsule.compat.tacz.TaczShooter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * A neutral humanoid that wears the vanilla player model with the classic (4px) arms.
 * <p>
 * It is deliberately built on {@link PathfinderMob} with player-shaped attributes and a player-sized hitbox: that is
 * what keeps the arm swing, walk cycle and eye height matching a player, and what player-model animation mods expect.
 * <p>
 * Behaviour, in order of the design: it never starts fights with players; it clears hostile mobs and answers attacks on
 * players and on its own squad (see {@link HumanoidCombatRules} and {@link HumanoidSquad}). Combat is one loop —
 * {@link HumanoidCombatGoal} decides stance, movement and the attack in the same tick, so "shoot" and "walk" can never
 * cancel each other out. The stance is a plain distance rule: the gun while the target is beyond melee reach, the blade
 * once it is inside. It carries a shield in its off hand and raises it when something gets close.
 * <p>
 * Equipment is two named slots — a gun and a melee weapon — plus the off-hand shield. A player can hand over a
 * replacement for any of them; whatever was replaced is dropped (except the built-in sword, which is kit).
 */
public class NeutralHumanoid extends PathfinderMob implements RangedAttackMob {
    /** Which weapon slot is currently in the main hand. */
    public enum Stance {
        RANGED,
        MELEE
    }

    /** Ticks between regeneration ticks; one second, so the profile value reads as "health per second". */
    private static final int REGEN_INTERVAL = 20;
    /** Largest share of max health one hit may remove; see hurt. */
    private static final float MAX_HIT_FRACTION = 0.35F;
    /** Hits below this are ignored outright, so swarms of tiny hits cannot grind the entity down. */
    private static final float MIN_MEANINGFUL_HIT = 6.0F;
    /** Ticks between accepted hits: the invulnerability window borrowed from the old Wulfrum Wither. */
    private static final int HIT_GATE_TICKS = 4;
    /** Ticks of grace after a last stand saves the entity. */
    private static final int LAST_STAND_GRACE = 60;
    /** Ticks before last stand can save it again. */
    private static final int LAST_STAND_COOLDOWN = 1800;
    /** Tick until which damage is ignored after a last stand. */
    private int lastStandUntil = -1;
    /** Tick from which last stand is available again. */
    private int lastStandReadyAt;
    /** Tick of the last hit that landed. */
    private int lastAcceptedHit = -100;
    /** Set while a creative player's hit is applied; see bypassesProtections. */
    private boolean bypassProtections;
    /** True while this entity's own hurt is being applied, so a real hit may still kill it. */
    private boolean applyingHit;
    /** True while a converted direct health write is being applied; such a drain is real even when tiny. */
    private boolean convertedHit;
    /** Distance the target must close before switching back to the rifle, so the choice does not oscillate. */
    private static final double LONG_RANGE_HYSTERESIS = 8.0D;
    /** A single incoming hit at least this large breaks the shield. */
    private static final float SHIELD_BREAK_DAMAGE = 100.0F;
    /** Ticks between slash arts: ten seconds, long enough that it reads as a special move. */
    private static final int BLADE_SKILL_INTERVAL = 200;
    /**
     * Monotonic counter bumped on every melee swing. It is synched data, so the client sees the action even when the
     * vanilla swing flags do not behave the way an animation mod expects.
     */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> DATA_ATTACK_SEQ =
            net.minecraft.network.syncher.SynchedEntityData.defineId(NeutralHumanoid.class,
                    net.minecraft.network.syncher.EntityDataSerializers.INT);

    /** Extra distance past melee reach before the gun comes back out; see shouldHoldGun for why it is this wide. */
    private static final double GUN_DRAW_DISTANCE = 3.0D;
    private static final float EYE_HEIGHT = 1.62F;
    /** Beyond this distance the humanoid sprints instead of walking, the way a player closes a gap. */
    private static final double SPRINT_DISTANCE = 20.0D;
    /** Vanilla caps target search at the follow range, so it has to clear the widest engagement radius. */
    private static final double FOLLOW_RANGE = 512.0D;

    private static final String TAG_LONG_RANGED = "LongRangedWeapon";
    private static final String TAG_RANGED = "RangedWeapon";
    private static final String TAG_MELEE = "MeleeWeapon";
    private static final String TAG_STANCE = "Stance";

    private final HumanoidProfile profile;
    private final GunAim gunAim = new GunAim();
    private boolean loadoutLogged;

    private ItemStack rangedWeapon = ItemStack.EMPTY;
    /**
     * Second gun, meant for long range. A rifle reaches about sixty blocks; past that the falloff makes it useless, so a
     * humanoid that owns both switches to this one when the target is far away.
     */
    private ItemStack longRangedWeapon = ItemStack.EMPTY;
    private ItemStack meleeWeapon = ItemStack.EMPTY;
    /** The off-hand shield, kept here while the gun is out so it can be put back. */
    private ItemStack shield = ItemStack.EMPTY;
    /** Last observed off-hand contents, so {@code auditOffhand} only logs changes. */
    private String lastOffhand = "";
    private Stance stance = Stance.MELEE;
    /** Where it should walk to when idle; cleared on arrival. */
    @Nullable
    private BlockPos destination;
    /** Where it idles around when it has neither a destination nor a player to follow. */
    @Nullable
    private BlockPos home;
    /** Whether it trails the nearest player when idle. */
    private boolean followEnabled = true;
    /** Ticks the shield stays broken after an axe or mace hit. */
    private int shieldBreakTicks;
    /** How many times the shield has been broken; the third one destroys it. */
    private int shieldBreaks;

    private int regenCooldown = REGEN_INTERVAL;
    /** Ticks until the next slash art; blades are meant to feel like a power move, not a basic attack. */
    private int bladeSkillCooldown;
    /** How many arts have been performed, used to alternate between the common and the high-end one. */
    private int bladeSkillUses;
    /** Guns are issued once per life; see refreshLoadout. */
    private boolean gunsIssued;
    /** Which of the two guns is in hand; a flag, so hysteresis survives the stack being copied into the slot. */
    private boolean usingLongGun;
    /** TaCZ needs one {@code draw} before the first shot; reset whenever the main hand changes. */
    private boolean gunDrawn;
    /** Cached classification of the main hand, so the per-tick goal/aim checks never re-run reflection. */
    private boolean holdsGun;
    private boolean holdsBlade;

    public NeutralHumanoid(EntityType<? extends NeutralHumanoid> type, Level level, HumanoidProfile profile) {
        super(type, level);
        this.profile = profile;
        this.xpReward = 40;
        this.setPersistenceRequired();
        this.setDropChance(EquipmentSlot.MAINHAND, 1.0F);
        this.setDropChance(EquipmentSlot.OFFHAND, 1.0F);
        if (!level.isClientSide) {
            this.makeConversionImmune();
            // Deliberately no refreshLoadout here: a new entity instance is created for every chunk load and dimension
            // change, so issuing weapons in the constructor handed out a fresh random pair before the saved one was
            // restored. It is issued in finalizeSpawn instead, which only runs for a genuinely new spawn.
        }
    }

    /**
     * Issues the starting kit, once, when the entity is actually spawned into the world.
     * <p>
     * A constructor runs for every instance the game creates, including ones rebuilt from a save, so rolling weapons
     * there re-randomised a humanoid's guns whenever it was reloaded.
     */
    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor level,
                                                                  net.minecraft.world.DifficultyInstance difficulty,
                                                                  net.minecraft.world.entity.MobSpawnType spawnType,
                                                                  @Nullable net.minecraft.world.entity.SpawnGroupData spawnGroupData,
                                                                  @Nullable CompoundTag tag) {
        net.minecraft.world.entity.SpawnGroupData data =
                super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData, tag);
        this.refreshLoadout();
        return data;
    }

    /**
     * Rebuilds the kit from the current {@link HumanoidToggles}. Safe to call at any time; used by the spawn path and by
     * the {@code /returncapsule} command.
     */
    public void refreshLoadout() {
        if (this.level().isClientSide) {
            return;
        }
        boolean guns = HumanoidToggles.isEnabled(HumanoidToggles.Slot.RANGED)
                && this.profile.weapons().allowsGun();
        boolean blades = HumanoidToggles.isEnabled(HumanoidToggles.Slot.MELEE);
        if (!guns) {
            this.rangedWeapon = ItemStack.EMPTY;
            this.longRangedWeapon = ItemStack.EMPTY;
        } else if (this.rangedWeapon.isEmpty() && !this.gunsIssued) {
            this.rangedWeapon = HumanoidWeapons.startingGun(this.random);
            this.longRangedWeapon = HumanoidWeapons.startingLongGun(this.random);
            // Issued once per life. Without this guard any path that emptied the slot — a reload from an old save, a
            // toggle flip, a player swap that dropped the old gun — would hand out a brand new random weapon, which
            // reads in game as the humanoid cycling through unrelated guns.
            this.gunsIssued = true;
            // The fire mode is part of the line: it is the difference between a rifle that feels automatic and one that
            // fires a single round per trigger pull, and there is no other way to see what was actually written.
            ReturnCapsuleMod.LOGGER.info("[humanoid] issued guns: rifle={} ({}) long={} ({})",
                    com.curiats.returncapsule.compat.tacz.TaczRifles.gunId(this.rangedWeapon),
                    fireModeOf(this.rangedWeapon),
                    com.curiats.returncapsule.compat.tacz.TaczRifles.gunId(this.longRangedWeapon),
                    fireModeOf(this.longRangedWeapon));
        }
        if (!blades) {
            this.meleeWeapon = ItemStack.EMPTY;
        } else if (this.meleeWeapon.isEmpty()) {
            this.meleeWeapon = HumanoidWeapons.startingMelee(this.profile, this.level(), this.random);
        }
        if (!HumanoidToggles.isEnabled(HumanoidToggles.Slot.ITEM)
                || !HumanoidToggles.isEnabled(HumanoidToggles.Slot.SHIELD)) {
            this.shield = ItemStack.EMPTY;
            this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        } else if (this.shield.isEmpty()) {
            this.shield = HumanoidWeapons.builtinShield();
        }
        this.applyStance();
        this.logLoadout();
    }

    /**
     * Logs every change of the off-hand contents, with the stance at that moment.
     * <p>
     * Something has been putting a sword into the off hand and then leaving it there. Only two places in this class
     * write that slot, and both are supposed to write a shield, so either a non-shield is being classified as one or
     * a third writer exists. This turns that into a log line with the exact item, instead of guesswork.
     */
    private void auditOffhand() {
        String now = describe(this.getOffhandItem());
        if (now.equals(this.lastOffhand)) {
            return;
        }
        this.lastOffhand = now;
        ReturnCapsuleMod.LOGGER.info("[humanoid] offhand -> {} (stance={}, shieldSlot={})",
                now, this.stance, describe(this.shield));
    }

    /** One line per humanoid at spawn, so a missing gun is obvious from the log instead of guesswork. */
    private void logLoadout() {
        if (this.loadoutLogged) {
            return;
        }
        this.loadoutLogged = true;
        ReturnCapsuleMod.LOGGER.info("[humanoid] spawned {}: ranged={} gun={} long={} melee={} shield={}",
                this.profile.id(),
                describe(this.rangedWeapon),
                com.curiats.returncapsule.compat.tacz.TaczRifles.gunId(this.rangedWeapon),
                describe(this.longRangedWeapon),
                describe(this.meleeWeapon), describe(this.getOffhandItem()));
        if (this.rangedWeapon.isEmpty() && this.profile.weapons().allowsGun()) {
            ReturnCapsuleMod.LOGGER.warn("[humanoid] no usable TaCZ gun was available for {}; it will fight in melee",
                    this.profile.id());
        }
    }

    private static String describe(ItemStack stack) {
        return stack.isEmpty() ? "none" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /**
     * A second line of defence next to blocking every status effect: the parasite mod that ships with this pack
     * reads its own "cannot be converted" marker from the entity's persistent data, so it is set here as well.
     */
    private void makeConversionImmune() {
        this.getPersistentData().putInt("srpcothimmunity", 1);
    }

    public HumanoidProfile profile() {
        return this.profile;
    }

    public GunAim gunAim() {
        return this.gunAim;
    }

    public static AttributeSupplier.Builder createAttributes(HumanoidProfile profile) {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, profile.maxHealth())
                .add(Attributes.ARMOR, profile.armor())
                .add(Attributes.MOVEMENT_SPEED, HumanoidProfile.WALK_SPEED)
                .add(Attributes.ATTACK_SPEED, HumanoidProfile.PLAYER_ATTACK_SPEED)
                .add(Attributes.ATTACK_DAMAGE, 1.0D)
                // Detection is wide on purpose: what a humanoid actually walks to is decided per target in
                // HumanoidCombatRules, this only stops vanilla from clipping the search at 16-35 blocks.
                .add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // One goal owns stance, movement and attacking; see HumanoidCombatGoal for why it is not split up.
        this.goalSelector.addGoal(2, new HumanoidCombatGoal(this));
        this.goalSelector.addGoal(3, new ShieldGoal(this));
        // Deliberately no stroll/random-walk goal: an idle humanoid stands where it is instead of drifting around.
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));
        // Retaliation first, then the squad's own target picking.
        this.goalSelector.addGoal(6, new HumanoidIdleGoal(this));
        this.targetSelector.addGoal(0, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(1, new HumanoidTargetGoal(this));
    }

    /**
     * Player-like swing reach. Vanilla's mob reach is derived from the hitbox and lands at roughly 1.4 blocks for a
     * player-sized body, which is why melee attacks barely connected.
     */
    @Override
    public double getMeleeAttackRangeSqr(LivingEntity target) {
        double reach = HumanoidCombatRules.MELEE_REACH;
        return reach * reach;
    }

    public boolean wieldsGun() {
        return this.holdsGun;
    }

    public boolean wieldsBlade() {
        return this.holdsBlade;
    }

    public boolean holdsGun() {
        return this.holdsGun;
    }

    public boolean isRangedStance() {
        return this.stance == Stance.RANGED;
    }

    public boolean isMeleeStance() {
        return this.stance == Stance.MELEE;
    }

    /** Fire mode written into a gun's tag, for diagnostics. */
    private static String fireModeOf(ItemStack stack) {
        return stack.isEmpty() ? "none" : stack.getOrCreateTag().getString("GunFireMode");
    }

    /** Drop a fight and regroup once this far from the player. */
    private static final double GROUP_REGROUP_DISTANCE = 24.0D;

    /** Squared distance to the nearest player, or a very large value when there is none. */
    private double distanceToNearestPlayerSqr() {
        Player player = this.level().getNearestPlayer(this, 256.0D);
        return player == null ? Double.MAX_VALUE : this.distanceToSqr(player);
    }

    public boolean hasShield() {
        // The item switch is the master switch for everything carried, shield included.
        return HumanoidToggles.isEnabled(HumanoidToggles.Slot.ITEM)
                && HumanoidToggles.isEnabled(HumanoidToggles.Slot.SHIELD)
                && !this.shield.isEmpty();
    }

    /**
     * Practical range of the gun in hand; zero for a melee-only humanoid. This is not a firing limit — a humanoid
     * fires across its whole engagement range — it is the distance it stops sprinting at when it has to reposition
     * for a line of sight.
     */
    public double weaponRange() {
        return this.holdsGun
                ? TaczRifles.effectiveRange(TaczRifles.gunType(this.getMainHandItem()))
                : 0.0D;
    }

    /**
     * How far away this humanoid will pick a fight with {@code target}: the target's own value decides it, but a
     * blade-only humanoid is capped at its melee reach no matter how valuable the target is.
     */
    public double engagementRadiusFor(LivingEntity target) {
        double radius = HumanoidCombatRules.engagementRadius(target);
        return this.rangedWeapon.isEmpty() ? Math.min(radius, HumanoidCombatRules.MELEE_ENGAGEMENT) : radius;
    }

    // ---------------------------------------------------------------- stance

    /**
     * Whether the gun should be in hand at this distance.
     * <p>
     * Pure distance rule with hysteresis: once holding the gun, it is kept until the target is inside melee reach; once
     * in melee, the gun only comes back out after the target has been pushed past the reach plus a margin. No timers and
     * nothing derived from damage taken, so no rule here can feed another.
     */
    public boolean shouldHoldGun(double distance, double hysteresis) {
        if (this.rangedWeapon.isEmpty() || !HumanoidToggles.isEnabled(HumanoidToggles.Slot.RANGED)) {
            return false;
        }
        if (!HumanoidToggles.isEnabled(HumanoidToggles.Slot.MELEE)) {
            return true;
        }
        // Wide hysteresis on purpose. Switching weapons changes the held item, which changes the attack action type,
        // which makes MoBends rebuild its action layer — so a stance that flips every few seconds also kills the attack
        // animation. Putting three blocks between the two thresholds stops that churn.
        double keepGun = HumanoidCombatRules.MELEE_SWITCH_DISTANCE;
        double drawGun = HumanoidCombatRules.MELEE_SWITCH_DISTANCE + GUN_DRAW_DISTANCE;
        return distance > (this.stance == Stance.RANGED ? keepGun : drawGun);
    }

    /** Switches stance and re-equips only when the stance actually changes. */
    public void useStance(Stance next) {
        if (next == this.stance) {
            return;
        }
        this.stance = next;
        this.applyStance();
    }

    /** Aims and fires one round; aim bias and lead come from {@link GunAim}. */
    public void fireGun(LivingEntity target) {
        GunAim aim = this.gunAim;
        aim.track(target);
        double distance = Math.sqrt(this.distanceToSqr(target));
        aim.resolveExpired(this.level().getGameTime(), distance);
        this.snapAim(target, aim, distance);
        this.performRangedAttack(target, 1.0F);
        aim.onShot(target);
    }

    /**
     * Points the body straight at the target before the shot. The look control interpolates over several ticks, which is
     * far too slow when the round leaves the barrel immediately and has to travel hundreds of blocks, so the body
     * rotation is set directly. Lead and the adaptive miss bias are folded in here.
     */
    private void snapAim(LivingEntity target, GunAim aim, double distance) {
        double lead = aim.leadTicks(distance);
        double dx = target.getX() + target.getDeltaMovement().x * lead - this.getX();
        double dz = target.getZ() + target.getDeltaMovement().z * lead - this.getZ();
        double dy = target.getEyeY() + target.getDeltaMovement().y * lead - this.getEyeY();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0E-4D) {
            return;
        }
        // Minecraft yaw: 0 faces +Z, +90 faces -X. Direction is (-sin(yaw), cos(yaw)), hence atan2(-dx, dz).
        double yaw = Math.toDegrees(Math.atan2(-dx, dz)) + aim.yawBias();
        // Negative pitch looks up, so the elevation bias is subtracted.
        double pitch = -Math.toDegrees(Math.atan2(dy, horizontal)) - aim.pitchBias();
        // The head tracks the target even while the body walks the other way, and the shot is fired along the head yaw.
        // Setting the body yaw here as well made a retreating gunner swivel back and forth between the walk direction
        // and the aim direction every time it fired.
        this.setYHeadRot((float) yaw);
        if (this.getNavigation().isDone()) {
            this.setYRot((float) yaw);
            this.yBodyRot = (float) yaw;
        }
        this.setXRot((float) pitch);
    }

    /**
     * Puts the weapon matching the current stance into the main hand, and the shield into the off hand only while
     * fighting at melee range.
     * <p>
     * A gunner should not be carrying a shield: it is visible on the model while firing, which looks wrong, and it does
     * nothing at that range. The shield is kept in {@link #shield} so it comes back the moment the stance flips.
     */
    private void applyStance() {
        ItemStack desired = this.stance == Stance.RANGED
                ? this.rangedWeapon
                : HumanoidToggles.isEnabled(HumanoidToggles.Slot.MELEE) ? this.bestMeleeWeapon() : ItemStack.EMPTY;
        if (desired.isEmpty()) {
            desired = this.meleeWeapon;
        }
        if (this.stance == Stance.RANGED) {
            // A stance change equips the rifle, so the long-gun flag has to be cleared with it. Leaving it set made the
            // flag say sniper while the hand held the rifle, and every later switch was a pointless rifle-to-rifle swap.
            this.usingLongGun = false;
        }
        ItemStack current = this.getMainHandItem();
        if (!current.isEmpty() && !desired.isEmpty() && current.getItem() == desired.getItem()) {
            this.applyShieldSlot();
            return;
        }
        if (current.isEmpty() && desired.isEmpty()) {
            this.applyShieldSlot();
            return;
        }
        this.setItemSlot(EquipmentSlot.MAINHAND, desired.isEmpty() ? ItemStack.EMPTY : desired.copy());
        this.applyShieldSlot();
    }

    /**
     * The shield is only carried while the blade is out. A gunner holding a shield is both wrong on screen and pointless
     * at range, and the stack is kept in {@link #shield} so it can be put back the moment the stance flips.
     */
    private void applyShieldSlot() {
        ItemStack desired = this.stance == Stance.MELEE ? this.shield : ItemStack.EMPTY;
        ItemStack current = this.getOffhandItem();
        if (current.isEmpty() && desired.isEmpty()) {
            return;
        }
        if (!current.isEmpty() && !desired.isEmpty() && current.getItem() == desired.getItem()) {
            return;
        }
        this.setItemSlot(EquipmentSlot.OFFHAND, desired.isEmpty() ? ItemStack.EMPTY : desired.copy());
    }

    /** Equips without the stance comparison, used when a specific stack must replace what is in hand. */
    private void forceMainHand(ItemStack stack) {
        this.setItemSlot(EquipmentSlot.MAINHAND, stack);
    }

    /**
     * The hardest hitting melee weapon it owns. Guns are excluded — their damage never reaches the item attributes,
     * so they cannot be compared this way — and the built-in sword is the fallback when nothing else is carried.
     */
    private ItemStack bestMeleeWeapon() {
        ItemStack best = ItemStack.EMPTY;
        for (ItemStack candidate : new ItemStack[]{this.meleeWeapon, this.rangedWeapon}) {
            if (candidate.isEmpty() || HumanoidWeapons.kindOf(candidate).isGun()) {
                continue;
            }
            if (HumanoidWeapons.dps(candidate) > HumanoidWeapons.dps(best)) {
                best = candidate;
            }
        }
        return best.isEmpty() ? HumanoidWeapons.builtinSword() : best;
    }

    // ---------------------------------------------------------------- stats

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        // Vanilla only clears a dead target from TargetGoal#stop; this entity's target goal is a plain Goal, so without
        // this a killed target stays on the books forever and the humanoid never picks another fight.
        LivingEntity currentTarget = this.getTarget();
        if (currentTarget != null && !currentTarget.isAlive()) {
            this.setTarget(null);
        }
        // Disengage when left behind. Holding a fight a long way from its player is how a humanoid gets picked off alone:
        // it keeps trading with whatever it is hitting instead of falling back to the group.
        LivingEntity held = this.getTarget();
        if (held != null && this.distanceToNearestPlayerSqr() > GROUP_REGROUP_DISTANCE * GROUP_REGROUP_DISTANCE) {
            this.setTarget(null);
        }
        this.updateSprint();
        if (this.bladeSkillCooldown > 0) {
            this.bladeSkillCooldown--;
        }
        if (this.shieldBreakTicks > 0) {
            // Without this the shield stayed broken forever after the first break, which is exactly what was reported.
            this.shieldBreakTicks--;
            if (this.shieldBreakTicks == 0) {
                ReturnCapsuleMod.LOGGER.info("[humanoid] shield is back up");
            }
        }
        this.auditOffhand();
        TaczShooter.tick(this);
        if (--this.regenCooldown > 0) {
            return;
        }
        this.regenCooldown = REGEN_INTERVAL;
        if (this.isAlive() && this.getHealth() < this.getMaxHealth()) {
            this.heal((float) this.profile.regenPerSecond());
        }
    }

    /**
     * Players walk up to a fight and sprint the last long stretch. The threshold is the design's 20 blocks, except
     * for a gun user: it stops sprinting once inside its weapon's effective range, because TaCZ refuses to shoot while
     * the shooter is sprinting, and a guard that sprints up to its target would never get a shot away otherwise.
     * <p>
     * {@code setSprinting} is the player mechanism itself — it applies the vanilla {@code +0.3} movement speed
     * modifier, so no extra speed bookkeeping is needed here.
     */
    private void updateSprint() {
        LivingEntity target = this.getTarget();
        double stopDistance = this.holdsGun() ? Math.max(SPRINT_DISTANCE, this.weaponRange()) : SPRINT_DISTANCE;
        boolean chasing = target != null && this.distanceToSqr(target) > stopDistance * stopDistance;
        // Idle movement counts as well: a humanoid left behind sprints to catch up instead of strolling after its player.
        boolean catchingUp = target == null && this.isFollowEnabled() && this.isFarFromPlayer();
        // Only sprint while a path is actually being followed, so a blocked guard does not run on the spot.
        boolean sprint = (chasing || catchingUp) && !this.getNavigation().isDone();
        if (sprint != this.isSprinting()) {
            this.setSprinting(sprint);
        }
    }

    /** True when the nearest player is further away than a walk can comfortably close. */
    private boolean isFarFromPlayer() {
        Player player = this.level().getNearestPlayer(this, CATCH_UP_DISTANCE);
        return player == null || this.distanceToSqr(player) > CATCH_UP_DISTANCE * CATCH_UP_DISTANCE;
    }

    /** Distance past which an idle humanoid runs to reach its player. */
    private static final double CATCH_UP_DISTANCE = 16.0D;

    public boolean isGunDrawn() {
        return this.gunDrawn;
    }

    public void setGunDrawn(boolean drawn) {
        this.gunDrawn = drawn;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        if (slot == EquipmentSlot.MAINHAND) {
            // Sentinel: any writer that replaces a gun with a different gun is named here, including ones outside this
            // class. A single humanoid was seen cycling through guns it was never issued, and this is the only place
            // the main hand is actually written, so one line with the caller settles where it came from.
            if (this.tickCount > 0 && !this.level().isClientSide) {
                net.minecraft.resources.ResourceLocation before =
                        com.curiats.returncapsule.compat.tacz.TaczRifles.gunId(this.getMainHandItem());
                net.minecraft.resources.ResourceLocation after =
                        com.curiats.returncapsule.compat.tacz.TaczRifles.gunId(stack);
                if (before != null && after != null && !before.equals(after)) {
                    ReturnCapsuleMod.LOGGER.info("[humanoid] main hand gun {} -> {} from {}", before, after,
                            StackWalker.getInstance().walk(frames -> frames
                                    .skip(1).limit(1)
                                    .map(frame -> frame.getClassName() + "." + frame.getMethodName())
                                    .findFirst().orElse("?")));
                }
            }
            WeaponKind kind = HumanoidWeapons.kindOf(stack);
            this.gunDrawn = false;
            this.holdsGun = kind.isGun();
            this.holdsBlade = kind.isBlade();
            if (!this.holdsGun && this.level() != null && !this.level().isClientSide && this.tickCount > 0) {
                TaczShooter.aimOff(this);
            }
        }
        super.setItemSlot(slot, stack);
    }

    /**
     * Applies the flat damage reduction from the profile before armour absorbs anything, so the two stats in
     * the design sheet stay independent: a 50% reduction halves the hit, then 50 armour shaves that further.
     * Any attacker that lands a hit is announced to the squad, so the group converges on it.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || this.isInvulnerableTo(source)) {
            return false;
        }
        if (this.tickCount < this.lastStandUntil) {
            return false;
        }
        // Friendly fire is off: another humanoid, or a player in survival, cannot hurt this thing at all. Creative mode
        // stays lethal so it remains possible to remove one that is misbehaving.
        if (isFriendlySource(source)) {
            return false;
        }
        // A creative player gets through every guard below: no invulnerability window, no chip floor, no per-hit or
        // per-second ceiling. That is the deliberate administrative channel.
        if (isCreativeSource(source)) {
            this.bypassProtections = true;
            try {
                return super.hurt(source, amount);
            } finally {
                this.bypassProtections = false;
            }
        }
        // A raised shield stops everything except what no shield can stop: the void, magic, and administrative kills.
        if (this.blocksWithShield(source)) {
            // A single hit big enough to overwhelm the shield breaks it. Keying this on damage rather than on the
            // attacker's weapon avoids handing every axe-carrying mob in the pack a shield-break for free.
            boolean breaking = amount > SHIELD_BREAK_DAMAGE;
            if (breaking) {
                this.breakShield();
            }
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    breaking ? SoundEvents.SHIELD_BREAK : SoundEvents.SHIELD_BLOCK,
                    net.minecraft.sounds.SoundSource.HOSTILE, 0.8F, 1.0F);
            return false;
        }
        // Chip damage does nothing at all. Swarms that rely on many tiny hits — one point per tick, splash particles,
        // burning — would otherwise grind a 200 HP guard down while every defensive layer above stays satisfied.
        if (amount < MIN_MEANINGFUL_HIT && !this.convertedHit
                && !source.is(net.minecraft.world.damagesource.DamageTypes.MAGIC)) {
            return false;
        }
        // Invulnerability frames, the trick the old Wulfrum Wither uses: only one hit lands per window, so a stream of
        // twenty hits a second is throttled to a handful. Only magic ignores the window; the void and administrative
        // kills are ordinary damage here, which is what keeps them from deleting the entity in one call.
        if (!source.is(net.minecraft.world.damagesource.DamageTypes.MAGIC)) {
            int now = this.tickCount;
            if (now - this.lastAcceptedHit < HIT_GATE_TICKS) {
                return false;
            }
            this.lastAcceptedHit = now;
        }
        float reduced = amount * (1.0F - (float) this.profile.damageReduction());
        // The per-hit ceiling applies to everything except magic: an overcharged mod weapon, a scripted out-of-world
        // damage source and an administrative kill all become ordinary 70-point hits instead of one-shot deletions.
        if (!source.is(net.minecraft.world.damagesource.DamageTypes.MAGIC)) {
            reduced = Math.min(reduced, this.getMaxHealth() * MAX_HIT_FRACTION);
        }
        if (reduced <= 0.0F) {
            return false;
        }
        // Last stand: survive one lethal hit per cooldown instead of dying to a burst. This is what keeps a 200 HP unit
        // from being deleted the moment something lands a big hit; it is not invulnerability, because the shots after
        // the grace period land normally and the cooldown is long.
        if (!this.bypassProtections && this.tickCount >= this.lastStandReadyAt
                && this.getHealth() - reduced <= 0.0F && reduced < this.getMaxHealth()) {
            this.setHealth(1.0F);
            this.lastStandUntil = this.tickCount + LAST_STAND_GRACE;
            this.lastStandReadyAt = this.tickCount + LAST_STAND_COOLDOWN;
            ReturnCapsuleMod.LOGGER.info("[humanoid] last stand triggered");
            return true;
        }
        this.applyingHit = true;
        boolean hurt;
        try {
            hurt = super.hurt(source, reduced);
        } finally {
            this.applyingHit = false;
        }
        if (hurt && source.getEntity() instanceof LivingEntity attacker) {
            HumanoidSquad.report(this.level(), attacker);
        }
        return hurt;
    }

    /** True while a real hit is being applied; only then may health reach zero. */
    public void setConvertedHit(boolean converted) {
        this.convertedHit = converted;
    }

    public boolean isApplyingHit() {
        return this.applyingHit;
    }

    /** True while a hit from a creative player is being applied, so the setHealth budget stands aside. */
    public boolean bypassesProtections() {
        return this.bypassProtections;
    }

    /** True when the hit ultimately comes from a creative-mode player, directly or through a projectile. */
    private static boolean isCreativeSource(DamageSource source) {
        return source.getEntity() instanceof Player player && player.isCreative()
                || source.getDirectEntity() instanceof Player shooter && shooter.isCreative();
    }

    /**
     * True when the hit comes from something on the humanoid's own side: another humanoid, or a player who is not in
     * creative mode.
     * <p>
     * Both the direct entity and the owner are checked, because a gun round or an arrow has the shooter as its owner
     * rather than as the thing that physically touched the target.
     */
    private static boolean isFriendlySource(DamageSource source) {
        // SlashBlade's attacks carry no proper attacker for parts of their damage, so the ownership check below misses
        // them and a player holding a blade could cut a humanoid down. The source id is the reliable marker there.
        String id = source.getMsgId();
        if (id != null && (id.contains("slashblade") || id.contains("slash_blade"))) {
            return true;
        }
        Entity attacker = source.getEntity();
        Entity direct = source.getDirectEntity();
        if (attacker instanceof NeutralHumanoid || direct instanceof NeutralHumanoid) {
            return true;
        }
        if (attacker instanceof Player player) {
            return !player.isCreative();
        }
        return direct instanceof Player player && !player.isCreative();
    }

    /**
     * Whether the shield stops this hit.
     * <p>
     * The shield blocks every hit outright while it is up — that is the point of it — but the void, magic and an
     * administrative kill go through, and so does anything for the five seconds after the shield was broken.
     */
    private boolean blocksWithShield(DamageSource source) {
        if (!this.hasShield() || this.shieldBreakTicks > 0) {
            return false;
        }
        // The shield has to actually be in the off hand. `hasShield` only says the humanoid owns one — while a gun is
        // out the off hand is empty, and a two handed weapon cannot block in any case.
        if (!HumanoidWeapons.isShield(this.getOffhandItem())) {
            return false;
        }
        return !source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)
                && !source.is(net.minecraft.world.damagesource.DamageTypes.MAGIC)
                && !source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL);
    }

    /**
     * Breaks the shield for five seconds, and permanently destroys it on the third break.
     * <p>
     * A destroyed shield is not just hidden: the off-hand slot is emptied and the stack is gone, so nothing can bring
     * it back short of a new one from a player.
     */
    private void breakShield() {
        this.shieldBreakTicks = SHIELD_BREAK_TICKS;
        this.shieldBreaks++;
        if (this.shieldBreaks < SHIELD_BREAK_LIMIT) {
            ReturnCapsuleMod.LOGGER.info("[humanoid] shield broken ({}/{}), down for {} ticks",
                    this.shieldBreaks, SHIELD_BREAK_LIMIT, SHIELD_BREAK_TICKS);
            return;
        }
        this.shield = ItemStack.EMPTY;
        this.shieldBreaks = 0;
        this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        ReturnCapsuleMod.LOGGER.info("[humanoid] shield destroyed permanently");
    }

    /** Ticks the shield stays broken after a shield-breaking hit. */
    private static final int SHIELD_BREAK_TICKS = 100;
    /** Breaks needed to destroy the shield for good. */
    private static final int SHIELD_BREAK_LIMIT = 3;

    /**
     * Mobs do not inherit weapon attribute modifiers the way players do, so the held weapon's attack damage is
     * added explicitly. Without this, a slashblade would hit for bare-hand damage.
     */
    /** True when a slash art may be used right now. */
    public boolean bladeSkillReady() {
        return this.bladeSkillCooldown <= 0;
    }

    /** Starts the cooldown after an art was performed. */
    public void onBladeSkillUsed() {
        this.bladeSkillCooldown = BLADE_SKILL_INTERVAL;
        this.bladeSkillUses++;
    }

    /** True when the next art should be the high-end one, so the expensive move shows up occasionally. */
    public boolean wantsSuperArt() {
        return this.bladeSkillUses % 3 == 2;
    }

    /**
     * Puts the gun matching the distance into the main hand while keeping the ranged stance.
     * <p>
     * Called every tick from the combat loop, so it must be cheap: only the item types are compared, and the hand is
     * touched only when the choice actually changes.
     */
    public void chooseGunFor(double distance) {
        if (this.stance != Stance.RANGED) {
            return;
        }
        // Hysteresis on the gun choice, for the same reason the stance has it: a target hovering at the threshold made
        // the humanoid swap weapons every tick, and every swap forces a fresh draw, so it never got to fire.
        double threshold = this.usingLongGun ? LONG_RANGE_SWITCH - LONG_RANGE_HYSTERESIS : LONG_RANGE_SWITCH;
        boolean wantLong = distance > threshold && !this.longRangedWeapon.isEmpty();
        ItemStack wanted = wantLong ? this.longRangedWeapon : this.rangedWeapon;
        if (wanted.isEmpty()) {
            return;
        }
        // An explicit flag, not an identity comparison against the field: the hand holds a copy, so the comparison was
        // always false and the hysteresis never applied on the way back.
        if (wantLong == this.usingLongGun) {
            return;
        }
        this.usingLongGun = wantLong;
        this.setItemSlot(EquipmentSlot.MAINHAND, wanted.copy());
        ReturnCapsuleMod.LOGGER.info("[humanoid] gun switch at {} blocks: {} -> {}",
                Math.round(distance),
                com.curiats.returncapsule.compat.tacz.TaczRifles.gunId(this.getMainHandItem()),
                com.curiats.returncapsule.compat.tacz.TaczRifles.gunId(wanted));
    }

    /** Target distance past which the long gun is preferred. */
    private static final double LONG_RANGE_SWITCH = 64.0D;
    /** Ticks between slash arts: ten seconds, long enough to read as a special move. */

    @Nullable
    public BlockPos getDestination() {
        return this.destination;
    }

    public void setDestination(@Nullable BlockPos destination) {
        this.destination = destination;
    }

    public BlockPos getHome() {
        if (this.home == null) {
            this.home = this.blockPosition();
        }
        return this.home;
    }

    public void setHome(BlockPos home) {
        this.home = home.immutable();
    }

    public boolean isFollowEnabled() {
        return this.followEnabled;
    }

    public void setFollowEnabled(boolean enabled) {
        this.followEnabled = enabled;
    }

    @Override
    public void die(DamageSource source) {
        // A death event with health to spare did not come from damage — something forced it. Plenty of hostile content
        // does this directly rather than going through hurt, which bypasses every guard above. Refusing it here is the
        // last line: the entity keeps its remaining health and the forced death is ignored.
        if (this.getHealth() > 0.0F && !this.bypassProtections) {
            ReturnCapsuleMod.LOGGER.info("[humanoid] refused a forced death event (health {})", this.getHealth());
            return;
        }
        ReturnCapsuleMod.LOGGER.info("[humanoid] died from {}", source.getMsgId());
        // The healing seal this humanoid applied ends with it. Every victim it had wounded becomes healable again,
        // which is the intended contract: nothing is sealed permanently by a death the player could not prevent.
        com.curiats.returncapsule.util.DamageLock.clearAll();
        super.die(source);
        if (this.level().isClientSide) {
            return;
        }
        // Death blast: everything hostile within range loses most of its health. The unit is worth more dead than alive
        // when it is about to be overrun, and this keeps a losing fight from being free for the swarm.
        for (LivingEntity nearby : this.level().getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(DEATH_BLAST_RADIUS),
                candidate -> candidate != this && candidate.isAlive()
                        && !(candidate instanceof Player)
                        && !(candidate instanceof NeutralHumanoid))) {
            nearby.invulnerableTime = 0;
            nearby.hurt(this.damageSources().magic(), nearby.getMaxHealth() * DEATH_BLAST_FRACTION);
        }
        // Camera shake, applied to nearby players through this mod's own shake effect. Relying on the client's explosion
        // sound handler did nothing, because that only reacts to explosions it hears at full volume.
        for (Player nearby : this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(32.0D))) {
            double distance = nearby.distanceTo(this);
            int amplifier = distance < 8.0D ? 2 : distance < 16.0D ? 1 : 0;
            nearby.addEffect(new MobEffectInstance(
                    com.curiats.returncapsule.registry.ModEffects.SHAKE.get(), 30, amplifier, false, false, false));
        }
        if (this.level() instanceof net.minecraft.server.level.ServerLevel server) {
            // Explosion visuals to match the blast. The sound is also what the client's camera shake listens for — it
            // triggers on hearing an explosion at full volume — so the raised volume is what makes the shake reach.
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,
                    this.getX(), this.getY() + 0.8D, this.getZ(), 3, 1.2D, 0.8D, 1.2D, 0.0D);
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                    this.getX(), this.getY() + 0.6D, this.getZ(), 60, 2.2D, 1.4D, 2.2D, 0.03D);
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION,
                    this.getX(), this.getY() + 0.6D, this.getZ(), 12, 2.0D, 1.0D, 2.0D, 0.0D);
        }
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.GENERIC_EXPLODE, net.minecraft.sounds.SoundSource.HOSTILE, 4.0F, 0.8F);
        ReturnCapsuleMod.LOGGER.info("[humanoid] death blast fired");
    }

    /** Radius of the death blast, in blocks. */
    private static final double DEATH_BLAST_RADIUS = 8.0D;
    /** Share of a victim's maximum health removed by it. */
    private static final float DEATH_BLAST_FRACTION = 0.63F;

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ATTACK_SEQ, 0);
    }

    /**
     * Advances the swing counter. The client uses it to reset MoBends' attack clock, because that clock only ever grows
     * and the client-side swing flags proved unreliable for this entity.
     */
    public int markAttack() {
        int next = this.entityData.get(DATA_ATTACK_SEQ) + 1;
        this.entityData.set(DATA_ATTACK_SEQ, next);
        return next;
    }

    public int attackSequence() {
        return this.entityData.get(DATA_ATTACK_SEQ);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        // Let the blade's own combo engine advance (motion and animation) without changing who resolves the damage.
        if (this.holdsBlade) {
        }
        double damage = this.getAttributeValue(Attributes.ATTACK_DAMAGE)
                + HumanoidWeapons.attackBonus(this.getMainHandItem());
        if (damage <= 0.0D) {
            return false;
        }
        boolean hit = target.hurt(this.damageSources().mobAttack(this), (float) damage);
        if (hit) {
            this.doEnchantDamageEffects(this, target);
            this.setLastHurtMob(target);
        }
        return hit;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        TaczShooter.fire(this, target);
    }

    // ---------------------------------------------------------------- immunity

    /** Neutral humanoids ignore every status effect; entity-conversion debuffs are effects too. */
    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return false;
    }

    @Override
    public boolean addEffect(MobEffectInstance effect, @Nullable Entity source) {
        return false;
    }

    @Override
    public void forceAddEffect(MobEffectInstance effect, @Nullable Entity source) {
        // Deliberately empty: nothing may force an effect onto this entity.
    }

    // ---------------------------------------------------------------- equipment

    @Override
    public boolean canHoldItem(ItemStack stack) {
        WeaponKind kind = HumanoidWeapons.kindOf(stack);
        return kind == WeaponKind.NONE || this.profile.allows(kind);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack offered = player.getItemInHand(hand);
        // Handing over equipment requires sneaking. Without that, aiming or interacting near a humanoid while holding a
        // gun handed the gun to it: the old weapon was dropped and the new one equipped, which looked exactly like the
        // humanoid swapping to a weapon it was never issued.
        if (!player.isShiftKeyDown()) {
            return super.mobInteract(player, hand);
        }
        if (offered.isEmpty()) {
            return super.mobInteract(player, hand);
        }
        boolean shield = HumanoidToggles.isEnabled(HumanoidToggles.Slot.SHIELD)
                && HumanoidWeapons.isShield(offered);
        WeaponKind kind = shield ? WeaponKind.NONE : HumanoidWeapons.kindOf(offered);
        boolean gun = kind == WeaponKind.GUN && this.profile.weapons().allowsGun()
                && HumanoidToggles.isEnabled(HumanoidToggles.Slot.RANGED);
        boolean melee = kind == WeaponKind.BLADE && this.profile.weapons().allowsBlade()
                && HumanoidToggles.isEnabled(HumanoidToggles.Slot.MELEE);
        if (!shield && !gun && !melee) {
            return super.mobInteract(player, hand);
        }
        if (!this.level().isClientSide) {
            if (shield) {
                this.installShield(offered);
            } else if (gun) {
                this.installRanged(offered);
            } else {
                this.installMelee(offered);
            }
            if (!player.isCreative()) {
                offered.shrink(1);
            }
            this.playSound(SoundEvents.ARMOR_EQUIP_GENERIC, 1.0F, 1.0F);
            this.setPersistenceRequired();
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    private void installShield(ItemStack offered) {
        this.shield = single(offered);
        ItemStack previous = this.getOffhandItem();
        this.setItemSlot(EquipmentSlot.OFFHAND, single(offered));
        if (!previous.isEmpty() && !previous.is(Items.SHIELD)) {
            this.spawnAtLocation(previous);
        }
    }

    /**
     * Installs a gun handed over by a player, into the slot that matches its kind: a sniper or marksman rifle into the
     * long range slot, everything else into the rifle slot. Matching on the weapon's own type keeps a lever action from
     * displacing a rifle and vice versa, which is what the two slots are for.
     */
    private void installRanged(ItemStack offered) {
        ItemStack candidate = single(offered);
        TaczRifles.prepareForMob(candidate);
        String type = TaczRifles.gunType(candidate).toLowerCase(java.util.Locale.ROOT);
        boolean longRange = "sniper".equals(type) || "dmr".equals(type);

        ItemStack previous = longRange ? this.longRangedWeapon : this.rangedWeapon;
        if (longRange) {
            this.longRangedWeapon = candidate;
        } else {
            this.rangedWeapon = candidate;
        }
        if (!previous.isEmpty() && previous.getItem() != candidate.getItem()) {
            this.spawnAtLocation(previous);
        }
        this.stance = Stance.RANGED;
        ItemStack inHand = longRange ? this.longRangedWeapon : this.rangedWeapon;
        this.forceMainHand(inHand.copy());
    }

    private void installMelee(ItemStack offered) {
        ItemStack previous = this.meleeWeapon;
        this.meleeWeapon = single(offered);
        if (!previous.isEmpty() && !HumanoidWeapons.isBuiltinSword(previous)
                && previous.getItem() != this.meleeWeapon.getItem()) {
            this.spawnAtLocation(previous);
        }
        this.stance = Stance.MELEE;
        this.forceMainHand(this.meleeWeapon.copy());
    }

    private static ItemStack single(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    /**
     * The built-in sword is kit, not loot, so it never drops. A weapon that is currently not in a hand would otherwise
     * vanish with the entity, so it is dropped explicitly.
     */
    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        ItemStack carried = this.stance == Stance.RANGED ? this.meleeWeapon : this.rangedWeapon;
        if (!carried.isEmpty() && !HumanoidWeapons.isBuiltinSword(carried)) {
            this.spawnAtLocation(carried.copy());
        }
        if (HumanoidWeapons.isBuiltinSword(this.getMainHandItem())) {
            this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
        super.dropCustomDeathLoot(source, looting, recentlyHit);
    }

    // ---------------------------------------------------------------- persistence

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (!this.rangedWeapon.isEmpty()) {
            tag.put(TAG_RANGED, this.rangedWeapon.save(new CompoundTag()));
        }
        if (!this.longRangedWeapon.isEmpty()) {
            tag.put(TAG_LONG_RANGED, this.longRangedWeapon.save(new CompoundTag()));
        }
        if (!this.meleeWeapon.isEmpty()) {
            tag.put(TAG_MELEE, this.meleeWeapon.save(new CompoundTag()));
        }
        tag.putString(TAG_STANCE, this.stance.name());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.rangedWeapon = tag.contains(TAG_RANGED)
                ? ItemStack.of(tag.getCompound(TAG_RANGED))
                : ItemStack.EMPTY;
        this.gunsIssued = tag.contains(TAG_RANGED);
        this.longRangedWeapon = tag.contains(TAG_LONG_RANGED)
                ? ItemStack.of(tag.getCompound(TAG_LONG_RANGED))
                : ItemStack.EMPTY;
        this.meleeWeapon = tag.contains(TAG_MELEE)
                ? ItemStack.of(tag.getCompound(TAG_MELEE))
                : ItemStack.EMPTY;
        if (tag.contains(TAG_STANCE)) {
            try {
                this.stance = Stance.valueOf(tag.getString(TAG_STANCE));
            } catch (IllegalArgumentException ignored) {
                this.stance = Stance.MELEE;
            }
        }
        this.applyStance();
    }

    // ---------------------------------------------------------------- body

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return pose == Pose.STANDING ? EYE_HEIGHT : super.getStandingEyeHeight(pose, dimensions);
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 1.0F;
    }
}
