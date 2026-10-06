package com.miae2.items;

import com.miae2.guide.MiAe2Guide;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * 「使用手册」：右键打开**游戏内指南**里的本 mod 章节。
 *
 * <p>默认打开的是 **MI 自己的指南书**（{@code modern_industrialization:book}）并直接翻到我们的章节 ——
 * 与 Industrialization Overdrive 的并入方式一致，玩家看到的侧栏就是 MI 那一整套；MI 的指南书找不到时
 * 退回本 mod 自己注册的那本（见 {@link MiAe2Guide#open}）。
 *
 * <p>写法与 MI 的 {@code GuideBookItem}、AE2 的 {@code GuideItem} 相同：
 * <b>只在客户端</b>调用（指南是纯客户端界面，专用服上调它没有意义），两端都返回 {@code consume}
 * 让手臂有挥动反馈。
 *
 * <p>手册本身可在工作台用「MI 指南书 + 玻璃线缆」合成，也直接列在 MI 创造栏里 MI 指南书的旁边。
 */
public class GuideBookItem extends Item {

    private static final String DESC = "item.mi_ae2_pattern_provider.guide.desc";

    public GuideBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            MiAe2Guide.open(player);
        }
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(DESC).withStyle(ChatFormatting.GRAY));
    }
}
