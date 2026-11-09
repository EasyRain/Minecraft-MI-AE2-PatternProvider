package com.miae2.compat;

import aztech.modern_industrialization.machines.MachineBlock;
import com.miae2.MiAe2PatternProvider;
import com.miae2.machines.blockentities.MeInterfaceBlockEntity;
import com.miae2.machines.blockentities.MePatternProviderBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * 让本 mod 的样板供应仓在悬浮提示（Jade）里显示「设备在线 / 设备离线」，和 AE2 自己的机器（如 ME 接口）一致。
 *
 * <p><b>为什么不用 AE2 的 IGT 扩展点</b>（{@code appeng.api.integrations.igtooltip.TooltipProvider}）：
 * AE2 的 Jade 适配器 {@code appeng.integration.modules.jade.BodyProviderAdapter#appendTooltip} 是
 * <pre>T obj = this.objectClass.cast(accessor.getBlockEntity());</pre>
 * —— <b>先强转、不做类型判断</b>；而它的注册粒度是<b>方块类</b>
 * （{@code JadeModule#registerClient} 里 {@code registration.registerBlockComponent(adapter, blockClass)}）。
 * MI 的<b>所有机器共用同一个 {@code MachineBlock} 类</b>，于是我们一旦通过 IGT 注册，
 * 就会对<b>每一台 MI 机器</b>都被调用，悬停非本仓的机器（如处理阵列控制器）时强转抛
 * {@code ClassCastException} → 悬浮提示显示「&lt;发生错误，请反馈至 MI AE2 Pattern Provider&gt;」。
 * AE2 自己不会遇到这个问题，因为它的方块类与 BE 类是一一对应的。
 *
 * <p>因此这里改成<b>自己写一个 Jade 插件</b>：客户端按方块类注册（粒度同样是 {@code MachineBlock}，
 * Jade 的 API 只提供到这个粒度），但<b>我们自己先做 instanceof 判断</b>，非本仓直接返回，
 * 既不报错也不影响其它 MI 机器的提示；服务端按 <b>BE 类</b>注册（
 * {@code IWailaCommonRegistration#registerBlockDataProvider(provider, Class)}），只有本仓会同步数据。
 *
 * <p>节点在线状态是服务端信息（客户端没有网格节点），所以拆成两半：
 * 服务端 {@link NodeData} 往悬浮数据里写一个布尔，客户端 {@link NodeLine} 读它并加一行文本。
 *
 * <p>插件发现：Jade 在 `FMLLoadCompleteEvent` 里扫所有 mod 的 `ModFileScanData`，
 * 取 {@code @WailaPlugin} 标注的类，再按注解的 {@code value} 过滤
 * （{@code value} 为空 → 直接加载；非空 → 要求该 mod id 已加载），最后反射 newInstance + 调
 * {@code register/registerClient}。所以这里用<b>空 value</b>（与 AE2 自己的 JadeModule 一致），
 * 无需在 `neoforge.mods.toml` 里登记任何入口。
 */
@WailaPlugin
public final class MeProviderJadePlugin implements IWailaPlugin {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(MiAe2PatternProvider.MOD_ID, "grid_node");

    /**
     * 流体行的 UID，**必须与 {@link #UID} 不同** —— Jade 在 `FMLLoadCompleteEvent` 里会校验
     * 「UID → provider」唯一性，撞 UID 直接 `IllegalStateException: Duplicate UID` 崩游戏
     * （2026-11-08 实测）。
     */
    private static final ResourceLocation FLUID_UID =
            ResourceLocation.fromNamespaceAndPath(MiAe2PatternProvider.MOD_ID, "grid_node_fluids");

    /**
     * 接口仓专用的数据/渲染 UID。
     *
     * <p>**不能与样板仓共用 `grid_node`**：同一份 `NodeData` 注册给两个方块实体类时，Jade 会报
     * 「Found different data providers with same id … this may cause issues」，然后只认一个 ——
     * 表现就是「一类方块有在线状态、另一类没有」（2026-11-09 实测，测试客户端与 ATM10 各缺一边）。
     * 所以两个类各自一对「服务端 data provider + 客户端行 provider」，UID 唯一。
     */
    private static final ResourceLocation INTERFACE_UID =
            ResourceLocation.fromNamespaceAndPath(MiAe2PatternProvider.MOD_ID, "grid_node_interface");

    private static final String TAG_ONLINE = "miae2NodeOnline";
    private static final String TAG_NAME = "miae2NodeName";
    /** 36 槽接口仓的内容预览（物品条目 / 流体条目，各上限若干条）。 */
    private static final String TAG_ITEMS = "miae2Items";
    private static final String TAG_FLUIDS = "miae2Fluids";

    // 这一行的文案在 Jade 与 The One Probe（MeProviderTopPlugin）里共用，所以用本 mod 自己的前缀。
    private static final String KEY_ONLINE = "text.mi_ae2_pattern_provider.device_online";
    private static final String KEY_OFFLINE = "text.mi_ae2_pattern_provider.device_offline";

    /** 与 AE2 的 {@code TooltipProvider.DEFAULT_PRIORITY} 一致，保持这一行在提示里的位置不变。 */
    private static final int PRIORITY = 1000;

    /** 捕获 Jade 给的两个注册句柄，用于「加载完再注册一次」（最后注册者胜）。 */
    private static IWailaCommonRegistration commonRegistration;
    private static IWailaClientRegistration clientRegistration;

    @Override
    public void register(IWailaCommonRegistration registration) {
        commonRegistration = registration;
        registration.registerBlockDataProvider(new NodeData(UID), MePatternProviderBlockEntity.class);
        registration.registerBlockDataProvider(new NodeData(INTERFACE_UID), MeInterfaceBlockEntity.class);
        LOGGER.info("[悬浮提示] Jade 插件已注册（服务端数据：网格节点在线状态）");
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        clientRegistration = registration;
        registration.registerBlockComponent(new NodeLine(UID, false), MachineBlock.class);
        registration.registerBlockComponent(new NodeLine(INTERFACE_UID, true), MachineBlock.class);
        LOGGER.info("[悬浮提示] Jade 插件已注册（客户端行：设备在线 / 设备离线）");
    }

    /** 服务端：把「网格节点是否在线」写进悬浮数据。 */
    private static final class NodeData implements IServerDataProvider<BlockAccessor> {

        private final ResourceLocation uid;

        NodeData(ResourceLocation uid) {
            this.uid = uid;
        }

        @Override
        public ResourceLocation getUid() {
            return this.uid;
        }

        @Override
        public void appendServerData(CompoundTag serverData, BlockAccessor accessor) {
            try {
                boolean online;
                if (accessor.getBlockEntity() instanceof MePatternProviderBlockEntity be) {
                    online = be.miae2$isNodeOnline();
                } else if (accessor.getBlockEntity() instanceof MeInterfaceBlockEntity be) {
                    online = be.miae2$isNodeOnline();
                } else {
                    return;
                }
                serverData.putBoolean(TAG_ONLINE, online);
                // 自定义名也走服务端数据：客户端方块实体不一定能及时拿到（MI 的同步包不保证带它），
                // 而悬浮提示是客户端拼的，直接由服务端给最稳（2026-11-09 实测）。
                if (accessor.getBlockEntity() instanceof net.minecraft.world.Nameable nameable) {
                    var custom = nameable.getCustomName();
                    if (custom != null && !custom.getString().isBlank()) {
                        serverData.putString(TAG_NAME, custom.getString());
                    }
                }

                // 流体数据由 FluidData（UID = grid_node_fluids）单独发送 ——
                // Jade 的服务端数据是**按 provider UID 分开同步**的，混在 NodeData 里 FluidLine 收不到。
            } catch (Throwable ignored) {
                // 悬浮提示永远不该因为一个状态读取失败而报错
            }
        }
    }

    /** 客户端：读服务端写下的布尔并显示一行。 */
    private static final class NodeLine implements IBlockComponentProvider {

        private final ResourceLocation uid;
        private final boolean forInterface;

        NodeLine(ResourceLocation uid, boolean forInterface) {
            this.uid = uid;
            this.forInterface = forInterface;
        }

        @Override
        public ResourceLocation getUid() {
            return this.uid;
        }

        @Override
        public int getDefaultPriority() {
            return PRIORITY;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            try {
                // MI 所有机器共用 MachineBlock → 这个 provider 会被每一台 MI 机器调用，先认领自己的仓。
                boolean matches = this.forInterface
                        ? accessor.getBlockEntity() instanceof MeInterfaceBlockEntity
                        : accessor.getBlockEntity() instanceof MePatternProviderBlockEntity;
                if (!matches) {
                    return;
                }
                CompoundTag serverData = accessor.getServerData();
                if (serverData == null) {
                    return;
                }
                boolean online;
                if (serverData.contains(TAG_ONLINE)) {
                    // Jade 把各 provider 的数据直接写进根 tag
                    online = serverData.getBoolean(TAG_ONLINE);
                } else if (serverData.contains(UID.toString())) {
                    // 兜底：若 Jade 改为按 provider UID 分子 tag 存放
                    CompoundTag sub = serverData.getCompound(UID.toString());
                    if (!sub.contains(TAG_ONLINE)) {
                        return;
                    }
                    online = sub.getBoolean(TAG_ONLINE);
                } else {
                    return;
                }
                tooltip.add(Component.translatable(online ? KEY_ONLINE : KEY_OFFLINE)
                        .withStyle(ChatFormatting.GRAY));
            } catch (Throwable ignored) {
                // 同上：绝不让悬浮提示因为本 mod 报错
            }
        }
    }
}
