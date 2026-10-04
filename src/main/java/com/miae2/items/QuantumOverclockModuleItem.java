package com.miae2.items;

import aztech.modern_industrialization.MICommonProxy;
import com.miae2.config.MiAe2Config;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * 「量子超频模块」——高级超频模块的升级版。
 *
 * <p>一个物品提供两件事：
 * <ol>
 *   <li>高级超频模块的全部行为（满效率、做完立刻释放配方）：由 {@code AdvancedOverclockHook}
 *       按「槽里是不是本 mod 的超频模块」判定，因此这里什么都不用做；</li>
 *   <li>处理阵列的并行倍率：由 {@code ProcessingArrayParallelMixin} 放大 {@code getMaxMultiplier()}。</li>
 * </ol>
 * 两者都装在<b>同一个超频槽</b>里，所以「与高级超频模块互斥」是结构性保证（一个槽只能放一个物品）。
 *
 * <p>Tooltip 惯例与高级模块一致：描述收在 Shift 里，提示行复用 MI 自己的翻译键；
 * 并行倍率是配置值，所以第二行用 {@code %s} 把当前配置读出来显示。
 */
public class QuantumOverclockModuleItem extends Item {

    /** MI 自带的「按住 Shift 查看信息」提示（复用其翻译键）。 */
    private static final String SHIFT_HINT = "text.modern_industrialization.TooltipsShiftRequired";
    private static final String DESC_1 = "item.mi_ae2_pattern_provider.quantum_overclock_module.desc1";
    private static final String DESC_2 = "item.mi_ae2_pattern_provider.quantum_overclock_module.desc2";

    public QuantumOverclockModuleItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (MICommonProxy.INSTANCE.hasShiftDown()) {
            tooltip.add(Component.translatable(DESC_1).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(DESC_2, MiAe2Config.quantumParallelMultiplier())
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable(SHIFT_HINT).withStyle(ChatFormatting.GRAY));
        }
    }
}
