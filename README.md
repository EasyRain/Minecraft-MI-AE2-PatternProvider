# MI AE2 Pattern Provider（ME 样板供应仓）

一个 [Modern Industrialization](https://github.com/AztechMC/Modern-Industrialization) + [Extended Industrialization](https://github.com/Swedz/Extended-Industrialization) 的附属 mod，为 EI 的「处理阵列」（Processing Array）提供一个与 [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2) 联动的 **ME 样板供应仓**。

## 功能

- **四合一仓**：单个方块同时承担物品输入 / 物品输出 / 流体输入 / 流体输出四种接口，取代处理阵列原本的 4 种仓。
- **AE2 样板供应器**：本身是 AE2 网格节点 + 样板供应器，可在其中放置合成样板；AE2 下单后，样板输入物（物品 + 流体）被注入自身仓库存 → 处理阵列拿去合成 → 成品写回自身仓库存 → 自动抽回 ME 网络。
- **超堆叠**：**输入槽**容量直接到上限（`Integer.MAX_VALUE`），可接住 AE2 一次性推入的整份样板输入。
  **输出槽保持普通容量（64）**——有意为之：处理阵列会按「输出槽还装得下多少」反推并行倍率，而 tesseract 的真插入
  一格只装 64 且**会静默丢弃**超出的产物（详见 CHANGELOG「处理阵列单次产物超过 64 时被静默销毁」）；如实报 64
  才能让「算得多少」与「装得多少」一致，大合成会分成多次合成完成，总耗电不变。
  另外下单前有一道**输出格容量守卫**：接料之前核对「容量查询路径承诺的空间」是否大于「真插入的上限」
  （顺带把自己遗留的无上限标志夹回 64），不一致就整体拒收、让 AE 每 tick 重试 —— 宁可「一直等待」，
  也不让产物被静默销毁。
  为了不因此损失输出效率，**输出格会按产物摊开锁满**（每个产物保底 1 格，剩下的格补给当前瓶颈产物）：
   物品输出共 **4 排 = 36 格**（瞬时输出空间 `36 × 64 = 2304` 件）⇒ 倍率上限 `64 × 格数 / 单次产量`，
   一原材料出多种加工材料时也不会把格子全给同一种产物（只给 1 排时上限会被卡在 `64 / 单次产量`）。
   额外 1 排是给整合包并行仓留余量（如 bingxing「现代工业化并行仓」的精英 256 / 终极 512 / 创造 2048 档：
   它在同一 tick 内重跑最多 2047 遍配方且**不检查输出空间**，装不下的产物会被静默销毁，所以要求
   `并行等级 × 每批物品产物` 一次性装得下）；4 排 = 246px 已接近 MI 背景贴图 256 的上限，是不换贴图的极限。
- **自动命名（管理终端里）**：控制器里放了工作方块（EI 的处理阵列、**Industrialization Overdrive 的多方块处理阵列**，或任何实现 tesseract `ComponentStackHolder` 接口的阵列）→「<工作方块>处理阵列样板供应仓」；放在**普通多方块**里（电力高炉、蒸馏塔……）→「<控制器名>样板供应仓」；没成形时回落默认名。扩展仓对应位置带「扩展」。识别走**通用组件接口**（并确认那个组件里装的是真正的机器方块，不会把升级槽里的升级物品当成工作方块），所以不硬依赖 Overdrive 或其它阵列 mod（只依赖 MI + tesseract）。
- **挖掘保留内容**：挖掘时样板、返回区物品、在途输出都会随方块掉落。
- **扩展供应仓（ME 扩展样板供应仓）**：样板槽按 **4 页（144 个）**预留，默认解锁 1 页（36，4×9，对齐 ExtendedAE 的扩展样板供应器）；装 ExtendedAE-Plus 的**扩容卡**每张多解锁 1 页（最多 3 张 → 4 页）。
- **升级卡**：自动继承「原版样板供应器能用的一切升级卡」。当前实测可用的有 ExtendedAE-Plus 的**频道卡**、AppliedFlux 的**感应卡**，以及（仅扩展仓）ExtendedAE-Plus 的**扩容卡**。设计上是**扫描**而非白名单——任何附加 mod 只要给原版供应器登记了升级卡，本 mod 就自动跟着支持，只有明确冲突的卡才单独剔除（目前是 ExtendedAE-Plus 的虚拟合成卡，其机制与本仓冲突）。
- **感应卡的行为差异（有意为之）**：AppliedFlux 原版的感应卡是给供应器**周围 6 个方块**供电——插在多方块上的供应仓周围是阵列外壳，完全用不上。本 mod 把它**重定向为给处理阵列的能源输入仓供电**（即阵列里放在控制器正面的那个/那几个仓）。能量换算、速率限制、失效重试仍然全部由 AppliedFlux 自己完成，本 mod 只是把「送进哪个位置」换掉。
- **高级超频模块**：装进**任意 MI 电动机器**（含 EI 处理阵列控制器）的「超频模块」槽。与 MI 原版超频模块的区别：

  | | MI 原版超频模块 | 本 mod 高级超频模块 |
  |---|---|---|
  | 效率 | 靠「空转也持续耗电」钉住，**从 0 慢慢爬** | 直接设为上限，**开局即满速** |
  | 配方 | **锁死**，直到拆下模块或断电 | **做完立刻释放**，马上接下一个 |

  实现走 MI 官方 hook（`MIHookEfficiency`），不修改 MI 的任何逻辑；**MI 原版模块的行为完全不变**。物品的 tooltip 也按 MI 惯例把说明收在 Shift 里（按住才展开，未按住显示 MI 原生的「按住 [Shift] 以查看信息」）。
- **量子超频模块**：**高级超频模块的升级版**，装进**同一个**「超频模块」槽（两者**互斥**，一个槽只能放一个），
  既有高级超频的全部效果，**又给 EI 处理阵列提供并行**：把阵列的倍率天花板从「机器数」抬到「**机器数 × 倍率**」
  （倍率默认 **4×**、在 config 里可调 **2–16×**；64 机器的阵列 = 256 并行）。
  真实并行倍率仍由阵列自己按**输入料量**与**输出格空间**反推（上游 `MultipliedCrafterComponent#calculateMultiplier` 的二分），
  所以**装不下时自动降倍率，绝不吞产物**；EU 与耗时按上游语义同倍放大（4× 速度 = 4× EU/t、**总能量 4×**，
  能源供不上时耗时线性拉长），**不做免费产能**。
  **并行只对处理阵列生效，而且模块根本装不进别的机器**：右键或从 GUI 拖进单方块机器 / 其它多方块机器会被**拒收**
  （右键会在**聊天框**给一句「量子超频模块仅支持处理阵列」），**高级模块与原版模块照旧到处可插**。判定依据是「可插 = 并行真的会生效」这个
  等价关系（宿主标记由两个并行 mixin 顺手贴上 ⇒ 注入点失效时会被立刻拒收，而不是白装一次）。那些机器本来也没有
  「机器数」这一层（单方块走 MI 的 `CrafterComponent`，一次一个配方；EI 大型电炉与 IO 热解炉的
  `getMaxMultiplier()` 是**线圈档位的批处理上限**，不是机器数），本 mod 刻意不碰它们。
  倍率在 `config/mi_ae2_pattern_provider-common.toml` 的 `[quantum_overclock] parallel_multiplier` 里改
  （默认 4、范围 2–16；该文件的绝对路径会在启动时以 INFO 打进日志）。
  **装了 [Cloth Config](https://modrinth.com/mod/cloth-config) 就能在「Mods 界面 → 本 mod → Config」里游戏内直接改**，
  保存后**立即对下一次配方计算生效、不需要重启游戏**（倍率是每次算并行时现读的，这条有冒烟自检钉住）。
  两个超频模块都列在 **MI 自己的创造栏**里（紧跟原版超频模块之后，方便对照），因此在 JEI 里直接搜得到。
  为什么上限是 16×：本仓输出空间 `36 格 × 64 = 2304` 件，官方配方里「每批物品产物量 P」的分布是
  `≤1 52.6% / ≤2 66.1% / ≤4 77.2% / ≤9 95.1%`，而 `倍率 ≤ 36/P`（P=1→36、P=2→18、P=4→9、P=9→4）——
  16× 已在「单产物配方理论极限 36×」的一半以下，属于保守取值。
  已知会**随倍率被同比例放大**的上游（tesseract）缺陷，不是本模块引入的，但装它之前应该知道：
  多个流体产物共用一个输出槽时，可行性检查不扣减前一个产物的占用；概率产物完全不参与可行性检查；
  `recipeEnergy` 写档用 `putLong`、读档用 `getInt`（配方总 EU × 倍率 > `2^31` 时读档后进度损坏，
  阈值 `总EU > 2^31 / (64 × 倍率)`）。详见 [CHANGELOG](CHANGELOG.md) 的「量子超频模块」与「已知问题（上游）」两节。
- **合成表**：玻璃线缆 + 任意等级的物品/流体输入/输出仓 + ME 样板供应器。

## 交互

| 操作 | 打开的界面 |
|---|---|
| 空手右键 | AE2 样板供应器界面（放样板） |
| 手持扳手（`#modern_industrialization:wrenches`）右键 | MI 槽位界面（手动取出卡住的物品/流体） |
| 手持 `extendedae:pattern_provider_upgrade` + shift 右键（普通仓） | 升级为扩展供应仓 |

## 配方

- **普通供应仓**（工作台）：
  ```
  玻璃线缆 | 物品输入仓 | 玻璃线缆
  流体输入仓 | ME样板供应器 | 流体输出仓
  玻璃线缆 | 物品输出仓 | 玻璃线缆
  ```
  玻璃线缆匹配任意颜色（`#ae2:glass_cable`），物品/流体输入输出仓匹配任意等级。

- **扩展供应仓**（ExtendedAE 水晶装配器）：普通供应仓 + 容量卡×3 + 工作台×3 + 并发处理器 + 玻璃线缆×6。

- **高级超频模块**（MI 组装机，或工作台）：

  ```
  s d s      s = 不锈钢板  (stainless_steel_plate)
  s c s      d = 数字电路  (digital_circuit)
  s d s      c = MI 超频模块 (overdrive_module)
  ```
  组装机配方比工作台省两片：组装机 = 不锈钢板×4、数字电路×2、超频模块×1（`eu 16` / `duration 300`）；工作台 = 不锈钢板×6、数字电路×2、超频模块×1。

  档位对齐说明：MI 原版「超频模块」用到 `electronic_circuit_board`（**中压**档），本模块只往上走一级——`digital_*` 是**高压**档（同一档的材料还有 `stainless_steel_plate` / `aluminum_cable` / `silicon_battery`）。**刻意不使用 `quantum_*`（超导压）与铱板**，避免为了一个自动化道具横跨两三个电压等级。

- **量子超频模块**（MI 组装机，或工作台）：

  ```
  s . s      s = 超导体板    (superconductor_plate)
  q a q      q = 量子电路    (quantum_circuit)
  s . s      a = 高级超频模块 (advanced_overclock_module)
  ```
  组装机配方与之一致（超导体板×4、量子电路×2、高级超频模块×1，`eu 64` / `duration 1000`）。

  档位对齐说明：既然叫「量子」，材料就走**超导压**档，不再复用高级模块的高压档材料。量子电路本身还很贵（每个需要一张量子电路板 = 12 根超导电缆 + 6 片铱板 + 2 个钚电池 + 50 mB 氦-3，外加 2 量子比特 / 2 处理单元 / 2 冷却单元），所以 2 个量子电路的模块约为 MI 自家 `quantum_upgrade`（8 个量子电路 + 奇异物质 + 50 mB 反物质）的四分之一——是「比高级模块高一整档」而不是直接跳到终局。

## 依赖（Minecraft 1.21.1 / NeoForge）

| 依赖 | 版本 | 类型 |
|---|---|---|
| Applied Energistics 2 | 19.2.17 | required（硬依赖） |
| Extended Industrialization | 1.16.2 | required（硬依赖，提供处理阵列） |
| ExtendedAE | 1.21-2.2.21+ | optional（只在装了它时才注册「扩展样板供应仓」，其界面复用 ExtendedAE 的 ex_pattern_provider 菜单） |
| Industrialization Overdrive | 1.14.0+ | optional（不是依赖；它的「多方块处理阵列」会被自动命名认出来，见上方「自动命名」） |

> Modern Industrialization、Tesseract API、GuideME 由 AE2 / EI 传递引入（EI 硬依赖 MI + tesseract + guideme；AE2 硬依赖 guideme），无需单独声明。
> ExtendedAE-Plus / AppliedFlux 不是依赖：装了它们，对应的升级卡才存在；没装则本 mod 照常工作，只是扩展仓回到 1 页样板、且没有那些卡可插。

## 构建

JDK 21 + Gradle 8.14.3（依赖 jar 已放在 `libs/`，开箱即用）：

```bat
gradlew build
```

产物：`build/libs/mi_ae2_pattern_provider-1.2.0-1.21.1.jar`

## 许可证

本 mod 采用 [MIT](LICENSE)。

### 与其它 mod 的集成（不含其代码）

本 mod 可选地与几个第三方 mod 集成。**不打包、不分发它们的任何代码或 jar**（无 JarJar / shading / 源码复制），只做编译期链接与运行时 mixin 目标：

| mod | 许可证 | 集成方式 |
|---|---|---|
| [Modern Industrialization](https://github.com/AztechMC/Modern-Industrialization) | MIT | 硬依赖。「高级超频模块」的**贴图由 MI 原版「超频模块」贴图改色而来**（只把蓝色相映射为橙色相，轮廓/明暗/高光均保留）；「量子超频模块」的贴图再对**本 mod 的高级模块贴图**做一次色相映射（橙相 → 紫相，同样只动色相）。按 MIT 署名原作者 AztechMC |
| [ExtendedAE](https://github.com/GlodBlock/ExtendedAE) | LGPL-3.0 | `compileOnly` 引用其 `ex_pattern_provider` 菜单类型（产物内只有类名引用）；扩展仓的界面与分页由它 + EAEP 提供 |
| [ExtendedAE-Plus](https://github.com/GaLicn/ExtendedAE_Plus) | LGPL-3.0-or-later | 仅以 mixin 目标（字符串类名）适配其升级槽判定；本 mod 不含其代码，EAEP 缺席或改版时自动降级为「功能不可用但不崩」 |
| AppliedFlux | LGPL-3.0 | 仅作为可选的升级槽提供方被使用 |
| [Jade](https://modrinth.com/mod/jade) | CC-BY-NC-SA-4.0 | 悬浮提示插件（`compileOnly`，从 Modrinth Maven 取）。该许可**不允许再分发**，因此它的 jar 既不进产物、也不放进仓库的 `libs/`；未安装 Jade 时该插件不会被加载 |
| [The One Probe](https://modrinth.com/mod/the-one-probe) | MIT | 悬浮提示插件（`compileOnly`，同样从 Modrinth Maven 取）。走 NeoForge 的 IMC 登记，未安装时该插件不会被加载 |

各 mod 的版权归其各自作者所有。若你要二次分发本 mod，请一并遵守上述可选依赖的许可证。

### 悬浮提示：显示设备在线状态

供应仓在 **Jade** 和 **The One Probe（TOP）** 里都会显示一行「**设备在线** / **设备离线**」，与 AE2 自家机器（如 ME 接口）的表现一致。
两者是各自独立的小插件（互不影响，也不经过 AE2 自带的悬浮提示扩展点）：

- **Jade**（`com.miae2.compat.MeProviderJadePlugin`，裸 `@WailaPlugin`）：服务端按**方块实体类型**同步一个布尔，
  客户端按方块类加行、并**先判断这个方块实体是不是本 mod 的供应仓**，因此不会影响其它 MI 机器的悬浮提示。
- **The One Probe**（`com.miae2.compat.MeProviderTopPlugin`）：走 NeoForge 的 IMC（`theoneprobe` + `getTheOneProbe`）
  登记一个 `IProbeInfoProvider`；TOP 在服务端生成提示文本、再由它自己同步给客户端，所以直接读状态即可。

两者都是**可选依赖**：没装对应 HUD 时那两个类不会被加载，mod 的其它功能不受任何影响。

> 为什么不用 AE2 自带的悬浮提示扩展点（IGT）：它是一套同时覆盖 Jade / WTHIT / TOP 的公共抽象，一旦注册就在三个 HUD 里同时生效；
> 而它 Jade 那条路是**按方块类注册**、且适配器**先把方块实体强转成目标类型再去判断** ——
> MI 的所有机器共用同一个 `MachineBlock` 类，走那条路会让其它 MI 机器的悬浮提示报错。各写各的插件，行为完全可控。

### 使用约束：一个阵列只能放一个供应仓

**同一个处理阵列里只允许存在一个 ME 样板供应仓**；放第二个（含扩展仓）会让整个多方块判定为**结构无效**，
控制器会显示「结构无效」，并在服务端日志给出明确提示。移除多余的那个即恢复正常。

原因：阵列的合成产物会被写进**聚合库存里任意一个可接纳的输出格**，并不保证落回「发料的那一仓」。而本 mod
每个仓的产物锁定、余料回收、以及「等自己的产物回到网络才发下一份」（`LOCK_UNTIL_RESULT`）都是**按仓独立**
的 —— 产物落到别的仓时，是那一仓的 `returnInv` 回调解锁它自己的逻辑，发料仓却永远等不到自己的产物，于是卡死。
与其引入跨仓归属的复杂度，不如直接禁止：这样每个供应仓与它的合成任务是一一对应的。

### 与「效率类」mod 的兼容性

| mod | 处理方式 |
|---|---|
| [MI-Tweaks](https://github.com/Swedz/MI-Tweaks)（MIT） | **共存，且本 mod 优先级更高**。tesseract 把各 mod 的效率 hook 放在按优先级升序的集合里依次调用、最后由最后跑的那个决定结果，所以「优先级高 = 说了算」。MI-Tweaks 用 `Integer.MIN_VALUE`（最先跑），本 mod 用 `10000` —— 装了高级超频模块的机器由本 mod 决定，没装的机器仍按 MI-Tweaks 的配置走。 |
| [MI-Efficiency-Remover](https://github.com/Leclowndu93150/MI-Efficiency-Remover) | **声明为不兼容（`type="incompatible"`）**。它把所有 MI 机器的效率**全局**锁成满档（直接 mixin MI 的 `CrafterComponent` 与 tesseract 的 `AbstractModularCrafterComponent`，并 `cancel` 掉 `increase/decreaseEfficiencyTicks`）。那些 `cancel` 无法被外部 mixin 撤销，做不到「运行时柔性失效」，因此只能不兼容——本 mod 的「高级超频模块」提供的是同一效果的**按机器、按道具**版本，删掉它即可。 |
