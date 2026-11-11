package com.curiats.returncapsule;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public final class Config {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue SPAWN_HEIGHT;
    public static final ForgeConfigSpec.DoubleValue GRAVITY;
    public static final ForgeConfigSpec.DoubleValue TERMINAL_VELOCITY;

    public static final ForgeConfigSpec.BooleanValue BREAK_BLOCKS;
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
    public static final ForgeConfigSpec.DoubleValue LANDING_DROP_CHANCE;
    public static final ForgeConfigSpec.BooleanValue SCREEN_SHAKE;
    public static final ForgeConfigSpec.DoubleValue SHAKE_RADIUS;
    public static final ForgeConfigSpec.DoubleValue SHAKE_START_DISTANCE;
    public static final ForgeConfigSpec.DoubleValue IMPACT_SOUND_VOLUME;
    public static final ForgeConfigSpec.DoubleValue IMPACT_SOUND_PITCH;
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
    public static final ForgeConfigSpec.DoubleValue BOOM_MODEL_SCALE;
    public static final ForgeConfigSpec.DoubleValue BOOM_MODEL_BASE_PITCH;

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
        LANDING_DROP_CHANCE = builder
                .comment("弹坑周围的地表方块被破坏并掉落物品的概率(Probability that a surface block around the crater is broken and drops its items)")
                .defineInRange("landingDropChance", 0.4D, 0.0D, 1.0D);
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
        IMPACT_THUD_PITCH = builder
                .comment("落地闷响的播放音调，默认 1.0(Pitch used when playing the landing thud; default 1.0)",
                        "默认铁砧的慢放效果由 sounds.json 里的 pitch 0.45 提供，这里保持 1.0 即可(The slowed anvil is done by pitch 0.45 in sounds.json, so keep this at 1.0)")
                .defineInRange("impactThudPitch", 1.0D, 0.1D, 2.0D);
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
                .defineInRange("fillTarget", 0.75D, 0.0D, 1.0D);
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
        BOOM_MODEL_SCALE = builder
                .comment("巡航弹模型缩放倍率；模型本体约 55 单位长，1.0 即约 3.4 格(Model scale multiplier of the cruise bomb; the body is about 55 units long, so 1.0 is roughly 3.4 blocks)")
                .defineInRange("modelScale", 1.0D, 0.1D, 10.0D);
        BOOM_MODEL_BASE_PITCH = builder
                .comment("模型自身朝向的补偿角：模型默认朝下(-Y)时用 -90 把弹头转到 +Z；若模型在游戏里前后颠倒，改成 90(Base orientation of the model: -90 turns a nose-down (-Y) model to +Z; use 90 if the model flies backwards)")
                .defineInRange("modelBasePitch", -90.0D, -180.0D, 180.0D);
        builder.pop();

        builder.push("kill_marker");
        KILL_MARKER_RANGE = builder
                .comment("击杀指示器的准星标记距离，会被服务器模拟距离截断，保证目标在弹体可 tick 的范围内(Crosshair marking range of the kill indicator; clamped to the server simulation distance so the target is inside the ticking area)")
                .defineInRange("range", 128, 8, 512);
        KILL_MARKER_CHARGE_TICKS = builder
                .comment("长按多少 tick 后开始标记，默认 20 tick = 1 秒(Ticks the indicator must be held before it starts marking; 20 ticks = 1 second)")
                .defineInRange("chargeTicks", 20, 1, 200);
        KILL_MARKER_INTERVAL_TICKS = builder
                .comment("标记期间每隔多少 tick 发射一枚巡航弹，默认 20 tick = 1 秒(One bomb per this many ticks while marking; 20 ticks = 1 second)")
                .defineInRange("intervalTicks", 20, 1, 400);
        KILL_MARKER_DIAMONDS = builder
                .comment("每枚巡航弹消耗的钻石数量(Diamonds consumed by one bomb)")
                .defineInRange("diamonds", 9, 1, 64);
        KILL_MARKER_DIAMOND_BLOCKS = builder
                .comment("钻石不足时改为消耗的钻石块数量(Diamond blocks consumed instead when there are not enough diamonds)")
                .defineInRange("diamondBlocks", 1, 1, 9);
        KILL_MARKER_MIN_SIMULATION_CHUNKS = builder
                .comment("服务器模拟距离低于该区块数时，每次发射后额外等待 (差值 × 20) tick 再发射(When the server simulation distance is below this many chunks, wait an extra 20 ticks per missing chunk before the next shot)")
                .defineInRange("minSimulationChunks", 8, 1, 32);
        KILL_MARKER_MAX_QUEUED = builder
                .comment("潜行时最多暂存多少枚巡航弹(Maximum number of bombs queued while sneaking)")
                .defineInRange("maxQueued", 50, 1, 256);
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

    public static int designatorMaxCharge() {
        return DESIGNATOR_MAX_CHARGE.get();
    }

    private Config() {
    }
}
