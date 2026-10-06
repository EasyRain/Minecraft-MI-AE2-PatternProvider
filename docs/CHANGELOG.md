# Changelog

本项目遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [1.2.1] - 2026-10-06

### 新增

- **游戏内指南书（GuideME，中英双语）**：新增物品「**使用手册**」（`mi_ae2_pattern_provider:guide`，工作台无序合成 =
  「MI 指南书 + 任意颜色玻璃线缆」，也直接列在 **MI 创造栏里 MI 指南书的旁边**）。
  右键打开的是 **MI 自己的指南书**（`modern_industrialization:book`）并**直接翻到本 mod 章节** —— 侧栏就是 MI 那一整套
  （序言 / 蒸汽时代 / 电气时代 / 游戏中期 / 游戏终局 / Industrialization Overdrive / **ME样板供应仓**），
  与 Industrialization Overdrive 的并入方式一致，而不是一本只装我们页面的小册子。
  MI 的指南书用**内容目录名**（`Guide#getContentRootFolder() == "mi_guidebook"`）查找、不硬编码指南 id；
  万一找不到（MI 改了目录名）会打 WARN 并退回本 mod **自己注册的那一本**（保留为兜底，内容不至于没得看）。
  正文共 **8 页**，中英各一份（中文放在 GuideME 约定的 `guide/_zh_cn/`，按游戏语言切换、缺页回落英文）：
  总览、快速上手（含 `<MultiblockShape>` 直接渲染的处理阵列结构化场景）、供应仓本体（槽位 / 两个界面 / 容量策略 /
  自动命名 / 在线状态）、样板与自动合成、「一个阵列只能一个仓」的原因、扩展供应仓（分页与扩容卡）、升级卡
  （频道卡 / 感应卡重定向 / 虚拟合成卡剔除 / 与效率类 mod 的关系）、超频模块（高级 + 量子：并行、配置、16× 取值依据、
  会被倍率同比例放大的上游瑕疵）、疑难排查（按游戏里实际症状写的对照表）。
  手册贴图沿用本 mod 的既有做法：拿 MI 原版「MI 指南书」贴图**只做色相映射**（黄相 48° → AE2 福鲁伊克斯紫相 268°，
  饱和度同时对到福鲁伊克斯的 0.55），书页是纯灰所以一点不动 ⇒ 与 MI 自己的手册画风统一、只有配色不同。
  指南注册走 `MiAe2Guide.init()`（mod 构造函数里 `Guide.builder(...)`），并挂上 MI 的 `MultiblockShapeCompiler`
  与 tesseract 的标签包（与 EI 的 `EI#setupGuide` 同款）。
- **同一套正文并进 MI 的指南书（侧栏多一个「ME样板供应仓」章节）**：MI 的指南用 `folder("mi_guidebook")` 注册，而
  GuideME 读页面是「**任意命名空间**下的这个目录」（官方文档原话：Pages for a guidebook are read from all resource packs
  across all namespace）—— IO 就是这么并进 MI 指南书的。我们把自己那套页面镜像到**自己命名空间**下的
  `assets/mi_ae2_pattern_provider/mi_guidebook/`：页面 id 仍是 `mi_ae2_pattern_provider:<页名>`（放到 MI 命名空间会与
  MI 自己的 `index.md` 之类**撞 id**），页内省略命名空间的物品 id 也仍按我们自己的命名空间解析 ⇒ 与自带指南那一份
  **完全相同**。根页面导航位置 **100**（MI 原版 0–4、IO 99）。
  **正文只维护一份**：这份拷贝由 `build.gradle` 的 `processResources` 从 `guides/…/guide/` 生成，仓库里没有第二份正文。
  配方显示刻意只用**原版工作台配方**的精确 id（`<Recipe id="..._asbl" />`）：MI 组装机 / EAEP 水晶装配器这些自定义
  配方类型需要 `RecipeTypeMappingSupplier`，MI / EI 都没提供，写上去只会得到「找不到配方」的红框；差异改用文字说明。

### 修复

- **可选 mod 缺席时指南页面会报红（用户 m 提问「GuideME 里用了非常多可选 mod 的素材，那些 mod 没装是否会显示错误」）**：
  查证结论是**会**——GuideME 21.1.17 的 `<ItemImage>` / `<ItemLink>` / `<BlockImage>` 都走
  `MdxAttrs#getRequiredItemAndId`，物品在注册表里查不到就直接 `appendError(...)` 在页面上画一段红色错误文字，
  **没有任何 fallback / optional 机制**（官方文档里那个 `ItemLink fallback="text"` 在这个版本并不存在：
  `fallback` 字面量只出现在 `RecipeCompiler` 的 `fallbackText` 里）。
  所以把页面里所有**可能不存在**的引用改成正文：① ExtendedAE 的样板供应器升级 / 水晶装配器 / 扩展样板供应器；
  ② ExtendedAE-Plus 的频道卡 / 扩容卡 / 虚拟合成卡；③ AppliedFlux 的感应卡；④ **本 mod 自己的扩展仓方块**
  （它只在装了 ExtendedAE 时才注册，属「命名空间必需、但 id 条件注册」）；⑤ 这些页面的**导航图标**也从条件注册 /
  可选 mod 的物品换成必然存在的（`ae2:pattern_provider` 等）。`item_ids` 里的扩展仓 id 保留 —— 那只是索引，
  查不到无害，装了 ExtendedAE 时反而让悬浮键可用。
  验证三层：① **诱饵**——故意塞回一个 `<ItemImage id="appflux:induction_card" />`，自检在两个落点都报红并点名；
  ② **真缺席**——把 `extendedae_plus` 与 `AppliedFlux` 移出 `run/mods` 后双端自检仍全绿（物品存在性按**当时真实注册表**
  校验，所以这条同时证明页面里已没有任何可选 mod 的引用）；③ 恢复后全绿。
- **「只装必需 mod」的完整验证（用户 m 追加「不止 EAEP，EAE 也是可选的」）**：把 ExtendedAE、Glodium、ExtendedAE-Plus、
  AppliedFlux、Industrialization Overdrive 全部移出（连 `build.gradle` 里那两条 `localRuntime` 也临时注释掉），
  只留 MI / EI / AE2 / tesseract / guideme 跑 `runServer` + `runClient`：**零 ❌**、14 条自检全绿，其中
  `✅ 指南自检通过（资源层）…112 个物品/配方 id 全部存在`、`✅ 并入 MI 指南书自检通过`。
  （顺带一个**整合包层面**的事实：`AppliedFlux 1.21-2.1.6` **硬依赖 ExtendedAE** —— 少了 ExtendedAE 时它会让整个游戏
  在 mod 加载阶段就崩，与本 mod 无关，但做这类验证时得把它一起去掉。）

### 内部

- **新增冒烟自检「指南」**（`SmokeTestAutoStop#assertGuide` / `#clientTickGuideCheck` / `#checkMergedIntoMiGuide`），
  钉住三层：① **资源层**（专用服可查）——指南已在 GuideME 注册、「使用手册」物品在本 mod 命名空间、
  8 页正文 + 8 页 `_zh_cn` 翻译共 16 个文件在**两个落点**（自带指南 `guides/…` 与并入 MI 指南书的 `mi_guidebook`）都在，
  且页面里引用的每个物品 / 配方 / 导航图标 id **都真的存在**；② **页面编译层**（客户端）——`getPages()` 能读出本 mod 的
  8 页，且 **MI 的指南书里也编译出了这 8 页**、手册的深链页面 `mi_ae2_pattern_provider:index.md` 确实存在
  （GuideME **只在客户端**加载/编译页面，专用服上 `getPages()` 会抛 `Pages are not loaded yet.`，所以这一层挂在
  `SmokeTestAutoStopClient` 上、每 tick 重试直到可读）；③ 专用服侧页面校验走 `-Dguideme.validateAtStartup=…`。
  另外加了**守卫**：页面标签只允许引用**必然存在**的 id —— 命名空间白名单（`minecraft` / `ae2` /
  `modern_industrialization` / `extended_industrialization` / 本 mod）+ 条件注册 id 黑名单（扩展仓），
  谁再把可选 mod 的 id 写进标签就直接变红并提示「请改成正文」。
- **自检不再因可选 mod 缺席而假报警**：① `assertRecipesLoaded` 学会「条件配方」——水晶装配器那条配方带
  `neoforge:conditions`（`mod_loaded: extendedae`），没装 ExtendedAE 时它**本来就不该**在配方管理器里，
  现在记成「按条件跳过」并在通过日志里点名；② `runInductionRetargetSelfCheck` 先判断 AppliedFlux 在不在，
  缺席时记为正常降级（以前会把 `ClassNotFoundException` 当成「真机也出错」报红）。
  配方自检也自动覆盖到新增的 `guide.json`（现在共 **7 个**配方文件）。
- **文档口径修正**：README 交互表里的扳手标签原本写成 `#modern_industrialization:wrenches`（并不存在），
  实际 MI 用的是**公共标签** `c:tools/wrench`（`MITags.item(...)` 固定挂在 `c` 命名空间）—— 也就是说
  **任意**加入了公共扳手标签的扳手（MI 自己的、以及绝大多数 mod 的扳手）都能打开 MI 槽位界面；指南页面同此口径。

## [1.2.0] - 2026-10-04

### 新增

- **下单前的「输出格容量守卫」（`MePatternProviderBlockEntity#miae2$outputCapacityIsTruthful`，由用户 m03741 提议）**：
  `pushPattern` 接料之前核对一条可自证的不变量 —— **容量查询路径承诺的空间**不得超过**真插入能兑现的上限**：
  `getRemainingCapacityFor(probe) + getAmount() <= probe.getMaxStackSize()`（`probe` 取这一份样板的每个物品产物，
  逐个输出格查）。不一致就**整体拒收**、不发配材料（AE 每 tick 重试 ⇒ 合成在界面上显示为「一直等待」），
  同一次调用还会把本仓自己留下的"无上限"输出槽标志夹回普通容量，所以下一 tick 的 push 就能过。
  为什么不做「预判产物数量 vs 槽位数」：阵列的倍率本来就是按输出空间反推的（预测必然一致、纯属多余），
  而**无视空间**的第三方并行仓（bingxing）根本不走这条模拟；只按自己 36 格预测还会**误拒**
  （多方块里可能还有别的输出仓/控制器槽位）⇒ 反而把能跑的整合包卡死。
  上游依据：tesseract `CrafterComponentHelper#putItemOutputs` 里那条**故意**的口径差异
  （「If putting the output, don't respect the adjusted capacity in case it was reduced during the processing.」
  ⇒ 真插入按 `variant.getMaxStackSize() - amount`、模拟按 `min(getMaxStackSize(), adjustedCapacity) - amount`），
  只有「容量查询路径的 maxStackSize 被抬高」**且**「`adjustedCapacity` 也被抬到真实堆叠数之上」时两者才会分叉
  —— 正是下面「输出槽无上限」那条根因的组合。

- **量子超频模块（`quantum_overclock_module`，由用户 m03941 提议）**：**高级超频模块的升级版** ——
  装进**同一个**「超频模块」槽（两者**互斥**，同一个槽只能放一个物品，互斥性是结构性保证、不需要额外判断），
  既有高级超频的全部效果（`AdvancedOverclockHook` 的家族判定覆盖 `isOverclockModule`），**又给 EI 处理阵列提供并行**。
  实现：`ProcessingArrayParallelMixin` 在 `ProcessingArrayBlockEntity#getMaxMultiplier()` 的 `RETURN` 处
  把它从「机器数」抬成「机器数 × 配置倍率」（`OverclockModules#scaleMaxMultiplier`），IO 的处理阵列走一个
  `@Pseudo` + 字符串 targets 的姊妹 mixin（缺 IO 时静默不生效）。**抬的是天花板，不是算完的结果** ——
  真实并行倍率仍由 tesseract `MultipliedCrafterComponent#calculateMultiplier` 按输入料量与输出格空间二分反推
  ⇒ 装不下时自动降倍率、绝不吞产物（若改成「把结果乘 N」，输入侧会出现「模拟够、真扣不够」）。
  倍率可在 config 调（`mi_ae2_pattern_provider-common.toml` 的 `quantum_overclock.parallel_multiplier`，
  默认 **4×**、范围 **2–16×**；64 机器 = 256 并行）。上限取 16× 的依据：本仓输出空间
  `36 格 × 64 = 2304` 件，官方配方每批物品产物量 P 的分布为 `≤1 52.6% / ≤2 66.1% / ≤4 77.2% / ≤9 95.1%`，
  而 `倍率 ≤ 36/P`（P=1→36、P=2→18、P=4→9、P=9→4）⇒ 16× 是「单产物理论极限 36×」的一半以下。
  **不是免费产能**：EU 与耗时沿用上游语义同倍放大（4× 速度 = 4× EU/t、总能量 4×，
  能源供不上时耗时线性拉长，见 `EuCostTransformer` × `getRecipeMultiplier()`）。**不动输出格真实容量**。
  **并行只对处理阵列生效**：同一块模块插进单方块机器或其它多方块机器时只等于高级超频模块（满效率 + 立刻释放配方），
  不会多出并行 —— 那些机器没有「机器数」这一层（单方块走 MI 的 `CrafterComponent`；EI 大型电炉 /
  IO 热解炉的 `getMaxMultiplier()` 是**线圈档位的批处理上限**），而所有 Mult* 机器的共享漏斗
  `MultipliedCrafterComponent#getMaxMultiplier()` 挂上去就等于给它们免费加产能，属明确越界，故只挂数组自己的类。
  配方（**超导压**档 —— 用户 m04415 指出「都叫量子了合成材料不能和高级的用同一阶的材料」后重做）：
  组装机（`eu 64` / `duration 1000`）或工作台 —— 高级超频模块×1 + **量子电路×2 + 超导体板×4**
  （工作台摆法 `s.s / qaq / s.s`：s = 超导体板、q = 量子电路、a = 高级超频模块）。
  量子电路本身还要量子电路板（12 超导电缆 + 6 铱板 + 2 钚电池 + 50 mB 氦-3）⇒ 2 个量子电路约为 MI 自家
  `quantum_upgrade`（8 量子电路 + 奇异物质 + 50 mB 反物质）的四分之一：高一整档，但不是直接跳终局。
  贴图由高级模块贴图再经一次色相映射（橙 → 紫）得到，画风与 MI 一致。

- **量子超频模块「只许装进处理阵列控制器」（用户 m04850 要求）**：右键或从 GUI 拖进普通机器时会被**拒收**
  （右键还会在**聊天框**提示「量子超频模块仅支持处理阵列」—— 不用动作栏是因为右键普通机器会打开它的 GUI、
  动作栏文字会被 GUI 挡住，聊天框里关掉 GUI 也仍看得见；用户 m05085 实测反馈），**高级模块与原版模块照旧到处可插**，
  创造栏/JEI 里的可见性也不变。判定依据是**「可插 = 并行真的会生效」这个等价关系**：`QuantumParallelHost`
  标记接口由两个并行 mixin 在自己的目标类上顺手 `implements`，所以宿主天然只有 EI 处理阵列
  （以及装了 IO 时的多处理阵列）—— mixin 注入点一旦失效，接口就不会贴上，玩家会**立刻看到模块被拒收**，
  而不是白装一个没效果的模块。
  实现（两条插入路径各自拦）：右键在 `OverdriveComponentMixin#onUse` 按机器拒收；
  GUI 在 `SlotPanel#setupMenu` 用 `@Redirect` 把 `MenuFacade` 换成会包装超频槽的那一个（`OverclockSlotGate`），
  包装出的槽 `mayPlace` 先问机器再问原谓词 —— **拦在服务端槽位谓词上，物品不会离开玩家光标（不丢件）**。
  刻意**不改** `OverdriveComponent#setStackServer`：那才是「物品已经从光标拿走、再拒绝就丢件」的时刻。
  代价是客户端预览会短暂把模块画进槽里（服务端回滚并重同步）—— 客户端 `SlotPanelClient` 的槽没有任何机器引用，
  要做客户端拦截就得多引一个客户端专属 mixin，不值。
  冒烟自检第 14 条三层钉住：判定表（非宿主拒收量子 / 宿主放行 / 原版与高级到处放行 / 空手不触发）；
  标记接口覆盖面（EI 处理阵列 = 宿主，MI 单方块与 MI 多方块 = 非宿主，装了 IO 则多处理阵列也必须是宿主）；
  **真实槽位**（拿真实机器的真实 `SlotPanel` 真建一次菜单，断言宿主槽收量子、`modern_industrialization:assembler`
  这类普通机器槽不收量子但照收原版/高级）—— 最后一条是唯一能证明 `@Redirect` 真贴上了的断言。

- **Cloth Config 游戏内配置界面（可选依赖，用户 m04598 要求）**：装了 [Cloth Config](https://modrinth.com/mod/cloth-config)
  （modId `cloth_config`）时，在「Mods 界面 → 本 mod → Config」里直接调并行倍率，字段带范围 2–16、默认值 4 与
  说明 tooltip。实现：`com.miae2.client.MiAe2ConfigScreen` 用 Cloth Config 画界面，通过 NeoForge 的
  `IConfigScreenFactory` 扩展点注册（`ModContainer#registerExtensionPoint`）。
  **刻意没有挂「需要重启」徽章**（Cloth Config 的 `requireRestart()`）：倍率在
  `MiAe2Config.quantumParallelMultiplier()` ← `OverclockModules.scaleMaxMultiplier()` ← `ProcessingArrayParallelMixin`
  这条链上是每次算配方时**现读**的，保存后下一次配方计算就生效，标「需要重启」反而是错误暗示；这件事由冒烟自检的
  「改配置 → 立刻读到新值 → 还原」活性断言钉住。
  客户端专属类必须关在单独的文件里（`IConfigScreenFactory` 在 `net.neoforged.neoforge.client.gui`，专用服加载即
  `NoClassDefFoundError`），调用点用 `FMLEnvironment.dist.isClient() && ModList.get().isLoaded("cloth_config")` 双重守护。
  同时撤掉了物品 Shift tooltip 里那行配置说明 —— 配置项该出现在配置界面里，而不是塞在物品说明里。
  Cloth Config 是 LGPL-3.0，jar 随 `libs/` 分发（与 AE2 同理），只做编译期链接 + dev 运行时，没装它的玩家一切照旧。

### 修复

- **两个超频模块不在创造物品栏里，JEI 物品列表里也搜不到（用户 m04362 报告）**：
  根因是 **JEI 19 的物品列表就是创造栏的内容** —— `mezz.jei.library.plugins.vanilla.ingredients.ItemStackListFactory`
  读的是各创造栏的 `displayItems`，所以「没进任何创造栏」的物品在创造栏与 JEI 里都缺席（合成配方里仍查得到，
  因为配方索引是另一条路）。本 mod 的机壳之所以能被找到，是因为它走 MI 的注册体系
  （`MachineRegistrationHelper` → `MIBlock.block`，而 `BlockDefinition` 里 `MIItem.item(...)` 会把物品塞进
  `MIItem.ITEM_DEFINITIONS`，MI 的 `general` 栏就是遍历这张表），两个模块却只注册在本 mod 自己的
  `DeferredRegister` 里。**不改注册命名空间**（那会把 id 从 `mi_ae2_pattern_provider:*` 变成
  `modern_industrialization:*`，破坏已有存档里的物品与配方 id），改为监听 `BuildCreativeModeTabContentsEvent`
  把两个模块 `insertAfter` 到 MI 原版超频模块之后（NeoForge 先跑完 MI 的 `displayItems` 再派发事件，
  锚点必然在位；万一不在就退回追加到栏尾，任何情况下不抛异常）。
- **量子模块的工作台配方曾经整条解析失败、物品只能靠组装机做出来（本轮自查发现）**：上一轮把工作台摆法写成
  `"s.s" / "qaq" / "s.s"`，那个 `.` 在原版 `crafting_shaped` 里是**未定义的符号**（空位只认空格），于是
  `Parsing error loading recipe mi_ae2_pattern_provider:quantum_overclock_module_asbl: Pattern references symbol '.' but it's not defined in the key`
  —— 游戏只在日志里留一行 ERROR，照跑不崩，玩家视角就是「这条配方不存在」。现已改成空格 `"s s" / "qaq" / "s s"`，
  并新增冒烟自检 `assertRecipesLoaded`（见「内部」）钉住这一类问题。
- **量子模块的并行倍率「找不到哪里能设置」（用户 m04362 报告）**：值一直可配，藏在
  `config/mi_ae2_pattern_provider-common.toml` 的 `[quantum_overclock] parallel_multiplier`，只是没有任何地方
  告诉玩家。现在：启动时打一条 INFO 给出该文件的**绝对路径**（`FMLPaths.CONFIGDIR`），
  并接上 **Cloth Config 的游戏内配置界面**（「Mods 界面 → 本 mod → Config」，见「新增」）。
  物品 Shift tooltip 里那行配置说明按用户要求撤掉了 —— 配置项应该出现在配置界面里，而不是塞在物品说明里。

### 改动

- **物品输出槽从 3 排扩到 4 排（27 → 36 格，瞬时输出空间 1728 → 2304 件）**：为整合包的并行仓留出余量
  （例如 bingxing「现代工业化并行仓」：基础 8 / 高级 64 / 精英 256 / 终极 512 / 创造 2048 档）。
  那类 mod 在配方完成的那一 tick 里额外重跑最多 2047 遍配方，**完全不检查输出空间** —— MI 的
  `CrafterComponent#putItemOutputs` 装不下时只置 `ok = false` 并把剩余产物原地丢弃
  （`mi-src/.../components/CrafterComponent.java:589-591`），而调用方丢弃了返回值 ⇒ 输入照扣、产物静默销毁、
  AE 的合成任务永远等不到足量产而挂起。所以「并行等级 × 每批物品产物」必须**一次性**装得下。
  4 排后：高级 64 档可支撑每批 ≤36 件、精英 256 档 ≤9 件、终极 512 档 ≤4 件、创造 2048 档仅每批 1 件
  （流体输出本来就是 9 格 × `Integer.MAX_VALUE`，不是瓶颈）。
  GUI 高度由 228 → 246（仍是公式算出，在 MI 背景贴图 256 的上限内 —— 这是不换贴图的极限，再加一排 264 会画到贴图外）。
  旧存档兼容：新增的格子读档时是「空且未锁」，`onLoad` 的 `lockAllEmpty` 会按原不变量补锁。
  自检补了「GUI 高 ≤ 256（贴图上限）」与「瞬时输出空间 ≥ 256 并行 × 8 件/批」两条断言。

### 内部

- **新增冒烟自检「极端量边界」（`SmokeTestAutoStop#assertExtremeAmountPush`）**：确认单次 push 每个键
  的上限是「输入槽数 × `Integer.MAX_VALUE`」（当前 9 × 2147483647 = 19,327,352,823 个物品 / mB），
  **恰好装满被完整接纳**，**再多 1 个单位被整体拒绝且槽内容分毫不动**（不部分接收 = 不静默丢料；
  超限时 AE2 会每 tick 重试 ⇒ 任务挂起但绝不损坏）。同时给「槽位容量保护」自检补了
  「无上限槽装入 `Integer.MAX_VALUE` 级内容后仍能原样过 NBT 往返」的断言。
  ——纯自检代码，**无游戏行为差异**，只在设了 `-Dmi_ae2_pattern_provider.smokeTest=...` 时运行。

- **新增冒烟自检「量子超频模块」（`SmokeTestAutoStop#assertQuantumModule`）**：钉住家族判定
  （高级/量子 = true，MI 原版与空 = false）、`grantsParallel`（只有量子 = true）、配置落在 `[2, 16]`、
  `scaleMaxMultiplier(64) == 64 × 配置` 且 `scale(0) == 0`、以及**保守性**（`上限 × 64 ≤ 36 × 64`、
  `默认 × 64 × 9 ≤ 36 × 64`）—— 谁把常量改大都会立刻红。另外它还断言 `SPEC.isLoaded()`（防「配置没加载 →
  静默回落默认值」），以及**配置活性**：写一个新值 → 立刻读到新值 → 还原（这条保证游戏内配置界面
  「保存即生效、不用重启」那句话成立；谁把倍率改成启动时缓存都会立刻红）。
- **新增冒烟自检「量子并行（真实阵列）」（`SmokeTestAutoStop#assertQuantumParallel`，端到端）**：
  上面的自检只验证算术，**证明不了 mixin 真的贴上了**（目标改名 / 被别的 mod 抢先 / 注入点被 inline 都会
  静默失效而算术照旧全绿）。这条放一个**真实** EI 处理阵列控制器方块（无需成型：
  `getMaxMultiplier()` 只读 `ProcessingArrayMachineComponent#getMachineCount()`），往它的超频槽依次放
  空 / MI 原版超频模块 / 高级超频模块 / 量子超频模块，读同一个 `getMaxMultiplier()`：只有量子那一次
  必须变成「机器数 × 配置倍率」。日志：`✅ 量子并行（真实阵列）自检通过——机器数 1：空=1、原版超频=1、
  高级超频=1、量子超频=4（期望 4，配置 4×）`。
- **新增冒烟自检「配方加载」（`SmokeTestAutoStop#assertRecipesLoaded`）**：用资源管理器列出
  `data/mi_ae2_pattern_provider/recipe/` 下的**所有**文件，逐个到 `RecipeManager#byKey` 里查 ——
  文件在、配方不在 = 这个 JSON 没解析成功。**配方解析失败是「静默故障」**：游戏只在日志里留一行 ERROR，
  照跑不崩，物品就是合不出来，玩家无从判断（本轮就是这么发现工作台配方里的 `.` 号事故的）。
  这条自检不需要维护配方清单，以后新增/改名配方都自动覆盖。冒烟自检 12 条 → **13 条**，全绿。

### 已知问题（上游 tesseract / EI，被并行倍率**同比例放大**，非本模块引入）

- **多个流体产物共用一个输出槽时会静默丢流体**：`MultipliedCrafterComponent` 的流体可行性检查
  对每个流体产物**各自**按整槽剩余空间求倍率、**不扣减前一个产物已占用的量**
  （`MultipliedCrafterComponent.java:289-290`），而真插入被 `Math.min(..., getRemainingSpace())` 截断后
  只置 `ok = false`（`CrafterComponentHelper.java:262`），调用方 `AbstractModularCrafterComponent.java:283`
  **丢弃了返回值** ⇒ 倍率越高，单次损失越大。官方配方里最大流体产物总量 17000 mB，绝大多数配方只有一个流体产物。
- **概率产物（`probability < 1.0`）完全不做可行性检查**：`MultipliedCrafterComponent.java:213/:281` 用
  `if (!(output.probability() < 1.0F))` 把它们排除在 `canItemOutputsAllFit` 之外 ⇒ 掷中后装不下的部分同样静默丢。
  官方配方里 34 条有概率产物（macerator 22、quarry 7、centrifuge 4、heat_exchanger 1），且官方上限本就是
  64 并行（N=1）时也可能发生。
- **`recipeEnergy` 读档截断**：写档用 `putLong`、读档用 `tag.getInt`
  （`AbstractModularCrafterComponent.java:324-341`，`CompoundTag#getInt` 对 LongTag 静默截断）⇒
  `配方总 EU × 倍率 > 2^31-1` 时**读档后合成进度损坏**，阈值 `总EU > 2^31 / (64 × 倍率)`。
  触及它的官方配方只有 `packer` 的量子装备升级（1,000,000 EU/t × 200 = 200M）与 `fusion_reactor`
  （多方块），而 `N = 1`（原版阵列 64 并行）时 packer 那条**已经超阈值** ⇒ 既有上游问题，本模块只是放大它。
  **建议**：把量子模块用在普通产线（本仓扫描显示 1941 条有物品产物的配方里，`总EU ≤ 2.1M` 的占绝大多数），
  不要拿它去挂 packer 的量子升级与聚变堆。

## [1.1.1] - 2026-10-04

### 新增

- **悬浮提示显示「设备在线 / 设备离线」**：在 **Jade** 与 **The One Probe（TOP）** 里与 AE2 自家机器一致，
  各带一个自己的小插件（`com.miae2.compat.MeProviderJadePlugin`、`com.miae2.compat.MeProviderTopPlugin`；
  两者都是可选依赖）。**没有**走 AE2 的 IGT 扩展点 —— 它按方块类注册、且适配器先强转方块实体再做判断，
  而 MI 的所有机器共用 `MachineBlock`，会把提供者挂到每一台 MI 机器上并抛 `ClassCastException`
  （悬浮提示显示「发生错误」），还会顺手删掉其它 MI 机器「详细信息」里的能量/流体行。

### 修复

- **大型合成中途被误判「任务结束」而永久卡死（重要）**：回收本仓残留输入 / 解锁输出格的判据原先用了
  `ICraftingService#getRequestedAmount(AEKey)` —— 它返回的其实是 `job.waitingFor`，也就是
  「**已经发配出去、还在等产物回来**」的数量（见 `CraftingService.java:388-396`），
  机器两次发配之间天然为 0。于是大合成（例如一次下单 128 个产物）做到一半就会被本仓判为「任务结束」，
  把还在输入表里的材料退回**通用网络存储**并解锁输出格；而 AE2 的 CPU 存储**只认 `waitingFor` 里的键**
  （`CraftingCpuLogic.java:197-201`），退出去的材料再也拿不回去 → 该步骤永久死锁。
  表现：供应仓里没有材料、机器也不工作、AE 网络里材料却充足、剩余数量永远不动。
  现在改用 `ICraftingCPU#isBusy()`（= `craftingLogic.hasJob()`）：**只要网格上还有任何合成任务在跑就不回收**，
  真正没有任务时才回收并解锁（日志会打 `[任务回收] 网格上已无进行中的合成任务…`）。
  ⚠️ 已经卡住的旧存档：材料躺在通用网络存储里、AE 的 CPU 按设计取不回来，
  **需要先取消那个合成任务再重新下单**才能恢复。
  （注：这条是**加固**，不是用户 2026-10-04 复测那次卡死的原因 —— 那次是下面「输出槽无上限」那条；
  两者症状的区别：清理误判会把输出格的锁解掉，而输出槽销毁产物时**输出格仍然锁着**。）
- **处理阵列单次产物超过 64 时被静默销毁 → 大型合成永久卡死（重要，2026-10-04 定位）**：本仓的**输出槽**
  曾和输入槽一样被设成「无上限容量」。EI 的处理阵列会按「输出槽还装得下多少」反推并行倍率
  （`MultipliedCrafterComponent#calculateItemOutputRecipeMultiplier` → `canItemOutputsAllFit` →
  `MIStorage.insert` → `getRemainingCapacityFor`），无上限槽让它永远返回「装得下」，倍率于是被抬到
  `min(可用输入数, 阵列机器数)`；而**真插入**走的是 tesseract 的
  `CrafterComponentHelper#putItemOutputs`，真实路径算的是 `variant.getMaxStackSize() - amount`
  （一格 64；其余输出格又被 `lockAllEmpty` 锁成「空」、对任何真实物品一律拒绝），超出的部分只把返回值
  置 false，而调用方 `AbstractModularCrafterComponent#tickRecipe` 是
  `this.putOutputs(this.activeRecipe, false, false);` —— **根本不看返回值** ⇒ 产物被静默销毁。
  于是 AE 的 CPU 永远等不到足量产（`waitingFor` 凑不齐），任务永久挂起；本仓因「CPU 还在请求」而不清理 ⇒
  `activePattern` 一直挂着：**用扳手右击供应仓，输出格还是锁着的、输入格是空的、机器也不动**
  （「1 板出 2 线」的配方只要阵列机器数 &gt; 32 就会触发；现象就是「先做完一批、剩下的永远卡住」）。
  现在**输出槽恢复普通容量（64）**、输入槽继续无上限（AE 要一次性推整批，`insertPatternInputs`
  要求完整接纳否则整体拒绝），并在构造与读档初始化时各摁一次策略
  （`MePatternProviderBlockEntity#miae2$enforceSlotCapacityPolicy()`）；EI 的倍率上限与它自己的真插入上限
  从此完全一致，结构上不可能再销毁产物。代价：阵列并行度被限制到「一格 64 / 每样板产物」
  （约等于原版 EI 行为），大合成会分成多次合成完成，总耗电不变。（这条自身的代价「并行度被限制到一个输出格 64 个」已由下面「输出格按产物摊开锁满」解决，同时保留这里不丢产物的性质。）
- **不再把其它 mod / 玩家机器配置设定的槽位容量抹成 64（重要）**：`ConfigurableItemStackMixin` 挂在 MI 的
  公共类上、会作用于**全游戏每一个** `ConfigurableItemStack`，而旧实现在读档时**无条件**写
  `adjustedCapacity = 64`（连「关闭无上限」的分支也照写一遍）。Extended Industrialization 的
  「机器配置」物品正是通过 `ConfigurableItemStackAccessor#setAdjustedCapacity` 把槽位容量设成 &gt;64、
  并持久化在 NBT 的 `adjCap` 里的 —— 于是**每次读档容量都被静默抹回 64**
  （症状：机器做满一组 64 就再也不动；旧存档里的 `adjCap` 已被写坏，需要用机器配置重新设一次）。
  现在：只有带本 mod 标记的槽才动容量；开启无上限前先备份原值、关闭时原样还原；
  写 NBT 时把 `adjCap` 还原成原值，不把 `Integer.MAX_VALUE` 带进存档。
- **冒烟测试新增「槽位容量保护」自检**：直接造一个容量 1024 的 `ConfigurableItemStack` 走 NBT 往返与
  拷贝构造、断言容量不变，并断言本 mod 的无上限槽往返后仍无上限、存档里的 `adjCap` 不是 `MAX`。
- **冒烟测试新增「槽位容量策略」自检**：断言输入槽全部无上限、输出槽全部是普通容量（≤64）——
  输出槽一旦被标成无上限，就会重现上面那条「产物被静默销毁 → 大型合成永久卡死」。
- **同一样板切换不了 → 单台阵列做「连续多步配方」时永久卡住（重要，2026-10-04 用户复测）**：本仓原先
  只要 `activePattern` 不是当前样板就一律拒收（`MePatternProviderBlockEntity#pushPattern`），而
  `activePattern` 只能由清理逻辑清空、清理又要求「**网格上没有任何 CPU 忙**」—— 大合成中途永远不满足 ⇒
  第一步做完、AE 的 CPU 去推第二步的样板时被拒之门外。现象（用户原话）：**输出格还锁着上一步的产物、
  输入格是空的、机器不动、AE 里显示等待**。现在改成「**上一份真的做完了就放行**」：只有在
  `miae2$isAnyCraftInFlight()`（输出格上有机器锁 = 阵列正握着这一份）、`miae2$hasAnyInput()`（上一份材料还没吃完）、
  `miae2$hasAnyOutput()`（产物还没搬回网络）三条任一成立时才拒收，否则按新样板**重新摊开输出格锁**并继续收料。
  判据依赖的机器锁生命周期已核对：MI 的 `CrafterComponent` 与 tesseract 的 `AbstractModularCrafterComponent`
  都是「开工上机器锁、完成时 `putOutputs(...) + clearLocks()`（只清机器锁，玩家锁保留）」，
  且 `tryContinueRecipe()`（会空转上锁的那条路）在 EI 阵列里**只在结构重匹配后调一次**。
  ⚠️ 换样板还受 EI 的「配方保温」影响：`efficiencyTicks > 0` 时阵列只肯跑当前那一个配方
  （`MultipliedCrafterComponent#getRecipes()`）。本 mod 的**高级超频模块**会在停转时把效率归零、立刻释放配方，
  所以装了模块的阵列能马上换下一步；没装模块时得等效率自然掉到 0（最长约几十秒）。
- **输出格按产物摊开锁满（输出效率）**：`lockOutputs` 原先给每个产物只锁「刚好装得下这一份」的格子（通常 1 格），
  于是处理阵列的并行倍率被卡在 `64 / 单次产量`。现在会把产物摊到**所有输出格**上：每个产物保底 1 格，
  剩下的格依次补给「当前倍率上限最低」的产物（一原材料出多种加工材料时不会把所有格全给同一种），
  倍率上限随之抬到 `64 × 格数 / 单次产量`。摊开用 MI 自己的
  `AbstractConfigurableStack.playerLockNoOverride(item, 1L, slots)` 实现 —— `requiredAmount` 传 1，
  每调一次恰好多锁一格。因为模拟（`getRemainingCapacityFor`）与真插入（`getMaxStackSize - amount`）
  在普通容量下等价，摊开只抬高上限，不会重新引入丢产物的问题。
- **物品输出槽从 1 排扩到 3 排（27 格，输出效率）**：摊开锁定的上限是 `64 × 格数 / 单次产量`，
  所以**格数本身就是产能**：1 排（9 格）时一个产物最多 9 格，3 排后 27 格（例：1 板出 2 线 ⇒ 倍率上限
  从 288 抬到 864）。GUI 高度随之从写死的 190 改为**按槽位行数算出**的 228
  （`MePatternProviderBlockEntity#requiredGuiHeight`）：MI 把玩家背包首行放在 `backgroundHeight - 82`、
  背包标题放在 `- 94`，高度不够时最下面两行槽位会被背包压住，而且**不会有任何报错**，只表现为「格子看不见」。
  流体输出仍是 1 排：流体那一侧的倍率本来就对**所有**能装该流体的格子取最小，且模拟与真插入共用
  `getRemainingSpace()`，多锁几格既不会抬高上限（上限是单格容量），还可能被半满的格拖低。
  旧存档的兼容：存档里只有原来那 9 格带着锁，新增的 18 格是「空且未锁」，而处理阵列靠
  `areAllOutputSlotsLocked()` 消除配方歧义 ⇒ 读档初始化（`miae2$onLoadInit()`）现在会给「空且未锁」的
  输出格补锁（物品锁成 AIR、流体锁成空），**不碰**任何已有的玩家锁（那是产品锁，解锁会让在途产物被销毁）。
- **冒烟测试新增「输出格摊开锁定」自检**：`lockOutputs` 之后断言全部输出格**都被按产物锁上**
  （只锁一格就会退化成上面那条输出效率问题）。
- **冒烟测试新增「输出格分配」自检**：把格数分配抽成纯函数
  `MePatternProviderBlockEntity#spreadOutputQuotas(long[], int)`，直接验算「单次产量 2/4/8、27 格」
  ⇒ 断言每产物 ≥ 1 格、**没有产物吃满全部格**、各产物倍率天花板（`64 × 格数 / 产量`）最高/最低 < 1.5
  （实测分到 `4/8/15`，天花板 `120~128`）——即分配近似正比于单次产量，不会退化成「每个产物 1 格 +
  剩下全给某一个产物」。
- **冒烟测试新增「GUI 槽位布局」自检**：断言 `backgroundHeight ≥ requiredGuiHeight(...)`、且物品输出槽数与常量一致
  （两者写错都只会表现为「格子看不见」，所以在启动时直接查一遍）。
- **管理终端里的自动命名认不出「普通多方块」，也认不出 Industrialization Overdrive 的处理阵列（已修）**：
  终端名原先只对 EI 的处理阵列特殊处理（`instanceof ProcessingArrayBlockEntity` + 一个 mixin 访问器读它的
  `machines` 组件），所以放在电力高炉这种普通多方块里只剩默认名「ME样板供应仓」。现在改成通用两段式：
  控制器是 `MachineBlockEntity` 且能从它的 `components` 里查到 tesseract 的 `ComponentStackHolder` 组件
  **并且该组件里装的是 MI 的机器方块物品**（= 阵列里放了工作方块；EI 的处理阵列与 Overdrive 的多方块处理阵列
  实现的都是这个接口）→「<工作方块名>处理阵列(扩展)样板供应仓」；否则（普通多方块）→「<控制器名>(扩展)样板供应仓」。
  好处：**不 `instanceof` 任何 mod 的阵列类**，所以既不依赖 Overdrive（它不是必需 mod），
  也能自动认得将来任何实现同一接口的阵列。顺带删掉了只为旧写法存在的 `ProcessingArrayBlockEntityAccessor` mixin。
- **修复：阵列里的供应仓被命名成「中级升级处理阵列…样板供应仓」（用户实机复测抓到）**：
  第一版实现用 `components.getNullable(ComponentStackHolder.class)` 取「第一个命中」的组件当工作方块，
  但 tesseract 用 accessor mixin（`UpgradeComponentAccessor` 等，列在 `tesseract_api_mi.mixins.json` 的
  **`mixins` 段**、服务端也生效）把这个接口**也混进了 MI 的升级 / 红石 / 超频 / 外壳组件**，而阵列基类
  `AbstractElectricMultipliedCraftingMultiblockBlockEntity` 构造时先注册 `upgrades, redstoneControl, overdrive`、
  后由子类注册 `machines`，`getNullable` 又只返回第一个命中 ⇒ 读到的是**升级槽里的 `turbo_upgrade`**
  （中文名「中级升级」）。现在改为遍历 `getAll(ComponentStackHolder.class)`、只认
  「`BlockItem` 且其方块是 MI `MachineBlock`」的那一个（`pickHostedWorkBlock`），一个都不匹配就回落到
  普通多方块名 —— 宁可朴素，也不显示错误名字。EI 阵列与 Overdrive 阵列都因此恢复正常。
- **冒烟测试新增「自动命名」自检**：世界里真放一个 MI 的仓（普通多方块分支）与一个 EI 处理阵列控制器
  （阵列分支：工作方块显式灌进 EI 的 `ProcessingArrayMachineComponent`，其余 `ComponentStackHolder` 槽位
  全塞 `turbo_upgrade` 当干扰，专门复现上面那个 bug），断言终端名分别等于「<控制器名>样板供应仓」
  「<工作方块名>处理阵列样板供应仓」、都不等于默认名、且 `findHostedWorkBlock` 挑中的是工作方块；
  再加纯函数断言「候选混着升级物品时只认机器方块物品、只有升级物品时返回空」；最后断言未接控制器
  （`controllerPos = null` 与指向空位两种）回落默认名。比对用「同样组件拼出的期望字符串」，不依赖服务端语言文件。

## [1.1.0] - 2026-09-26

### 新增

- **高级超频模块**：装进任意 MI 电动机器（含 EI 处理阵列控制器）的「超频模块」槽。
  与 MI 原版超频模块的区别：**开局即按最高效率运行**，且**做完立刻释放配方**（原版靠空转耗电钉住效率，
  代价是锁死配方）。实现走 MI 官方 hook（`MIHookEfficiency`），不修改 MI 逻辑；**原版模块行为不变**。
  tooltip 按 MI 惯例把说明收在 Shift 里（复用 MI 自己的提示键）。配方为高压（数字）档：
  MI 超频模块×1 + 数字电路×2 + 不锈钢板×4（组装机 / 工作台均可）。贴图由 MI 原版超频模块贴图改色而来
  （MIT，已署名）。
- **升级卡自动嫁接**：自动扫描并继承「原版样板供应器能用的一切升级卡」（含第三方 mod 提供的），
  按各自登记的上限分别装到普通仓与扩展仓；**虚拟合成卡**因功能与供应仓冲突而单独剔除。
- **感应卡重定向**：AppliedFlux 的感应卡原本给供应器周围 6 个方块供电（多方块上全是外壳，用不上），
  现改为给**处理阵列的能源输入仓**供电。能量换算/限速/失效重试仍全部由 AppliedFlux 负责。
- **原版供应器功能对齐**：此前 `pushPattern` 覆写跳过了 AE2 基类的整套逻辑，导致 GUI 里这些设置**形同虚设**。
  现在按原版语义补齐（"目标"换成本仓自己的输入表）：
  - **阻挡模式**：仓里还存着样板输入时不再接收，等机器消耗完再发下一份；
  - **锁定合成模式**：低/高电平锁定（红石控制）、红石脉冲解锁、以及**直到产物返回网络才发下一份**。
    其中"直到产物返回"依赖 AE2 自己的 `PatternProviderReturnInventory` 回调解锁，而本 mod 的产物正是经
    `returnInv` 回网的，因此整条链天然接通（基类的私有 `onPushPatternSuccess` 已逐行复刻）。
  - 优先级、样板访问终端可见性等本来就在基类里，未受影响。
  - **EAEP 的「智能阻挡」被天然屏蔽**：它是挂在 AE2 **基类** `pushPattern` 方法体上的 `@WrapOperation`，
    而本 mod 的 `pushPattern` 不调用 `super`，因此永远轮不到它 —— 无需改动对方配置。
  - **一个阵列只允许一个供应仓**：放第二个（含扩展仓）会把整个多方块判为**结构无效**（控制器显示「结构无效」，
    服务端日志给出提示）。产物会被写进聚合库存里任意可接纳的输出格、并不保证落回发料的那一仓，而各仓的锁定/
    回收/`LOCK_UNTIL_RESULT` 都是按仓独立的 —— 产物落到别的仓就会让发料仓永远等不到自己的产物而卡死。
    另外，供应仓未被任何多方块匹配上时不再接收材料，避免 AE 把料灌进一个不会工作的仓。
- **网格节点创建加固**：不再只依赖 `GridHelper.onFirstTick`（该回调在某些加载/替换方块实体的场景下不触发），
  `tick()` 每帧兜底创建 —— 节点缺失会导致供应仓永远离线。
- **频道卡连接在重进存档后不再断开**：EAEP 的频道卡是在 `PatternProviderLogic.readFromNBT` 的 TAIL 就
  `onLoaded()` 的，那时我们的网格节点尚未创建，它内部的 `wakeNode` 落空 → 链路再也建不起来（电缆连接不受影响，
  这正是「只有频道卡会断」的原因）。现在节点就绪后会再请求它恢复一次。

### 修复

- **读档后样板失效**：补上 AE2 `PatternProviderBlockEntity#onReady()` 里的 `logic.updatePatterns()`。
  此前读档后 `getAvailablePatterns()` 恒为空 —— 终端里看得见供应器与样板，但样板完全不参与合成。
- **读档后材料发配了但阵列不工作**：不再用不可控的 AE2 `PatternProviderTarget.get(...)` 解析目标存储，
  改为直接插入本仓输入表。
- **能合成但扳手 GUI / Jade 看不到材料**：`MIInventory` 会深拷贝传入的槽位对象、且每次读档重建一批，
  导致「处理阵列」与「GUI/方块能力」各看一套库存。现统一为**内容搬回我方永不更换的对象、并让 MI 的列表
  指向它们**，并补 `setChanged()` / `sync()` 让 GUI 立即反映。
- **读档后短暂工作随后永久停工**：多方块的库存聚合发生在「区块加载触发的重匹配」时，若之后更换槽位对象，
  阵列手里的引用会悬空 —— 表现是能把存盘的旧配方做完，但之后再也匹配不到配方。上一条的修复同时解决此问题。
- **取消合成不回收材料、不重置格子锁**：清理判据不再依赖可能一直挂着的输出槽机器锁，改为「ME 网络是否
  还在请求本样板的产物」+ 40 tick 去抖。
- **读档后误回收正在使用的材料**：`activePattern` 是瞬态字段，读档后为 null；此时改问「本供应器任何样板
  的产物是否仍被请求」，避免把 AE 排队中（AE 合成队列是持久化的）的材料退回网络。
- **发配时部分接纳会静默丢料**：模拟校验改为要求**完整**接纳。

### 兼容性

- **ExtendedAE-Plus 智能翻倍**：修复「开启后不发配材料」。EAEP 会推一个包装过的缩放样板，且它靠
  `@WrapOperation` 改写 AE2 `pushPattern` 里的 `List.contains` 来放行；我们覆写了 `pushPattern` 且不调
  `super`，那条 mixin 就失效了。现复刻其判定语义（反射读 `getOriginal()` 退回原始样板匹配）。
- **MI-Tweaks**：可共存，且本 mod 优先级更高（tesseract 的 hook 集合按优先级升序调用、后跑者生效；
  本 mod 用 `10000`，MI-Tweaks 用 `Integer.MIN_VALUE`）。装了高级超频模块的机器由本 mod 决定，
  未装的仍按其配置。
- **MI-Efficiency-Remover**：声明为**不兼容**（`type="incompatible"`）。它把效率全局锁满并 `cancel` 掉
  `increase/decreaseEfficiencyTicks`，那些 `cancel` 无法被外部 mixin 撤销，做不到柔性失效；
  高级超频模块已提供同效果的按机器版本。

### 其它

- 扩展样板供应仓（36 槽 × 页数）与 ExtendedAE 的 ex_pattern_provider 菜单/分页集成。
- 新增冒烟测试自检：感应卡能量重定向、以及**真实复现「重进存档」**验证样板注册（红/绿测试）。

## [1.0.0] - 2026-09-20

- 首个版本：ME 样板供应仓 —— 替代 EI 处理阵列原生的 4 个仓口，作为 AE2 样板供应器使用。
