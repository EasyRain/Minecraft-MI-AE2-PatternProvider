package com.miae2.compat;

import com.miae2.MiAe2PatternProvider;
import com.miae2.machines.blockentities.MePatternProviderBlockEntity;
import com.mojang.logging.LogUtils;
import java.util.function.Function;
import mcjty.theoneprobe.api.IProbeHitData;
import mcjty.theoneprobe.api.IProbeInfo;
import mcjty.theoneprobe.api.IProbeInfoProvider;
import mcjty.theoneprobe.api.ITheOneProbe;
import mcjty.theoneprobe.api.ProbeMode;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/**
 * 让本 mod 的样板供应仓在 The One Probe（TOP）里也显示「设备在线 / 设备离线」，与 Jade 那一份保持一致。
 *
 * <p><b>注册方式</b>：TOP 没有注解扫描，它走 NeoForge 的 IMC —— 见
 * {@code MiAe2PatternProvider#onInterModEnqueue} 里的
 * {@code InterModComms.sendTo("theoneprobe", "getTheOneProbe", MeProviderTopPlugin::new)}
 * （照 AE2 的 {@code appeng.integration.modules.theoneprobe.TOP#enqueueIMC} 写法）。IMC 只在
 * {@code ModList.get().isLoaded("theoneprobe")} 为真时才发送，所以 TOP 缺席时本类（以及它对
 * {@code mcjty.theoneprobe} 的引用）永远不会被加载，属安全的可选依赖。
 *
 * <p><b>为什么不做成 AE2 的 IGT 扩展点</b>：AE2 的 IGT 是一套同时覆盖 Jade / WTHIT / TOP 的公共抽象，
 * 一旦注册就在三个 HUD 里同时生效，而它 Jade 那条路在 MI 这种「所有机器共用 {@code MachineBlock}」的
 * mod 上必然抛 {@code ClassCastException}（详见 {@link MeProviderJadePlugin} 的类注释）。本 mod 因此
 * 改为给每个 HUD 各写一个小插件：TOP 这份最简单 —— TOP 在<b>服务端</b>调用 {@link IProbeInfoProvider}
 * 生成提示、再由它自己把结果文本同步给客户端，所以这里直接读方块实体的状态即可，不必自建 NBT 通道。
 */
public final class MeProviderTopPlugin implements Function<ITheOneProbe, Void> {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(MiAe2PatternProvider.MOD_ID, "grid_node");

    private static final String KEY_ONLINE = "text.mi_ae2_pattern_provider.device_online";
    private static final String KEY_OFFLINE = "text.mi_ae2_pattern_provider.device_offline";

    /** TOP 的 IMC 载荷：拿到 {@link ITheOneProbe} 后登记一个 provider 就行，不需要接管标准信息显示。 */
    @Override
    public Void apply(ITheOneProbe probe) {
        probe.registerProvider(new NodeLine());
        LOGGER.info("[悬浮提示] The One Probe 插件已注册（设备在线 / 设备离线）");
        return null;
    }

    private static final class NodeLine implements IProbeInfoProvider {

        @Override
        public ResourceLocation getID() {
            return UID;
        }

        @Override
        public void addProbeInfo(ProbeMode mode, IProbeInfo probeInfo, Player player, Level level,
                BlockState blockState, IProbeHitData data) {
            try {
                // TOP 在服务端生成提示（客户端那次调用拿不到权威的节点状态），因此只认服务端。
                if (!(player instanceof ServerPlayer)) {
                    return;
                }
                // MI 所有机器共用 MachineBlock，但这个接口是按方块实体判定的，先认领自己的仓。
                if (!(level.getBlockEntity(data.getPos()) instanceof MePatternProviderBlockEntity be)) {
                    return;
                }
                probeInfo.mcText(Component.translatable(be.miae2$isNodeOnline() ? KEY_ONLINE : KEY_OFFLINE)
                        .withStyle(ChatFormatting.GRAY));
            } catch (Throwable ignored) {
                // 悬浮提示永远不该因为一个状态读取失败而报错
            }
        }
    }
}
