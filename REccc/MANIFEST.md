# REccc MANIFEST（verifier 产出，2026-11-23）

依据：`build/review/capsule-anchor.md` 的逻辑校验结论。所有草稿**未修改 `src/`**，镜像路径对应 `src/` 同路径文件；`REccc` 不参与构建。

## 验证记录

- 源码全量编译：`javac 17 -proc:none -encoding UTF-8 -nowarn -d build/verify-parkour/classes -cp <runServer classpath> @build/verify-parkour/sources.txt` → **EXIT=0**（仅 `PlayerEquipmentEffects` deprecation 提示）。
- 草稿编译：将下列 11 个 `.java` 草稿用同一 classpath（`build/verify-parkour/classes` + runServer classpath）编译到 `build/review/draft-classes` → **EXIT=0**。
- mixin 目标验证：`javap -p` 检查 mapped jar。`thunderHit(ServerLevel, LightningBolt)` 存在于 `Pig`/`MushroomCow`/`Villager`；`maybeConvertToZombified` 在 `Piglin`/`AbstractPiglin`/`Mob`/`LivingEntity` 及 `methods.csv` 中均不存在。
- blockstate 验证：`ModelBakery` 字节码确认 variant key 为“部分属性谓词匹配”（`manned_capsule.json` 的 `facing=*` 安全），未匹配状态 `logger.warn("... missing model for variant ...")` 并回退 missing model（不崩溃）。

## 合并顺序建议

`CapsulePartBlock` → `CapsuleStructure` → `CapsuleBlockEntity` → `CapsuleLanding`/`CapsulePlacer`（与实体同步）→ `AbstractCapsuleEntity`/`SupplyCapsuleEntity`/`TransportCapsuleEntity`/`MannedCapsuleEntity` → `AnchorField`/`AnchorFieldEvents`（两者必须同批，签名联动）→ 资源文件。合并后按 README 跑全量 `javac`，再启动一次客户端确认无 missing variant WARN。

---

## 1. `src/main/java/com/curiats/returncapsule/util/AnchorField.java`

- 改动：减速改为幂等——首次进入满仓场时把当时速度存进 `persistentData`，之后每次扫描都按“保存速度 × 0.1”设置，不再叠加乘 0.1；新增 `restore(Projectile)` 与 `restoreLeftovers(MinecraftServer)`，弹体离开所有满仓场/锚点失效后恢复原速度并清除 `TAG_SLOWED`；`damageMultiplier(Entity, DamageSource)` 增加伤害来源参数，排除 `EXPLOSION/PLAYER_EXPLOSION`（爆炸 AOE 会对每个受害者 ×10），倍率取保存因子的倒数并封顶 100；覆盖范围从 `<=` 改严格 `<`（恢复 64×64×16）。
- 为什么：报告 H1（×10 泄漏到 AOE/离场后仍生效）、H2（非幂等减速与固定补偿不匹配）、M7（边界多 1 格）。
- 风险：①恢复时用的是“进入场时的速度”近似，忽略场内期间重力/阻力的累积，出圈后箭矢可能略快/略慢（可接受，优于永久减速）；②`SLOWED_PROJECTILES` 是静态表，靠每 2 tick 的存活校验与离开校验清理，服务器关闭/维度卸载不会泄漏实体引用（只存 UUID + dimension），但同一 JVM 多服务器实例不适用（DSH 单机环境不存在）；③`damageMultiplier` 签名变了，调用点只有 `AnchorFieldEvents`（草稿已同步），外部无调用。
- 行为变更：**是**（这是修复本身）。

## 2. `src/main/java/com/curiats/returncapsule/event/AnchorFieldEvents.java`

- 改动：`onHurt` 把 `DamageSource` 一起传给 `damageMultiplier`；每 2 tick 先 `AnchorField.restoreLeftovers(server)`；`srpcothimmunity` 从“满仓时写一次、永久保留”改为“持有锚点玩家的范围内维护 + 离开所有场/锚点失效时清除”，用 `FLAGGED_PETS` 跟踪被标记宠物。
- 为什么：报告 H1、M6。
- 风险：①宠物免疫的触发条件由“满仓”变成“持有任意锚点”，与 `MobEffectEvent` 的拦截条件（held）一致；若产品上要求必须满仓，把 `refreshPetImmunity` 里的 `held` 改回 `full` 即可；②每 20 tick 多一次宠物遍历，服务器宠物多时开销小幅增加；③`FLAGGED_PETS` 的清理依赖实体存活校验，宠物跨维度/卸载时可能在下一轮被清掉标记（符合预期：离开场就失去免疫）。
- 行为变更：**是**。

## 3. `src/main/java/com/curiats/returncapsule/block/CapsuleStructure.java`

- 改动：`isMain()` 同时接受 `CAPSULE` 与 `MANNED_CAPSULE`；`findMain` 不再扫描邻域，改为从 `PART` 索引直接反解 `{dx,dy,dz}`（`CapsulePartBlock.offsetOf`）并校验主块类型（manned 部件必须对应 manned 主块，反之亦然）；新增 `kindOf(BlockState)` 与 `isIntact(Level,BlockPos,CapsuleKind)`；`placeParts` 的 base 改为 `kind == MANNED ? 18 : 0` 并对 `MAX_PART` 越界跳过；`crush` 按主块实际类型移除 5×5×3/3×3×2。
- 为什么：报告 H3（manned 主块不被识别）、H4（kind 缺失）、M10（扫描误判）。
- 风险：①旧存档中若出现“部件索引与主块不匹配”的异常残留（例如被其他模组/命令单独放置的 capsule_part），`findMain` 会返回 null 而不是像旧扫描那样碰巧找到主块——这是更严格的正确性取舍，正常游戏内不会发生；②`removeParts` 仍按 kind 的整块范围删除，两个胶囊重叠（manned 5×5 可覆盖相邻结构）时仍可能误删对方部件；报告里建议过按索引精确删除，但会改变 supply 旧行为，未在本草稿中做；③`crush` 的 kind 由主块类型决定，若以后统一主块为同一方块+property 需同步调整。
- 行为变更：**是**。

## 4. `src/main/java/com/curiats/returncapsule/block/CapsulePartBlock.java`

- 改动：`SUPPLY_PARTS`/`MAX_PART` 公开，`PART` 用 `MAX_PART` 生成（仍 0..92）；新增 `offsetOf(int part)` 供 `CapsuleStructure` 反解主块偏移。`shapeFor` 的几何逻辑未改。
- 为什么：M10/L2（单一常量来源 + 精确反解）。
- 风险：无逻辑变化；仅 API 可见性扩大。`MAX_PART` 推导式 `18 + 5*5*3 - 1 = 92`，若 manned 尺寸变化需同步 `CapsuleKind.MANNED`。
- 行为变更：**否**。

## 5. `src/main/java/com/curiats/returncapsule/block/CapsuleBlockEntity.java`

- 改动：`serverTick` 用 `manned()` 选 `CapsuleKind` 传给 `isIntact/placeParts`；manned 的 `renderBox` 放大到 `(-2,0,-2)..(3,4,3)`。
- 为什么：报告 H4（manned 外圈永不修复）、L4（渲染包围盒）。
- 风险：①manned 自修复会补回玩家/爆炸拆掉的外圈部件（与旧 supply 行为一致，40 tick 周期）；②渲染盒只影响视锥剔除，不影响逻辑；③`load()` 的 `slots` 计算未改（manned=6 与 `BoundLoadout.SIZE+1` 一致）。
- 行为变更：**是**。

## 6. `src/main/java/com/curiats/returncapsule/entity/fall/CapsuleLanding.java`

- 改动：点火中心从 `Vec3.atCenterOf(landing)`（y+0.5）恢复为 `y+1.0`（旧 supply/transport 的实现）；`land()` / `landWithLootTable()` 返回主块是否真的落下（`mainPlaced`）。
- 为什么：报告 M2（失败兜底不可达）、M4（火焰中心下移）。
- 风险：①返回值语义变化：Transport 忽略返回值（无影响），Manned 使用；②`mainPlaced` 以“方块类型等于该 kind 的主块”判断，若以后主块统一为同方块需调整；③`canPlace` 仍为未使用的公共 API（保留给调用方）。
- 行为变更：**是**（返回值 + 火焰位置）。

## 7. `src/main/java/com/curiats/returncapsule/entity/fall/CapsulePlacer.java`

- 改动：放置失败的兜底掉落按 kind 区分（manned 掉 `manned_capsule`，supply 掉 `capsule`）。
- 为什么：M2 的配套修复（否则 manned 放置失败会掉一个 supply 方块）。
- 风险：仅影响 `setBlock` 失败的极端情况；`withLootTable` 仍固定 supply 兜底（无 manned loot 调用方）。
- 行为变更：**是**（仅失败路径）。

## 8. `src/main/java/com/curiats/returncapsule/entity/AbstractCapsuleEntity.java`

- 改动：`onLanded` 模板恢复重构前两套实现的共同顺序：`impactEffects` → 采集 `shockExclusions` → `beforeShock` → 真伤（中心恢复 `y+1.0`）→ `afterShock` → `afterImpact` → `placeCargo`；新增可 override 的 `beforeShock`/`afterShock` 空钩子；`impactEffects` 的注释由“子类加粒子”改为“子类加自己的效果”。
- 为什么：报告 M3（Supply 世界破坏先于真伤）、M4（中心高度）、M5（Transport 弹出顺序）。
- 风险：①新增虚方法，现有 3 个子类已在草稿中同步；②基类 `impactEffects` 仍对所有 kind 执行 `ShakeBurst.around`——旧 Transport 只有 `forPassengers`，这是**未消除的残余差异**（见“未做项”）；③若未来子类在 `placeCargo` 依赖 `afterImpact` 的顺序，现模板保持 `afterImpact` 在 `placeCargo` 之前（与旧一致）。
- 行为变更：**是**。

## 9. `src/main/java/com/curiats/returncapsule/entity/SupplyCapsuleEntity.java`

- 改动：`impactEffects` 只保留“基类效果 + 冲击粒子”（粒子重新位于真伤之前）；新增 `afterShock`：爆炸云 + `LandingSite.scatterDrops` + `clearArea`（恢复旧顺序：真伤判定先于世界破坏）。
- 为什么：报告 M3。
- 风险：①粒子相对 shake 的先后与旧实现略有不同（旧：粒子→shake；新：shake→粒子），纯表现层；②`afterShock` 在真伤后执行，若真伤把玩家/实体推走，落点清场位置不变（与旧一致）。
- 行为变更：**是**（顺序恢复）。

## 10. `src/main/java/com/curiats/returncapsule/entity/TransportCapsuleEntity.java`

- 改动：乘客弹出从 `afterImpact` 移到 `beforeShock`（恢复旧顺序：采集排除集 → 弹出 → 真伤）；爆炸粒子移到 `afterShock`；`capsuleKind()` 保留 `SUPPLY` 但加注释说明原因与“新增 TRANSPORT 常量需要同步 CapsuleStructure 的 base 判定”。
- 为什么：报告 M5、L1。
- 风险：①`shockExclusions` 在 `beforeShock` 之前调用，乘客仍在骑乘，排除集正确；②全局 screen shake 仍是新增行为（旧 Transport 没有）——草稿未改，需要 lead 决定是否接受；③`interact`/菜单/座位偏移未改。
- 行为变更：**是**（顺序恢复）。

## 11. `src/main/java/com/curiats/returncapsule/entity/MannedCapsuleEntity.java`

- 改动：`spawnHumanoid` 改为在 footprint 之外的 ring=3/4/5 上寻找“脚下与头顶碰撞为空”的格子，兜底 `landing.above(MANNED.height())`（不再落到顶层实心 part 上）；`placeCargo` 依据 `CapsuleLanding.land` 的返回值决定是否掉落 cargo，放置失败时不生成人形。
- 为什么：报告 M1（人形生成在实心外壳内）、M2（cargo 丢失）。
- 风险：①搜索顺序固定，人形落点可能不是最优（但一定在胶囊外）；②`hasStandingRoom` 只检查碰撞形状，不考虑水/岩浆（可能生成在水里，建议后续加流体检查）；③兜底 `above(3)` 在极端地形（头顶被堵）仍可能冲突；④`TAG_OWNER` 与基类 `ownerUuid` 的重复保存未在草稿中清理（无功能影响，留待 lead 决定）。
- 行为变更：**是**。

## 12. `src/main/resources/assets/returncapsule/blockstates/capsule_part.json`

- 改动：改为无条件 `multipart` 的 `apply`（等价于“所有 part 状态都用 capsule 模型”），消除 `part=18..92` 共 75 个状态在客户端资源加载时的 `missing model for variant` WARN。
- 为什么：报告 M9。
- 风险：`CapsulePartBlock.getRenderShape` 为 `INVISIBLE`，模型不影响画面；若以后改成可见渲染，需要按状态逐一定义模型。
- 行为变更：**是**（仅日志/资源层）。

## 13. `src/main/resources/returncapsule.mixins.json`

- 改动：从 `mixins` 列表移除 `PiglinConversionMixin`。
- 为什么：报告 M8——`Piglin.maybeConvertToZombified` 在 1.20.1 不存在（`javap` + `methods.csv` 双重验证），`require = 0` 使其静默失效；猪灵保护已由 `AnchorFieldEvents.onConversion`（`LivingConversionEvent.Pre`，在 `playConvertedSound`/`finishConversion` 之前触发）完整覆盖，删除不改变行为。
- 同步要求：建议同时删除 `src/main/java/com/curiats/returncapsule/mixin/PiglinConversionMixin.java`（不删也不影响运行，只是留下一个永不注册/永不生效的死 mixin 类；已全仓 grep 无其他引用）。
- 风险：若其他子智能体/后续改动引用了该类，会编译失败（已全仓 grep：无引用）。
- 行为变更：**否**（移除死代码）。

## 未做项（建议 lead 决定，未写草稿）

1. **Transport 的全局 screen shake**：基类 `impactEffects` 对 transport 也执行 `ShakeBurst.around`，旧实现只有 `forPassengers`。若要严格等价，需在模板里给 `impactEffects` 提供“是否全服震屏”的开关（如 `protected boolean shakesNearbyPlayers() { return true; }`，Transport 覆盖为 false）。
2. **`CapsuleStructure.removeParts` 按 PART 索引精确删除**：目前仍按 kind 的整块范围删除，重叠胶囊会误删；改精确删除会改变 supply 旧行为，风险大于收益，暂留。
3. **`CapsuleKind` 增加 `TRANSPORT` 常量**：`TransportCapsuleEntity.capsuleKind()` 返回 `SUPPLY` 只是语义问题；若加常量必须同时把 `CapsuleStructure.placeParts` 的 base 判定从 `kind == SUPPLY ? 0 : 18` 改成 `kind == MANNED ? 18 : 0`，否则部件索引越界。
4. **`MannedCapsuleEntity` 的重复 `Owner` 字段**：与基类 `ownerUuid` 写同一个 NBT 键，无功能影响，清理收益低。
5. **锚点场扫描性能**：每 2 tick 每满仓玩家一次 65×65×17 AABB 扫描 + 每次 `slow()` 再遍历玩家；PvP 服玩家多时是热点。可改为低频（4~10 tick）、缓存 `covers` 结果或按 `ServerLevel` 维护“持有锚点玩家”缓存。
6. **旁观/创造模式玩家投射锚点场**：`level.players()` 未过滤，若产品上不允许，需要在 `covers/slowsProjectiles` 过滤 `isSpectator()/isCreative()`。
