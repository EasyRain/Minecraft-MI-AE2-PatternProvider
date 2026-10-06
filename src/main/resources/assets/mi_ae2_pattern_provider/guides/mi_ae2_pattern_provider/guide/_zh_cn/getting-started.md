---
navigation:
  title: "快速上手"
  icon: "modern_industrialization:me_pattern_provider_hatch"
  parent: index.md
  position: 1
---

# 快速上手

这一页从零搭一套能跑的：一座处理阵列、一个供应仓、一条样板、一次合成。

## 1. 搭处理阵列

EI 的**处理阵列**是个多方块：一个控制器、一圈机壳、一个供电用的**能源输入仓**，以及处理物品与流体的各种仓。

<GameScene zoom="2" interactive={true} fullWidth={true}>
  <MultiblockShape controller="extended_industrialization:processing_array" />
  <MultiblockShape controller="extended_industrialization:processing_array" useBigShape={true} x="-6" z="-8" />
</GameScene>

*小型与大型两种阵列形状 —— 按住左键拖动可以旋转，右键拖动可以平移。*

装了本 mod 之后，那四个仓**不用再放**了：<ItemLink id="modern_industrialization:me_pattern_provider_hatch" />
直接占掉仓的位置，同时提供物品输入、物品输出、流体输入、流体输出。**能源输入仓仍然要放**，机壳也按形状照搭。

<Recipe id="mi_ae2_pattern_provider:me_pattern_provider_hatch" />

*合成：原版 AE2 的 ME 样板供应器 + 四种仓各一个 + 任意颜色的玻璃线缆。仓的等级不限。*

## 2. 成型

放好控制器、搭好外壳、把供应仓与能源输入仓放进各自的仓位上即可 —— MI 的多方块**形状一搭对就自己成型**，
不需要扳手或任何额外操作。形状对了控制器就会亮起来，开始接受机器与供电。

## 3. 给阵列机器与电

* 打开**控制器**，把你想让它跑的电动物品机器插进去（默认最多 64 台）。阵列跑的是**它们的**配方，而且是批处理。
* 用 MI 线缆给能源输入仓供电，EU/t 要够。阵列的耗电是 `配方 EU/t × 批数`，并行越高耗电越大。

## 4. 接 ME 网络

拉一根 **ME 玻璃线缆**到供应仓，并确保网络里还有空闲频道（或者给它插一张**频道卡**，见[升级卡](upgrade-cards.md)）。
供应仓就是普通的 AE2 网络节点：用 Jade 或 The One Probe 悬停，应当显示**设备在线**。

## 5. 放样板

**空手**右键供应仓，打开的就是 AE2 样板供应器界面。把在 ME 样板编码终端里编码好的**处理样板**放进去：

* **输入**：这一步配方要消耗的材料（物品与流体都行）。
* **输出**：阵列应当产出的东西。

<ItemImage id="ae2:pattern_provider" /> <ItemImage id="ae2:processing_pattern" />

供应仓有 **9 个样板槽** —— 与原版 ME 样板供应器一样的一页。扩展仓更多（见[扩展供应仓](extended-hatch.md)）。

## 6. 下单

在 ME 合成终端里下单，之后的流程是自动的：

1. AE2 把一份样板的材料推进供应仓的**输入槽**。
2. 阵列把材料取走，跑一批，把成品写进供应仓的**输出槽**。
3. 供应仓把成品送回 ME 网络，并释放这条样板，等待下一份。

供应仓本身就是一小块缓冲，所以即便网络一时装不下成品，自动合成也不会失败 —— 它会先攒在供应仓里。

## 下一步

* [供应仓](hatch.md) —— 每个槽位与两个界面的作用。
* [样板与自动合成](patterns.md) —— 供应仓与 AE2 的交互，以及「一个阵列只能一个仓」的原因。
* [超频模块](overclock-modules.md) —— 让阵列跑出机器数 16 倍的并行。
