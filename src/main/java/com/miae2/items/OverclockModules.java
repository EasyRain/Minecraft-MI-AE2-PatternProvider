package com.miae2.items;

import aztech.modern_industrialization.machines.MachineBlockEntity;
import aztech.modern_industrialization.machines.components.OverdriveComponent;
import com.miae2.config.MiAe2Config;
import net.minecraft.world.item.ItemStack;

/**
 * 本 mod「超频模块」家族的共用判定。
 *
 * <p>家族里有两个物品，都是<b>同一个超频槽</b>的合法内容物（因此天然互斥，一个槽只能装一个）：
 * <ul>
 *   <li>{@code advanced_overclock_module}：满效率 + 做完立刻释放配方（见 {@code AdvancedOverclockHook}）；</li>
 *   <li>{@code quantum_overclock_module}：<b>包含</b>高级模块的全部行为，再额外给处理阵列加并行倍率。</li>
 * </ul>
 * 「插槽准入」分散在四处（右键 {@code OverdriveComponent#onUse}、GUI 拖拽/点击
 * {@code SlotPanel} 服务端建槽时的 {@code mayPlace}、效率 hook、以及
 * {@code AdvancedOverclockHook} 的家族判定），所以判定只写这一份，避免新增物品时漏改一处
 * 导致「能右键装、拖不进去」这类不一致。
 */
public final class OverclockModules {

    /**
     * 取出某台 MI 机器（含 EI 处理阵列控制器、IO 多处理阵列）超频槽里的模块物品。
     *
     * <p>{@code mapOrDefault} 在同类型组件多于一个时会抛异常；这两种控制器都只有一个
     * {@code OverdriveComponent}（由 tesseract 基类创建并注册），所以是安全的。
     */
    public static ItemStack moduleIn(MachineBlockEntity machine) {
        return machine.components.mapOrDefault(
                OverdriveComponent.class, OverdriveComponent::getDrop, ItemStack.EMPTY);
    }

    /** 是不是本 mod 的超频模块（高级或量子）——两者都提供「满效率 + 立刻释放配方」。 */
    public static boolean isOverclockModule(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.is(ModItems.ADVANCED_OVERCLOCK_MODULE.get())
                || stack.is(ModItems.QUANTUM_OVERCLOCK_MODULE.get()));
    }

    /** 是否提供并行倍率（只有量子模块）。 */
    public static boolean grantsParallel(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModItems.QUANTUM_OVERCLOCK_MODULE.get());
    }

    /**
     * 这个模块放进这台机器的超频槽里是不是<b>白装</b>（目前只有一种情况）。
     *
     * <p>量子超频模块的全部额外价值就是并行倍率，而并行只在处理阵列上成立
     * （见 {@link com.miae2.api.QuantumParallelHost}）。装进单方块电动机器、MI 多方块、
     * EI 大型电炉或 IO 热解炉，它和高级超频模块<b>完全等价</b> —— 所以这种白装我们直接拒收，
     * 免得玩家把一个超导压档的模块浪费在没用的地方（用户 m04850 要求）。
     *
     * <p>注意方向：这里只管「我们的规则要不要拒收」，MI 原版超频模块与高级超频模块一律返回 false
     * （前者不归我们管，后者到处都有意义）—— 调用方仍然要 && MI 自己的谓词。
     */
    public static boolean blockedIn(MachineBlockEntity machine, ItemStack stack) {
        return blockedIn(machine instanceof com.miae2.api.QuantumParallelHost, stack);
    }

    /**
     * {@link #blockedIn(MachineBlockEntity, ItemStack)} 的可测版本：把「机器是不是宿主」拆成布尔量，
     * 冒烟测试就能不依赖真实机器实例检查整张判定表。
     */
    public static boolean blockedIn(boolean parallelHost, ItemStack stack) {
        return grantsParallel(stack) && !parallelHost;
    }

    /**
     * 把处理阵列自己算出的「机器数」上限按配置放大。
     *
     * <p><b>只放大上限</b>：真正的倍率仍由 tesseract 按输入料量与输出格空间反推并夹紧
     * （{@code calculateMultiplier} = min(输入能撑的, 输出装得下的, 这个上限)）。
     * 反过来说，绝不能在算完之后把结果整体乘一个系数 —— 那会让输入侧的模拟口径与真实扣料脱节，
     * 重现「模拟够、真扣不够」的丢料路径。
     */
    public static int scaleMaxMultiplier(int machineCount) {
        if (machineCount <= 0) {
            return machineCount;
        }
        long scaled = (long) machineCount * MiAe2Config.quantumParallelMultiplier();
        return scaled > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) scaled;
    }

    private OverclockModules() {
    }
}
