# MI AE2 Pattern Provider（ME 样板供应仓）

一个 [Modern Industrialization](https://github.com/AztechMC/Modern-Industrialization) + [Extended Industrialization](https://github.com/Swedz/Extended-Industrialization) 的附属 mod，为 EI 的「处理阵列」（Processing Array）提供一个与 [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2) 联动的 **ME 样板供应仓**。

## 功能

- **四合一仓**：单个方块同时承担物品输入 / 物品输出 / 流体输入 / 流体输出四种接口，取代处理阵列原本的 4 种仓。
- **AE2 样板供应器**：本身是 AE2 网格节点 + 样板供应器，可在其中放置合成样板；AE2 下单后，样板输入物（物品 + 流体）被注入自身仓库存 → 处理阵列拿去合成 → 成品写回自身仓库存 → 自动抽回 ME 网络。
- **超堆叠**：槽容量直接到上限（`Integer.MAX_VALUE`），可接住 AE2 一次性推入的整份样板输入。
- **自动命名**：样板管理终端里显示「<工作方块>处理阵列样板供应仓」。
- **挖掘保留内容**：挖掘时样板、返回区物品、在途输出都会随方块掉落。
- **扩展供应仓（ME 扩展样板供应仓）**：36 个样板槽（4×9，对齐 ExtendedAE 的扩展样板供应器）。
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

## 依赖（Minecraft 1.21.1 / NeoForge）

| 依赖 | 版本 | 类型 |
|---|---|---|
| Applied Energistics 2 | 19.2.17 | required（硬依赖） |
| Extended Industrialization | 1.16.2 | required（硬依赖，提供处理阵列） |

> Modern Industrialization、Tesseract API、GuideME 由 AE2 / EI 传递引入（EI 硬依赖 MI + tesseract + guideme；AE2 硬依赖 guideme），无需单独声明。

## 构建

JDK 21 + Gradle 8.14.3（依赖 jar 已放在 `libs/`，开箱即用）：

```bat
gradlew build
```

产物：`build/libs/mi_ae2_pattern_provider-1.0.0-1.21.1.jar`

## 许可证

[MIT](LICENSE)
