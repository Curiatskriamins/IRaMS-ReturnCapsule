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

    private int leapCooldown;

    private boolean crawling;

    private boolean combatProne;

    private long vehicleShotMillis;

    private boolean drivingVehicle;

    private boolean vehicleCombat;

    private static final double SPRINT_JUMP_REACH = 1.0D;

    private static final double MAX_JUMP_REACH = 3.5D;

    private static final double SPRINT_JUMP_BOOST = 0.2D;



    private static final double JUMP_UP = 0.42D;

    private static final int RUN_UP_TICKS = 12;

    private static final double RUN_UP_DISTANCE = 2.0D;

    private int runUpTicks;

    private int runUpDone;

    private boolean runUpReady;

    private double runUpX;

    private double runUpZ;

    private double lastFallSpeed;

    private net.minecraft.world.phys.Vec3 lastPosition;

    private static final double DOWNED_PUSH_DISTANCE = 0.7D;

    private int downedTicks;

    private int downedCooldownUntil;

    private int farFromPlayerTicks;

    private int stuckBelowTicks;

    private int reactionGraceTicks;

    private static final int MOVE_INTERVAL = 10;

    private int moveCooldown;

    private int movePriority;

    private net.minecraft.core.BlockPos testGoal;

    private HostileCluster cluster;

    private int clusterTicks;

    private int clusterEmptyChecks;

    private static final int CLUSTER_CHECK_TICKS = 20;

    private static final int CLUSTER_EMPTY_CHECKS = 5;

    private int testGoalRetry;

    private int testGoalRescan;

    private int testGoalFailures;

    private int testGoalStall;

    private net.minecraft.world.phys.Vec3 testGoalLastPos = net.minecraft.world.phys.Vec3.ZERO;

    private int platformY = Integer.MIN_VALUE;

    private static final int STUCK_TELEPORT_TICKS = 1200;

    private static final double LEASH_DISTANCE = 100.0D;

    private static final int LEASH_TICKS = 600;

    private static final int DOWNED_COOLDOWN = 120;

    private static final int DOWNED_TICKS = 100;

    private static final float DOWNED_MIN_DAMAGE = 30.0F;

    private static final double DOWNED_FALL_SPEED = 0.85D;

    private static final double DOWNED_FAST_SPEED = 0.85D;

    private boolean bypassProtections;

    private boolean applyingHit;

    private boolean convertedHit;

    private static final double LONG_RANGE_HYSTERESIS = 8.0D;
    private static final double AIRBORNE_SWITCH_HEIGHT = 2.0D;

    private static final float SHIELD_BREAK_DAMAGE = 100.0F;

    private static final int BLADE_SKILL_INTERVAL = 200;

    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_LEAP =
            net.minecraft.network.syncher.SynchedEntityData.defineId(NeutralHumanoid.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

    private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> DATA_DOWNED =
            net.minecraft.network.syncher.SynchedEntityData.defineId(NeutralHumanoid.class,
                    net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

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

    private final java.util.List<ItemStack> throwables = new java.util.ArrayList<>();

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
        this.moveControl = new HumanoidMoveControl(this);
        this.xpReward = 40;
        this.setPersistenceRequired();
        this.setDropChance(EquipmentSlot.MAINHAND, 1.0F);
        this.setDropChance(EquipmentSlot.OFFHAND, 1.0F);
        if (!level.isClientSide) {
            this.makeConversionImmune();

        }
    
        this.setMaxUpStep(1.0F);}

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

    public java.util.List<ItemStack> getThrowables() {
        return this.throwables;
    }

    public boolean isIndoors() {
        if (this.level().canSeeSky(this.blockPosition())) {
            return false;
        }
        int blocked = 0;
        net.minecraft.core.BlockPos head = this.blockPosition().above();
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            for (int step = 1; step <= 3; step++) {
                net.minecraft.core.BlockPos probe = head.relative(direction, step);
                if (!this.level().getBlockState(probe).getCollisionShape(this.level(), probe).isEmpty()) {
                    blocked++;
                    break;
                }
            }
        }
        return blocked >= 3;
    }

    private void refillThrowables() {
        if (!this.throwables.isEmpty()) {
            return;
        }
        java.util.List<ItemStack> pool = com.curiats.returncapsule.compat.lrt.Throwables.available();
        if (pool.isEmpty()) {
            return;
        }
        for (int i = 0; i < 3; i++) {
            ItemStack candidate = pool.get(this.random.nextInt(pool.size())).copy();
            candidate.setCount(Math.min(3, candidate.getMaxStackSize()));
            this.throwables.add(candidate);
        }
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
    
        this.refillThrowables();}

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
    protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level) {
        if (Boolean.getBoolean("returncapsule.vanillaNavigation")) {
            return super.createNavigation(level);
        }
        return new HumanoidNavigation(this, level);
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

    private int descentCooldown;
    private net.minecraft.core.BlockPos descentEdge;

    private void descendToward(net.minecraft.core.BlockPos toward) {
        if (this.level().isClientSide) {
            return;
        }
        if (this.descentCooldown > 0) {
            this.descentCooldown--;
        }
        if (this.descentEdge != null) {
            double distance = this.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(this.descentEdge));
            if (distance < 1.6D) {
                net.minecraft.core.BlockPos landing = this.descentEdge.below(2);
                double dx = landing.getX() + 0.5D - this.getX();
                double dz = landing.getZ() + 0.5D - this.getZ();
                double length = Math.sqrt(dx * dx + dz * dz);
                if (length > 1.0E-3D) {
                    this.setDeltaMovement(dx / length * 0.28D, 0.0D, dz / length * 0.28D);
                }
                this.descentEdge = null;
                this.descentCooldown = 60;
                return;
            }
            this.getNavigation().moveTo(this.descentEdge.getX() + 0.5D, this.descentEdge.getY(),
                    this.descentEdge.getZ() + 0.5D, 1.3D);
            return;
        }
        if (this.descentCooldown > 0) {
            return;
        }
        if (toward.getY() > this.blockPosition().getY() - 4) {
            return;
        }
        net.minecraft.core.BlockPos spot = HumanoidTraversal.findDescentSpot(this.level(), this.blockPosition(), toward);
        if (spot != null) {
            this.descentEdge = spot;
        } else {
            this.descentCooldown = 40;
        }
    }

    public boolean shouldHoldGun(double distance, double hysteresis) {
        if (this.isPassenger()) {
            return false;
        }
        if (this.rangedWeapon.isEmpty() || !HumanoidToggles.isEnabled(HumanoidToggles.Slot.RANGED)) {
            return false;
        }
        if (!HumanoidToggles.isEnabled(HumanoidToggles.Slot.MELEE)) {
            return true;
        }

        if (this.getTarget() != null && this.getTarget().getY() - this.getY() > AIRBORNE_SWITCH_HEIGHT) {
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

    public boolean aimAtPoint(net.minecraft.world.phys.Vec3 point, float tolerance) {
        double dx = point.x - this.getX();
        double dz = point.z - this.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0E-4D) {
            return true;
        }
        float desiredYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float desiredPitch = (float) -Math.toDegrees(Math.atan2(point.y - this.getEyeY(), horizontal));
        float yawDelta = net.minecraft.util.Mth.wrapDegrees(desiredYaw - this.getYRot());
        float pitchDelta = net.minecraft.util.Mth.wrapDegrees(desiredPitch - this.getXRot());
        this.setYRot(this.getYRot() + net.minecraft.util.Mth.clamp(yawDelta, -14.0F, 14.0F));
        this.setXRot(this.getXRot() + net.minecraft.util.Mth.clamp(pitchDelta, -12.0F, 12.0F));
        this.yBodyRot = this.getYRot();
        this.setYHeadRot(this.getYRot());
        return Math.abs(yawDelta) < tolerance && Math.abs(pitchDelta) < tolerance * 1.4F;
    }

    public boolean precisionWeapon() {
        String type = com.curiats.returncapsule.compat.tacz.TaczRifles.gunType(this.getMainHandItem());
        return "sniper".equalsIgnoreCase(type) || "dmr".equalsIgnoreCase(type);
    }

    public boolean aimAt(LivingEntity target) {
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0E-4D) {
            return true;
        }
        float desiredYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float desiredPitch = (float) -Math.toDegrees(Math.atan2(target.getEyeY() - this.getEyeY(), horizontal));
        float yawDelta = net.minecraft.util.Mth.wrapDegrees(desiredYaw - this.getYRot());
        float pitchDelta = net.minecraft.util.Mth.wrapDegrees(desiredPitch - this.getXRot());
        this.setYRot(this.getYRot() + net.minecraft.util.Mth.clamp(yawDelta, -10.0F, 10.0F));
        this.setXRot(this.getXRot() + net.minecraft.util.Mth.clamp(pitchDelta, -8.0F, 8.0F));
        this.yBodyRot = this.getYRot();
        this.setYHeadRot(this.getYRot());
        return Math.abs(yawDelta) < 3.0F && Math.abs(pitchDelta) < 4.0F;
    }

    public boolean fireGun(LivingEntity target) {
        if (friendlyOnLine(target)) {
            return false;
        }
        GunAim aim = this.gunAim;
        aim.track(target);
        double distance = Math.sqrt(this.distanceToSqr(target));
        aim.resolveExpired(this.level().getGameTime(), distance);
        this.performRangedAttack(target, 1.0F);
        aim.onShot(target);
        return true;
    }

    private boolean friendlyOnLine(LivingEntity target) {
        net.minecraft.world.phys.Vec3 from = this.getEyePosition();
        net.minecraft.world.phys.Vec3 to = target.getEyePosition();
        net.minecraft.world.phys.Vec3 segment = to.subtract(from);
        double length = segment.length();
        if (length < 1.0E-3D) {
            return false;
        }
        for (net.minecraft.world.entity.player.Player player : this.level().getEntitiesOfClass(
                net.minecraft.world.entity.player.Player.class,
                this.getBoundingBox().inflate(length + 4.0D))) {
            if (player.isSpectator()) {
                continue;
            }
            if (player.getVehicle() != null) {
                if (this.distanceToSqr(player.getVehicle()) < length * length) {
                    return true;
                }
            }
            if (distanceToSegment(from, to, player.getEyePosition()) < 1.2D) {
                return true;
            }
        }
        for (NeutralHumanoid other : this.level().getEntitiesOfClass(NeutralHumanoid.class,
                this.getBoundingBox().inflate(length + 4.0D))) {
            if (other != this && distanceToSegment(from, to, other.getEyePosition()) < 1.2D) {
                return true;
            }
        }
        return false;
    }

    private static double distanceToSegment(net.minecraft.world.phys.Vec3 from,
            net.minecraft.world.phys.Vec3 to, net.minecraft.world.phys.Vec3 point) {
        net.minecraft.world.phys.Vec3 segment = to.subtract(from);
        double lengthSqr = segment.lengthSqr();
        if (lengthSqr < 1.0E-6D) {
            return point.distanceTo(from);
        }
        double t = net.minecraft.util.Mth.clamp(point.subtract(from).dot(segment) / lengthSqr, 0.0D, 1.0D);
        return point.distanceTo(from.add(segment.scale(t)));
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
        if (this.isPassenger()) {
            return;
        }
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
        if (this.lastPosition == null) {
            this.lastPosition = this.position();
        }
        this.tickStuckRecovery();
        this.tickReturnToPlayer();
        this.tickPlatformReposition();
        this.tickRunUp();
        com.curiats.returncapsule.compat.vehicle.VehicleRider.tick(this);
        com.curiats.returncapsule.compat.vehicle.VehicleGunner.tick(this);
        com.curiats.returncapsule.compat.vehicle.VehicleDriverCompat.tick(this);
        this.tickLeapFlag();
        if (this.moveCooldown > 0) {
            this.moveCooldown--;
        } else {
            this.movePriority = 0;
        }
        this.tickClusters();
        this.tickTestGoal();
        this.tickPlatformState();
        this.tickCrawl();
        this.tickLaneCentering();
        this.tickClimb();
        this.tickParkour();
        this.tickDowned();
        this.applyMovementReactions();
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
        net.minecraft.world.entity.LivingEntity descentGoal = this.getTarget();
        if (descentGoal == null) {
            net.minecraft.world.entity.player.Player nearest = this.level().getNearestPlayer(this, 32.0D);
            if (nearest != null) {
                descentGoal = nearest;
            }
        }
        if (descentGoal != null) {
            this.descendToward(descentGoal.blockPosition());
        }
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
        if (this.isDowned()) {
            return false;
        }
        if (source.is(net.minecraft.world.damagesource.DamageTypes.EXPLOSION)
                || source.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_EXPLOSION)) {
            this.knockDown(DOWNED_TICKS);
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

    public void suppressReactions(int ticks) {
        this.reactionGraceTicks = Math.max(this.reactionGraceTicks, ticks);
    }

    private void applyMovementReactions() {
        if (this.level().isClientSide) {
            return;
        }
        if (this.reactionGraceTicks > 0) {
            this.reactionGraceTicks--;
            this.lastPosition = this.position();
            this.lastFallSpeed = 0.0D;
            return;
        }
        double moved = this.position().subtract(this.lastPosition).horizontalDistance();
        this.lastPosition = this.position();
        if (this.isDowned()) {
            return;
        }
        boolean grounded = this.onGround();
        if (grounded) {
            if (this.lastFallSpeed > DOWNED_FALL_SPEED) {
                this.knockDown(DOWNED_TICKS);
            }
            this.lastFallSpeed = 0.0D;
        } else {
            double falling = -this.getDeltaMovement().y;
            if (falling > this.lastFallSpeed) {
                this.lastFallSpeed = falling;
            }
        }
        if (this.getDeltaMovement().horizontalDistance() > DOWNED_FAST_SPEED
                || (moved > DOWNED_PUSH_DISTANCE && this.getNavigation().isDone())) {
            this.knockDown(DOWNED_TICKS);
        }
    }

    private void applyReactions(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (this.level().isClientSide || !this.isDownedCandidate(amount)) {
            return;
        }
        boolean heavy = amount >= DOWNED_MIN_DAMAGE
                || source.is(net.minecraft.world.damagesource.DamageTypes.LIGHTNING_BOLT)
                || source.is(net.minecraft.world.damagesource.DamageTypes.FALLING_BLOCK)
                || source.is(net.minecraft.world.damagesource.DamageTypes.EXPLOSION);
        if (heavy) {
            this.knockDown(DOWNED_TICKS);
        }
    }

    private boolean isDownedCandidate(float amount) {
        return amount > 0.0F;
    }

    public boolean isLeaping() {
        return this.entityData.get(DATA_LEAP);
    }

    public boolean isDownedSynced() {
        return this.entityData.get(DATA_DOWNED);
    }

    private net.minecraft.core.BlockPos intendedStep() {
        if (!(this.getNavigation() instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation ground)) {
            return null;
        }
        net.minecraft.world.level.pathfinder.Path path = ground.getPath();
        if (path == null || path.isDone()) {
            return null;
        }
        return path.getNextNodePos();
    }

    private net.minecraft.world.phys.Vec3 intendedDirection() {
        net.minecraft.world.entity.LivingEntity goal = this.getTarget();
        if (goal == null) {
            goal = this.level().getNearestPlayer(this, 64.0D);
        }
        if (goal == null) {
            net.minecraft.world.phys.Vec3 motion = this.getDeltaMovement();
            if (motion.horizontalDistanceSqr() < 1.0E-4D) {
                return null;
            }
            return new net.minecraft.world.phys.Vec3(motion.x, 0.0D, motion.z).normalize();
        }
        double dx = goal.getX() - this.getX();
        double dz = goal.getZ() - this.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.5D) {
            return null;
        }
        return new net.minecraft.world.phys.Vec3(dx / length, 0.0D, dz / length);
    }

    private void tickRunUp() {
        if (this.runUpTicks > 0) {
            this.runUpTicks--;
        }
    }

    private void tickPlatformReposition() {
        if (this.level().isClientSide || this.getTarget() != null || !this.isRangedStance()) {
            return;
        }
        if (this.testGoal != null || this.atPlatformEdge() == false) {
            return;
        }
        if (this.tickCount % 80 != 0) {
            return;
        }
        net.minecraft.core.BlockPos here = this.blockPosition();
        for (int attempt = 0; attempt < 12; attempt++) {
            int dx = this.random.nextInt(13) - 6;
            int dz = this.random.nextInt(13) - 6;
            net.minecraft.core.BlockPos candidate = here.offset(dx, 0, dz);
            if (HumanoidTraversal.canStandOn(this.level(), candidate)) {
                this.requestMove(candidate.getX() + 0.5D, candidate.getY(),
                        candidate.getZ() + 0.5D, 1.0D, 1);
                return;
            }
        }
    }

    public boolean onHighPlatform() {
        return this.platformY != Integer.MIN_VALUE;
    }

    public int platformY() {
        return this.platformY;
    }

    public HostileCluster cluster() {
        return this.cluster;
    }

    public net.minecraft.world.phys.Vec3 aimPointFor(LivingEntity target) {
        if (this.cluster != null && this.cluster.contains(target)) {
            return this.cluster.center();
        }
        return new net.minecraft.world.phys.Vec3(target.getX(), target.getEyeY(), target.getZ());
    }

    private void tickClusters() {
        if (this.level().isClientSide) {
            return;
        }
        if (this.getTarget() == null) {
            this.cluster = null;
            this.clusterEmptyChecks = 0;
            return;
        }
        if (this.clusterTicks > 0) {
            this.clusterTicks--;
            return;
        }
        this.clusterTicks = CLUSTER_CHECK_TICKS;
        if (this.cluster != null) {
            int members = HostileCluster.countInside(this.level(), this.cluster.box());
            if (members > 0) {
                this.clusterEmptyChecks = 0;
                return;
            }
            if (++this.clusterEmptyChecks < CLUSTER_EMPTY_CHECKS) {
                return;
            }
            this.cluster = null;
            this.clusterEmptyChecks = 0;
        }
        this.cluster = HostileCluster.build(this.level(), this.position(), 64.0D, this);
    }

    public String debugState() {
        net.minecraft.world.level.pathfinder.Path path = this.getNavigation().getPath();
        String node = "none";
        if (path != null && !path.isDone()) {
            int index = path.getNextNodeIndex();
            if (index >= 0 && index < path.getNodeCount()) {
                net.minecraft.world.level.pathfinder.Node next = path.getNode(index);
                node = next.x + "," + next.y + "," + next.z;
            }
        }
        return "pos=" + this.blockPosition()
                + " testGoal=" + (this.testGoal == null ? "none" : this.testGoal)
                + " navDone=" + this.getNavigation().isDone()
                + " node=" + node
                + " leapCd=" + this.leapCooldown
                + " runUp=" + this.runUpTicks + "/" + this.runUpReady
                + " ground=" + this.onGround()
                + " moveCd=" + this.moveCooldown + " prio=" + this.movePriority
                + " platform=" + this.platformY
                + " combat=" + this.vehicleCombat
                + " | " + com.curiats.returncapsule.compat.vehicle.VehicleGunner.describe(this);
    }

    public boolean hasTestGoal() {
        return this.testGoal != null;
    }

    public boolean requestMove(double x, double y, double z, double speed, int priority) {
        if (priority < this.movePriority) {
            return false;
        }
        if (this.moveCooldown > 0 && priority <= this.movePriority) {
            return false;
        }
        this.movePriority = priority;
        this.moveCooldown = MOVE_INTERVAL;
        return this.getNavigation().moveTo(x, y, z, speed);
    }

    public boolean requestMove(net.minecraft.world.entity.Entity target, double speed, int priority) {
        if (priority < this.movePriority) {
            return false;
        }
        if (this.moveCooldown > 0 && priority <= this.movePriority) {
            return false;
        }
        this.movePriority = priority;
        this.moveCooldown = MOVE_INTERVAL;
        return this.getNavigation().moveTo(target, speed);
    }

    private void tickTestGoal() {
        if (this.level().isClientSide) {
            return;
        }
        if (this.getTarget() != null || this.isPassenger() || this.isDowned()) {
            return;
        }
        if (this.testGoalRescan > 0) {
            this.testGoalRescan--;
        }
        if (this.testGoalRetry > 0) {
            this.testGoalRetry--;
        }
        if (this.testGoalStall > 0) {
            this.testGoalStall--;
        }
        if (this.testGoal == null && this.testGoalRescan <= 0) {
            this.testGoal = this.findTestGoal();
            this.testGoalRescan = 200;
            this.testGoalFailures = 0;
            this.testGoalStall = 0;
            this.testGoalLastPos = this.position();
        }
        if (this.testGoal == null) {
            return;
        }
        net.minecraft.core.BlockPos stand = this.testGoal.above();
        if (this.blockPosition().equals(stand)) {
            this.getNavigation().stop();
            return;
        }
        if (this.tickCount % 20 == 0) {
            if (this.position().distanceToSqr(this.testGoalLastPos) < 0.25D) {
                this.testGoalStall++;
            } else {
                this.testGoalStall = 0;
            }
            this.testGoalLastPos = this.position();
        }
        if (this.testGoalStall >= 2) {
            this.testGoalFailures++;
            this.testGoalStall = 0;
            this.getNavigation().stop();
            if (this.testGoalFailures >= 3) {
                this.testGoal = null;
                this.testGoalRescan = 400;
                this.testGoalRetry = 60;
                return;
            }
            this.testGoalRetry = 40 * this.testGoalFailures;
            return;
        }
        if (this.testGoalRetry > 0) {
            return;
        }
        if (this.tryClimbOnto(stand)) {
            this.testGoalRetry = 20;
            return;
        }
        double flat = Math.sqrt(this.distanceToSqr(stand.getX() + 0.5D, this.getY(), stand.getZ() + 0.5D));
        if (flat <= 16.0D) {
            this.getNavigation().stop();
            this.getMoveControl().setWantedPosition(stand.getX() + 0.5D, stand.getY(),
                    stand.getZ() + 0.5D, 1.1D);
            return;
        }
        if (!this.getNavigation().isDone()) {
            return;
        }
        this.requestMove(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D, 1.2D, 2);
        this.testGoalRetry = 40;
    }

    private boolean tryClimbOnto(net.minecraft.core.BlockPos stand) {
        if (!this.onGround() || this.blockPosition().equals(stand)) {
            return false;
        }
        if (!this.horizontalCollision) {
            return false;
        }
        double dx = stand.getX() + 0.5D - this.getX();
        double dz = stand.getZ() + 0.5D - this.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        double dy = stand.getY() - this.getY();
        if (flat > 1.8D || dy < 0.3D || dy > 1.6D) {
            return false;
        }
        if (this.leapCooldown > 0) {
            return false;
        }
        this.getNavigation().stop();
        double push = 0.26D;
        this.setDeltaMovement(dx / flat * push, 0.42D, dz / flat * push);
        this.hasImpulse = true;
        this.leapCooldown = 10;
        return true;
    }

    private net.minecraft.core.BlockPos findTestGoal() {
        net.minecraft.core.BlockPos here = this.blockPosition();
        net.minecraft.core.BlockPos best = null;
        double bestDistance = 24.0D * 24.0D;
        for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(
                here.offset(-24, -4, -24), here.offset(24, 4, 24))) {
            if (!this.level().getBlockState(pos).is(net.minecraft.world.level.block.Blocks.NETHERITE_BLOCK)) {
                continue;
            }
            double distance = pos.distSqr(here);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.immutable();
            }
        }
        return best;
    }

    private void tickPlatformState() {
        if (this.level().isClientSide || this.tickCount % 20 != 0) {
            return;
        }
        this.platformY = HumanoidTraversal.nearHighEdge(this.level(), this.blockPosition())
                ? this.blockPosition().getY()
                : Integer.MIN_VALUE;
    }

    public void setCombatProne(boolean prone) {
        if (this.combatProne == prone) {
            return;
        }
        this.combatProne = prone;
        if (this.isDowned()) {
            return;
        }
        this.setShiftKeyDown(prone);
        this.setPose(prone
                ? net.minecraft.world.entity.Pose.SWIMMING
                : net.minecraft.world.entity.Pose.STANDING);
    }

    public boolean isCombatProne() {
        return this.combatProne;
    }

    public boolean atPlatformEdge() {
        net.minecraft.core.BlockPos feet = this.blockPosition();
        float yaw = (float) Math.toRadians(this.getYRot());
        double dirX = -Math.sin(yaw);
        double dirZ = Math.cos(yaw);
        for (double step = 0.7D; step <= 1.6D; step += 0.45D) {
            int x = (int) Math.floor(this.getX() + dirX * step);
            int z = (int) Math.floor(this.getZ() + dirZ * step);
            net.minecraft.core.BlockPos below = new net.minecraft.core.BlockPos(x, feet.getY() - 1, z);
            if (this.level().getBlockState(below).getCollisionShape(this.level(), below).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void tickCrawl() {
        if (this.level().isClientSide || this.isDowned()) {
            return;
        }
        if (this.combatProne) {
            return;
        }
        if (this.onHighPlatform()
                && HumanoidTraversal.platformDrop(this.level(), this.blockPosition()) > 1) {
            this.crawling = false;
            this.setPose(net.minecraft.world.entity.Pose.STANDING);
            return;
        }
        net.minecraft.core.BlockPos here = this.blockPosition();
        boolean headHere = HumanoidTraversal.headBlocked(this.level(), here);
        float yaw = (float) Math.toRadians(this.getYRot());
        double dirX = -Math.sin(yaw);
        double dirZ = Math.cos(yaw);
        boolean headAhead = HumanoidTraversal.headBlocked(this.level(), net.minecraft.core.BlockPos.containing(
                this.getX() + dirX, this.getY(), this.getZ() + dirZ));
        boolean needCrawl = headHere || headAhead;
        if (needCrawl && !this.crawling) {
            this.crawling = true;
            this.setPose(net.minecraft.world.entity.Pose.SWIMMING);
            return;
        }
        if (this.crawling && !needCrawl) {
            this.crawling = false;
            this.setPose(net.minecraft.world.entity.Pose.STANDING);
            return;
        }
        if (this.crawling && this.onGround()
                && HumanoidTraversal.stepUpAhead(this.level(), here, dirX, dirZ)) {
            this.getJumpControl().jump();
        }
    }

    private void tickLeapFlag() {
        if (!this.level().isClientSide && this.entityData.get(DATA_LEAP) && this.onGround()) {
            this.entityData.set(DATA_LEAP, false);
        }
    }

    private boolean roomBehind(double dirX, double dirZ) {
        for (double step = 1.0D; step <= RUN_UP_DISTANCE; step += 0.75D) {
            net.minecraft.core.BlockPos probe = net.minecraft.core.BlockPos.containing(
                    this.getX() - dirX * step, this.getY(), this.getZ() - dirZ * step);
            if (!HumanoidTraversal.canStandOn(this.level(), probe)) {
                return false;
            }
        }
        return true;
    }

    private boolean stepUpAhead(double dirX, double dirZ) {
        net.minecraft.core.BlockPos ahead = net.minecraft.core.BlockPos.containing(
                this.getX() + dirX, this.getY(), this.getZ() + dirZ);
        net.minecraft.core.BlockPos feet = ahead;
        net.minecraft.core.BlockPos head = ahead.above();
        boolean feetSolid = !this.level().getBlockState(feet).getCollisionShape(this.level(), feet).isEmpty();
        boolean headFree = this.level().getBlockState(head).getCollisionShape(this.level(), head).isEmpty();
        return feetSolid && headFree;
    }

    private void tickLaneCentering() {
        if (this.level().isClientSide || this.isDowned() || this.isPassenger()) {
            return;
        }
        if (this.testGoal != null) {
            return;
        }
        net.minecraft.world.phys.Vec3 delta = this.getDeltaMovement();
        double speed = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (speed < 0.06D) {
            return;
        }
        double offset = HumanoidTraversal.corridorOffset(this.level(), this.blockPosition(),
                delta.x, delta.z, 4);
        if (Math.abs(offset) < 0.5D) {
            return;
        }
        offset = net.minecraft.util.Mth.clamp(offset, -2.0D, 2.0D);
        double dirX = delta.x / speed;
        double dirZ = delta.z / speed;
        double push = offset * 0.02D;
        this.setDeltaMovement(delta.x - dirZ * push, delta.y, delta.z + dirX * push);
    }

    private double pathGap(double dirX, double dirZ, net.minecraft.core.BlockPos next) {
        double dx = next.getX() + 0.5D - this.getX();
        double dz = next.getZ() + 0.5D - this.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat < 1.6D) {
            return -1.0D;
        }
        for (double step = 0.6D; step <= flat; step += 0.4D) {
            net.minecraft.core.BlockPos probe = net.minecraft.core.BlockPos.containing(
                    this.getX() + dirX * step, this.getY() - 1.0D, this.getZ() + dirZ * step);
            if (this.level().getBlockState(probe).getCollisionShape(this.level(), probe).isEmpty()) {
                net.minecraft.core.BlockPos landing = net.minecraft.core.BlockPos.containing(
                        this.getX() + dirX * flat, this.getY() - 1.0D, this.getZ() + dirZ * flat);
                if (this.level().getBlockState(landing).getCollisionShape(this.level(), landing).isEmpty()) {
                    return -1.0D;
                }
                return flat;
            }
        }
        return -1.0D;
    }

    private void tickClimb() {
        if (this.level().isClientSide || this.isDowned()) {
            return;
        }
        boolean onLadder = HumanoidTraversal.isClimbable(this.level(), this.blockPosition())
                || HumanoidTraversal.isClimbable(this.level(), this.blockPosition().below());
        if (!onLadder) {
            if (this.jumpToLadder()) {
                return;
            }
            return;
        }
        net.minecraft.world.level.pathfinder.Path path = this.getNavigation().getPath();
        if (path == null || path.isDone()) {
            return;
        }
        int index = path.getNextNodeIndex();
        if (index < 0 || index >= path.getNodeCount()) {
            return;
        }
        net.minecraft.world.level.pathfinder.Node node = path.getNode(index);
        if (node == null) {
            return;
        }
        double dy = node.y - this.getY();
        if (dy > 0.4D) {
            this.setDeltaMovement(this.getDeltaMovement().x, 0.2D, this.getDeltaMovement().z);
            this.hasImpulse = true;
        } else if (dy < -0.6D) {
            this.setDeltaMovement(this.getDeltaMovement().x, -0.2D, this.getDeltaMovement().z);
            this.hasImpulse = true;
        }
    }

    private boolean jumpToLadder() {
        if (!this.onGround() || this.leapCooldown > 0) {
            return false;
        }
        double yaw = Math.toRadians(this.getYRot());
        double dirX = -Math.sin(yaw);
        double dirZ = Math.cos(yaw);
        for (double step = 0.6D; step <= 1.6D; step += 0.5D) {
            net.minecraft.core.BlockPos probe = net.minecraft.core.BlockPos.containing(
                    this.getX() + dirX * step, this.getY(), this.getZ() + dirZ * step);
            if (!HumanoidTraversal.isClimbable(this.level(), probe.above())) {
                continue;
            }
            if (!HumanoidTraversal.canStandOn(this.level(), this.blockPosition())) {
                return false;
            }
            this.setDeltaMovement(this.getDeltaMovement().x + dirX * 0.12D, 0.42D,
                    this.getDeltaMovement().z + dirZ * 0.12D);
            this.hasImpulse = true;
            this.leapCooldown = 10;
            return true;
        }
        return false;
    }

    private void tickParkour() {
        if (this.leapCooldown > 0) {
            this.leapCooldown--;
        }
        if (this.level().isClientSide || this.isDowned()) {
            return;
        }
        if (this.runUpTicks > 0) {
            this.runUpTicks--;
            if (this.runUpTicks == 0) {
                this.runUpReady = true;
            }
            return;
        }
        if (!this.onGround() || this.leapCooldown > 0) {
            return;
        }
        net.minecraft.world.entity.LivingEntity chase = this.getTarget();
        if (chase != null && chase.getY() < this.getY() - 5.0D) {
            return;
        }
        net.minecraft.core.BlockPos next = this.intendedStep();
        double dx;
        double dz;
        if (next != null) {
            dx = next.getX() + 0.5D - this.getX();
            dz = next.getZ() + 0.5D - this.getZ();
        } else {
            net.minecraft.world.phys.Vec3 intent = this.intendedDirection();
            if (intent == null) {
                return;
            }
            dx = intent.x;
            dz = intent.z;
            next = net.minecraft.core.BlockPos.containing(this.getX() + dx * 4.0D, this.getY(),
                    this.getZ() + dz * 4.0D);
        }
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 0.6D) {
            return;
        }
        dx /= length;
        dz /= length;
        double yaw = Math.toRadians(this.getYRot());
        if (dx * -Math.sin(yaw) + dz * Math.cos(yaw) < 0.3D) {
            this.getLookControl().setLookAt(next.getX() + 0.5D, next.getY() + 1.0D, next.getZ() + 0.5D,
                    30.0F, 30.0F);
            this.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
            this.yBodyRot = this.getYRot();
            return;
        }
        double gap = HumanoidTraversal.gapWidthAhead(this.level(), this.blockPosition(), dx, dz, 6);
        if (gap < 0.0D) {
            gap = this.pathGap(dx, dz, next);
        }
        if (gap < 0.0D) {
            this.runUpReady = false;
            return;
        }
        if (this.onHighPlatform()) {
            net.minecraft.core.BlockPos landing = HumanoidTraversal.landingOf(this.level(),
                    this.blockPosition(), dx, dz, 6);
            if (landing == null || Math.abs(landing.getY() - this.platformY) > 1) {
                this.runUpReady = false;
                return;
            }
        }
        if (this.onHighPlatform()) {
            net.minecraft.core.BlockPos landing = HumanoidTraversal.landingOf(this.level(),
                    this.blockPosition(), dx, dz, 6);
            if (landing != null && landing.getY() > this.getY()) {
                this.setDeltaMovement(dx * 0.28D, JUMP_UP, dz * 0.28D);
                this.hasImpulse = true;
                this.leapCooldown = 8;
                return;
            }
        }
        net.minecraft.core.BlockPos goalPos = this.getTarget() != null
                ? this.getTarget().blockPosition()
                : net.minecraft.core.BlockPos.containing(this.getX() + dx * 8.0D, this.getY(),
                        this.getZ() + dz * 8.0D);
        double before = Math.sqrt(this.blockPosition().distSqr(goalPos));
        double after = Math.sqrt(HumanoidTraversal.landingOf(this.level(), this.blockPosition(),
                dx, dz, 6) != null
                ? HumanoidTraversal.landingOf(this.level(), this.blockPosition(), dx, dz, 6).distSqr(goalPos)
                : this.blockPosition().distSqr(goalPos));
        if (after > before - 0.5D) {
            return;
        }
        double edgeGap = Math.max(0.0D, gap - this.getBbWidth());
        boolean sprint = edgeGap > SPRINT_JUMP_REACH && !this.stepUpAhead(dx, dz);
        if (sprint && !this.runUpReady) {
            if (!this.roomBehind(dx, dz)) {
                this.runUpReady = true;
            } else {
                this.runUpTicks = RUN_UP_TICKS;
                this.requestMove(this.getX() - dx * RUN_UP_DISTANCE, this.getY(),
                        this.getZ() - dz * RUN_UP_DISTANCE, 1.3D, 3);
                return;
            }
        }
        if (edgeGap > MAX_JUMP_REACH) {
            return;
        }
        net.minecraft.world.phys.Vec3 momentum = this.getDeltaMovement();
        double boost = sprint ? SPRINT_JUMP_BOOST : 0.0D;
        this.setSprinting(sprint);
        this.entityData.set(DATA_LEAP, true);
        this.setDeltaMovement(momentum.x + dx * boost, JUMP_UP, momentum.z + dz * boost);
        this.hasImpulse = true;
        this.leapCooldown = 12;
        this.runUpReady = false;
    }

    private void tickStuckRecovery() {
        if (this.level().isClientSide) {
            return;
        }
        net.minecraft.world.entity.player.Player player = this.level().getNearestPlayer(this, 96.0D);
        if (player == null || this.distanceToSqr(player) > 48.0D * 48.0D) {
            this.stuckBelowTicks = 0;
            return;
        }
        boolean cannotPath = this.getNavigation().isDone();
        boolean walled = HumanoidTraversal.isTrapped(this.level(), this.blockPosition());
        boolean unreachable = cannotPath && walled;
        if (player.getY() - this.getY() < 5.0D && !unreachable) {
            this.stuckBelowTicks = 0;
            return;
        }
        if (++this.stuckBelowTicks < STUCK_TELEPORT_TICKS) {
            return;
        }
        this.stuckBelowTicks = 0;
        net.minecraft.core.BlockPos landing = HumanoidTraversal.findLandingSpot(this.level(),
                player.blockPosition(), 5);
        net.minecraft.core.BlockPos spot = landing != null ? landing : player.blockPosition();
        this.getNavigation().stop();
        this.suppressReactions(80);
        this.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, this.getYRot(), this.getXRot());
        this.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        this.hasImpulse = false;
    }

    private void tickReturnToPlayer() {
        if (this.level().isClientSide) {
            return;
        }
        net.minecraft.world.entity.player.Player player = this.level().getNearestPlayer(this, 256.0D);
        if (player == null || this.distanceToSqr(player) < LEASH_DISTANCE * LEASH_DISTANCE) {
            this.farFromPlayerTicks = 0;
            return;
        }
        if (++this.farFromPlayerTicks < LEASH_TICKS) {
            return;
        }
        this.farFromPlayerTicks = 0;
        net.minecraft.core.BlockPos landing = HumanoidTraversal.findLandingSpot(this.level(),
                player.blockPosition(), 4);
        net.minecraft.core.BlockPos spot = landing != null ? landing : player.blockPosition();
        this.getNavigation().stop();
        this.suppressReactions(80);
        this.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, this.getYRot(), this.getXRot());
        this.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        this.hasImpulse = false;
    }

    public boolean isVehicleCombat() {
        return this.vehicleCombat;
    }

    public void setVehicleCombat(boolean value) {
        this.vehicleCombat = value;
    }

    public boolean isDrivingVehicle() {
        return this.drivingVehicle;
    }

    public void setDrivingVehicle(boolean driving) {
        this.drivingVehicle = driving;
    }

    public long vehicleShotMillis() {
        return this.vehicleShotMillis;
    }

    public void markVehicleShot(long millis) {
        this.vehicleShotMillis = millis;
    }

    public void knockDownServer(int ticks) {
        if (!this.level().isClientSide) {
            this.knockDown(ticks);
        }
    }

    public boolean isDowned() {
        return this.downedTicks > 0;
    }

    public void knockDown(int ticks) {
        if (this.level().isClientSide || this.isDowned()) {
            return;
        }
        if (this.tickCount < this.downedCooldownUntil) {
            return;
        }
        this.downedCooldownUntil = this.tickCount + ticks + DOWNED_COOLDOWN;
        this.downedTicks = Math.max(this.downedTicks, ticks);
        this.entityData.set(DATA_DOWNED, true);
        this.clearAggro();
        this.getNavigation().stop();
        this.setDeltaMovement(this.getDeltaMovement().x * 0.4D, 0.18D, this.getDeltaMovement().z * 0.4D);
        this.hasImpulse = true;
    }

    private void clearAggro() {
        for (net.minecraft.world.entity.Mob mob : this.level().getEntitiesOfClass(
                net.minecraft.world.entity.Mob.class, this.getBoundingBox().inflate(48.0D))) {
            if (mob.getTarget() == this) {
                mob.setTarget(null);
            }
        }
    }

    private void tickDowned() {
        if (this.downedTicks <= 0) {
            return;
        }
        this.downedTicks--;
        this.getNavigation().stop();
        if (this.downedTicks % 10 == 0) {
            this.clearAggro();
        }
        if (this.downedTicks == 0) {
            this.entityData.set(DATA_DOWNED, false);
            this.setPose(net.minecraft.world.entity.Pose.STANDING);
            this.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            this.hasImpulse = false;
        }
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

    @Override
    public net.minecraft.world.entity.HumanoidArm getMainArm() {
        return net.minecraft.world.entity.HumanoidArm.RIGHT;
    }

    private static boolean isFriendlyCreature(LivingEntity entity) {
        if (entity instanceof net.minecraft.world.entity.OwnableEntity own && own.getOwnerUUID() != null) {
            return true;
        }
        return !(entity instanceof net.minecraft.world.entity.monster.Enemy);
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
        boolean indoorBlock = this.isIndoors()
                && "rpg".equalsIgnoreCase(com.curiats.returncapsule.compat.tacz.TaczRifles.gunType(this.longRangedWeapon));
        boolean wantLong = distance > threshold && !this.longRangedWeapon.isEmpty() && !indoorBlock;
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
            if (!(nearby instanceof Player) && !isFriendlyCreature(nearby)) {
                com.curiats.returncapsule.util.DamageLock.lockFraction(nearby, this.level().getGameTime(),
                        DEATH_BLAST_FRACTION, DEATH_BLAST_LOCK_TICKS);
            }
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

    private static final int DEATH_BLAST_LOCK_TICKS = 3600;

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ATTACK_SEQ, 0);
        this.entityData.define(DATA_DOWNED, false);
        this.entityData.define(DATA_LEAP, false);
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
    public void setTarget(@javax.annotation.Nullable LivingEntity target) {
        if (target instanceof net.minecraft.world.entity.player.Player
                || target instanceof NeutralHumanoid) {
            super.setTarget(null);
            return;
        }
        super.setTarget(target);
    }

    public boolean doHurtTarget(Entity target) {
        if (target instanceof net.minecraft.world.entity.player.Player
                || target instanceof NeutralHumanoid) {
            return false;
        }

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
