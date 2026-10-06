---
navigation:
  title: "ME样板供应仓"
  icon: "modern_industrialization:me_pattern_provider_hatch"
  position: 100
item_ids:
  - mi_ae2_pattern_provider:guide
---

# ME样板供应仓

![Logo](assets/logo.png)

**MI AE2 Pattern Provider** 是 [Modern Industrialization](https://modrinth.com/mod/modern-industrialization) /
[Extended Industrialization](https://modrinth.com/mod/extended-industrialization) 与
[Applied Energistics 2](https://modrinth.com/mod/ae2) 之间的桥梁。

它只加了一个方块：<ItemLink id="modern_industrialization:me_pattern_provider_hatch" />。这一个方块**同时**
取代了 EI **处理阵列**（<ItemImage id="extended_industrialization:processing_array" />）的全部四个输入/输出仓，
本身又是一个 AE2 样板供应器。把样板放进它、把它接进 ME 网络，你的 ME 系统就能把自动合成任务直接推进阵列
—— 物品与流体自动分流，成品再被抽回网络。

<BlockImage id="modern_industrialization:me_pattern_provider_hatch" scale="6" />

*这就是你最先用到的 ME样板供应仓。它的加大版 **ME扩展样板供应仓**（需要
[ExtendedAE](https://modrinth.com/mod/extended-ae)）见[扩展供应仓](extended-hatch.md) —— 指南里**可选 mod 的内容
一律写成文字**，因为缺了那个 mod 的物品图标会变成页面上的错误提示。*

## 本指南包含

<SubPages />

## 六步速览

1. 搭一座 EI **处理阵列**，把「ME样板供应仓」放在原本物品输入仓的位置 —— 那四个仓都不再需要了。
2. 给阵列供电（能源输入仓 + MI 线缆），并在控制器界面里插入你想让阵列跑的单方块机器。
3. 用 ME 玻璃线缆把供应仓接进网络。
4. 空手右键供应仓，放进**处理样板**。
5. 在 ME 终端里下单。
6. 阵列从供应仓取料、合成，把成品写回供应仓，供应仓再把成品送回网络。

哪一步卡住了就直接看[疑难排查](troubleshooting.md) —— 那里按游戏里**实际看到的症状**（「一直等待」、「结构无效」……）来写。

## 依赖

| mod | 要求 |
| --- | --- |
| [Modern Industrialization](https://modrinth.com/mod/modern-industrialization) | 必需 |
| [Extended Industrialization](https://modrinth.com/mod/extended-industrialization) | 必需（提供处理阵列） |
| [Applied Energistics 2](https://modrinth.com/mod/ae2) | 必需 |
| [ExtendedAE](https://modrinth.com/mod/extended-ae) | 可选 —— 只影响扩展供应仓 |
| [ExtendedAE-Plus](https://github.com/GaLicn/ExtendedAE_Plus) | 可选 —— 提供频道卡、扩容卡 |
| [AppliedFlux](https://github.com/GlodBlock/AppliedFlux) | 可选 —— 提供感应卡 |
| [Cloth Config](https://modrinth.com/mod/cloth-config) | 可选 —— 游戏内改并行倍率 |
| [Jade](https://modrinth.com/mod/jade) / [The One Probe](https://modrinth.com/mod/the-one-probe) | 可选 —— 在供应仓上显示「设备在线 / 设备离线」 |
