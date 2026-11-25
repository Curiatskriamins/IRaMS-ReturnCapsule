package com.curiats.returncapsule;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public final class Config {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue SPAWN_HEIGHT;
    public static final ForgeConfigSpec.DoubleValue GRAVITY;
    public static final ForgeConfigSpec.DoubleValue TERMINAL_VELOCITY;
    public static final ForgeConfigSpec.BooleanValue MANNED_FALL_REALISTIC;
    public static final ForgeConfigSpec.IntValue MANNED_SPAWN_HEIGHT;
    public static final ForgeConfigSpec.DoubleValue MANNED_CRUISE_SPEED;
    public static final ForgeConfigSpec.DoubleValue MANNED_REALISTIC_SPEED;
    public static final ForgeConfigSpec.IntValue MANNED_HORIZONTAL_DISTANCE;
    public static final ForgeConfigSpec.IntValue MANNED_HORIZONTAL_START;

    public static final ForgeConfigSpec.BooleanValue BREAK_BLOCKS;

    public static final ForgeConfigSpec.BooleanValue HEALTH_GUARD;
    public static final ForgeConfigSpec.IntValue SMASH_INNER_RADIUS;
    public static final ForgeConfigSpec.IntValue SMASH_OUTER_RADIUS;
    public static final ForgeConfigSpec.DoubleValue SMASH_OUTER_CHANCE;
    public static final ForgeConfigSpec.IntValue SMASH_COLUMN_PASSES;
    public static final ForgeConfigSpec.IntValue SMASH_SOUND_INTERVAL;
    public static final ForgeConfigSpec.DoubleValue SMASH_SOUND_VOLUME;
    public static final ForgeConfigSpec.DoubleValue SMASH_SOUND_PITCH;

    public static final ForgeConfigSpec.DoubleValue IMPACT_HALF_SIZE;
    public static final ForgeConfigSpec.DoubleValue TRUE_DAMAGE;
    public static final ForgeConfigSpec.BooleanValue DAMAGE_OWNER;
    public static final ForgeConfigSpec.BooleanValue CLEAR_LANDING_AREA;
    public static final ForgeConfigSpec.BooleanValue CAPSULE_BREAK_BLOCKS;
    public static final ForgeConfigSpec.DoubleValue LANDING_DROP_CHANCE;
    public static final ForgeConfigSpec.DoubleValue LANDING_FIRE_CHANCE;
    public static final ForgeConfigSpec.IntValue LANDING_FIRE_RADIUS;
    public static final ForgeConfigSpec.DoubleValue BOOM_FIRE_CHANCE;
    public static final ForgeConfigSpec.BooleanValue SCREEN_SHAKE;
    public static final ForgeConfigSpec.DoubleValue SHAKE_RADIUS;
    public static final ForgeConfigSpec.DoubleValue SHAKE_START_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue IMPACT_SOUND_VOLUME;
    public static final ForgeConfigSpec.DoubleValue IMPACT_SOUND_PITCH;
    public static final ForgeConfigSpec.DoubleValue IMPACT_THUD_VOLUME;
    public static final ForgeConfigSpec.DoubleValue IMPACT_THUD_PITCH;

    public static final ForgeConfigSpec.IntValue SHOCK_STUN_TICKS;
    public static final ForgeConfigSpec.DoubleValue SHOCK_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue SHOCK_TICK_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue SHOCK_OCCLUDED_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue SHOCK_REFRESH_TICKS;

    public static final ForgeConfigSpec.IntValue TRANSPORT_MAX_PASSENGERS;
    public static final ForgeConfigSpec.IntValue CONTAINER_SLOTS;
    public static final ForgeConfigSpec.IntValue DESIGNATOR_MAX_CHARGE;
    public static final ForgeConfigSpec.IntValue DESIGNATOR_CONFIRM_TICKS;
    public static final ForgeConfigSpec.IntValue DESIGNATOR_COOLDOWN_TICKS;
    public static final ForgeConfigSpec.IntValue PLATFORM_SEARCH_RADIUS;
    public static final ForgeConfigSpec.IntValue MARKER_GRACE_TICKS;
    public static final ForgeConfigSpec.IntValue MARKER_CHUNK_RADIUS;

    public static final ForgeConfigSpec.IntValue CAPSULE_OPEN_TICKS;
    public static final ForgeConfigSpec.IntValue CAPSULE_CLOSE_TICKS;
    public static final ForgeConfigSpec.IntValue LID_OPEN_TICKS;
    public static final ForgeConfigSpec.IntValue LID_CLOSE_TICKS;

    public static final ForgeConfigSpec.DoubleValue PARTICLE_SCALE;
    public static final ForgeConfigSpec.DoubleValue TRAIL_SCALE;
    public static final ForgeConfigSpec.DoubleValue SHAKE_AMPLITUDE;
    public static final ForgeConfigSpec.DoubleValue SHAKE_SOUND_RANGE;
    public static final ForgeConfigSpec.DoubleValue MODEL_YAW_OFFSET;

    public static final ForgeConfigSpec.BooleanValue ADD_MOD_ITEMS;
    public static final ForgeConfigSpec.IntValue MOD_ITEM_ROLLS;
    public static final ForgeConfigSpec.IntValue VANILLA_LOOT_STACKS;
    public static final ForgeConfigSpec.DoubleValue UNIQUE_WEAPON_CHANCE;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> MOD_LOOT_EXCLUDED_NAMESPACES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> MOD_LOOT_EXCLUDED_ITEMS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLADE_NAMESPACES;
    public static final ForgeConfigSpec.DoubleValue FILL_TARGET;
    public static final ForgeConfigSpec.IntValue TACZ_MIN_GUNS;
    public static final ForgeConfigSpec.IntValue TACZ_MAX_GUNS;
    public static final ForgeConfigSpec.IntValue TACZ_MIN_ATTACHMENTS;
    public static final ForgeConfigSpec.IntValue TACZ_MAX_ATTACHMENTS;
    public static final ForgeConfigSpec.DoubleValue TACZ_AMMO_MULTIPLIER;

    public static final ForgeConfigSpec.DoubleValue BOOM_POWER;
    public static final ForgeConfigSpec.BooleanValue BOOM_DESTROY_TERRAIN;
    public static final ForgeConfigSpec.DoubleValue BOOM_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue BOOM_TRUE_DAMAGE;
    public static final ForgeConfigSpec.IntValue BOOM_SHOCK_TICKS;
    public static final ForgeConfigSpec.IntValue BOOM_RAYS;
    public static final ForgeConfigSpec.DoubleValue BOOM_MIN_DAMAGE_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue BOOM_RADIUS_PER_POWER;
    public static final ForgeConfigSpec.DoubleValue BOOM_INCIDENCE_DEGREES;
    public static final ForgeConfigSpec.IntValue BOOM_SPAWN_MARGIN_CHUNKS;
    public static final ForgeConfigSpec.BooleanValue BOOM_HURT_OWNER;
    public static final ForgeConfigSpec.IntValue BOOM_MAX_LIFETIME_TICKS;
    public static final ForgeConfigSpec.IntValue BOOM_SPAWN_HEIGHT;
    public static final ForgeConfigSpec.BooleanValue BOOM_CHUNK_LOADING;
    public static final ForgeConfigSpec.DoubleValue BOOM_MODEL_SCALE;
    public static final ForgeConfigSpec.DoubleValue BOOM_MODEL_BASE_PITCH;
    public static final ForgeConfigSpec.DoubleValue BOOM_FLARE_SCALE;

    public static final ForgeConfigSpec.BooleanValue VEHICLE_AMMO_FREEZE;
    public static final ForgeConfigSpec.BooleanValue VEHICLE_AMMO_KEEP_HOMING;
    public static final ForgeConfigSpec.BooleanValue VEHICLE_AMMO_KEEP_LONG_RANGE;
    public static final ForgeConfigSpec.IntValue VEHICLE_AMMO_LONG_RANGE_BLOCKS;
    public static final ForgeConfigSpec.IntValue VEHICLE_AMMO_TTL_TICKS;

    public static final ForgeConfigSpec.BooleanValue EQUIPMENT_SET_ENABLED;
    public static final ForgeConfigSpec.DoubleValue EQUIPMENT_HIT_CAP_FRACTION;
    public static final ForgeConfigSpec.DoubleValue EQUIPMENT_LOW_DAMAGE_IMMUNITY;
    public static final ForgeConfigSpec.DoubleValue EQUIPMENT_DAMAGE_REDUCTION;
    public static final ForgeConfigSpec.DoubleValue EQUIPMENT_SECOND_CAP_FRACTION;
    public static final ForgeConfigSpec.BooleanValue EQUIPMENT_EFFECT_IMMUNITY;
    public static final ForgeConfigSpec.BooleanValue ANCHOR_GUARD_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ANCHOR_GUARD_WHITELIST;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> ANCHOR_GUARD_BOSSES;
    public static final ForgeConfigSpec.BooleanValue ANCHOR_GUARD_REFLECT;
    public static final ForgeConfigSpec.BooleanValue EQUIPMENT_COTH_IMMUNITY;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> EQUIPMENT_BLOCKED_EFFECTS;
    public static final ForgeConfigSpec.BooleanValue EQUIPMENT_REGEN_ENABLED;
    public static final ForgeConfigSpec.IntValue EQUIPMENT_REGEN_AMPLIFIER;
    public static final ForgeConfigSpec.DoubleValue HUMANOID_SEAL_SHARE;
    public static final ForgeConfigSpec.DoubleValue HUMANOID_HIT_CAP_FRACTION;
    public static final ForgeConfigSpec.DoubleValue HUMANOID_SECOND_CAP_FRACTION;
    public static final ForgeConfigSpec.BooleanValue EQUIPMENT_BLADE_ENABLED;
    public static final ForgeConfigSpec.DoubleValue EQUIPMENT_BLADE_PERCENT;
    public static final ForgeConfigSpec.DoubleValue EQUIPMENT_BLADE_MAX_HEALTH_CAP;
    public static final ForgeConfigSpec.DoubleValue EQUIPMENT_SUPPORT_HEAL_PERCENT;
    public static final ForgeConfigSpec.DoubleValue EQUIPMENT_ARMOR_TOUGHNESS;
    public static final ForgeConfigSpec.DoubleValue EQUIPMENT_BLADE_PLAYER_FLOOR;
    public static final ForgeConfigSpec.DoubleValue EQUIPMENT_HEALTH_BONUS;
    public static final ForgeConfigSpec.DoubleValue EQUIPMENT_HEALTH_REGEN;
    public static final ForgeConfigSpec.BooleanValue EQUIPMENT_GUARD_ENABLED;
    public static final ForgeConfigSpec.DoubleValue EQUIPMENT_GUARD_CHANCE;
    public static final ForgeConfigSpec.IntValue EQUIPMENT_GUARD_TICKS;

    public static final ForgeConfigSpec.IntValue KILL_MARKER_RANGE;
    public static final ForgeConfigSpec.IntValue KILL_MARKER_CHARGE_TICKS;
    public static final ForgeConfigSpec.IntValue KILL_MARKER_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue KILL_MARKER_DIAMONDS;
    public static final ForgeConfigSpec.IntValue KILL_MARKER_DIAMOND_BLOCKS;
    public static final ForgeConfigSpec.IntValue KILL_MARKER_MIN_SIMULATION_CHUNKS;
    public static final ForgeConfigSpec.IntValue KILL_MARKER_MAX_QUEUED;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("fall");
        MANNED_FALL_REALISTIC = builder
            .comment("拟真下坠：约 10 分钟；关闭则按 mannedCruiseSpeed 缩短(Realistic fall takes about ten minutes)",
                    "现实返回舱没有降落伞阶段的模拟，只是把下落速度放慢(Only the descent speed changes)")
            .define("mannedFallRealistic", false);
    MANNED_SPAWN_HEIGHT = builder
            .comment("载人返回舱起始高度，约 500km 近地轨道(Start height of the manned capsule, about 500 km)")
            .defineInRange("mannedSpawnHeight", 499697, 100, 8_000_000);
    MANNED_CRUISE_SPEED = builder
            .comment("拟真关闭时的巡航速度，约 3 分钟落地(Cruise speed when realistic fall is off, about three minutes)")
            .defineInRange("mannedCruiseSpeed", 160.0D, 8.0D, 2048.0D);
    MANNED_REALISTIC_SPEED = builder
            .comment("拟真开启时的巡航速度，约 10 分钟落地(Cruise speed when realistic fall is on, about ten minutes)")
            .defineInRange("mannedRealisticSpeed", 45.0D, 4.0D, 2048.0D);
    MANNED_HORIZONTAL_DISTANCE = builder
            .comment("拟真档的横向进场距离；0 表示纯垂直(Realistic lateral approach distance, 0 for a vertical fall)")
            .defineInRange("mannedHorizontalDistance", 2048, 0, 8192);
    MANNED_HORIZONTAL_START = builder
            .comment("开始横向移动的高度(Altitude where the lateral approach starts)")
            .defineInRange("mannedHorizontalStart", 10000, 100, 120000);
    SPAWN_HEIGHT = builder
                .comment("掉落高度 1740 格，落地约需 20 秒(Drop height 1740, about 20 seconds)" +
                        "a_air = 2s / t² = (2 × 1740) / 20² = 8.7 m/s², 但实际上是1470，我算错了")
                .defineInRange("spawnHeight", 1470, 20, 4000);
        GRAVITY = builder
                .comment("加速度(+speed)")
                .defineInRange("gravity", 0.08D, 0.0D, 1.0D);
        TERMINAL_VELOCITY = builder
                .comment("终端速度(Max=(+speed+0))")
                .defineInRange("terminalVelocity/终端速度", 3.92D, 0.1D, 20.0D);
        builder.pop();

        builder.push("behaviour");
        BREAK_BLOCKS = builder
                .comment("破坏方块(break Blocks)")
                .define("breakBlocks", true);
        HEALTH_GUARD = builder
                .comment("实体生命值为 0 或 NaN 时强制调用 die()(Force die() on a living entity whose health ended up zero or NaN; some modpacks leave such bodies standing)",
                        "只作为整合包兼容的兜底，默认开启(Only a compatibility fallback for packs; on by default)")
                .define("killInvalidHealth", true);
        SMASH_INNER_RADIUS = builder
                .comment("通道破坏范围(inner radius)")
                .defineInRange("innerRadius", 1, 0, 8);
        SMASH_OUTER_RADIUS = builder
                .comment("额外破坏范围(outer radius)")
                .defineInRange("额外破坏范围", 2, 0, 12);
        SMASH_OUTER_CHANCE = builder
                .comment("额外破坏范围内每 tick 的破坏概率(outer ring break chance per tick)")
                .defineInRange("最小破坏", 0.35D, 0.0D, 1.0D);
        SMASH_COLUMN_PASSES = builder
                .comment("单列方块每 tick 最多可被破坏的方块数(How many blocks a single smashed column may break per tick)")
                .defineInRange("columnPasses", 5, 1, 32);
        SMASH_SOUND_INTERVAL = builder
                .comment("下落时破坏方块音效的最小间隔 tick 数(Minimum ticks between block-breaking sounds while falling)")
                .defineInRange("soundIntervalTicks", 4, 1, 200);
        SMASH_SOUND_VOLUME = builder
                .comment("下落时破坏方块音效的音量(Volume of the block-breaking sound while falling)")
                .defineInRange("soundVolume", 1.0D, 0.0D, 16.0D);
        SMASH_SOUND_PITCH = builder
                .comment("下落时破坏方块音效的音调(Pitch of the block-breaking sound while falling)")
                .defineInRange("soundPitch", 0.8D, 0.1D, 2.0D);
        builder.pop();

        builder.push("impact");
        IMPACT_HALF_SIZE = builder
                .comment("真伤边长，4.0 表示 8×8×8 的立方体(Half extent of the cubic true-damage area on every axis. 4.0 means an 8x8x8 cube)")
                .defineInRange("impactHalfSize", 4.0D, 0.5D, 64.0D);
        TRUE_DAMAGE = builder
                .comment("终结真伤(Finishing true damage dealt to every still standing living entity in the impact area)")
                .defineInRange("trueDamage", 5000.0D, 0.0D, 1_000_000.0D);
        DAMAGE_OWNER = builder
                .comment("使用召唤器的玩家是否也会受到伤害(Whether the player who used the caller is damaged too)")
                .define("damageOwner", true);
        CLEAR_LANDING_AREA = builder
                .comment("放置返回舱方块前，清空锚点上方空间(Clear a 3x3x2 space above the anchor before placing the capsule block)")
                .define("clearLandingArea", true);
        CAPSULE_BREAK_BLOCKS = builder
                .define("capsuleBreakBlocks", true);
        LANDING_DROP_CHANCE = builder
                .comment("弹坑周围的地表方块被破坏并掉落物品的概率(Probability that a surface block around the crater is broken and drops its items)")
                .defineInRange("landingDropChance", 0.4D, 0.0D, 1.0D);
        LANDING_FIRE_CHANCE = builder
                .defineInRange("landingFireChance", 0.35D, 0.0D, 1.0D);
        LANDING_FIRE_RADIUS = builder
                .defineInRange("landingFireRadius", 4, 0, 16);
        SCREEN_SHAKE = builder
                .comment("镜头震动(Send a camera shake to players near the impact)")
                .define("screenShake", true);
        SHAKE_RADIUS = builder
                .comment("镜头震动范围(Players within this distance of the impact receive the camera shake)")
                .defineInRange("shakeRadius", 64.0D, 4.0D, 256.0D);
        SHAKE_START_DISTANCE = builder
                .comment("返回舱震动范围(Start shaking only when the capsule is this close to the target)")
                .defineInRange("shakeStartDistance", 60.0D, 0.0D, 512.0D);
        IMPACT_SOUND_VOLUME = builder
                .comment("落地爆炸音效的音量(Volume of the landing explosion sound)")
                .defineInRange("impactSoundVolume", 5.0D, 0.0D, 16.0D);
        IMPACT_SOUND_PITCH = builder
                .comment("落地爆炸音效的音调(Pitch of the landing explosion sound)")
                .defineInRange("impactSoundPitch", 0.55D, 0.1D, 2.0D);
        IMPACT_THUD_VOLUME = builder
                .comment("落地闷响/内置音效包的音量(Volume of the landing anvil thud / bundled sound pack impact)",
                        "默认 4.0，让铁砧/泰坦陨落不至于被 5.0 的爆炸声完全盖住(Default 4.0 so the anvil/titan-fall thud is not buried by the 5.0 explosion)")
                .defineInRange("impactThudVolume", 4.0D, 0.0D, 16.0D);
        IMPACT_THUD_PITCH = builder
                .comment("落地闷响的播放音调，默认 0.55 让原版铁砧听起来更慢更沉(Pitch used when playing the landing thud; 0.55 slows the vanilla anvil down)",
                        "使用泰坦陨落音效包时文件本身已有音高，可以改回 1.0(When using the titan-fall pack the file already has its own pitch, so raise this back to 1.0)")
                .defineInRange("impactThudPitch", 0.55D, 0.1D, 2.0D);
        builder.pop();

        builder.push("shock");
        SHOCK_STUN_TICKS = builder
                .comment("被震慑的生物在终结一击落下前保持眩晕的时长(How long a shocked entity stays stunned before the finishing blow lands)")
                .defineInRange("stunTicks", 60, 0, 1200);
        SHOCK_DAMAGE = builder
                .comment("撞击造成的伤害(Damage applied by the impact itself, before the stun starts)")
                .defineInRange("impactDamage", 200.0D, 0.0D, 1_000_000.0D);
        SHOCK_TICK_DAMAGE = builder
                .comment("目标处于眩晕期间每秒造成的伤害(Damage applied once per second while a target is stunned)")
                .defineInRange("tickDamage", 1.0D, 0.0D, 1000.0D);
        SHOCK_OCCLUDED_MULTIPLIER = builder
                .comment("相对撞击点处于掩体之后的目标所受伤害的倍率(Damage multiplier for targets that are behind cover relative to the impact)")
                .defineInRange("occludedMultiplier", 0.25D, 0.01D, 1.0D);
        SHOCK_REFRESH_TICKS = builder
                .comment("目标眩晕期间刷新震慑效果的间隔 tick 数(How often the stun effect is refreshed while a target is stunned)")
                .defineInRange("refreshTicks", 3, 1, 40);
        builder.pop();

        builder.push("transport");
        TRANSPORT_MAX_PASSENGERS = builder
                .comment("运输舱内最多可乘坐的玩家数量(How many players can ride inside a transport capsule)")
                .defineInRange("maxPassengers", 3, 1, 8);
        CONTAINER_SLOTS = builder
                .comment("返回舱的存储槽位数，会向下取整为 9 的倍数，菜单会自动分页(Storage slots of a capsule. Rounded down to a multiple of 9; the menu pages automatically)")
                .defineInRange("containerSlots", 324, 9, 540);
        DESIGNATOR_MAX_CHARGE = builder
                .comment("运输指示器的最大充能(Maximum charge of the transport designator)",
                        "由于物品注册阶段配置值尚未加载，充能保存在物品 NBT 中(Charge is stored in item NBT because config values are not loaded yet during item registration)",
                        "仍携带旧耐久值的物品会在使用时自动迁移(items that still carry the old durability value migrate on use)")
                .defineInRange("designatorMaxCharge", 1000, 1, 100_000);
        DESIGNATOR_CONFIRM_TICKS = builder
                .comment("第二次使用指示器即可发射返回舱的时间窗口(Time window in which a second use of the designator launches the capsule)")
                .defineInRange("confirmWindowTicks", 200, 1, 2400);
        DESIGNATOR_COOLDOWN_TICKS = builder
                .comment("发射或登舱后施加的冷却时间(Cooldown applied after a capsule is launched or boarded)")
                .defineInRange("useCooldownTicks", 200, 0, 2400);
        PLATFORM_SEARCH_RADIUS = builder
                .comment("在被点击方块周围搜索黑曜石发射平台的半径(Radius searched around the clicked block for an obsidian launch platform)")
                .defineInRange("platformSearchRadius", 2, 0, 8);
        MARKER_GRACE_TICKS = builder
                .comment("指示器标记消失后，运输标记继续存在的时长(How long a marker survives after its designator token disappears)")
                .defineInRange("markerGraceTicks", 6000, 20, 240_000);
        MARKER_CHUNK_RADIUS = builder
                .comment("围绕生效中的运输标记保持强制加载的区块半径(Chunk radius kept force-loaded around an active transport marker)")
                .defineInRange("markerChunkRadius", 1, 0, 4);
        builder.pop();

        builder.push("animation");
        CAPSULE_OPEN_TICKS = builder
                .comment("返回舱达到完全打开状态所需的 tick 数(Ticks the capsule takes to reach the fully open state)",
                        "服务端与客户端应使用相同的值，以保持舱盖动画同步(Server and client should use the same value to keep the lid animation in sync)")
                .defineInRange("openTicks", 43, 1, 200);
        CAPSULE_CLOSE_TICKS = builder
                .comment("返回舱达到完全关闭状态所需的 tick 数(Ticks the capsule takes to reach the fully closed state)")
                .defineInRange("closeTicks", 20, 1, 200);
        LID_OPEN_TICKS = builder
                .comment("开启动画中用于移动舱盖的时长(Portion of the opening animation spent moving the lid)")
                .defineInRange("lidOpenTicks", 15, 1, 200);
        LID_CLOSE_TICKS = builder
                .comment("关闭动画中用于移动舱盖的时长(Portion of the closing animation spent moving the lid)")
                .defineInRange("lidCloseTicks", 10, 1, 200);
        builder.pop();

        builder.push("visual");
        PARTICLE_SCALE = builder
                .comment("服务端所有撞击与尾迹粒子爆发的倍率(Multiplier for every server side impact and trail particle burst)")
                .defineInRange("particleScale", 1.0D, 0.0D, 4.0D);
        TRAIL_SCALE = builder
                .comment("客户端下落返回舱烟雾尾迹的倍率(Multiplier for the smoke trail emitted by falling capsules on the client)")
                .defineInRange("trailScale", 1.0D, 0.0D, 4.0D);
        SHAKE_AMPLITUDE = builder
                .comment("镜头震动的最大幅度，单位为度(Maximum camera shake amplitude in degrees)")
                .defineInRange("shakeAmplitude", 6.0D, 0.0D, 30.0D);
        SHAKE_SOUND_RANGE = builder
                .comment("只有该距离内的爆炸音效才会额外触发镜头震动(Only explosion sounds within this distance trigger an additional camera shake)")
                .defineInRange("shakeSoundRange", 64.0D, 0.0D, 256.0D);
        MODEL_YAW_OFFSET = builder
                .comment("模型朝向偏移，单位度(Model yaw offset in degrees)",
                        "默认 180 让舱门朝向玩家；如果模型正面其实是反的，改成 0(180 makes the door face the player; set 0 if the model's front is reversed)")
                .defineInRange("modelYawOffset", 180.0D, -360.0D, 360.0D);
        builder.pop();

        builder.push("loot");
        ADD_MOD_ITEMS = builder
                .comment("从已安装的其他模组中随机抽取物品，加入补给舱战利品表(Add a random draw of items from other installed mods to the supply capsule loot table)")
                .define("addModItems", true);
        MOD_ITEM_ROLLS = builder
                .comment("每次开舱从模组物品池抽取多少次，0 表示关闭(How many draws a capsule makes from the mod item pool; 0 disables it)",
                        "原版战利品表约有 33-44 次抽取，所以这个值太小的话舱里会几乎全是原版物品(The vanilla tables roll 33-44 times, so a small value here leaves the capsule almost entirely vanilla)")
                .defineInRange("modItemRolls", 32, 0, 256);
        VANILLA_LOOT_STACKS = builder
                .comment("每个舱最多保留多少堆原版战利品，超出部分丢弃，稀有度高的优先保留(How many vanilla loot stacks a capsule keeps; the rarest rolls are kept first)",
                        "原版表有 33-44 次抽取，不限制的话会压过模组物品(The vanilla tables roll 33-44 stacks, which otherwise drown out the mod items)",
                        "0 表示不限制(0 keeps every roll)")
                .defineInRange("vanillaLootStacks", 20, 0, 64);
        UNIQUE_WEAPON_CHANCE = builder
                .comment("三阶舱携带一把无合成表独特武器的概率，每个舱最多一把(Chance that a tier III capsule carries one uncraftable unique weapon; at most one per capsule)",
                        "目前支持 T.E.B.L. Slashblades (tcpl)，装了才会生效(Currently supports T.E.B.L. Slashblades (tcpl); only active when that mod is installed)")
                .defineInRange("uniqueWeaponChance", 0.5D, 0.0D, 1.0D);
        BLADE_NAMESPACES = builder
                .comment("哪些模组命名空间的武器算作拔刀剑，按攻击伤害分阶级(Bleeding-edge: namespaces whose weapons count as slashblades, tiered by attack damage)",
                        "伤害 < 7 进一阶，7-19 进二阶，> 19 进三阶；每个舱最多一把(Damage < 7 goes to tier I, 7-19 to tier II, > 19 to tier III; at most one per capsule)")
                .defineListAllowEmpty("bladeNamespaces",
                        List.of("slashblade", "slashblade_addon", "tcpl"),
                        value -> value instanceof String);
        FILL_TARGET = builder
                .comment("开舱后目标填充率，0-1；不足的部分用同一阶的模组物品补满(Target fill ratio after opening; remaining slots are topped up with mod items of the same tier)",
                        "1.0 会把 324 格全部塞满，0 则不补(1.0 fills all 324 slots, 0 disables the top-up)")
                .defineInRange("fillTarget", 0.6D, 0.0D, 1.0D);
        MOD_LOOT_EXCLUDED_NAMESPACES = builder
                .comment("排除在随机模组物品池之外的模组命名空间，不区分大小写的子串匹配(Mod namespaces excluded from the random mod item pool, case-insensitive substring match)",
                        "默认排除枪械模组，因为它们的枪、配件和弹药需要 NBT 数据才能运作(Gun mods are excluded by default because their guns, attachments and ammo need NBT data to work)",
                        "安装了 TaCZ 时，TaCZ 联动会自动用有效的枪械填充返回舱(the TaCZ integration already fills the capsule with valid guns when present)")
                .defineListAllowEmpty("excludedModNamespaces",
                        List.of("tacz", "lrtactical", "gunsmithlib",
                                "slashblade", "slashblade_addon", "tcpl"),
                        value -> value instanceof String);
        MOD_LOOT_EXCLUDED_ITEMS = builder
                .comment("按完整物品 ID 排除，例：\"srparasites:some_item\"(Exact item ids excluded from the random mod item pool)",
                        "模组刷怪蛋、NBT 驱动道具等自动过滤漏掉的物品可以写在这里(Use this for spawn eggs or NBT driven items that slip through the automatic filters)")
                .defineListAllowEmpty("excludedItems", List.of(), value -> value instanceof String);
        TACZ_MIN_GUNS = builder
                .comment("TaCZ 联动：单个返回舱内放置的最少枪械数量(TaCZ integration: minimum number of guns placed in one capsule)")
                .defineInRange("taczMinGuns", 1, 0, 32);
        TACZ_MAX_GUNS = builder
                .comment("TaCZ 联动：单个返回舱内放置的最多枪械数量(TaCZ integration: maximum number of guns placed in one capsule)")
                .defineInRange("taczMaxGuns", 3, 0, 32);
        TACZ_MIN_ATTACHMENTS = builder
                .comment("TaCZ 联动：每把枪最少附加的配件数量(TaCZ integration: minimum attachments added per gun)")
                .defineInRange("taczMinAttachments", 3, 0, 32);
        TACZ_MAX_ATTACHMENTS = builder
                .comment("TaCZ 联动：每把枪最多附加的配件数量(TaCZ integration: maximum attachments added per gun)")
                .defineInRange("taczMaxAttachments", 5, 0, 32);
        TACZ_AMMO_MULTIPLIER = builder
                .comment("TaCZ 联动：作用于每把枪弹药数量的倍率(TaCZ integration: multiplier applied to the ammo count of every gun)",
                        "1.0 保持各枪型默认值，即步枪 120-527 发、狙击枪 20-120 发(1.0 keeps the type specific defaults: a rifle gets 120-527 rounds, a sniper 20-120)")
                .defineInRange("taczAmmoMultiplier", 1.0D, 0.0D, 100.0D);
        builder.pop();

        builder.push("boom");
        BOOM_POWER = builder
                .comment("巡航弹爆炸威力(Explosion power of the cruise bomb)",
                        "默认配置不破坏地形，该数值只决定伤害范围(The default config keeps terrain intact, so this only decides the damage radius)")
                .defineInRange("power", 1.8D, 0.0D, 16.0D);
        BOOM_RADIUS_PER_POWER = builder
                .comment("每点威力换算的伤害半径，最终半径 = 威力 × 该值(Damage radius per point of power; final radius = power x this value)")
                .defineInRange("radiusPerPower", 2.0D, 0.5D, 16.0D);
        BOOM_DESTROY_TERRAIN = builder
                .comment("巡航弹是否破坏地形(Whether the cruise bomb destroys terrain)")
                .define("destroyTerrain", false);
        BOOM_DAMAGE = builder
                .comment("命中锚点二型时的爆炸伤害(Explosion damage when the bomb reaches the anchor)")
                .defineInRange("damage", 500.0D, 0.0D, 1_000_000.0D);
        BOOM_TRUE_DAMAGE = builder
                .comment("提前撞到方块或实体时的真伤(True damage when the bomb hits a block or entity before the anchor)")
                .defineInRange("trueDamage", 500.0D, 0.0D, 1_000_000.0D);
        BOOM_SHOCK_TICKS = builder
                .comment("爆炸附加的震撼时长 tick(Shock duration applied by the explosion in ticks)")
                .defineInRange("shockTicks", 160, 0, 1200);
        BOOM_RAYS = builder
                .comment("每个目标用于计算减伤的射线数量(Rays cast at every target to compute the damage reduction)")
                .defineInRange("damageRays", 30, 1, 128);
        BOOM_MIN_DAMAGE_MULTIPLIER = builder
                .comment("全部射线被阻挡时保留的伤害倍率(Damage multiplier kept when every ray is blocked)")
                .defineInRange("minDamageMultiplier", 0.1D, 0.0D, 1.0D);
        BOOM_INCIDENCE_DEGREES = builder
                .comment("第一枚巡航弹的入射角，后续巡航弹会自动错开，单位为度，0 为水平、90 为垂直(Incidence angle of the first bomb in degrees above the horizon; later bombs deliberately use different angles)")
                .defineInRange("incidenceDegrees", 45.0D, 5.0D, 85.0D);
        BOOM_SPAWN_MARGIN_CHUNKS = builder
                .comment("从服务器模拟距离边缘向内缩进多少区块生成，0 表示正好在边缘(Spawn this many chunks inside the server simulation distance edge; 0 means exactly on the edge)")
                .defineInRange("spawnMarginChunks", 1, 0, 8);
        BOOM_HURT_OWNER = builder
                .comment("使用者是否会被自己的巡航弹伤到(Whether the user can be hurt by their own cruise bomb)")
                .define("hurtOwner", false);
        BOOM_MAX_LIFETIME_TICKS = builder
                .comment("巡航弹最长飞行时间，超时自爆；默认 1200 tick = 1 分钟(Maximum flight time in ticks before the bomb self destructs; 1200 ticks = 1 minute)")
                .defineInRange("maxLifetimeTicks", 1200, 40, 12000);
        BOOM_SPAWN_HEIGHT = builder
                .comment("巡航弹发射点相对目标的垂直高度，越高到达越慢；默认 600(Bomb launch height above the target; higher means a longer descent; default 600)")
                .defineInRange("spawnHeight", 600, 100, 4096);
        BOOM_CHUNK_LOADING = builder
                .comment("PERF. 是否为飞行中的巡航弹强制加载它所在的区块(Whether a flying cruise bomb force loads its own chunk)",
                        "关掉它可以省下大量 TPS，代价是：弹丸飞出玩家模拟距离后会停在未加载的区块里，直到玩家靠近(Same trade off as Superb Warfare's projectile_chunk_loading: turning it off saves a lot of TPS, but a round that leaves the simulated area simply stops until a player comes near)",
                        "齐射规模大或安装了弹幕类模组时建议关闭(Recommended off for large volleys or bullet heavy modpacks)")
                .define("chunkLoading", true);
        BOOM_FIRE_CHANCE = builder
                .defineInRange("boomFireChance", 0.4D, 0.0D, 1.0D);
        BOOM_MODEL_SCALE = builder
                .comment("巡航弹模型缩放倍率；模型本体约 55 单位长，1.0 即约 3.4 格，默认 1.5 更有压迫感(Model scale multiplier of the cruise bomb; the body is about 55 units long, so 1.0 is roughly 3.4 blocks; 1.5 reads as more threatening)")
                .defineInRange("modelScale", 1.5D, 0.1D, 10.0D);
        BOOM_MODEL_BASE_PITCH = builder
                .comment("模型自身朝向的补偿角：模型默认朝下(-Y)时用 -90 把弹头转到 +Z；若模型在游戏里前后颠倒，改成 90(Base orientation of the model: -90 turns a nose-down (-Y) model to +Z; use 90 if the model flies backwards)")
                .defineInRange("modelBasePitch", -90.0D, -180.0D, 180.0D);
        BOOM_FLARE_SCALE = builder
                .comment("巡航弹十字星光晕大小倍率(Cross flare size multiplier; 3.0 default)")
                .defineInRange("flareScale", 3.0D, 0.1D, 16.0D);
        builder.pop();

        builder.push("vehicle_ammo");
        VEHICLE_AMMO_FREEZE = builder
                .comment("只对我们这边生效的修正：第三方载具模组（永无止境）的弹药飞入未加载区块时是否冻结，而不是替它强制加载区块",
                        "Third party vehicle mod ammunition freeze instead of force loading chunks when it flies into unloaded terrain",
                        "永无止界的 EntityUtil.keepChunkLoaded 会为每发弹药每两次 tick 各加两张半径 3（7x7 区块）的区域票据，",
                        "弹幕一密就是成百上千个区块，这是实测的 TPS 主因",
                        "The vehicle mod adds two radius 3 (7x7 chunk) region tickets per projectile every other tick, which is what eats TPS")
                .define("freezeInUnloadedChunks", true);
        VEHICLE_AMMO_KEEP_HOMING = builder
                .comment("已锁定目标的导弹保持原行为（继续加载区块），用于 PVP 超视距打击(Missiles with a locked entity keep loading chunks, for beyond visual range PvP)")
                .define("keepHomingMissilesLoaded", true);
        VEHICLE_AMMO_KEEP_LONG_RANGE = builder
                .comment("远程打击弹药保持原行为：目标点在超过 longRangeBlocks 之外、且没有锁定实体（默认包的东风等）(Long range strike munitions keep loading chunks: a preset point farther than longRangeBlocks away and no locked entity, e.g. the default pack's DF)")
                .define("keepLongRangeStrikesLoaded", true);
        VEHICLE_AMMO_LONG_RANGE_BLOCKS = builder
                .comment("判定为远程打击的距离（格）(Distance in blocks that counts as a long range strike)")
                .defineInRange("longRangeBlocks", 160, 32, 2048);
        VEHICLE_AMMO_TTL_TICKS = builder
                .comment("被冻结的弹药存活上限（tick），0 表示永不清理(Ticks a frozen round may sit in the world before it is removed; 0 keeps it forever)",
                        "冻结的弹药不 tick，所以它自己的寿命计时通常不会走；这一项是兜底，按有效期做低频清理(Frozen rounds do not tick, so their own lifetime rarely expires; this is the backstop and is swept at a low frequency)",
                        "默认 12000 tick = 10 分钟(Default 12000 ticks = 10 minutes)")
                .defineInRange("frozenTtlTicks", 12000, 0, 96000);
        builder.pop();

        builder.push("kill_marker");        KILL_MARKER_RANGE = builder
                .comment("击杀指示器的准星标记距离，会被服务器模拟距离截断，保证目标在弹体可 tick 的范围内(Crosshair marking range of the kill indicator; clamped to the server simulation distance so the target is inside the ticking area)")
                .defineInRange("range", 128, 8, 512);
        KILL_MARKER_CHARGE_TICKS = builder
                .defineInRange("chargeTicks", 20, 1, 200);
        KILL_MARKER_INTERVAL_TICKS = builder
                .defineInRange("intervalTicks", 10, 1, 400);
        KILL_MARKER_DIAMONDS = builder
                .comment("每枚巡航弹消耗的钻石数量(Diamonds consumed by one bomb)")
                .defineInRange("diamonds", 9, 1, 64);
        KILL_MARKER_DIAMOND_BLOCKS = builder
                .comment("钻石不足时改为消耗的钻石块数量(Diamond blocks consumed instead when there are not enough diamonds)")
                .defineInRange("diamondBlocks", 1, 1, 9);
        KILL_MARKER_MIN_SIMULATION_CHUNKS = builder
                .defineInRange("minSimulationChunks", 8, 1, 32);
        KILL_MARKER_MAX_QUEUED = builder
                .defineInRange("maxQueued", 50, 1, 256);
        builder.pop();

        builder.push("equipment");
        EQUIPMENT_SET_ENABLED = builder
                .comment("基础人形套装是否生效：整套穿戴时，直接写血的伤害会被转换成一次带攻击者的物理重击(Direct health writes are converted into one physical hit with the attacker recorded while the full base humanoid set is worn)")
                .define("setEnabled", true);
        EQUIPMENT_HIT_CAP_FRACTION = builder
                .comment("每次转换伤害的上限（最大生命值比例），对齐基础人形的 20%(Per hit ceiling on converted damage as a share of max health, default five percent)")
                .defineInRange("hitCapFraction", 0.05D, 0.0D, 1.0D);
        EQUIPMENT_LOW_DAMAGE_IMMUNITY = builder
                .comment("低于该值（绝对值）的转换伤害直接忽略，对齐基础人形的低伤免疫(Converted hits below this absolute value are ignored, matching the base humanoid's low damage immunity)")
                .defineInRange("lowDamageImmunity", 1.5D, 0.0D, 20.0D);
        EQUIPMENT_DAMAGE_REDUCTION = builder
                .comment("转换伤害的等效减伤比例，对齐基础人形的 50%(Equivalent damage reduction applied to converted hits, matching the base humanoid's fifty percent)")
                .defineInRange("damageReduction", 0.5D, 0.0D, 0.9D);
        EQUIPMENT_SECOND_CAP_FRACTION = builder
                .comment("每秒最多承受的转换伤害（最大生命值比例），对齐基础人形的 25%/s(Cap on converted damage per second as a share of max health, matching the base humanoid's twenty five percent per second)")
                .defineInRange("secondCapFraction", 0.10D, 0.0D, 2.0D);
        ANCHOR_GUARD_ENABLED = builder
            .comment("标准存储锚点是否提供强制位移反制(Whether the storage anchor blocks forced displacement)")
            .define("anchorGuardEnabled", true);
    ANCHOR_GUARD_WHITELIST = builder
            .comment("允许的位移物品，使用后短时间内不会被反制(Self displacement items that bypass the guard)")
            .defineListAllowEmpty("anchorGuardWhitelist",
                    List.of("minecraft:ender_pearl", "minecraft:chorus_fruit"),
                    entry -> entry instanceof String);
    ANCHOR_GUARD_BOSSES = builder
            .comment("Boss only has its displacement blocked; no drain, shock or freeze")
            .defineListAllowEmpty("anchorGuardBosses",
                    List.of("minecraft:wither", "minecraft:ender_dragon", "minecraft:warden"),
                    entry -> entry instanceof String);
    ANCHOR_GUARD_REFLECT = builder
            .comment("Reflect damage that broke through the guard, same source, no hurt animation")
            .define("anchorGuardReflect", true);
    EQUIPMENT_EFFECT_IMMUNITY = builder
                .comment("整套穿戴时免疫负面效果，以及伪装成正面效果、实际有害的转换类效果(Wearing the full set immunises debuffs, and conversion style effects that present as beneficial)")
                .define("effectImmunity", true);
        EQUIPMENT_COTH_IMMUNITY = builder
                .comment("整套穿戴时写入寄生虫模组的寄巢之唤免疫标记，从源头阻止生物转换(Writes the parasite mod's Call of the Hive immunity tag while the full set is worn, stopping conversion at its source)")
                .define("cothImmunity", true);
        EQUIPMENT_BLOCKED_EFFECTS = builder
                .comment("无论是否被判定为正面效果都一律免疫的效果命名空间或完整 id，用于伪装成正面 buff 的负面效果(Effect namespaces or full ids always blocked, for debuffs disguised as buffs)",
                        "默认封锁寄生虫模组：转换生物的效果多在其命名空间下(Defaults to the parasite mod, whose conversion effects live under its namespace)")
                .defineList("blockedEffects", List.of(
                        "srparasites:coth",
                        "srparasites:viral",
                        "srparasites:conta",
                        "srparasites:distorted_enlightenment",
                        "srparasites:the_sign"),
                entry -> entry instanceof String);
        EQUIPMENT_REGEN_ENABLED = builder
                .comment("手持列装剑（主手或副手）且整套穿戴时给予生命恢复(Grants regeneration while holding the standard issue blade in either hand and wearing the full set)")
                .define("swordRegeneration", true);
        EQUIPMENT_REGEN_AMPLIFIER = builder
                .comment("手持列装剑时生命恢复的等级：2 = 生命恢复 III(Regeneration amplifier while holding the blade; 2 = Regeneration III)")
                .defineInRange("regenerationAmplifier", 2, 0, 4);
        EQUIPMENT_BLADE_ENABLED = builder
                .comment("列装剑是否附加最大生命值百分比伤害(Whether the standard issue blade strips a percentage of max health on hit)")
                .define("bladePercentDamage", true);
        EQUIPMENT_BLADE_PERCENT = builder
                .comment("列装剑每次命中的最大生命值扣除百分比：玩家按百分比真伤结算，其余生物永久扣减最大生命值；一命中一次(Percentage of max health stripped per blade hit; players take percentage true damage, everything else loses max health permanently; once per swing)")
                .defineInRange("bladePercent", 5.0D, 0.0D, 50.0D);
        EQUIPMENT_BLADE_MAX_HEALTH_CAP = builder
                .comment("列装剑对同一目标最多扣除的最大生命值百分比，达到上限后不再扣除，也不会转为真伤(Maximum share of max health the blade may strip from one target; past the cap it stops entirely, with no true damage conversion)")
                .defineInRange("bladeMaxHealthCap", 20.0D, 0.0D, 90.0D);
        EQUIPMENT_SUPPORT_HEAL_PERCENT = builder
                .comment("潜行攻击自己宠物时，改用玩家最大生命值的这个百分比治疗宠物；按比例而不是固定值，因为整合包里玩家血量可能远超 20(Sneak attacking your own pet heals it instead, for this share of the player's max health; a percentage rather than a flat value, since packs hand players pools far above twenty)")
                .defineInRange("petSupportHealPercent", 5.0D, 0.0D, 50.0D);
        EQUIPMENT_ARMOR_TOUGHNESS = builder
                .comment("列装套每件护甲韧性；四件合计为这个值的四倍，用于对抗高额单次伤害时保持减伤不失效(Per piece armour toughness; a full set is four times this, keeping damage reduction from collapsing against large single hits)")
                .defineInRange("armorToughness", 10.0D, 0.0D, 40.0D);
        EQUIPMENT_BLADE_PLAYER_FLOOR = builder
                .comment("潜行给宠物换血时，玩家最低保留的生命值比例；扣到下限后换血停止(The player never pays health below this share of max health when trading health into a pet)")
                .defineInRange("bladePlayerHealthFloor", 1.0D, 0.0D, 50.0D);
        EQUIPMENT_HEALTH_BONUS = builder
                .comment("整套穿戴时追加的最大生命值，默认 180 使其与基础人形同为 200(Max health added while the full set is worn)")
                .defineInRange("healthBonus", 180.0D, 0.0D, 2000.0D);
        EQUIPMENT_HEALTH_REGEN = builder
                .comment("整套穿戴时每秒回复的生命值(Health per second restored while the full set is worn)")
                .defineInRange("healthRegen", 5.0D, 0.0D, 100.0D);
        EQUIPMENT_GUARD_ENABLED = builder
                .comment("整套穿戴或手持列装剑时，受击有概率完全格挡并短暂免疫(Whether the set or blade may shrug off a hit and stay immune briefly)")
                .define("guardEnabled", true);
        EQUIPMENT_GUARD_CHANCE = builder
                .comment("触发完全格挡的概率(Chance to fully shrug off an incoming hit)")
                .defineInRange("guardChance", 0.30D, 0.0D, 1.0D);
        EQUIPMENT_GUARD_TICKS = builder
                .comment("格挡触发后的免疫时长(Ticks of immunity after a successful guard)")
                .defineInRange("guardTicks", 60, 0, 600);
        builder.pop();

        builder.push("humanoid");
        HUMANOID_SEAL_SHARE = builder
                .comment("基础人形每次命中封锁的最大生命值比例(Share of max health the base humanoid seals per hit)")
                .defineInRange("sealShare", 0.03D, 0.0D, 0.5D);
        HUMANOID_HIT_CAP_FRACTION = builder
                .comment("基础人形单次承伤上限（最大生命值比例）(Per hit damage ceiling of the base humanoid as a share of max health)")
                .defineInRange("hitCapFraction", 0.20D, 0.0D, 1.0D);
        HUMANOID_SECOND_CAP_FRACTION = builder
                .comment("基础人形每秒承伤上限（最大生命值比例）(Per second damage ceiling of the base humanoid as a share of max health)")
                .defineInRange("secondCapFraction", 0.25D, 0.0D, 2.0D);
        builder.pop();

        SPEC = builder.build();
    }

    public static int containerSlots() {
        int requested = CONTAINER_SLOTS.get();
        return Math.max(9, requested - requested % 9);
    }

    public static double boomRadius() {
        return BOOM_POWER.get() * BOOM_RADIUS_PER_POWER.get();
    }

    public static int spawnHeight() {
        return SPAWN_HEIGHT.get();
    }

    public static int mannedSpawnHeight() {
        return MANNED_SPAWN_HEIGHT.get();
    }

    public static boolean mannedFallRealistic() {
        return MANNED_FALL_REALISTIC.get();
    }

    public static boolean anchorGuardEnabled() {
        return ANCHOR_GUARD_ENABLED.get();
    }

    public static boolean anchorGuardBoss(String entityId) {
        for (String entry : ANCHOR_GUARD_BOSSES.get()) {
            if (entry != null && entry.equals(entityId)) {
                return true;
            }
        }
        return false;
    }

    public static boolean anchorGuardReflect() {
        return ANCHOR_GUARD_REFLECT.get();
    }

    public static boolean anchorGuardWhitelisted(String itemId) {
        for (String entry : ANCHOR_GUARD_WHITELIST.get()) {
            if (entry != null && entry.equals(itemId)) {
                return true;
            }
        }
        return false;
    }

    public static double mannedCruiseSpeed() {
        return mannedFallRealistic() ? MANNED_REALISTIC_SPEED.get() : MANNED_CRUISE_SPEED.get();
    }

    public static int mannedHorizontalDistance() {
        return mannedFallRealistic() ? MANNED_HORIZONTAL_DISTANCE.get() : 0;
    }

    public static int mannedHorizontalStart() {
        return MANNED_HORIZONTAL_START.get();
    }

    public static int designatorMaxCharge() {
        return DESIGNATOR_MAX_CHARGE.get();
    }

    private Config() {
    }
}
