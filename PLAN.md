# MI-AE2 Pattern Provider（ME 样板供应器接口）项目计划

> 状态：已完成（阶段 1~7 全部完成，功能已在测试客户端联调验证）。
> 目标游戏版本：1.21.1 NeoForge（测试客户端 `minecraft\.minecraft\versions\1.21.1-NeoForge-MI`）。

## 1. 项目概述

做一个 **Extended Industrialization（EI）** 的附属 mod，功能是与 **Applied Energistics 2（AE2）** 联动，提供一个「ME 样板供应器接口」方块：

- 它是 EI「处理阵列」（Processing Array）的一个**仓（hatch）**，单方块同时承担物品输入 / 物品输出 / 流体输入 / 流体输出四种接口的角色（取代原版 4 种仓）。
- 它同时是一个 **AE2 网格节点 + 样板供应器**，玩家往里面放样板（与 AE2 原版样板供应器一致）。
- AE2 下单后：样板输入物（物品 + 流体）被注入**本方块自己的仓库存** → 处理阵列拿去合成 → 成品写回**本方块自己的仓库存** → 我方把成品抽回 ME 网络。

- **mod id**：`mi_ae2_pattern_provider`
- **项目文件夹**：`D:\DshWorkSpace\MCMOD\MI-AE2-PatternProvider\`
- **AE2 依赖**：硬依赖（required）
- **参考 mod**（用户提供，在项目文件夹内，已反编译到 `.ref\mih-src`、`.ref\misu-src`）：
  - `mi_itemfluid_hatch-1.0.3.jar`：物品+流体合一输入/输出仓（作者 KuDikan）
  - `mi_stack_upgrade-1.0.2.jar`：增加输入输出仓堆叠（作者 KuDikan）

## 2. 依赖与版本（测试客户端实测）

| 依赖 | 版本 | 作用 |
|---|---|---|
| NeoForge | 1.21.1（21.1.x） | 运行时 |
| Modern Industrialization | 2.5.6 | `HatchBlockEntity` / `HatchTypes` / `MIInventory` / 机器注册 |
| Extended Industrialization | 1.16.2 | `ProcessingArrayBlockEntity`（处理阵列） |
| tesseract-api | 1.12.16 | EI 前置，`AbstractElectricMultipliedCraftingMultiblockBlockEntity` |
| Applied Energistics 2 | 19.2.17 | `PatternProviderLogic` / `PatternProviderLogicHost` / `GridHelper` |

构建约定（沿用工作区其它 1.21.1 项目）：ModDevGradle 1.0.17、JDK 21、gradle 8.14.3、共享 `GRADLE_USER_HOME`。依赖 jar 放本项目 `libs/`，`compileOnly files(...)`；`mods.toml` 声明依赖（参考 mih 的 `[[dependencies]]` 写法：`neoforge`/`minecraft`/`modern_industrialization` 加 `extended_industrialization`/`tesseract`/`ae2`）。

## 3. 关键调研结论（已反编译核实）

反编译产物：`.ref\mi-src`（MI 2.5.6）、`.ref\ei-src`（EI 1.16.2）、`.ref\ae2-src`（AE2 19.2.17）、`.ref\mih-src`、`.ref\misu-src`。

### 3.1 MI 仓（hatch）系统

- `HatchBlockEntity extends MachineBlockEntity implements Tickable`：
  - `abstract HatchType getHatchType()` —— 一个方块实体只返回一个 HatchType。
  - `appendItemInputs/appendItemOutputs/appendFluidInputs/appendFluidOutputs(List)` —— 多方块聚合库存时调用，把本仓的 `ConfigurableItemStack`/`ConfigurableFluidStack` 加进多方块总列表。
  - `link()/unlink()/clearRemoved()` 生命周期。
- `HatchTypes.register(id, ...)` 是公开 API，可注册自定义类型。
- 标准仓：`ItemHatch`/`FluidHatch`，靠 `input` 布尔只贡献 input **或** output 之一。

### 3.2 形状匹配与库存聚合

- `ShapeMatcher.matches(pos)`：位置是 `HatchBlockEntity` 时，取该位置 `HatchFlags`，`flags.allows(hatch.getHatchType())` 判定。
- `AbstractCraftingMultiblockBlockEntity.onRematch()` → `MultiblockInventoryComponent.rebuild(shapeMatcher)`：按 Y 排序所有匹配仓，调用 4 个 `appendXxx` 聚合成 4 个平面列表（itemInputs/itemOutputs/fluidInputs/fluidOutputs）。**合成直接读写这些仓自己的 `MIInventory`，与物理位置无关（位置只影响形状匹配）。**

### 3.3 EI 处理阵列

- `ProcessingArrayBlockEntity`，`ProcessingArrayMachineComponent` 持有工作方块 `ItemStack machines`。
- 形状：front=`ENERGY_INPUT`；top=`ITEM_INPUT,FLUID_INPUT`；bottom=`ITEM_OUTPUT,FLUID_OUTPUT`。

### 3.4 AE2 样板供应器

- `PatternProviderBlockEntity extends AENetworkedBlockEntity implements PatternProviderLogicHost`，持有 `PatternProviderLogic`。
- `PatternProviderLogic(IManagedGridNode, PatternProviderLogicHost[, size])`：样板清单 + `sendList` + `returnInv`；`pushPattern()` 推进目标库存，`returnInv.injectIntoNetwork()` 把成品回注网络。
- `PatternProviderLogicHost` 默认提供 config/优先级/`openMenu(PatternProviderMenu)`/样板清单访问。
- 节点：`GridHelper.createManagedNode(this, listener)`，经 `IInWorldGridNodeHost` 能力被发现。

### 3.5 参考 mod：mi_itemfluid_hatch（合一仓 + 匹配多种类型）

- `MultiblockBusHatches`：`HatchTypes.register(MI.id("item_fluid_input"), ...)` 注册 `ITEM_FLUID_INPUT`/`ITEM_FLUID_OUTPUT`/`ITEM_FLUID_FORBID` 三个自定义类型。
- `HatchFlagsMixin`（**核心**）：mixin MI 的 `HatchFlags.allows(HatchType)`：
  ```java
  if (type == ITEM_FLUID_INPUT) return !allowed.contains(FORBID)
      && (allowed.contains(ITEM_INPUT) || allowed.contains(FLUID_INPUT) || allowed.contains(ITEM_FLUID_INPUT));
  // ITEM_FLUID_OUTPUT 同理，映射 ITEM_OUTPUT/FLUID_OUTPUT
  ```
- `ItemFluidHatch extends HatchBlockEntity`：`MIInventory`（物品槽 + 流体槽），按 `input` 布尔贡献 append；`getHatchType()` 返回 `ITEM_FLUID_INPUT/OUTPUT`。
- 注册：`MachineRegistrationHelper.registerMachine(name, id, factory, Consumer[]{registerFluidApi, registerItemApi})`。

### 3.6 参考 mod：mi_stack_upgrade（超堆叠）

- `ConfigurableItemStackMixin`：加 `oversize$multiplier` 字段（NBT `ex$multiplier`），`adjustedCapacity = 64*mult`，`@Redirect` 把 `ItemVariant.getMaxStackSize()` × mult。
- 升级物品经 datamap `ItemStackUpgrade(stackMultiplier)`（`OversizeDataMaps.ITEM_STACK_UPGRADES`）；`StackUpgradeComponent` 持有升级物品，`ItemHatchMixin` 实现 `IStackSizeMultiplierUpgradable.oversize$upgradeStackSize()` 把倍率应用到该仓所有 `ConfigurableItemStack`。

### 3.7 MI `ConfigurableItemStack` 容量事实（决定「百万物品」如何做）

- `amount` 是 **`long`**（能存大数，无 codec 问题——区别于 vanilla ItemStack 的 intRange(1,99)）。
- 但 `getCapacity() = min(adjustedCapacity, key.getMaxStackSize())`，`adjustedCapacity` 被 `adjustCapacity()` 钳在 **0~64**。
- 结论：AE2 侧 `MEStorage.insert(AEItemKey, long)` 可传百万，但**我方 MI 槽默认容量 64，必须放大**才能接住（见 §4.4）。

## 4. 架构设计

### 4.1 方块 `MePatternProviderBlockEntity`

- `extends HatchBlockEntity`（MI）。
- `getHatchType()` 返回自定义类型 `mi_ae2_pattern_provider:me_pattern_provider`（`HatchTypes.register`）。
- 持有 `MIInventory`，含：物品输入槽（`standardInputSlot`）+ 物品输出槽（`standardOutputSlot`）+ 流体输入槽 + 流体输出槽。
- **输入 / 输出仓库严格分开（硬性约束）**：输入槽只进 `appendItemInputs`/`appendFluidInputs`，输出槽只进 `appendItemOutputs`/`appendFluidOutputs`，二者绝不进同一个列表。
  > 若共用：MI 合成器从 `itemInputs`/`fluidInputs` 匹配配方，成品一旦也在这两个列表里，就会被当成下一份配方的原料继续消耗掉（用户明确指出此坑）。因此输入槽与输出槽必须是**不同的 `ConfigurableItemStack`/`ConfigurableFluidStack` 对象**，分别挂到输入/输出列表。
- 实现 `IInWorldGridNodeHost` + `IGridNodeListener` + `PatternProviderLogicHost`。
- 注册能力：物品/流体能力（`registerItemApi`/`registerFluidApi`）、`AECapabilities.IN_WORLD_GRID_NODE_HOST`、`AECapabilities.ME_STORAGE`。
- **为未来「扩展供应仓」预留**：方块实体构造参数化槽位数量（参照 mih 的 `registerItemFluidHatches` / `registerExItemFluidHatches`），普通版与扩展版只是槽数不同的两次 `registerMachine`，共享同一套方块实体 + AE2 逻辑。

### 4.2 匹配多种仓 —— mixin `HatchFlags.allows`（沿用 mih 思路）

- 仅需一个自定义类型 `ME_PATTERN_PROVIDER`。mixin `HatchFlags.allows(type)`：
  - `type == ME_PATTERN_PROVIDER` 时，返回 `allowed.contains(ITEM_INPUT) || allowed.contains(FLUID_INPUT) || allowed.contains(ITEM_OUTPUT) || allowed.contains(FLUID_OUTPUT)`。
- 这样单方块在 EI 处理阵列的 top（输入）和 bottom（输出）位置都被接受；实际只需放一个在 top 即可同时承担输入+输出（靠 §4.1 的 append 覆写）。
- **优势**：只 mixin MI 的稳定小方法 `HatchFlags.allows`，不碰 EI，风险远低于原「mixin EI 静态块」。

### 4.3 AE2 侧

- 建节点：`mainNode = GridHelper.createManagedNode(this, this)`，`setFlags(REQUIRE_CHANNEL)`、`setInWorldNode(true)`、`setTagName(...)`。
- 生命周期：`clearRemoved()` → `GridHelper.onFirstTick(...)` → `mainNode.create(level,pos)`；`setRemoved()`/卸载 → `mainNode.destroy()`（沿用 SimpleAutoFarm 已验证写法）。
- `PatternProviderLogic` 子类：
  - 覆写 `pushPattern`：把样板输入注入**自身输入槽**（物品 `AEItemKey`↔`ItemStack`、流体 `AEFluidKey`↔流体量，经自身 `MEStorage`/物品+流体能力桥接），处理锁定模式。
  - 输出路径：在 MI `tick()` 里把输出槽成品搬进 `returnInv`（`getReturnInv()`），父类 `doWork()` 自动 `injectIntoNetwork`。
  - `getTargets()` 返回空集或单方向（仅用于终端分组显示）。

### 4.4 超堆叠（AE 百万物品）—— 直接到上限，不做升级

- 必要性：AE2 `pushPattern` 一次性推入整份样板输入量，MI 槽默认容量 64，接不住。
- **决策（用户已定）**：ME 样板供应仓（及未来的扩展供应仓）只有一个等级，**不做升级物品 / datamap / 倍率进阶**，槽容量直接设到上限。
- 物品槽：mixin `ConfigurableItemStack` 加一个「无上限」标记（访问器接口，我方方块创建槽后调用置位），使 `getCapacity()` / `getRemainingCapacityFor()` / `getTotalCapacityFor()` / 槽 `getMaxStackSize()` 返回 `Integer.MAX_VALUE`（照抄 mi_stack_upgrade 的 `ConfigurableItemStackMixin`，但把 multiplier 换成固定的「无上限」，去掉升级物品 / `StackUpgradeComponent` / datamap）。
- 流体槽：容量本身是构造参数（`standardInputSlot(capacity)`），直接传超大值即可（需实测 `ConfigurableFluidStack` 是否有类似钳制）。
- 显示：`toStack()`/GUI 用 `(int)amount` 截断，超大值仅影响 GUI 显示，逻辑以 `long` 为准（`ConfigurableItemStack.amount` 是 `long`）。

### 4.5 GUI

- 主界面用 AE2 `PatternProviderMenu`（放样板），`PatternProviderLogicHost.openMenu` 默认已接好。
- MI `MachineBlockEntity` 自带槽位 GUI 倾向隐藏（库存由 AE2 自动管理），是否保留 MI 自动弹出/朝向 GUI 待实现时定；参考 AdvancedAE/ExtendedAE 自定义样板 Menu/Screen。

## 5. 实施步骤（分阶段）

1. **脚手架**：`MI-AE2-PatternProvider` 工程（build.gradle / mods.toml / settings.gradle / 主类），`libs/` 放 MI/EI/tesseract/AE2 四个 jar，空 mod 能编译进客户端。
2. **MI 仓方块**：`MePatternProviderBlockEntity extends HatchBlockEntity`（MIInventory 四类槽 + 4 个 append 覆写 + 自定义 HatchType + 方块/物品/模型注册），暂不做 AE2。
3. **HatchFlags mixin**：mixin `HatchFlags.allows` 让单方块被处理阵列顶/底 hatch 槽接受，验证阵列成形 + 合成（此时手动放料）。
4. **AE2 集成**：网格节点 + `PatternProviderLogic` 子类 + 自身库存 push/pull 桥接。
5. **超堆叠**：按 §4.4 选定的方案放大槽容量，实测 AE 大量推入。
6. **GUI**：样板菜单/界面接入。
7. **联调**：处理阵列 + 样板 + AE2 下单，验证物品/流体输入 → 合成 → 成品回网络全流程；`runClient` 冒烟（日志 `run\logs\latest.log`）。

## 6. 参考资料

- 反编译源码：`.ref\mi-src`、`.ref\ei-src`、`.ref\ae2-src`、`.ref\mih-src`、`.ref\misu-src`
- 参考 mod（项目文件夹内）：`mi_itemfluid_hatch-1.0.3.jar`、`mi_stack_upgrade-1.0.2.jar`
- 同类实现（测试客户端内）：`AdvancedAE`（`AdvPatternProviderLogic`）、`ExtendedAE`（`TileExPatternProvider`）
- 上游仓库：[Modern Industrialization](https://github.com/AztechMC/Modern-Industrialization)、[Extended Industrialization](https://github.com/Swedz/Extended-Industrialization)、[Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2)
- AE2 文档：[The Pattern Provider](https://guide.appliedenergistics.org/1.21.1/items-blocks-machines/pattern_provider)

## 7. 风险与待验证项

- **HatchFlags mixin**：目标是 MI `HatchFlags.allows`（稳定小方法），风险低；MI 升级需复核（mih 的 `require = 1` 会在签名变化时报错提醒）。
- **单方块同时当输入+输出仓（输入/输出仓库必须分开）**：输入槽只进输入列表、输出槽只进输出列表，杜绝「成品被当原料再消耗」；实现时实测合成器在同一仓内先扣输入后写输出、且输出不会回流到输入。
- **AE2↔MI 库存桥接**：MI `ConfigurableStack`/fabrictransfer ↔ Forge 能力 ↔ AE2 `MEStorage` 的双向映射边界。
- **流体样板**：处理样板支持流体输入/输出，确认 `pushPattern` 对 `AEFluidKey` 的注入路径。
- **超堆叠直接到上限**：mixin `ConfigurableItemStack` 加「无上限」标记；确认 `ConfigurableFluidStack` 容量传超大值无钳制、以及 `Integer.MAX_VALUE` 是否够用（用户场景「百万级」远低于 2.1B，够）。
- **GUI 双系统共存**：MI 菜单体系与 AE2 `PatternProviderMenu` 并存。

## 8. 完成总结（2026-09-06）

阶段 1~7 全部完成，功能已在测试客户端联调验证。

### 最终实现

- **方块**：`MePatternProviderBlockEntity extends HatchBlockEntity`，单方块 4×9 槽（物品输入/输出、流体输入/输出，各 9 格）；容量直接 `Integer.MAX_VALUE`（超堆叠，单等级、无升级）。
- **仓匹配**：自定义 HatchType `me_pattern_provider` + mixin `HatchFlags.allows` 映射到 ITEM/FLUID 输入/输出。
- **AE2 集成**：`IInWorldGridNodeHost + IGridNodeListener + PatternProviderLogicHost + IActionHost`；`MePatternProviderLogic extends PatternProviderLogic`，push 目标=自身库存，输出经 `returnInv` 回注网络。
- **GUI**：右键开 AE2 `PatternProviderMenu`（放样板）；**手持扳手（`#modern_industrialization:wrenches`）右键开 MI 槽位 GUI**（卡物品的后手）。GUI 标题「ME样板供应仓」（mixin `AEBaseScreen` 覆盖 `dialog_title`）。
- **命名**：样板管理终端显示「<工作方块>处理阵列样板供应仓」（覆写 `getTerminalGroup()`）；无工作方块时「ME样板供应仓」。
- **材质**：自定义机壳 `MachineCasings.create`（`供应仓.png`，单独放置/拿手上的样式）+ 成型后 `overlay_side.png` 侧面叠层（去掉 item_auto/fluid_auto/output）。
- **挖掘掉落**：覆写 `dropExtra()` 调 `logic.addDrops()` 掉落样板/返回区/在途输出；`mineable/pickaxe` + `needs_stone_tool` 标签。
- **扩展仓（ME 扩展样板供应仓，36 样板，对齐 ExtendedAE）**：复用同一 `MePatternProviderBlockEntity`（构造参数化 `patternSlots`/`hatchType`/`extended`/`iconSupplier`），`MePatternProviderLogic(mainNode, host, 36)`；菜单 `ContainerExtendedPatternProvider` + 界面复用 ExtendedAE 的 `ex_pattern_provider.json` 样式；命名「ME扩展样板供应仓」/「xxx处理阵列扩展样板供应仓」。
- **升级**：手持 `extendedae:pattern_provider_upgrade` + shift 右键普通仓 → `RightClickBlock` 事件里 `upgradeToExtended()`（`saveWithFullMetadata`→换块→`loadWithComponents` 保留库存+样板）。
- **配方**：① 普通仓合成表（玻璃线缆×4 + 物品输入/输出仓 + 流体输入/输出仓 + AE2 样板供应器，仓按任意等级标签匹配）；② 扩展仓水晶装配器配方（`extendedae:crystal_assembler`，`neoforge:mod_loaded extendedae` 门控）。

### 与计划的差异

- 超堆叠：用「无上限」标记 mixin `ConfigurableItemStack`（`Integer.MAX_VALUE`），未做升级/倍率（用户决定）。
- GUI：未做自定义 Menu/Screen，复用 AE2 `PatternProviderMenu`；MI 槽位 GUI 仅作扳手后手。
- 保存：MI `saveAdditional/loadAdditional` 是 `final` → 用自定义 `MachineComponent` 挂 `logic` 的 NBT。
- 扩展仓方块**仅 ExtendedAE 存在时注册**；挖掘标签（`mineable/pickaxe`、`needs_stone_tool`）用 NeoForge 的 per-entry 条件（`neoforge:conditions` + `mod_loaded extendedae`）门控扩展仓条目，普通仓无条件。这样无 ExtendedAE 时扩展仓不存在、也不会因标签引用未注册方块报错。
