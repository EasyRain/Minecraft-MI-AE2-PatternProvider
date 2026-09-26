package com.miae2.compat;

import appeng.api.integrations.igtooltip.ClientRegistration;
import appeng.api.integrations.igtooltip.CommonRegistration;
import appeng.api.integrations.igtooltip.TooltipBuilder;
import appeng.api.integrations.igtooltip.TooltipContext;
import appeng.api.integrations.igtooltip.TooltipProvider;
import appeng.api.integrations.igtooltip.providers.BodyProvider;
import appeng.api.integrations.igtooltip.providers.ServerDataProvider;
import aztech.modern_industrialization.machines.MachineBlock;
import com.miae2.MiAe2PatternProvider;
import com.miae2.machines.blockentities.MePatternProviderBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * 让本 mod 的样板供应仓在悬浮提示（Jade / WTHIT / AE2 自带的 IGT）里显示「设备在线 / 设备离线」，
 * 和 AE2 自己的机器（如 ME 接口）一致。
 *
 * <p>实现路径是 AE2 的**公开扩展点**：它用
 * {@code ServiceLoader.load(TooltipProvider.class, ...)} 加载额外提供者，因此我们只要提供
 * {@code META-INF/services/appeng.api.integrations.igtooltip.TooltipProvider} 即可接入，
 * <b>不需要</b>编译期依赖 Jade，也不需要自己写 Jade 插件。
 *
 * <p>按 AE2 的惯例拆成两半：服务端 {@link Data} 只往悬浮数据里写一个布尔（节点是否在线），
 * 客户端 {@link Body} 读它并加一行文本 —— 节点状态是服务端信息，客户端拿不到。
 *
 * <p>方块类用的是 MI 的 {@link MachineBlock}（MI 所有机器共用这一个方块类）。这对其它 MI 机器没有影响：
 * BE 类型不匹配时我们不会输出任何行。
 */
public final class MeProviderHoverProvider implements TooltipProvider {

    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(MiAe2PatternProvider.MOD_ID, "grid_node");

    private static final String TAG_ONLINE = "miae2NodeOnline";

    private static final String KEY_ONLINE = "jade.mi_ae2_pattern_provider.device_online";
    private static final String KEY_OFFLINE = "jade.mi_ae2_pattern_provider.device_offline";

    @Override
    public void registerCommon(CommonRegistration registration) {
        registration.addBlockEntityData(ID, MePatternProviderBlockEntity.class, new Data());
    }

    @Override
    public void registerClient(ClientRegistration registration) {
        registration.addBlockEntityBody(MePatternProviderBlockEntity.class, MachineBlock.class, ID,
                new Body(), TooltipProvider.DEFAULT_PRIORITY);
    }

    /** 服务端：把「网格节点是否在线」写进悬浮数据。 */
    private static final class Data implements ServerDataProvider<MePatternProviderBlockEntity> {
        @Override
        public void provideServerData(Player player, MePatternProviderBlockEntity be, CompoundTag serverData) {
            try {
                serverData.putBoolean(TAG_ONLINE, be.miae2$isNodeOnline());
            } catch (Throwable ignored) {
                // 悬浮提示永远不该因为一个状态读取失败而报错
            }
        }
    }

    /** 客户端：读服务端写下的布尔并显示一行。 */
    private static final class Body implements BodyProvider<MePatternProviderBlockEntity> {
        @Override
        public void buildTooltip(MePatternProviderBlockEntity be, TooltipContext context, TooltipBuilder tooltip) {
            CompoundTag serverData = context.serverData();
            if (serverData == null || !serverData.contains(TAG_ONLINE)) {
                return;
            }
            boolean online = serverData.getBoolean(TAG_ONLINE);
            tooltip.addLine(Component.translatable(online ? KEY_ONLINE : KEY_OFFLINE)
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
