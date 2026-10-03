# Changelog

本项目遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

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
