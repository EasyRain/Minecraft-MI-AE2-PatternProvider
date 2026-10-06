---
navigation:
  title: "供应仓"
  icon: "modern_industrialization:me_pattern_provider_hatch"
  parent: index.md
  position: 2
item_ids:
  - modern_industrialization:me_pattern_provider_hatch
  - modern_industrialization:me_extended_pattern_provider_hatch
---

# 供应仓

<ItemLink id="modern_industrialization:me_pattern_provider_hatch" /> 是一个 MI 机器方块，行为与其它 MI 仓一致，
只是额外多了三重身份：AE2 网络节点、AE2 样板供应器，以及「四个仓合成一个」。

## 一个方块 = 四个仓

| 取代的仓 | 槽位数 | 单槽容量 |
| --- | --- | --- |
| 物品输入仓 | 9 | **无上限**（`Integer.MAX_VALUE`） |
| 物品输出仓 | **36**（4 排） | 64 |
| 流体输入仓 | 9 | **无上限**（`Integer.MAX_VALUE` mB） |
| 流体输出仓 | 9 | **无上限**（`Integer.MAX_VALUE` mB） |

输入侧刻意做成无上限：AE2 会把一份样板的材料一次性推进来，一个槽必须吞得下。**输出**侧则刻意**不**无上限
—— 原因见下一节。

## 为什么输出槽只有 64

处理阵列是**按「输出槽还剩多少空间」反推并行倍率**的，然后再往这些槽里**真插入**。如果供应仓谎报无上限，
阵列就会放心地规划 64 台机器的批量，而真插入每槽只留得下 64 个，多出来的会被 tesseract **静默销毁** ——
AE 的合成任务于是永远等不到足量产，一直挂在那里。

所以输出槽如实报「每槽 64、共 36 格 = **2304 件**缓冲」，大合成会被拆成多次小批量完成，而不是丢东西。
拆批**不会**增加总耗电。

为了让「算得多少」与「装得多少」永远不脱节，供应仓在接料之前还有一道**输出格容量守卫**：它会把
「容量查询路径承诺的空间」与「真插入能兑现的上限」逐槽比对，一旦不一致就**整体拒收**。这时终端里显示的是
**一直等待**，AE2 每 tick 重试 —— 宁可等待，也不让产物被静默销毁。

## 两个界面

| 手持什么右键 | 打开的界面 |
| --- | --- |
| 空手 | **AE2 样板供应器**界面（放样板） |
| **任意扳手** | **MI 机器**界面：所有可配置的物品/流体槽，卡住的料可以手动取出来 |

MI 判断扳手用的是公共标签 `c:tools/wrench`，所以别的 mod 的扳手与 MI 自己的扳手一样有效。

<Row gap="12">
  <ItemImage id="modern_industrialization:me_pattern_provider_hatch" />
  <ItemImage id="ae2:pattern_provider" />
  <ItemImage id="ae2:processing_pattern" />
</Row>

## 输出摊开锁满

一次合成产出多种物品时，供应仓会把它们摊到 36 个输出槽上：每个产物保底 1 格，剩下的格补给当前更缺空间的产物。
这样「一个产物占一大批、另一个只出一点」的配方就不会被单格卡死。

## 在 ME 终端里的名字

供应仓会按它所在的多方块自动命名，终端里一眼能分清：

* 在**处理阵列**里：`<你插入的工作方块名>处理阵列样板供应仓`（例如「离心机处理阵列样板供应仓」）。
* 在**普通 MI 多方块**里（电力高炉、蒸馏塔……）：`<控制器名>样板供应仓`。
* 还没成型时：默认名。
* 扩展仓在上述名字里多一个「扩展」。

识别走的是 tesseract 的 `ComponentStackHolder` 通用接口，所以别的 mod 的阵列（例如 Industrialization
Overdrive 的多方块处理阵列）也能被认出来，而且**不会**把升级槽里的升级物品当成工作方块。

## Jade / The One Probe 里的状态

悬停供应仓会显示**设备在线**或**设备离线**，与 AE2 自家机器一致。两个插件都不走 AE2 的公共悬浮提示扩展点，
所以不会影响其它 MI 机器的提示；Jade 与 TOP 都是可选依赖。

## 一个阵列只能放一个供应仓

同一座处理阵列里只允许存在**一个** ME 样板供应仓（含扩展仓）。放第二个会让整个多方块判定为**结构无效**，
并在服务端日志里说明原因；移除多余的那个即恢复正常。原因见[样板与自动合成](patterns.md)。
