package com.curiats.returncapsule.entity.humanoid;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.compat.tacz.TaczRifles;
import com.curiats.returncapsule.compat.tacz.TaczShooter;
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
    private static final float EYE_HEIGHT = 1.62F;
    /** Beyond this distance the humanoid sprints instead of walking, the way a player closes a gap. */
    private static final double SPRINT_DISTANCE = 20.0D;
    /** Vanilla caps target search at the follow range, so it has to clear the widest engagement radius. */
    private static final double FOLLOW_RANGE = 512.0D;

    private static final String TAG_RANGED = "RangedWeapon";
    private static final String TAG_MELEE = "MeleeWeapon";
    private static final String TAG_STANCE = "Stance";

    private final HumanoidProfile profile;
    private final GunAim gunAim = new GunAim();
    private boolean loadoutLogged;

    private ItemStack rangedWeapon = ItemStack.EMPTY;
    private ItemStack meleeWeapon = ItemStack.EMPTY;
    /** The off-hand shield, kept here while the gun is out so it can be put back. */
    private ItemStack shield = ItemStack.EMPTY;
    private Stance stance = Stance.MELEE;

    private int regenCooldown = REGEN_INTERVAL;
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
            this.refreshLoadout();
        }
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
        } else if (this.rangedWeapon.isEmpty()) {
            this.rangedWeapon = HumanoidWeapons.startingGun(this.random);
        }
        if (!blades) {
            this.meleeWeapon = ItemStack.EMPTY;
        } else if (this.meleeWeapon.isEmpty()) {
            this.meleeWeapon = HumanoidWeapons.startingMelee(this.profile, this.level(), this.random);
        }
        if (!HumanoidToggles.isEnabled(HumanoidToggles.Slot.SHIELD)) {
            this.shield = ItemStack.EMPTY;
            this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        } else if (this.shield.isEmpty()) {
            this.shield = HumanoidWeapons.builtinShield();
        }
        this.applyStance();
        this.logLoadout();
    }

    /** One line per humanoid at spawn, so a missing gun is obvious from the log instead of guesswork. */
    private void logLoadout() {
        if (this.loadoutLogged) {
            return;
        }
        this.loadoutLogged = true;
        ReturnCapsuleMod.LOGGER.info("[humanoid] spawned {}: ranged={} melee={} shield={}",
                this.profile.id(),
                describe(this.rangedWeapon), describe(this.meleeWeapon), describe(this.getOffhandItem()));
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

    public boolean hasShield() {
        return HumanoidToggles.isEnabled(HumanoidToggles.Slot.SHIELD)
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
        double keepGun = HumanoidCombatRules.MELEE_REACH;
        double drawGun = HumanoidCombatRules.MELEE_REACH + hysteresis;
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
        this.updateSprint();
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
        // Only sprint while a path is actually being followed, so a blocked guard does not run on the spot.
        boolean sprint = chasing && !this.getNavigation().isDone();
        if (sprint != this.isSprinting()) {
            this.setSprinting(sprint);
        }
    }

    public boolean isGunDrawn() {
        return this.gunDrawn;
    }

    public void setGunDrawn(boolean drawn) {
        this.gunDrawn = drawn;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        if (slot == EquipmentSlot.MAINHAND) {
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
        float reduced = amount * (1.0F - (float) this.profile.damageReduction());
        if (reduced <= 0.0F) {
            return false;
        }
        boolean hurt = super.hurt(source, reduced);
        if (hurt && source.getEntity() instanceof LivingEntity attacker) {
            HumanoidSquad.report(this.level(), attacker);
        }
        return hurt;
    }

    /**
     * Mobs do not inherit weapon attribute modifiers the way players do, so the held weapon's attack damage is
     * added explicitly. Without this, a slashblade would hit for bare-hand damage.
     */
    @Override
    public boolean doHurtTarget(Entity target) {
        // Let the blade's own combo engine advance (motion and animation) without changing who resolves the damage.
        if (this.holdsBlade) {
            com.curiats.returncapsule.compat.slashblade.SlashBlades.advanceCombo(this, target);
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

    private void installRanged(ItemStack offered) {
        ItemStack previous = this.rangedWeapon;
        this.rangedWeapon = single(offered);
        TaczRifles.prepareForMob(this.rangedWeapon);
        if (!previous.isEmpty() && previous.getItem() != this.rangedWeapon.getItem()) {
            this.spawnAtLocation(previous);
        }
        this.stance = Stance.RANGED;
        this.forceMainHand(this.rangedWeapon.copy());
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
