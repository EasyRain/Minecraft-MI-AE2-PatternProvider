# MI AE2 Pattern Provider（ME 样板供应仓）

一个 [Modern Industrialization](https://github.com/AztechMC/Modern-Industrialization) + [Extended Industrialization](https://github.com/Swedz/Extended-Industrialization) 的附属 mod，为 EI 的「处理阵列」（Processing Array）提供一个与 [Applied Energistics 2](https://github.com/AppliedEnergistics/Applied-Energistics-2) 联动的 **ME 样板供应仓**。

## 功能

- **四合一仓**：单个方块同时承担物品输入 / 物品输出 / 流体输入 / 流体输出四种接口，取代处理阵列原本的 4 种仓。
- **AE2 样板供应器**：本身是 AE2 网格节点 + 样板供应器，可在其中放置合成样板；AE2 下单后，样板输入物（物品 + 流体）被注入自身仓库存 → 处理阵列拿去合成 → 成品写回自身仓库存 → 自动抽回 ME 网络。
- **超堆叠**：槽容量直接到上限（`Integer.MAX_VALUE`），可接住 AE2 一次性推入的整份样板输入。
- **自动命名**：样板管理终端里显示「<工作方块>处理阵列样板供应仓」。
- **挖掘保留内容**：挖掘时样板、返回区物品、在途输出都会随方块掉落。

## 交互

| 操作 | 打开的界面 |
|---|---|
| 空手右键 | AE2 样板供应器界面（放样板） |
| 手持扳手（`#modern_industrialization:wrenches`）右键 | MI 槽位界面（手动取出卡住的物品/流体） |

## 依赖（Minecraft 1.21.1 / NeoForge）

| 依赖 | 版本 | 类型 |
|---|---|---|
| Modern Industrialization | 2.5.6 | required |
| Applied Energistics 2 | 19.2.17 | required |
| Extended Industrialization | 1.16.2 | optional（功能依赖其处理阵列） |

> Tesseract API（EI 前置）与 GuideME 由上述依赖传递引入。

## 构建

JDK 21 + Gradle 8.14.3（依赖 jar 已放在 `libs/`，开箱即用）：

```bat
gradlew build
```

产物：`build/libs/mi_ae2_pattern_provider-1.0.0-1.21.1.jar`

## 许可证

[MIT](LICENSE)
