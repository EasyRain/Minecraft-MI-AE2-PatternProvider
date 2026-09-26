# MI AE2 Pattern Provider（ME 样板供应仓）

一个 [Modern Industrialization](https://github.com/AztechMC/Modern-Industrialization) + [Extended Industrialization](https://github.com/Swedz/Extended-Industrialization) 的附属 mod，为 EI 的「处理阵列」（Processing Array）提供一个与 [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2) 联动的 **ME 样板供应仓**。

## 功能

- **四合一仓**：单个方块同时承担物品输入 / 物品输出 / 流体输入 / 流体输出四种接口，取代处理阵列原本的 4 种仓。
- **AE2 样板供应器**：本身是 AE2 网格节点 + 样板供应器，可在其中放置合成样板；AE2 下单后，样板输入物（物品 + 流体）被注入自身仓库存 → 处理阵列拿去合成 → 成品写回自身仓库存 → 自动抽回 ME 网络。
- **超堆叠**：槽容量直接到上限（`Integer.MAX_VALUE`），可接住 AE2 一次性推入的整份样板输入。
- **自动命名**：样板管理终端里显示「<工作方块>处理阵列样板供应仓」。
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
  组装机配方与之一致（不锈钢板×4、数字电路×2、超频模块×1，`eu 16` / `duration 300`）。

  档位对齐说明：MI 原版「超频模块」用到 `electronic_circuit_board`（**中压**档），本模块只往上走一级——`digital_*` 是**高压**档（同一档的材料还有 `stainless_steel_plate` / `aluminum_cable` / `silicon_battery`）。**刻意不使用 `quantum_*`（超导压）与铱板**，避免为了一个自动化道具横跨两三个电压等级。

## 依赖（Minecraft 1.21.1 / NeoForge）

| 依赖 | 版本 | 类型 |
|---|---|---|
| Applied Energistics 2 | 19.2.17 | required（硬依赖） |
| Extended Industrialization | 1.16.2 | required（硬依赖，提供处理阵列） |
| ExtendedAE | 1.21-2.2.21+ | optional（只在装了它时才注册「扩展样板供应仓」，其界面复用 ExtendedAE 的 ex_pattern_provider 菜单） |

> Modern Industrialization、Tesseract API、GuideME 由 AE2 / EI 传递引入（EI 硬依赖 MI + tesseract + guideme；AE2 硬依赖 guideme），无需单独声明。
> ExtendedAE-Plus / AppliedFlux 不是依赖：装了它们，对应的升级卡才存在；没装则本 mod 照常工作，只是扩展仓回到 1 页样板、且没有那些卡可插。

## 构建

JDK 21 + Gradle 8.14.3（依赖 jar 已放在 `libs/`，开箱即用）：

```bat
gradlew build
```

产物：`build/libs/mi_ae2_pattern_provider-1.1.0-1.21.1.jar`

## 许可证

本 mod 采用 [MIT](LICENSE)。

### 与其它 mod 的集成（不含其代码）

本 mod 可选地与几个 **LGPL-3.0** 授权的 mod 集成。**不打包、不分发它们的任何代码或 jar**（无 JarJar / shading / 源码复制），只做编译期链接与运行时 mixin 目标：

| mod | 许可证 | 集成方式 |
|---|---|---|
| [Modern Industrialization](https://github.com/AztechMC/Modern-Industrialization) | MIT | 硬依赖。「高级超频模块」的**贴图由 MI 原版「超频模块」贴图改色而来**（只把蓝色相映射为橙色相，轮廓/明暗/高光均保留），按 MIT 署名原作者 AztechMC |
| [ExtendedAE](https://github.com/GlodBlock/ExtendedAE) | LGPL-3.0 | `compileOnly` 引用其 `ex_pattern_provider` 菜单类型（产物内只有类名引用）；扩展仓的界面与分页由它 + EAEP 提供 |
| [ExtendedAE-Plus](https://github.com/GaLicn/ExtendedAE_Plus) | LGPL-3.0-or-later | 仅以 mixin 目标（字符串类名）适配其升级槽判定；本 mod 不含其代码，EAEP 缺席或改版时自动降级为「功能不可用但不崩」 |
| AppliedFlux | LGPL-3.0 | 仅作为可选的升级槽提供方被使用 |

各 mod 的版权归其各自作者所有。若你要二次分发本 mod，请一并遵守上述可选依赖的许可证。

### 与「效率类」mod 的兼容性

| mod | 处理方式 |
|---|---|
| [MI-Tweaks](https://github.com/Swedz/MI-Tweaks)（MIT） | **共存，且本 mod 优先级更高**。tesseract 把各 mod 的效率 hook 放在按优先级升序的集合里依次调用、最后由最后跑的那个决定结果，所以「优先级高 = 说了算」。MI-Tweaks 用 `Integer.MIN_VALUE`（最先跑），本 mod 用 `10000` —— 装了高级超频模块的机器由本 mod 决定，没装的机器仍按 MI-Tweaks 的配置走。 |
| [MI-Efficiency-Remover](https://github.com/Leclowndu93150/MI-Efficiency-Remover) | **声明为不兼容（`type="incompatible"`）**。它把所有 MI 机器的效率**全局**锁成满档（直接 mixin MI 的 `CrafterComponent` 与 tesseract 的 `AbstractModularCrafterComponent`，并 `cancel` 掉 `increase/decreaseEfficiencyTicks`）。那些 `cancel` 无法被外部 mixin 撤销，做不到「运行时柔性失效」，因此只能不兼容——本 mod 的「高级超频模块」提供的是同一效果的**按机器、按道具**版本，删掉它即可。 |
