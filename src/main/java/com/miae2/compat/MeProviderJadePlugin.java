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

    private static final String TAG_ONLINE = "miae2NodeOnline";
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

    /**
     * 在 `FMLLoadCompleteEvent` 里再注册一次流体存储 provider。
     *
     * <p>原因：Jade 的存储 provider 表是「一个类 → 一个 provider」，MI 给 `MachineBlockEntity`
     * 也注册了，谁的注册晚谁生效。我们在插件 `register()` 里那次会被 MI 覆盖掉
     * （实测 `getGroups` 一次都没被调用），所以必须等所有 mod 加载完再补一次。
     */
    public static void reRegisterFluidStorageLate() {
        try {
            if (commonRegistration != null) {
                commonRegistration.registerFluidStorage(new InterfaceFluidStorageProvider(),
                        MeInterfaceBlockEntity.class);
            }
            if (clientRegistration != null) {
                clientRegistration.registerFluidStorageClient(new InterfaceFluidStorageProvider());
            }
            LOGGER.info("[JadeFluid] late register done");
        } catch (Throwable t) {
            LOGGER.warn("[悬浮提示] 流体存储 provider 延迟注册失败", t);
        }
    }

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(new NodeData(), MePatternProviderBlockEntity.class);
        commonRegistration = registration;
        registration.registerBlockDataProvider(new NodeData(), MeInterfaceBlockEntity.class);
        registration.registerFluidStorage(new InterfaceFluidStorageProvider(), MeInterfaceBlockEntity.class);
        LOGGER.info("[悬浮提示] Jade 插件已注册（服务端数据：网格节点在线状态）");
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        clientRegistration = registration;
        registration.registerBlockComponent(new NodeLine(), MachineBlock.class);
        registration.registerFluidStorageClient(new InterfaceFluidStorageProvider());
        LOGGER.info("[悬浮提示] Jade 插件已注册（客户端行：设备在线 / 设备离线）");
    }

    /** 服务端：把「网格节点是否在线」写进悬浮数据。 */
    private static final class NodeData implements IServerDataProvider<BlockAccessor> {

        @Override
        public ResourceLocation getUid() {
            return UID;
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

                // 兜底：只有「标准流体存储 provider 没有被 Jade 调用」时才自己渲染流体行，
                // 否则会和标准管道输出重复（2026-11-07 反复踩过的坑）。
                if (!InterfaceFluidStorageProvider.wasCalled()
                        && accessor.getBlockEntity() instanceof MeInterfaceBlockEntity be) {
                    be.miae2$writeContentsPreview(serverData);
                }
            } catch (Throwable ignored) {
                // 悬浮提示永远不该因为一个状态读取失败而报错
            }
        }
    }

    /**
     * 渲染流体预览：每行「图标 + `1B / 64B`」，用 Jade 的盒子包一圈 —— 就是原版 AE / Jade 标准胶囊样式。
     *
     * <p>物品那行不在这里做：MI 自己的物品预览对我们这几个仓是可用 的，重复输出会变成两行。
     */
    private static void appendPreview(ITooltip tooltip, BlockAccessor accessor, CompoundTag serverData) {
        ListTag fluids = serverData.getList("miae2Fluids", net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < fluids.size(); i++) {
            String[] parts = fluids.getString(i).split("\\|");
            if (parts.length != 3) {
                continue;
            }
            net.minecraft.world.level.material.Fluid fluid =
                    net.minecraft.core.registries.BuiltInRegistries.FLUID.get(net.minecraft.resources.ResourceLocation.parse(parts[0]));
            if (fluid == net.minecraft.world.level.material.Fluids.EMPTY) {
                continue;
            }
            long amount;
            long capacity;
            try {
                amount = Long.parseLong(parts[1]);
                capacity = Long.parseLong(parts[2]);
            } catch (NumberFormatException e) {
                continue;
            }
            // 纯文字一行：`熔岩 1B`，按住 Shift 才追加 ` / 64B`（2026-11-08 用户定的样式，放弃框和进度条）
            String label = fluid.getFluidType().getDescription().getString() + " "
                    + MeInterfaceBlockEntity.miae2$formatFluid(amount);
            if (accessor.showDetails()) {
                label += " / " + MeInterfaceBlockEntity.miae2$formatFluid(capacity);
            }
            tooltip.add(Component.literal(label).withStyle(ChatFormatting.GRAY));
        }

        if (serverData.contains("miae2Free")) {
            tooltip.add(Component.literal(
                    "空 " + MeInterfaceBlockEntity.miae2$formatFluid(serverData.getLong("miae2Free")))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /** 客户端：读服务端写下的布尔并显示一行。 */
    private static final class NodeLine implements IBlockComponentProvider {

        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        @Override
        public int getDefaultPriority() {
            return PRIORITY;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            try {
                // MI 所有机器共用 MachineBlock → 这个 provider 会被每一台 MI 机器调用，先认领自己的仓。
                if (!(accessor.getBlockEntity() instanceof MePatternProviderBlockEntity)
                        && !(accessor.getBlockEntity() instanceof MeInterfaceBlockEntity)) {
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

                // 36 槽接口仓的内容预览（原版档由 MI 自己的 provider 提供，别重复）
                if (accessor.getBlockEntity() instanceof MeInterfaceBlockEntity
                        && serverData.contains("miae2Fluids")) {
                    appendPreview(tooltip, accessor, serverData);
                }
            } catch (Throwable ignored) {
                // 同上：绝不让悬浮提示因为本 mod 报错
            }
        }
    }
}
