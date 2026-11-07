---
navigation:
  title: "ME接口仓"
  icon: "modern_industrialization:me_interface_hatch"
  parent: index.md
  position: 5
item_ids:
  - modern_industrialization:me_interface_hatch
  - modern_industrialization:me_extended_interface_hatch
  - modern_industrialization:me_oversize_interface_hatch
---

# ME接口仓

<BlockImage id="modern_industrialization:me_interface_hatch" scale="6" />

<ItemLink id="modern_industrialization:me_interface_hatch" /> 是同一套东西里的「反向」方块：
样板供应仓负责**把料推进阵列**，接口仓负责**让阵列在没订单时也有料可用** —— 它就是一个 AE2 的 ME 接口，
只是身份是 MI 的仓，能装进处理阵列的输入/输出位。

## 它怎么工作

* 在 AE 界面里给某个槽**配置物品/流体**（这就是「保持量」）。只要网络里有货，接口就会自动把那份量**补满**；
* 阵列从接口把这些料**取走**后，接口会再从网络补回来 —— 所以它天然是「常备料仓」；
* 配置槽里没有的、或超出保持量的东西，会被接口按 AE 规则**推回网络**，不会堆在仓里；
* 成品/副产物如果写进了接口的输出槽，会立刻被抽回网络（断网时先留在缓冲里）。

## 三个档位

| 方块 | 配置槽 | 每槽容量 |
| --- | --- | --- |
| ME接口仓 | 9 | 物品 64 / 流体 4 B |
| ME扩展接口仓（需 ExtendedAE） | 36（4 页） | 物品 64 / 流体 4 B |
| ME超大接口仓（需 ExtendedAE） | 36（4 页） | **物品 1024 / 流体 64 B** |

超大档的「物品 1024」直接沿用 ExtendedAE 的 Oversize 实现，所以 EAE 那边调倍率，这边跟着变。

## 和样板供应仓一起用

同一座阵列里既有样板供应仓、又有接口仓时：

1. **下单后接口会被屏蔽输入** —— 阵列读不到、也抽不走接口里的料，订单材料只能来自供应仓；
2. 订单结束后接口恢复供料；
3. 接口按保持量长期备料，等于给阵列准备了一条「不经过 AE 订单」的常备供给线。

> 供应仓空闲时会把自己的输出格全部锁空，所以「接口供料触发的自由合成」产物只会进接口自己的输出缓冲。

## 两个界面

| 操作 | 打开 |
| --- | --- |
| **右键** | AE 接口界面：配置槽、保持量、优先级、升级卡 |
| **扳手右键** | MI 自己的槽位界面（36 槽档位是 **3 页**：物品入 / 流体入 / 输出） |

## 升级卡

和 AE2 原版接口一致：**合成卡 ×1、模糊卡 ×1**。此外所有「为原版接口登记过」的附属卡也会自动嫁接过来
（例如 ExtendedAE-Plus 的频道卡），而**虚拟合成卡**是样板供应仓专用，接口不接受。

## 悬浮提示

装了 Jade / The One Probe 时，悬停接口仓会显示：

* **设备在线 / 设备离线**（与 AE2 自己的机器一致）；
* 36 槽档位额外显示**流体内容**（每个流体一行，按住 Shift 显示每格容量）与**剩余容量合计**。
