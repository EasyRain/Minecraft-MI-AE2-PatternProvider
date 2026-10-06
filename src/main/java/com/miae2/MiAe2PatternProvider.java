package com.miae2;

import appeng.api.AECapabilities;
import appeng.api.networking.IInWorldGridNodeHost;
import aztech.modern_industrialization.MIItem;
import com.miae2.ae.MePatternProviderUpgrades;
import com.miae2.client.MiAe2ConfigScreen;
import com.miae2.compat.MeProviderTopPlugin;
import com.miae2.config.MiAe2Config;
import com.miae2.guide.MiAe2Guide;
import com.miae2.items.ModItems;
import com.miae2.machines.blockentities.MePatternProviderBlockEntity;
import com.miae2.machines.init.ModHatches;
import com.miae2.util.SmokeTestAutoStop;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.InterModComms;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(MiAe2PatternProvider.MOD_ID)
public class MiAe2PatternProvider {

    public static final String MOD_ID = "mi_ae2_pattern_provider";
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * MI 的创造栏 key。本 mod 的物品注册在自己的命名空间（改注册命名空间会破坏已有存档里的物品与配方 id），
     * 所以要主动把它们「插」进 MI 的栏里——JEI 19 的物品列表就是创造栏的内容，不在任何栏里的物品
     * 在创造栏和 JEI 里都搜不到（但配方里查得到，因为配方索引是另一条路）。
     */
    private static final ResourceKey<CreativeModeTab> MI_GENERAL_TAB = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB, ResourceLocation.fromNamespaceAndPath("modern_industrialization", "general"));

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public MiAe2PatternProvider(IEventBus modEventBus, ModContainer modContainer) {
        ModHatches.init();
        ModItems.ITEMS.register(modEventBus);
        // 本 mod 自己的 GuideME 指南书（「使用手册」物品右键打开它）。与 EI / MI 一样在构造函数里注册：
        // 页面本身是资源，注册只是把「指南 id → 页面目录」这条映射登记进 GuideME。
        MiAe2Guide.init();
        // 配置（量子超频模块的并行倍率）。用 COMMON 而不是 SERVER：这个值只被服务端机器 tick 读，
        // COMMON 在两端都保证「已加载」，而 SERVER 配置在未连接服务器的客户端上读会抛异常。
        modContainer.registerConfig(ModConfig.Type.COMMON, MiAe2Config.SPEC);
        // 这个值藏在 config 里，玩家不容易找到——启动时把文件位置直接打进日志。
        LOGGER.info("量子超频模块的并行倍率可在 {} 里调整（键 quantum_overclock.parallel_multiplier；"
                        + "装了 Cloth Config 也能在 Mods 界面点本 mod 的 Config 按钮改，保存即生效）",
                FMLPaths.CONFIGDIR.get().resolve(MOD_ID + "-common.toml"));
        // 可选集成：Cloth Config 的游戏内配置界面。IConfigScreenFactory 是客户端专属类
        // （net.neoforged.neoforge.client.gui），专用服上加载即 NoClassDefFoundError，
        // 所以引用它的代码关在 MiAe2ConfigScreen 里，这里双重守护之后才调用那个类。
        if (FMLEnvironment.dist.isClient() && ModList.get().isLoaded("cloth_config")) {
            MiAe2ConfigScreen.register(modContainer);
        }
        // 把两个超频模块插进 MI 的创造栏（JEI 的物品列表就是创造栏的内容，不进栏就等于「搜不到」）。
        modEventBus.addListener(MiAe2PatternProvider::onBuildCreativeTabContents);
        modEventBus.addListener(MiAe2PatternProvider::registerCapabilities);
        // 升级支持登记放在加载最末尾：其它 mod 都在 FMLCommonSetupEvent 里登记升级卡，
        // 而 NeoForge 保证 FMLLoadCompleteEvent 在其之后派发，此时才能扫全。
        modEventBus.addListener(MiAe2PatternProvider::onLoadComplete);
        // 可选集成：把「设备在线 / 设备离线」交给 The One Probe（TOP 用 IMC 登记，见 MeProviderTopPlugin）。
        modEventBus.addListener(MiAe2PatternProvider::onInterModEnqueue);
        // 冒烟测试自动收尾：仅在 -Dmi_ae2_pattern_provider.smokeTest=<tick> 时生效（平时惰性）。
        SmokeTestAutoStop.init();
        if (SmokeTestAutoStop.isEnabled() && !FMLEnvironment.dist.isClient()) {
            NeoForge.EVENT_BUS.addListener(SmokeTestAutoStop::onServerTick);
        }
        NeoForge.EVENT_BUS.addListener(MiAe2PatternProvider::onRightClickBlock);
        LOGGER.info("MI AE2 Pattern Provider initialized");
    }

    /** 扫描「原版样板供应器能用的升级卡」并嫁接到本 mod 的供应仓上（enqueueWork 保证在主线程执行）。 */
    private static void onLoadComplete(FMLLoadCompleteEvent event) {
        event.enqueueWork(MePatternProviderUpgrades::register);
    }

    /**
     * 可选集成：给 The One Probe 登记悬浮提示 provider。
     *
     * <p>TOP 没有注解扫描，只能走 IMC（{@code "theoneprobe" + "getTheOneProbe"}，载荷是一个接收
     * {@code ITheOneProbe} 的 {@code Function}）。{@code ModList.isLoaded} 判断必须包在外面：
     * 方法引用 {@code MeProviderTopPlugin::new} 只有在 TOP 存在时才会被求值，TOP 缺席时那个类
     * （以及它对 {@code mcjty.theoneprobe} 的引用）根本不会被加载。
     */
    private static void onInterModEnqueue(InterModEnqueueEvent event) {
        if (ModList.get().isLoaded("theoneprobe")) {
            InterModComms.sendTo("theoneprobe", "getTheOneProbe", MeProviderTopPlugin::new);
        }
    }

    /** 手持 ExtendedAE 的「样板供应器升级」+ shift 右键：把普通供应仓原位升级为扩展供应仓。 */
    private static void onRightClickBlock(RightClickBlock event) {
        Player player = event.getEntity();
        InteractionHand hand = event.getHand();
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (player.isSpectator() || !level.mayInteract(player, pos)) {
            return;
        }
        if (!player.isShiftKeyDown()) {
            return;
        }
        if (!MePatternProviderBlockEntity.isPatternProviderUpgrade(player.getItemInHand(hand))) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof MePatternProviderBlockEntity be)) {
            return;
        }
        if (!level.isClientSide()) {
            // 服务端：真正替换方块并消耗升级物品
            if (be.upgradeToExtended() && !player.isCreative()) {
                player.getItemInHand(hand).shrink(1);
            }
        }
        // 两端都取消默认交互（避免 ExtendedAE 升级物品的 useOn / MI 的 useItemOn 再跑一遍）
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide()));
    }

    /**
     * 把「使用手册」插在 MI 自己的「MI 指南书」旁边（栏里第一格），两个超频模块插在「原版超频模块」后面
     * （视觉上是 原版 → 高级 → 量子）。
     *
     * <p>手册贴着 MI 指南书放是因为两者是一类东西（都是翻阅用的书），而栏尾要往下翻才看得到 ——
     * 用户实测反馈「放在 MI 手册的边上而不是需要往下面翻找」。
     *
     * <p>NeoForge 先跑完 MI 自己的 {@code displayItems} 再派发本事件，所以两个锚点都应该在位；
     * 万一将来 MI 换栏/换物品导致锚点不在，就退回追加到栏尾——这只是一条便利性代码，
     * 任何情况下都不该把游戏搞崩。
     */
    private static void onBuildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (!MI_GENERAL_TAB.equals(event.getTabKey())) {
            return;
        }
        // 手册贴着 MI 指南书
        insertAfterOrAppend(event, MIItem.GUIDE_BOOK.stack(), new ItemStack(ModItems.GUIDE.get()));
        // 两个模块仍贴着原版超频模块插：先插的会被后插的顶到后面，于是顺序是 原版 → 高级 → 量子，
        // 而且不依赖「上一次插入是否立刻在 parentEntries 里可见」这种实现细节。
        ItemStack anchor = MIItem.OVERDRIVE_MODULE.stack();
        insertAfterOrAppend(event, anchor, new ItemStack(ModItems.QUANTUM_OVERCLOCK_MODULE.get()));
        insertAfterOrAppend(event, anchor, new ItemStack(ModItems.ADVANCED_OVERCLOCK_MODULE.get()));
    }

    private static void insertAfterOrAppend(BuildCreativeModeTabContentsEvent event, ItemStack anchor, ItemStack entry) {
        if (event.getParentEntries().contains(anchor)) {
            event.insertAfter(anchor, entry, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        } else {
            event.accept(entry, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModHatches.ME_PATTERN_PROVIDER_BLOCK.blockEntityType().get(),
                (be, ctx) -> (IInWorldGridNodeHost) be
        );
        if (ModHatches.ME_EXTENDED_PATTERN_PROVIDER_BLOCK != null) {
            event.registerBlockEntity(
                    AECapabilities.IN_WORLD_GRID_NODE_HOST,
                    ModHatches.ME_EXTENDED_PATTERN_PROVIDER_BLOCK.blockEntityType().get(),
                    (be, ctx) -> (IInWorldGridNodeHost) be
            );
        }
    }
}
