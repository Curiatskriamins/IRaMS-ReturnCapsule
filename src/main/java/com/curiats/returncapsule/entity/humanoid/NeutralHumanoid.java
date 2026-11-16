package com.curiats.returncapsule.entity.humanoid;

import com.curiats.returncapsule.ReturnCapsuleMod;
import com.curiats.returncapsule.compat.tacz.TaczRifles;
import com.curiats.returncapsule.compat.tacz.TaczShooter;
import net.minecraft.core.BlockPos;
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
import net.minecraftforge.registries.ForgeRegistries;

@SuppressWarnings({"deprecation", "this-escape"})
public class NeutralHumanoid extends PathfinderMob implements RangedAttackMob {

    public enum Stance {
        RANGED,
        MELEE
    }

    private static final int REGEN_INTERVAL = 20;

    private static final float MAX_HIT_FRACTION = 0.35F;

    private static final float MIN_MEANINGFUL_HIT = 6.0F;

    private static final int HIT_GATE_TICKS = 4;

    private static final int LAST_STAND_GRACE = 60;

    private static final int LAST_STAND_COOLDOWN = 1800;

    private int lastStandUntil = -1;

    private int lastStandReadyAt;

    private int lastAcceptedHit = -100;

    private boolean bypassProtections;

    private boolean applyingHit;

    private boolean convertedHit;

    private static final double LONG_RANGE_HYSTERESIS = 8.0D;

    private static final float SHIELD_BREAK_DAMAGE = 100.0F;

    private static final int BLADE_SKILL_INTERVAL = 200;

    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> DATA_ATTACK_SEQ =
            net.minecraft.network.syncher.SynchedEntityData.defineId(NeutralHumanoid.class,
                    net.minecraft.network.syncher.EntityDataSerializers.INT);

    private static final double GUN_DRAW_DISTANCE = 3.0D;
    private static final float EYE_HEIGHT = 1.62F;

    private static final double SPRINT_DISTANCE = 20.0D;

    private static final double FOLLOW_RANGE = 512.0D;

    private static final String TAG_LONG_RANGED = "LongRangedWeapon";
    private static final String TAG_RANGED = "RangedWeapon";
    private static final String TAG_MELEE = "MeleeWeapon";
    private static final String TAG_STANCE = "Stance";

    private final HumanoidProfile profile;
    private final GunAim gunAim = new GunAim();
    private boolean loadoutLogged;

    private ItemStack rangedWeapon = ItemStack.EMPTY;

    private ItemStack longRangedWeapon = ItemStack.EMPTY;
    private ItemStack meleeWeapon = ItemStack.EMPTY;

    private ItemStack shield = ItemStack.EMPTY;

    private String lastOffhand = "";
    private Stance stance = Stance.MELEE;

    @Nullable
    private BlockPos destination;

    @Nullable
    private BlockPos home;

    private boolean followEnabled = true;

    private int shieldBreakTicks;

    private int shieldBreaks;

    private int regenCooldown = REGEN_INTERVAL;

    private int bladeSkillCooldown;

    private int bladeSkillUses;

    private boolean gunsIssued;

    private boolean usingLongGun;

    private boolean gunDrawn;

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

        }
    }

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

            this.gunsIssued = true;

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

    private void auditOffhand() {
        String now = describe(this.getOffhandItem());
        if (now.equals(this.lastOffhand)) {
            return;
        }
        this.lastOffhand = now;
        ReturnCapsuleMod.LOGGER.info("[humanoid] offhand -> {} (stance={}, shieldSlot={})",
                now, this.stance, describe(this.shield));
    }

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
        return stack.isEmpty() ? "none" : ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
    }

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

                .add(Attributes.FOLLOW_RANGE, FOLLOW_RANGE);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));

        this.goalSelector.addGoal(2, new HumanoidCombatGoal(this));
        this.goalSelector.addGoal(3, new ShieldGoal(this));

        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));

        this.goalSelector.addGoal(6, new HumanoidIdleGoal(this));
        this.targetSelector.addGoal(0, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(1, new HumanoidTargetGoal(this));
    }

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

    private static String fireModeOf(ItemStack stack) {
        return stack.isEmpty() ? "none" : stack.getOrCreateTag().getString("GunFireMode");
    }

    private static final double GROUP_REGROUP_DISTANCE = 24.0D;

    private double distanceToNearestPlayerSqr() {
        Player player = this.level().getNearestPlayer(this, 256.0D);
        return player == null ? Double.MAX_VALUE : this.distanceToSqr(player);
    }

    public boolean hasShield() {

        return HumanoidToggles.isEnabled(HumanoidToggles.Slot.ITEM)
                && HumanoidToggles.isEnabled(HumanoidToggles.Slot.SHIELD)
                && !this.shield.isEmpty();
    }

    public double weaponRange() {
        return this.holdsGun
                ? TaczRifles.effectiveRange(TaczRifles.gunType(this.getMainHandItem()))
                : 0.0D;
    }

    public double engagementRadiusFor(LivingEntity target) {
        double radius = HumanoidCombatRules.engagementRadius(target);
        return this.rangedWeapon.isEmpty() ? Math.min(radius, HumanoidCombatRules.MELEE_ENGAGEMENT) : radius;
    }

    public boolean shouldHoldGun(double distance, double hysteresis) {
        if (this.rangedWeapon.isEmpty() || !HumanoidToggles.isEnabled(HumanoidToggles.Slot.RANGED)) {
            return false;
        }
        if (!HumanoidToggles.isEnabled(HumanoidToggles.Slot.MELEE)) {
            return true;
        }

        double keepGun = HumanoidCombatRules.MELEE_SWITCH_DISTANCE;
        double drawGun = HumanoidCombatRules.MELEE_SWITCH_DISTANCE + GUN_DRAW_DISTANCE;
        return distance > (this.stance == Stance.RANGED ? keepGun : drawGun);
    }

    public void useStance(Stance next) {
        if (next == this.stance) {
            return;
        }
        this.stance = next;
        this.applyStance();
    }

    public void fireGun(LivingEntity target) {
        GunAim aim = this.gunAim;
        aim.track(target);
        double distance = Math.sqrt(this.distanceToSqr(target));
        aim.resolveExpired(this.level().getGameTime(), distance);
        this.snapAim(target, aim, distance);
        this.performRangedAttack(target, 1.0F);
        aim.onShot(target);
    }

    private void snapAim(LivingEntity target, GunAim aim, double distance) {
        double lead = aim.leadTicks(distance);
        double dx = target.getX() + target.getDeltaMovement().x * lead - this.getX();
        double dz = target.getZ() + target.getDeltaMovement().z * lead - this.getZ();
        double dy = target.getEyeY() + target.getDeltaMovement().y * lead - this.getEyeY();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0E-4D) {
            return;
        }

        double yaw = Math.toDegrees(Math.atan2(-dx, dz)) + aim.yawBias();

        double pitch = -Math.toDegrees(Math.atan2(dy, horizontal)) - aim.pitchBias();

        this.setYHeadRot((float) yaw);
        if (this.getNavigation().isDone()) {
            this.setYRot((float) yaw);
            this.yBodyRot = (float) yaw;
        }
        this.setXRot((float) pitch);
    }

    private void applyStance() {
        ItemStack desired = this.stance == Stance.RANGED
                ? this.rangedWeapon
                : HumanoidToggles.isEnabled(HumanoidToggles.Slot.MELEE) ? this.bestMeleeWeapon() : ItemStack.EMPTY;
        if (desired.isEmpty()) {
            desired = this.meleeWeapon;
        }
        if (this.stance == Stance.RANGED) {

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

    private void forceMainHand(ItemStack stack) {
        this.setItemSlot(EquipmentSlot.MAINHAND, stack);
    }

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

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }

        LivingEntity currentTarget = this.getTarget();
        if (currentTarget != null && !currentTarget.isAlive()) {
            this.setTarget(null);
        }

        LivingEntity held = this.getTarget();
        if (held != null && this.distanceToNearestPlayerSqr() > GROUP_REGROUP_DISTANCE * GROUP_REGROUP_DISTANCE) {
            this.setTarget(null);
        }
        this.updateSprint();
        if (this.bladeSkillCooldown > 0) {
            this.bladeSkillCooldown--;
        }
        if (this.shieldBreakTicks > 0) {

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

    private void updateSprint() {
        LivingEntity target = this.getTarget();
        double stopDistance = this.holdsGun() ? Math.max(SPRINT_DISTANCE, this.weaponRange()) : SPRINT_DISTANCE;
        boolean chasing = target != null && this.distanceToSqr(target) > stopDistance * stopDistance;

        boolean catchingUp = target == null && this.isFollowEnabled() && this.isFarFromPlayer();

        boolean sprint = (chasing || catchingUp) && !this.getNavigation().isDone();
        if (sprint != this.isSprinting()) {
            this.setSprinting(sprint);
        }
    }

    private boolean isFarFromPlayer() {
        Player player = this.level().getNearestPlayer(this, CATCH_UP_DISTANCE);
        return player == null || this.distanceToSqr(player) > CATCH_UP_DISTANCE * CATCH_UP_DISTANCE;
    }

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

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || this.isInvulnerableTo(source)) {
            return false;
        }
        if (this.tickCount < this.lastStandUntil) {
            return false;
        }

        if (isFriendlySource(source)) {
            return false;
        }

        if (isCreativeSource(source)) {
            this.bypassProtections = true;
            try {
                return super.hurt(source, amount);
            } finally {
                this.bypassProtections = false;
            }
        }

        if (this.blocksWithShield(source)) {

            boolean breaking = amount > SHIELD_BREAK_DAMAGE;
            if (breaking) {
                this.breakShield();
            }
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                    breaking ? SoundEvents.SHIELD_BREAK : SoundEvents.SHIELD_BLOCK,
                    net.minecraft.sounds.SoundSource.HOSTILE, 0.8F, 1.0F);
            return false;
        }

        if (amount < MIN_MEANINGFUL_HIT && !this.convertedHit
                && !source.is(net.minecraft.world.damagesource.DamageTypes.MAGIC)) {
            return false;
        }

        if (!source.is(net.minecraft.world.damagesource.DamageTypes.MAGIC)) {
            int now = this.tickCount;
            if (now - this.lastAcceptedHit < HIT_GATE_TICKS) {
                return false;
            }
            this.lastAcceptedHit = now;
        }
        float reduced = amount * (1.0F - (float) this.profile.damageReduction());

        if (!source.is(net.minecraft.world.damagesource.DamageTypes.MAGIC)) {
            reduced = Math.min(reduced, this.getMaxHealth() * MAX_HIT_FRACTION);
        }
        if (reduced <= 0.0F) {
            return false;
        }

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

    public void setConvertedHit(boolean converted) {
        this.convertedHit = converted;
    }

    public boolean isApplyingHit() {
        return this.applyingHit;
    }

    public boolean bypassesProtections() {
        return this.bypassProtections;
    }

    private static boolean isCreativeSource(DamageSource source) {
        return source.getEntity() instanceof Player player && player.isCreative()
                || source.getDirectEntity() instanceof Player shooter && shooter.isCreative();
    }

    private static boolean isFriendlySource(DamageSource source) {

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

    private boolean blocksWithShield(DamageSource source) {
        if (!this.hasShield() || this.shieldBreakTicks > 0) {
            return false;
        }

        if (!HumanoidWeapons.isShield(this.getOffhandItem())) {
            return false;
        }
        return !source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)
                && !source.is(net.minecraft.world.damagesource.DamageTypes.MAGIC)
                && !source.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL);
    }

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

    private static final int SHIELD_BREAK_TICKS = 100;

    private static final int SHIELD_BREAK_LIMIT = 3;

    public boolean bladeSkillReady() {
        return this.bladeSkillCooldown <= 0;
    }

    public void onBladeSkillUsed() {
        this.bladeSkillCooldown = BLADE_SKILL_INTERVAL;
        this.bladeSkillUses++;
    }

    public boolean wantsSuperArt() {
        return this.bladeSkillUses % 3 == 2;
    }

    public void chooseGunFor(double distance) {
        if (this.stance != Stance.RANGED) {
            return;
        }

        double threshold = this.usingLongGun ? LONG_RANGE_SWITCH - LONG_RANGE_HYSTERESIS : LONG_RANGE_SWITCH;
        boolean wantLong = distance > threshold && !this.longRangedWeapon.isEmpty();
        ItemStack wanted = wantLong ? this.longRangedWeapon : this.rangedWeapon;
        if (wanted.isEmpty()) {
            return;
        }

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

    private static final double LONG_RANGE_SWITCH = 64.0D;

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

        if (this.getHealth() > 0.0F && !this.bypassProtections) {
            ReturnCapsuleMod.LOGGER.info("[humanoid] refused a forced death event (health {})", this.getHealth());
            return;
        }
        ReturnCapsuleMod.LOGGER.info("[humanoid] died from {}", source.getMsgId());

        com.curiats.returncapsule.util.DamageLock.clearAll();
        super.die(source);
        if (this.level().isClientSide) {
            return;
        }

        for (LivingEntity nearby : this.level().getEntitiesOfClass(LivingEntity.class,
                this.getBoundingBox().inflate(DEATH_BLAST_RADIUS),
                candidate -> candidate != this && candidate.isAlive()
                        && !(candidate instanceof Player)
                        && !(candidate instanceof NeutralHumanoid))) {
            nearby.invulnerableTime = 0;
            nearby.hurt(this.damageSources().magic(), nearby.getMaxHealth() * DEATH_BLAST_FRACTION);
        }

        for (Player nearby : this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(32.0D))) {
            double distance = nearby.distanceTo(this);
            int amplifier = distance < 8.0D ? 2 : distance < 16.0D ? 1 : 0;
            nearby.addEffect(new MobEffectInstance(
                    com.curiats.returncapsule.registry.ModEffects.SHAKE.get(), 30, amplifier, false, false, false));
        }
        if (this.level() instanceof net.minecraft.server.level.ServerLevel server) {

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

    private static final double DEATH_BLAST_RADIUS = 8.0D;

    private static final float DEATH_BLAST_FRACTION = 0.63F;

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ATTACK_SEQ, 0);
    }

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

    }

    @Override
    public boolean canHoldItem(ItemStack stack) {
        WeaponKind kind = HumanoidWeapons.kindOf(stack);
        return kind == WeaponKind.NONE || this.profile.allows(kind);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack offered = player.getItemInHand(hand);

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
