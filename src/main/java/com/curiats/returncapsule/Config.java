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

    public static final ForgeConfigSpec.BooleanValue ADD_MOD_ITEMS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> MOD_LOOT_EXCLUDED_NAMESPACES;
    public static final ForgeConfigSpec.IntValue TACZ_MIN_GUNS;
    public static final ForgeConfigSpec.IntValue TACZ_MAX_GUNS;
    public static final ForgeConfigSpec.IntValue TACZ_MIN_ATTACHMENTS;
    public static final ForgeConfigSpec.IntValue TACZ_MAX_ATTACHMENTS;
    public static final ForgeConfigSpec.DoubleValue TACZ_AMMO_MULTIPLIER;

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
        builder.pop();

        builder.push("loot");
        ADD_MOD_ITEMS = builder
                .comment("从已安装的其他模组中随机抽取物品，加入补给舱战利品表(Add a random draw of items from other installed mods to the supply capsule loot table)")
                .define("addModItems", true);
        MOD_LOOT_EXCLUDED_NAMESPACES = builder
                .comment("排除在随机模组物品池之外的模组命名空间，不区分大小写的子串匹配(Mod namespaces excluded from the random mod item pool, case-insensitive substring match)",
                        "默认排除枪械模组，因为它们的枪、配件和弹药需要 NBT 数据才能运作(Gun mods are excluded by default because their guns, attachments and ammo need NBT data to work)",
                        "安装了 TaCZ 时，TaCZ 联动会自动用有效的枪械填充返回舱(the TaCZ integration already fills the capsule with valid guns when present)")
                .defineListAllowEmpty("excludedModNamespaces",
                        List.of("tacz", "lrtactical", "gunsmithlib"),
                        value -> value instanceof String);
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

        SPEC = builder.build();
    }

    public static int containerSlots() {
        int requested = CONTAINER_SLOTS.get();
        return Math.max(9, requested - requested % 9);
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
