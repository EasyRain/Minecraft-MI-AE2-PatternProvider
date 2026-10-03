package com.miae2.util;

import aztech.modern_industrialization.api.energy.EnergyApi;
import aztech.modern_industrialization.inventory.ConfigurableItemStack;
import aztech.modern_industrialization.machines.MachineBlockEntity;
import aztech.modern_industrialization.machines.multiblocks.HatchBlockEntity;
import aztech.modern_industrialization.machines.multiblocks.HatchTypes;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.miae2.machines.blockentities.MePatternProviderBlockEntity;
import com.miae2.machines.init.ModHatches;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.swedz.extended_industrialization.machines.component.craft.processingarray.ProcessingArrayMachineComponent;
import net.swedz.extended_industrialization.machines.guicomponent.processingarraymachineslot.ProcessingArrayMachineSlot;
import net.swedz.tesseract.neoforge.compat.mi.api.ComponentStackHolder;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * 冒烟测试自动收尾，避免「启动 → 傻等 → 手动 stop」。
 *
 * <p>用法（工作区惯例：用 {@code JAVA_TOOL_OPTIONS} 给被 fork 的游戏 JVM 传系统属性，
 * 和 {@code -Dmixin.debug.verbose=true} 同一套路）：
 * <pre>
 * $env:JAVA_TOOL_OPTIONS="-Dmi_ae2_pattern_provider.smokeTest=100"   # 跑满 100 tick 后自动关闭
 * gradle runServer
 * </pre>
 * 属性缺省时整个机制惰性（每次 tick 只是一次布尔判断），正常运行不受影响。客户端由
 * {@code SmokeTestAutoStopClient} 挂 {@code ClientTickEvent.Post} 收尾。
 */
public final class SmokeTestAutoStop {

    /** 系统属性名；值 = 启动后允许运行的 tick 数。 */
    public static final String PROPERTY = "mi_ae2_pattern_provider.smokeTest";

    private static final int DEFAULT_TICKS = 100;
    private static final int MIN_TICKS = 20;

    private static final Logger LOGGER = LogUtils.getLogger();

    private static boolean enabled;
    private static int budget;
    private static boolean fired;
    private static boolean selfCheckDone;
    private static boolean capacityCheckDone;
    private static boolean patternCheckSetupDone;
    private static boolean patternCheckAsserted;
    @Nullable
    private static BlockPos patternCheckPos;

    private SmokeTestAutoStop() {
    }

    /** 由 mod 构造函数调用一次：解析系统属性，决定是否进入冒烟测试模式。 */
    public static void init() {
        String raw = System.getProperty(PROPERTY);
        if (raw == null) {
            return;
        }
        int ticks;
        try {
            ticks = raw.isBlank() ? DEFAULT_TICKS : Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            ticks = DEFAULT_TICKS;
        }
        budget = Math.max(MIN_TICKS, ticks);
        enabled = true;
        LOGGER.info("冒烟测试模式：将在启动后 {} tick 自动收尾（-D{}=...）", budget, PROPERTY);
        forceLoadOptionalMixinTargets();
    }

    /**
     * 本 mod 有些可选 mixin 的目标类只在「实际用到」时才被加载（例如扩展仓被创建时才会碰
     * ExtendedAE-Plus 的 {@code UpgradeSlotCompat}）—— 空跑冒烟测试永远看不到它们，
     * 也就无法在日志里确认 mixin 是否真的贴上了。这里在冒烟测试模式下提前把它们加载一遍，
     * 让 Mixin 的 "Mixing ... into ..." 调试行出现在日志里（加载但不执行静态初始化）。
     */
    private static void forceLoadOptionalMixinTargets() {
        for (String className : new String[]{
                "com.extendedae_plus.compat.UpgradeSlotCompat",
                "com.glodblock.github.appflux.common.me.energy.EnergyCapCache"}) {
            try {
                Class.forName(className, false, SmokeTestAutoStop.class.getClassLoader());
                LOGGER.info("冒烟测试：已提前加载可选 mixin 目标 {}", className);
            } catch (Throwable t) {
                LOGGER.info("冒烟测试：可选 mixin 目标 {} 不可用（{}）—— 对应功能按降级处理，不影响启动",
                        className, t.getClass().getSimpleName());
            }
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * 每 tick 调用一次消费预算。
     *
     * @return true 表示预算用尽、调用方应当主动关闭游戏
     */
    public static boolean tickAndShouldStop() {
        if (!enabled || fired) {
            return false;
        }
        if (--budget > 0) {
            return false;
        }
        fired = true;
        return true;
    }

    /** 扫注册表找一个 MI 的能源输入仓方块（档位前缀随 MI 版本可能不同，所以不写死 id）。 */
    @Nullable
    private static Block findMiEnergyInputHatchBlock() {
        return findBlock("modern_industrialization", "_energy_input_hatch");
    }

    /** 扫注册表按「命名空间 + 路径后缀」找方块（各类 id 的档位/前缀可能随版本变化，所以不写死全 id）。 */
    @Nullable
    private static Block findBlock(String namespace, String pathSuffix) {
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (namespace.equals(id.getNamespace()) && id.getPath().endsWith(pathSuffix)) {
                return block;
            }
        }
        return null;
    }

    /** 扫注册表按「命名空间 + 完整路径」找一个物品（用于取「中级升级」这种干扰物品）。 */
    @Nullable
    private static Item findItem(String namespace, String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(namespace, path);
        for (Item item : BuiltInRegistries.ITEM) {
            if (id.equals(BuiltInRegistries.ITEM.getKey(item))) {
                return item;
            }
        }
        return null;
    }

    /**
     * 扫注册表找一个「能放进 EI 处理阵列的工作方块」。
     *
     * <p>直接复用 EI 自己的判定 {@link ProcessingArrayMachineSlot#isMachine(Item)}，不自己实现一份规则 ——
     * 否则自检会因为「判定规则与 EI 不一致」而假失败。
     */
    @Nullable
    private static Item findProcessingArrayMachineItem() {
        for (Item item : BuiltInRegistries.ITEM) {
            if (!"modern_industrialization".equals(BuiltInRegistries.ITEM.getKey(item).getNamespace())) {
                continue;
            }
            if (ProcessingArrayMachineSlot.isMachine(item)) {
                return item;
            }
        }
        return null;
    }

    /** 组件渲染后的字符串是否一致（用文本比较，避免依赖服务端是否加载了语言文件）。 */
    private static boolean namesEqual(Component a, Component b) {
        return a.getString().equals(b.getString());
    }

    /** 专用服务端自动收尾（客户端时不注册本监听，改由客户端类收尾）。 */
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld != null && !selfCheckDone) {
            selfCheckDone = true;
            runInductionRetargetSelfCheck(server);
        }
        if (!capacityCheckDone) {
            capacityCheckDone = true;
            runCapacityPreservationSelfCheck(server);
        }
        if (overworld != null && !patternCheckSetupDone) {
            patternCheckSetupDone = true;
            setupPatternRegistrationCheck(overworld);
        } else if (overworld != null && !patternCheckAsserted && budget <= 60) {
            // 留出足够 tick 让「首次 tick 的读档初始化」跑完再断言
            patternCheckAsserted = true;
            assertPatternRegistration(overworld);
        }
        if (tickAndShouldStop()) {
            LOGGER.info("冒烟测试结束：主动关闭服务器（日志无异常即视为通过）");
            server.halt(false);
        }
    }

    /**
     * 冒烟测试：<b>真实复现「重进存档」</b>后样板是否还能参与合成。
     *
     * <p>背景（真实 bug）：我们漏了 AE2 {@code PatternProviderBlockEntity#onReady()} 里的
     * {@code logic.updatePatterns()}。而 {@code AppEngInternalInventory#readFromNBT} 是直接
     * {@code stacks.set(...)}、**不触发任何库存通知**，所以读档后 {@code patterns} 列表保持为空 ——
     * 终端看得见供应器与样板，但样板完全不参与合成。
     *
     * <p>注意：不能用 {@code setItemDirect} 来「造」这个场景 —— 它**会**通知
     * （{@code onChangeInventory} → {@code updatePatterns}），等于走了「玩家手动重插样板」那条捷径，
     * 会让检查变成假阳性。所以这里老老实实走一遍「存 NBT → 整个方块实体换新 → 灌回 NBT」。
     */
    private static void setupPatternRegistrationCheck(ServerLevel level) {
        try {
            if (ModHatches.ME_PATTERN_PROVIDER_BLOCK == null) {
                return;
            }
            // 关键：区块必须真的在 tick，方块实体才会被 tick —— 否则 AE2 的 GridHelper.onFirstTick
            // 与我们的 tick() 一次性初始化都不会执行（上一版自检就是这么「假失败」的）。
            level.setChunkForced(0, 0, true);
            BlockPos pos = new BlockPos(8, 200, 12);
            BlockState state = ModHatches.ME_PATTERN_PROVIDER_BLOCK.asBlock().defaultBlockState();
            level.setBlock(pos, state, 3);
            if (!(level.getBlockEntity(pos) instanceof MePatternProviderBlockEntity provider)) {
                LOGGER.info("冒烟测试：样板注册自检——供应仓方块实体未创建，跳过");
                patternCheckPos = null;
                return;
            }

            ItemStack pattern = PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)),
                    List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1)));
            provider.getLogic().getPatternInv().setItemDirect(0, pattern);

            CompoundTag contents = provider.saveWithFullMetadata(level.registryAccess());

            // 整个方块实体换新 + 灌回 NBT —— 等价于「重进存档」
            BlockEntity fresh = ModHatches.ME_PATTERN_PROVIDER_BLOCK.blockEntityType().get().create(pos, state);
            if (fresh == null) {
                patternCheckPos = null;
                return;
            }
            level.removeBlockEntity(pos);
            level.removeBlock(pos, false);
            level.setBlock(pos, state, 3);
            level.setBlockEntity(fresh);
            fresh.loadWithComponents(contents, level.registryAccess());
            fresh.setChanged();

            patternCheckPos = pos;
            LOGGER.info("冒烟测试：已构造「重进存档」场景（样板写入 NBT 并灌入新方块实体）");
        } catch (Throwable t) {
            patternCheckPos = null;
            LOGGER.error("冒烟测试：❌ 样板注册自检的准备阶段失败", t);
        }
    }

    private static void assertPatternRegistration(ServerLevel level) {
        if (patternCheckPos == null) {
            return;
        }
        try {
            if (!(level.getBlockEntity(patternCheckPos) instanceof MePatternProviderBlockEntity provider)) {
                LOGGER.info("冒烟测试：样板注册自检——供应仓已不在，跳过");
                return;
            }
            int count = provider.getLogic().getAvailablePatterns().size();
            if (count > 0) {
                LOGGER.info("冒烟测试：✅ 读档初始化自检通过——样板已注册进合成服务（patterns={}）", count);
            } else {
                LOGGER.error("冒烟测试：❌ 读档初始化自检失败——patterns 为空，样板不会参与合成"
                        + "（确认首次 tick 是否调用了 logic.updatePatterns()）");
            }
            assertSlotCapacityPolicy(provider);
            assertOutputLockSpreading(provider);
            assertOutputQuotaSpreading();
            assertGuiLayoutFitsSlots(provider);
            assertAutoNaming(level, provider);
            level.setBlock(patternCheckPos, Blocks.AIR.defaultBlockState(), 3);
        } catch (Throwable t) {
            LOGGER.error("冒烟测试：❌ 样板注册自检的断言阶段失败", t);
        }
    }

    /**
     * 冒烟测试：确认槽位容量策略正确 —— <b>输入槽无上限、输出槽普通容量（64）</b>。
     *
     * <p>背景（真实 bug「大型合成做到一半永久卡死、输出格还锁着、输入格是空的」）：输出槽曾被标成无上限，
     * EI 的处理阵列据此把并行倍率抬到阵列机器数（{@code canItemOutputsAllFit} 永远为真），而 tesseract 的
     * 真插入一格只装得下 64、多出来的产物被静默销毁 ⇒ AE 的 CPU 永远等不到足量产。详情见
     * {@code MePatternProviderBlockEntity#miae2$enforceSlotCapacityPolicy()}。
     */
    private static void assertSlotCapacityPolicy(MePatternProviderBlockEntity provider) {
        List<ConfigurableItemStack> inputs = provider.miae2$itemInputs();
        List<ConfigurableItemStack> outputs = new java.util.ArrayList<>();
        provider.appendItemOutputs(outputs);

        boolean inputsOk = !inputs.isEmpty() && inputs.stream().allMatch(stack ->
                ((IUnboundedItemAccessor) stack).miae2$isUnbounded()
                        && stack.getCapacity() == Integer.MAX_VALUE);
        boolean outputsOk = !outputs.isEmpty() && outputs.stream().allMatch(stack ->
                !((IUnboundedItemAccessor) stack).miae2$isUnbounded()
                        && stack.getCapacity() <= 64L);
        if (inputsOk && outputsOk) {
            LOGGER.info("冒烟测试：✅ 槽位容量策略自检通过——输入槽 {} 个全部无上限、输出槽 {} 个全部普通容量（≤64）",
                    inputs.size(), outputs.size());
        } else {
            LOGGER.error("冒烟测试：❌ 槽位容量策略自检失败——输入槽无上限={}（{} 个）、输出槽普通容量={}（{} 个）。"
                            + "输出槽若为无上限，处理阵列会把并行倍率抬到机器数、超出一格的产物被静默销毁，"
                            + "大型合成会永久卡在「输出格锁着、输入格空、机器不动」",
                    inputsOk, inputs.size(), outputsOk, outputs.size());
        }
    }

    /**
     * 冒烟测试：确认「输出格按产物摊开锁满」。
     *
     * <p>EI 的处理阵列按「输出格一共还能装多少」二分反推并行倍率（tesseract 的
     * {@code MultipliedCrafterComponent#calculateItemOutputRecipeMultiplier}），而真插入一格最多
     * {@code ItemVariant.getMaxStackSize()}=64 —— 只锁一格会把倍率卡在 {@code 64/单次产量}。
     * 所以 {@link MePatternProviderBlockEntity#lockOutputs} 应当把产物摊到所有输出格上。
     */
    private static void assertOutputLockSpreading(MePatternProviderBlockEntity provider) {
        List<IPatternDetails> patterns = provider.getLogic().getAvailablePatterns();
        if (patterns.isEmpty()) {
            return;
        }
        provider.lockOutputs(patterns.get(0));

        List<ConfigurableItemStack> outputs = new java.util.ArrayList<>();
        provider.appendItemOutputs(outputs);
        long locked = outputs.stream().filter(ConfigurableItemStack::isPlayerLocked).count();
        if (!outputs.isEmpty() && locked == outputs.size()) {
            LOGGER.info("冒烟测试：✅ 输出格摊开锁定自检通过——{} 个输出格全部按产物上锁"
                            + "（只锁一格时阵列并行倍率会被卡在 64/单次产量）", locked);
        } else {
            LOGGER.error("冒烟测试：❌ 输出格摊开锁定自检失败——只有 {} / {} 个输出格上了锁。"
                            + "只锁一格会让 EI 处理阵列的并行倍率被卡在 64/单次产量，输出效率大幅下降",
                    locked, outputs.size());
        }
    }

    /**
     * 冒烟测试：确认「一个产物吃满所有格」这种退化<b>不会</b>发生 —— 格数分配应当近似正比于单次产量。
     *
     * <p>分配的目标是抬高 {@code min_i(64 * 格数_i / 产量_i)}（阵列的可行倍率就是各产物的最小值），
     * 所以按产量成比例分配是最优解。这里用「往最低天花板加水」的贪心复核：产量 2 / 4 / 8、27 格
     * 应当得到约 4 / 8 / 15 格（各产物天花板抬到同一水平），而不是「每个 1 格 + 剩下 24 格全给某一个」。
     */
    private static void assertOutputQuotaSpreading() {
        long[] amounts = {2L, 4L, 8L};
        int slots = ModHatches.ITEM_OUTPUT_SLOTS;
        int[] quota = MePatternProviderBlockEntity.spreadOutputQuotas(amounts, slots);

        int sum = 0;
        double highest = 0.0D;
        double lowest = Double.MAX_VALUE;
        for (int i = 0; i < quota.length; i++) {
            sum += quota[i];
            double ceiling = 64.0D * quota[i] / amounts[i];
            highest = Math.max(highest, ceiling);
            lowest = Math.min(lowest, ceiling);
        }

        boolean eachHasSlot = true;
        for (int q : quota) {
            eachHasSlot &= q >= 1;
        }
        boolean nobodyHogs = quota[0] < slots && quota[1] < slots && quota[2] < slots;
        boolean ceilingsLevel = lowest > 0 && highest / lowest < 1.5D;

        if (sum == slots && eachHasSlot && nobodyHogs && ceilingsLevel) {
            LOGGER.info("冒烟测试：✅ 输出格分配自检通过——单次产量 2/4/8 分到 {}/{}/{} 格（共 {}），"
                            + "各产物倍率天花板 {}~{}（抬平、无人吃满）",
                    quota[0], quota[1], quota[2], sum, (long) lowest, (long) highest);
        } else {
            LOGGER.error("冒烟测试：❌ 输出格分配自检失败——产量 2/4/8 分到 {}/{}/{} 格（共 {}，应为 {}）、"
                            + "天花板 {}~{}（最高/最低 = {}，应 < 1.5）。"
                            + "若某个产物把格子吃满，其他产物会先撞上限，阵列倍率被它拖死",
                    quota[0], quota[1], quota[2], sum, slots, (long) lowest, (long) highest,
                    lowest > 0 ? highest / lowest : -1.0D);
        }
    }

    /**
     * 冒烟测试：确认 GUI 高度装得下所有槽位行、且槽位数与常量一致。
     *
     * <p>MI 把玩家背包首行放在 {@code backgroundHeight - 82}、背包标题放在 {@code backgroundHeight - 94}；
     * 我们的槽位行从 y=20 起、每行 18px。高度写小<b>不会有任何报错</b>，只会让最下面几行槽位被背包压住
     * （表现为「格子看不见」），所以这里按同一套公式复核一遍。
     */
    private static void assertGuiLayoutFitsSlots(MePatternProviderBlockEntity provider) {
        int needed = MePatternProviderBlockEntity.requiredGuiHeight(
                ModHatches.ITEM_INPUT_SLOTS, ModHatches.ITEM_OUTPUT_SLOTS,
                ModHatches.FLUID_INPUT_SLOTS, ModHatches.FLUID_OUTPUT_SLOTS);
        int actual = provider.guiParams.backgroundHeight;

        List<ConfigurableItemStack> outputs = new java.util.ArrayList<>();
        provider.appendItemOutputs(outputs);
        boolean slotsMatch = outputs.size() == ModHatches.ITEM_OUTPUT_SLOTS;

        if (actual >= needed && slotsMatch) {
            LOGGER.info("冒烟测试：✅ GUI 槽位布局自检通过——GUI 高 {}（需要 ≥ {}）、物品输出 {} 格",
                    actual, needed, outputs.size());
        } else {
            LOGGER.error("冒烟测试：❌ GUI 槽位布局自检失败——GUI 高 {}（需要 ≥ {}）、物品输出 {} 格（应为 {}）。"
                            + "高度不够时最下面几行槽位会被玩家背包压住（不报错、只表现为格子看不见）",
                    actual, needed, outputs.size(), ModHatches.ITEM_OUTPUT_SLOTS);
        }
    }

    /**
     * 冒烟测试：确认管理终端里的自动命名三条路径都对 ——
     * <ol>
     *   <li>没接控制器（未成形 / 控制器位置没有方块实体）：默认名；</li>
     *   <li>普通多方块（电力高炉这种）：{@code <控制器名>样板供应仓}；</li>
     *   <li>阵列（EI 处理阵列 / Industrialization Overdrive 多方块处理阵列）：{@code <工作方块名>处理阵列样板供应仓}。</li>
     * </ol>
     *
     * <p>这里真放方块、真读控制器方块实体；给阵列灌工作方块时<b>刻意不用</b>「取第一个
     * {@link ComponentStackHolder}」那种写法 —— 那正是踩过的坑：tesseract 把该接口也混进了 MI 的
     * 升级 / 红石 / 超频组件，而这些组件在阵列基类里注册得比 {@code machines} 更早，于是「第一个命中」
     * 拿到的是<b>升级槽里的升级物品</b>（曾把供应器命名成「中级升级处理阵列…样板供应仓」）。
     * 所以自检里：工作方块显式灌进 EI 自己的 {@code ProcessingArrayMachineComponent}，
     * 同时把所有其它 {@code ComponentStackHolder} 槽位都塞上「升级物品」来复现用户场景；
     * 另外再用纯函数直接喂「升级 + 机器」两种候选，确认只认机器方块物品、
     * 且只有升级物品时返回空（宁可回落普通多方块名，也不显示错误名字）。
     * 比对用「同样组件拼出来的期望字符串」，所以不依赖服务端是否加载了语言文件，不会假失败。
     */
    private static void assertAutoNaming(ServerLevel level, MePatternProviderBlockEntity provider) {
        try {
            Component expectedFallback = Component.translatable(provider.isExtended()
                    ? "text.mi_ae2_pattern_provider.extended_hatch_name"
                    : "text.mi_ae2_pattern_provider.hatch_name");

            // ① 没接控制器：controllerPos = null、以及指向「没有方块实体的位置」
            provider.miae2$setControllerPos(null);
            boolean nullOk = namesEqual(provider.getTerminalGroup().name(), expectedFallback);
            BlockPos emptyPos = provider.getBlockPos().offset(2, 0, 0);
            level.setBlock(emptyPos, Blocks.AIR.defaultBlockState(), 3);
            provider.miae2$setControllerPos(emptyPos);
            boolean emptyOk = namesEqual(provider.getTerminalGroup().name(), expectedFallback);

            // ② 普通多方块：拿一个 MI 的仓当控制器（是 MachineBlockEntity，但没有工作方块）
            Block hatchBlock = findMiEnergyInputHatchBlock();
            MachineBlockEntity controller = null;
            if (hatchBlock != null) {
                BlockPos multiblockPos = provider.getBlockPos().offset(3, 0, 0);
                level.setBlock(multiblockPos, hatchBlock.defaultBlockState(), 3);
                if (level.getBlockEntity(multiblockPos) instanceof MachineBlockEntity machine) {
                    controller = machine;
                    provider.miae2$setControllerPos(multiblockPos);
                }
            }
            Component multiblockActual = Component.empty();
            boolean multiblockOk = false;
            if (controller != null) {
                Component expectedMultiblock = controller.getDisplayName().copy().append(Component.translatable(
                        provider.isExtended()
                                ? "text.mi_ae2_pattern_provider.extended_multiblock_suffix"
                                : "text.mi_ae2_pattern_provider.multiblock_suffix"));
                multiblockActual = provider.getTerminalGroup().name();
                // 除了「等于期望」，还要确认它确实不是回落默认名（否则等于没测出分支）
                multiblockOk = namesEqual(multiblockActual, expectedMultiblock)
                        && !namesEqual(multiblockActual, expectedFallback);
            }

            // ③ 阵列：放一个 EI 处理阵列控制器；工作方块灌进 EI 自己的组件，其余槽位塞「升级物品」当干扰
            Component arrayActual = Component.empty();
            boolean arrayOk = false;
            boolean arrayTested = false;
            String arraySkip = "";
            Block arrayBlock = findBlock("extended_industrialization", "processing_array");
            Item machineItem = findProcessingArrayMachineItem();
            if (arrayBlock == null) {
                arraySkip = "找不到 EI 处理阵列方块";
            } else if (machineItem == null) {
                arraySkip = "找不到可用的工作方块物品";
            } else {
                BlockPos arrayPos = provider.getBlockPos().offset(4, 0, 0);
                level.setBlock(arrayPos, arrayBlock.defaultBlockState(), 3);
                if (!(level.getBlockEntity(arrayPos) instanceof MachineBlockEntity arrayMachine)) {
                    arraySkip = "处理阵列方块实体未创建";
                } else if (!(arrayMachine.components.getNullable(ProcessingArrayMachineComponent.class)
                        instanceof ProcessingArrayMachineComponent machines)) {
                    arraySkip = "处理阵列控制器上没有 EI 的 ProcessingArrayMachineComponent";
                } else {
                    // 干扰：把「中级升级」塞进其它 ComponentStackHolder 槽位（升级 / 红石 / 超频）——
                    // 复现用户存档里的阵列（升级槽放着升级物品）
                    Item upgradeItem = findItem("modern_industrialization", "turbo_upgrade");
                    if (upgradeItem != null) {
                        for (ComponentStackHolder holder : arrayMachine.components.getAll(ComponentStackHolder.class)) {
                            if (holder != machines) {
                                holder.setStack(new ItemStack(upgradeItem));
                            }
                        }
                    }
                    ItemStack workBlock = new ItemStack(machineItem);
                    machines.setStack(workBlock);
                    provider.miae2$setControllerPos(arrayPos);
                    arrayActual = provider.getTerminalGroup().name();
                    Component expectedArray = workBlock.getHoverName().copy().append(Component.translatable(
                            provider.isExtended()
                                    ? "text.mi_ae2_pattern_provider.extended_processing_array_suffix"
                                    : "text.mi_ae2_pattern_provider.processing_array_suffix"));
                    // 生产入口也直接验一遍：必须挑中工作方块，而不是升级物品
                    boolean pickedWorkBlock = ItemStack.isSameItemSameComponents(
                            MePatternProviderBlockEntity.findHostedWorkBlock(arrayMachine), workBlock);
                    arrayOk = namesEqual(arrayActual, expectedArray)
                            && !namesEqual(arrayActual, expectedFallback)
                            && pickedWorkBlock;
                    arrayTested = true;
                }
            }

            // ③′ 纯函数级回归：候选里混着升级物品时只认机器方块物品；只有升级物品时返回空
            Item probeUpgrade = findItem("modern_industrialization", "turbo_upgrade");
            Item probeMachine = findProcessingArrayMachineItem();
            boolean pickOk = true;
            String pickDetail = "（跳过：环境里没有 turbo_upgrade 或工作方块物品）";
            if (probeUpgrade != null && probeMachine != null) {
                ItemStack mixed = MePatternProviderBlockEntity.pickHostedWorkBlock(
                        List.of(new ItemStack(probeUpgrade), new ItemStack(probeMachine)));
                ItemStack upgradeAlone = MePatternProviderBlockEntity.pickHostedWorkBlock(
                        List.of(new ItemStack(probeUpgrade)));
                pickOk = ItemStack.isSameItemSameComponents(mixed, new ItemStack(probeMachine)) && upgradeAlone.isEmpty();
                pickDetail = "候选=「升级,机器」→「" + mixed.getHoverName().getString() + "」、候选=「升级」→"
                        + (upgradeAlone.isEmpty() ? "空" : "「" + upgradeAlone.getHoverName().getString() + "」");
            }

            boolean fallbackOk = nullOk && emptyOk;
            if (fallbackOk && multiblockOk && pickOk && arrayOk) {
                LOGGER.info("冒烟测试：✅ 自动命名自检通过——未接控制器「{}」、普通多方块「{}」、"
                                + "阵列（升级槽有干扰物品）「{}」、候选筛选 {}",
                        expectedFallback.getString(), multiblockActual.getString(),
                        arrayActual.getString(), pickDetail);
            } else if (!arrayTested) {
                LOGGER.error("冒烟测试：❌ 自动命名自检未覆盖阵列分支（{}）——默认名={}、普通多方块={}（实际「{}」）",
                        arraySkip, fallbackOk, multiblockOk, multiblockActual.getString());
            } else {
                LOGGER.error("冒烟测试：❌ 自动命名自检失败——默认名={}（null={}、空位={}）、普通多方块={}（实际「{}」）、"
                                + "阵列={}（实际「{}」）、候选筛选={} {}。升级槽物品被当成工作方块、普通多方块被误判成阵列、"
                                + "或新阵列 mod 认不出来时会这样",
                        fallbackOk, nullOk, emptyOk, multiblockOk, multiblockActual.getString(),
                        arrayOk, arrayActual.getString(), pickOk, pickDetail);
            }
        } catch (Throwable t) {
            LOGGER.error("冒烟测试：❌ 自动命名自检抛异常", t);
        }
    }

    /**
     * 冒烟测试专用：<b>真跑一遍</b>感应卡的能量重定向路径，而不是只看「mixin 贴上了」。
     *
     * <p>动机：曾经只验证了 mixin 注入绑定成功，结果运行时在
     * {@code miae2$resolve} 里因 {@code @Unique} 字段未初始化而 NPE 崩服。这里在世界里放一个
     * 本 mod 的供应仓 + 一个 MI 能源输入仓，然后反射构造 AppliedFlux 的 {@code EnergyCapCache}
     * 并调一次 {@code getEnergyCap} —— 这正是崩溃栈里那条路径。
     *
     * <p>全程 try/catch：自检失败只打日志，绝不影响启动。反射调用是为了零编译依赖
     * （AppliedFlux 是可选依赖，也不往我们产物里塞它的代码）。
     */
    private static void runInductionRetargetSelfCheck(MinecraftServer server) {
        BlockPos hatchPos = new BlockPos(8, 200, 8);
        BlockPos energyPos = new BlockPos(9, 200, 8);
        try {
            ServerLevel level = server.overworld();
            if (ModHatches.ME_PATTERN_PROVIDER_BLOCK == null) {
                LOGGER.info("冒烟测试：供应仓未注册，跳过感应卡自检");
                return;
            }
            level.setBlock(hatchPos, ModHatches.ME_PATTERN_PROVIDER_BLOCK.asBlock().defaultBlockState(), 3);

            Block energyHatchBlock = findMiEnergyInputHatchBlock();
            if (energyHatchBlock == null) {
                LOGGER.info("冒烟测试：找不到 MI 能源输入仓方块，跳过感应卡自检");
                return;
            }
            level.setBlock(energyPos, energyHatchBlock.defaultBlockState(), 3);

            if (!(level.getBlockEntity(hatchPos) instanceof MePatternProviderBlockEntity provider)) {
                LOGGER.info("冒烟测试：供应仓方块实体未创建，跳过感应卡自检");
                return;
            }
            boolean isEnergyInput = level.getBlockEntity(energyPos) instanceof HatchBlockEntity hatch
                    && hatch.getHatchType() == HatchTypes.ENERGY_INPUT;
            if (!isEnergyInput) {
                LOGGER.info("冒烟测试：放下的仓不是能源输入仓，感应卡自检无法覆盖重定向分支，跳过");
                return;
            }
            provider.miae2$setEnergyInputHatchPositions(List.of(energyPos));

            Class<?> cacheClass = Class.forName("com.glodblock.github.appflux.common.me.energy.EnergyCapCache");
            Object cache = cacheClass
                    .getConstructor(ServerLevel.class, BlockPos.class, Supplier.class)
                    .newInstance(level, hatchPos, (Supplier<Object>) () -> null);
            Method getEnergyCap = cacheClass.getMethod("getEnergyCap", BlockCapability.class, Direction.class);

            // 连调两次：第二次会走「位置集合没变 → 复用缓存」的分支
            Object feFirst = getEnergyCap.invoke(cache, Capabilities.EnergyStorage.BLOCK, Direction.UP);
            getEnergyCap.invoke(cache, Capabilities.EnergyStorage.BLOCK, Direction.DOWN);
            // MI 自己的 EU 能力：AppliedFlux 的 MI handler 走的是这条（比通用 FE 更可能命中）
            Object eu = getEnergyCap.invoke(cache, EnergyApi.SIDED, Direction.UP);
            // 再换一次能源仓位置，覆盖「集合变了 → 清空缓存重建」的分支（就是崩过的那行）
            provider.miae2$setEnergyInputHatchPositions(List.of(energyPos.above()));
            getEnergyCap.invoke(cache, Capabilities.EnergyStorage.BLOCK, Direction.UP);

            LOGGER.info("冒烟测试：✅ 感应卡能量重定向自检通过（无异常）。能源仓 {} 单独放置时的能力：通用FE={}、MI的EU={}"
                            + "（未成形进多方块时大概率都没有；能量是否真的流入需在实机阵列里验证）",
                    energyPos, feFirst != null ? "有" : "无", eu != null ? "有" : "无");

            level.setBlock(hatchPos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(energyPos, Blocks.AIR.defaultBlockState(), 3);
        } catch (Throwable t) {
            LOGGER.error("冒烟测试：❌ 感应卡能量重定向自检失败（真机上很可能同样出错）", t);
        }
    }

    /**
     * 冒烟测试：确认我们的 {@code ConfigurableItemStackMixin} <b>不会再把别人的槽位容量抹成 64</b>。
     *
     * <p>背景（真实 bug）：这个 mixin 挂在 MI 的公共类上，会作用于<b>全游戏每一个</b>
     * {@link ConfigurableItemStack}。旧实现无条件执行 {@code adjustedCapacity = unbounded ? MAX : 64;}，
     * 而 NBT 构造函数里也注入了一次 —— 于是<b>每次读档</b>都把容量硬写回 64，把 Extended
     * Industrialization「机器配置」物品设定的大容量（{@code MachineConfigSlots} 走
     * {@code ConfigurableItemStackAccessor.setAdjustedCapacity}）静默抹掉。症状与用户的报告完全一致：
     * <b>机器做满一组（64）就再也不动</b>。
     *
     * <p>这里覆盖三条路径：① 别人的 1024 容量必须原样活过 NBT 往返；② 本 mod 的无上限槽往返后仍无上限，
     * 且存档里的 {@code adjCap} 不得是 MAX；③ 拷贝构造同样不许动别人的容量。
     */
    private static void runCapacityPreservationSelfCheck(MinecraftServer server) {
        try {
            HolderLookup.Provider registries = server.registryAccess();
            Field capacityField = ConfigurableItemStack.class.getDeclaredField("adjustedCapacity");
            capacityField.setAccessible(true);

            // ① 模拟 EI「机器配置」把容量设成 1024，再走一遍 NBT 往返
            ConfigurableItemStack configured = new ConfigurableItemStack();
            capacityField.setInt(configured, 1024);
            CompoundTag saved = configured.toNbt(registries);
            ConfigurableItemStack reloaded = new ConfigurableItemStack(saved, registries);
            int afterReload = reloaded.getAdjustedCapacity();
            int copyOfConfigured = new ConfigurableItemStack(configured).getAdjustedCapacity();

            // ② 本 mod 自己的无上限槽：往返后仍无上限，且存档里不能写 MAX
            ConfigurableItemStack unbounded = new ConfigurableItemStack();
            ((IUnboundedItemAccessor) unbounded).miae2$setUnbounded(true);
            CompoundTag unboundedSaved = unbounded.toNbt(registries);
            ConfigurableItemStack unboundedReloaded = new ConfigurableItemStack(unboundedSaved, registries);
            boolean stillUnbounded = ((IUnboundedItemAccessor) unboundedReloaded).miae2$isUnbounded();
            boolean copyUnbounded = ((IUnboundedItemAccessor) new ConfigurableItemStack(unbounded))
                    .miae2$isUnbounded();
            int savedAdjCap = unboundedSaved.getInt("adjCap");
            long unboundedCapacity = unboundedReloaded.getCapacity();

            boolean ok = afterReload == 1024
                    && copyOfConfigured == 1024
                    && stillUnbounded
                    && copyUnbounded
                    && savedAdjCap == 64
                    && unboundedCapacity == Integer.MAX_VALUE;
            if (ok) {
                LOGGER.info("冒烟测试：✅ 槽位容量保护自检通过——别人的 1024 容量活过读档（={}）、拷贝构造保留（={}）、"
                                + "本 mod 无上限槽往返后仍无上限（容量={}）、存档 adjCap 未被写成 MAX（={}）",
                        afterReload, copyOfConfigured, unboundedCapacity, savedAdjCap);
            } else {
                LOGGER.error("冒烟测试：❌ 槽位容量保护自检失败——读档后别人的容量={}（应为 1024）、拷贝后={}（应为 1024）、"
                                + "本 mod 无上限槽往返后 unbounded={}（应为 true）、拷贝后 unbounded={}（应为 true）、"
                                + "存档 adjCap={}（应为 64）、无上限槽容量={}（应为 MAX）",
                        afterReload, copyOfConfigured, stillUnbounded, copyUnbounded, savedAdjCap, unboundedCapacity);
            }
        } catch (Throwable t) {
            LOGGER.error("冒烟测试：❌ 槽位容量保护自检执行出错", t);
        }
    }
}
