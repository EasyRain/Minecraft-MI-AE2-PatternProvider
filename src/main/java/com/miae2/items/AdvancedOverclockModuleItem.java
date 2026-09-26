package com.miae2.items;

import aztech.modern_industrialization.MICommonProxy;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * 「高级超频模块」。
 *
 * <p>Tooltip 按 MI 的惯例把描述**收在 Shift 里**：不按 Shift 只显示一行提示，
 * 按住才展开说明。两点刻意对齐 MI：
 * <ul>
 *   <li>提示行直接复用 MI 自己的翻译键 {@code text.modern_industrialization.TooltipsShiftRequired}
 *       （「按住 [Shift] 以查看信息」），中英文本就齐全，不必自己再翻一遍；</li>
 *   <li>Shift 判定走 MI 自己的 {@link MICommonProxy#hasShiftDown()}，而不是直接引 {@code Screen}
 *       —— 行为与 MI 原生物品一致，且专用服上不会碰到客户端类。</li>
 * </ul>
 */
public class AdvancedOverclockModuleItem extends Item {

    /** MI 自带的「按住 Shift 查看信息」提示（复用其翻译键）。 */
    private static final String SHIFT_HINT = "text.modern_industrialization.TooltipsShiftRequired";
    private static final String DESC_1 = "item.mi_ae2_pattern_provider.advanced_overclock_module.desc1";
    private static final String DESC_2 = "item.mi_ae2_pattern_provider.advanced_overclock_module.desc2";

    public AdvancedOverclockModuleItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (MICommonProxy.INSTANCE.hasShiftDown()) {
            tooltip.add(Component.translatable(DESC_1).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable(DESC_2).withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable(SHIFT_HINT).withStyle(ChatFormatting.GRAY));
        }
    }
}
