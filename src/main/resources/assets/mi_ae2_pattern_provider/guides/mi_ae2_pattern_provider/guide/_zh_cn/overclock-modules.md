---
navigation:
  title: "超频模块"
  icon: "mi_ae2_pattern_provider:quantum_overclock_module"
  parent: index.md
  position: 6
item_ids:
  - mi_ae2_pattern_provider:advanced_overclock_module
  - mi_ae2_pattern_provider:quantum_overclock_module
---

# 超频模块

两个模块都插在 MI 电动机器（含处理阵列控制器）的**超频模块槽**里，两者互斥：一个槽只能放一个。

| | MI 原版超频模块 | <ItemImage id="mi_ae2_pattern_provider:advanced_overclock_module" /> 高级 | <ItemImage id="mi_ae2_pattern_provider:quantum_overclock_module" /> 量子 |
| --- | --- | --- | --- |
| 效率 | 从 0 慢慢爬，靠空转耗电钉住 | **开局即上限** | 同高级 |
| 配方 | **锁死**，直到拆下模块或断电 | **做完立刻释放** | 同高级 |
| 并行 | — | — | **处理阵列：机器数 × 倍率** |

MI 原版模块的行为**完全不变** —— 本 mod 走的是 MI 官方效率 hook（`MIHookEfficiency`），没有 mixin MI 的逻辑。

## 高级超频模块

<ItemImage id="mi_ae2_pattern_provider:advanced_overclock_module" scale="2" />

* 插进**任意** MI 电动机器（单方块或多方块控制器）的超频模块槽。
* 直接把效率设为该机器的上限：**开局即满速**，而不是从 0 慢慢爬。
* **做完立刻释放配方**，机器马上接下一个，不必等玩家来拆模块。
* 与 MI-Tweaks 共存：在装了模块的机器上由本 mod 的效率 hook 说了算（见[升级卡](upgrade-cards.md)）。

<Recipe id="mi_ae2_pattern_provider:advanced_overclock_module_asbl" />

*工作台摆法。组装机配方同形状，但只需 4 片不锈钢板（`eu 16` / `duration 300`）。*

## 量子超频模块

<ItemImage id="mi_ae2_pattern_provider:quantum_overclock_module" scale="2" />

高级模块的全部效果，**外加处理阵列的并行**：把阵列的批处理天花板从「机器数」抬到「**机器数 × 倍率**」。

* 默认 **4×** ⇒ 64 台机器的阵列最多 **256 并行**。
* 倍率是**天花板**而不是固定批量：真实批数仍由阵列按**输入料量**与**输出格空间**自己反推，
  所以配方产出太大时只是批量变小，**绝不吞产物**。
* EU/t 与耗时按上游语义随批数缩放：4 倍并行 = 4 倍 EU/t、**总能量也是 4 倍**；电供不上时耗时线性拉长。
  **不是免费产能。**

<Recipe id="mi_ae2_pattern_provider:quantum_overclock_module_asbl" />

*工作台摆法（超导体板×4、量子电路×2、高级超频模块×1）。组装机配方与之相同，`eu 64` / `duration 1000`。*

### 只对处理阵列生效

**只认处理阵列控制器。** 插进普通机器会被拒收：

* 手持模块右键普通机器，**聊天框**会给出「量子超频模块仅支持处理阵列」。
* 从 GUI 里往普通机器的槽里拖也会被拒收 —— 物品会留在光标上，不会消失。

高级模块与原版模块照旧到处可插。

判定依据是「**可插 = 并行真的会生效**」：只有阵列有「机器数」这一层可以乘；单方块机器一次只跑一条配方，
其它多方块的 `getMaxMultiplier()` 是线圈档位的批处理上限。与其让模块在那些机器上白装，不如直接拒收。

## 调整倍率

倍率在 `config/mi_ae2_pattern_provider-common.toml` 里：

```toml
[quantum_overclock]
parallel_multiplier = 4   # 范围 2-16
```

该文件的完整路径会在启动时打进日志。

装了 [Cloth Config](https://modrinth.com/mod/cloth-config) 就能在游戏内改：
**Mods 界面 → MI AE2 Pattern Provider → Config**。保存后**下一次配方计算**即生效 —— 不需要重启游戏，也不需要重载世界。

### 为什么上限是 16

供应仓的输出空间是 `36 格 × 64 = 2304 件`，而阵列的倍率大致受 `输出格数 ÷ 每批产物量` 限制。官方配方里
每批物品产物量 `P` 的分布是 `≤1：52.6% / ≤2：66.1% / ≤4：77.2% / ≤9：95.1%`，对应上限 `36/P`
—— `P=1` 是 36×、`P=2` 是 18×、`P=4` 是 9×、`P=9` 是 4×。16× 已在「单产物理论极限 36×」的一半以下，属保守取值。

## 已知的上游瑕疵（会被倍率同比例放大）

这些是上游阵列代码自带的问题，**不是本模块引入的**，但倍率越高放大得越明显，装之前值得知道：

* **多个流体产物共用输出槽**：可行性检查对每个流体产物各自按整槽剩余空间求倍率，**不扣减前一个产物已占用的量**，
  真插入被截断后多出来的部分被静默销毁。
* **概率产物**（`probability < 1.0`）完全不参与可行性检查，掷中后装不下的部分同样会丢。
* **`recipeEnergy` 写档用 `long`、读档用 `int`**：`配方总 EU × 倍率 > 2^31` 时读档后合成进度损坏，
  阈值是 `总EU > 2^31 / (64 × 倍率)`。只有极端的配方会碰到（封装机的量子升级、聚变堆），
  而封装机那条在**原版** 64 并行时**就已经超标**。

**建议**：量子模块用在普通产线上。本仓扫描过官方配方，有物品产物的配方里绝大多数总 EU 都在 2.1M 以下。
封装机的量子升级与聚变堆请继续用普通的高级模块。
