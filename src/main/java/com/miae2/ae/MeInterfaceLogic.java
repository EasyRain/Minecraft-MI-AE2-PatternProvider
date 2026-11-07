package com.miae2.ae;

import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEKeyType;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import net.minecraft.world.item.Item;

/**
 * ME 接口仓的 <b>AE 侧</b>逻辑：直接复用 AE2 的 {@link InterfaceLogic}，只按档位改两处参数。
 *
 * <p>复用它能白拿这些行为（不必自己实现）：配置槽、按配置「保持库存」、缺货时从网络取、
 * 必要时下单合成（{@code MultiCraftingTracker}）、优先级、模糊卡、以及
 * {@code InterfaceInventory} 里那条「同网格内防乒乓」的优先级判断。
 *
 * <p>槽位数与容量按档位（2026-11-07 设计定稿）：
 * <ul>
 *   <li>{@link Tier#BASE}：9 槽，物品 64 / 流体 4B（对齐 AE2 原版接口）</li>
 *   <li>{@link Tier#EXTENDED}：36 槽，物品 64 / 流体 4B（对齐 ExtendedAE Ex Interface）</li>
 *   <li>{@link Tier#OVERSIZE}：36 槽，物品 1024（16 组）/ 流体 64B（16×，对齐 Oversize Interface）</li>
 * </ul>
 *
 * <p><b>注意</b>：容量只影响「输入侧保持多少」。<b>输出侧不经过这里</b> —— 输出是我们自己的缓冲，
 * 物品每格固定 64（阵列按输出空间反推并行，虚报容量会销毁产物），见设计文档 §4.5「容量口径」。
 */
public class MeInterfaceLogic extends InterfaceLogic {

    /**
     * 档位参数。{@code fluidCapacity} 单位是 mB（AE 的流体量），4B = 4000 mB。
     */
    public enum Tier {
        BASE(9, 64, 4_000L),
        EXTENDED(36, 64, 4_000L),
        OVERSIZE(36, 1_024, 64_000L);

        private final int configSlots;
        private final long itemCapacity;
        private final long fluidCapacity;

        Tier(int configSlots, long itemCapacity, long fluidCapacity) {
            this.configSlots = configSlots;
            this.itemCapacity = itemCapacity;
            this.fluidCapacity = fluidCapacity;
        }

        public int configSlots() {
            return this.configSlots;
        }

        public long itemCapacity() {
            return this.itemCapacity;
        }

        public long fluidCapacity() {
            return this.fluidCapacity;
        }
    }

    private final Tier tier;

    public MeInterfaceLogic(IManagedGridNode mainNode, InterfaceLogicHost host, Item item, Tier tier) {
        super(mainNode, host, item, tier.configSlots());
        this.tier = tier;
        applyInputCapacity(tier);
    }

    /**
     * 按档位设置输入侧每槽容量。
     *
     * <p>{@code getStorage()} / {@code getConfig()} 是 {@code ConfigInventory}，而它
     * {@code extends GenericStackInv}，{@code setCapacity} 是<b>公开</b>方法 ⇒ 不需要反射/accessor
     * （ExtendedAE 的 Oversize 之所以用反射，是因为它要整体替换成一个自定义 inventory 子类）。
     */
    private void applyInputCapacity(Tier tier) {
        getStorage().setCapacity(AEKeyType.items(), tier.itemCapacity());
        getStorage().setCapacity(AEKeyType.fluids(), tier.fluidCapacity());
    }

    public Tier tier() {
        return this.tier;
    }
}
