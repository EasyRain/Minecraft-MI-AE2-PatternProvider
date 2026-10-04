package com.miae2.util;

import aztech.modern_industrialization.MIItem;
import aztech.modern_industrialization.api.energy.EnergyApi;
import aztech.modern_industrialization.inventory.ConfigurableFluidStack;
import aztech.modern_industrialization.inventory.ConfigurableItemStack;
import aztech.modern_industrialization.inventory.MIInventory;
import aztech.modern_industrialization.inventory.SlotGroup;
import aztech.modern_industrialization.thirdparty.fabrictransfer.api.item.ItemVariant;
import aztech.modern_industrialization.machines.MachineBlockEntity;
import aztech.modern_industrialization.machines.blockentities.ElectricCraftingMachineBlockEntity;
import aztech.modern_industrialization.machines.blockentities.multiblocks.ElectricCraftingMultiblockBlockEntity;
import aztech.modern_industrialization.machines.components.OverdriveComponent;
import aztech.modern_industrialization.machines.gui.GuiComponent;
import aztech.modern_industrialization.machines.gui.MachineGuiParameters;
import aztech.modern_industrialization.machines.guicomponents.SlotPanel;
import aztech.modern_industrialization.machines.models.MachineCasings;
import aztech.modern_industrialization.machines.multiblocks.HatchBlockEntity;
import aztech.modern_industrialization.machines.multiblocks.HatchTypes;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.miae2.api.QuantumParallelHost;
import com.miae2.config.MiAe2Config;
import com.miae2.items.ModItems;
import com.miae2.items.OverclockModules;
import com.miae2.items.OverclockSlotGate;
import com.miae2.machines.blockentities.MePatternProviderBlockEntity;
import com.miae2.machines.init.ModHatches;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.swedz.extended_industrialization.machines.blockentity.multiblock.ProcessingArrayBlockEntity;
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

    /** 找「普通机器」时最多试放多少个 MI 方块实体方块（找不到就报未覆盖，不静默跳过）。 */
    private static final int MAX_PLAIN_MACHINE_TRIES = 40;

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
            assertExtremeAmountPush(provider);
            assertOutputCapacityGuard(provider);
            assertQuantumModule();
            assertQuantumParallel(level, provider);
            assertQuantumInsertRestriction(level, provider);
            assertRecipesLoaded(level.getServer());
            level.setBlock(patternCheckPos, Blocks.AIR.defaultBlockState(), 3);
        } catch (Throwable t) {
            LOGGER.error("冒烟测试：❌ 样板注册自检的断言阶段失败", t);
        }
    }

    /**
     * 冒烟测试：本 mod 发出去的所有配方文件都必须真的被加载进配方管理器。
     *
     * <p>动机：配方 JSON 解析失败时游戏只在日志里留一行 ERROR —— 游戏照跑、不崩，而那个物品就是「合不出来」，
     * 玩家视角毫无线索，开发者不翻日志也看不见。真实事故：量子模块的工作台配方一度写成 {@code "s.s"}，
     * 那个 {@code '.'} 被原版当成<b>未定义的符号</b>（空位只认空格），于是
     * {@code Parsing error loading recipe mi_ae2_pattern_provider:quantum_overclock_module_asbl:
     * Pattern references symbol '.' but it's not defined in the key}，工作台那条路静默消失。
     *
     * <p>做法：用资源管理器列出本 mod 命名空间下 {@code data/mi_ae2_pattern_provider/recipe/} 的<b>所有</b>文件，
     * 逐个到 {@code RecipeManager#byKey} 里查——文件在、配方不在，就是这个文件没解析成功。这样以后新增或改名
     * 配方都不用维护清单，谁写坏 JSON 谁就把冒烟测试弄红。
     */
    private static void assertRecipesLoaded(MinecraftServer server) {
        try {
            Map<ResourceLocation, Resource> files = server.getResourceManager().listResources(
                    "recipe", file -> file.getNamespace().equals(com.miae2.MiAe2PatternProvider.MOD_ID));
            List<String> missing = new java.util.ArrayList<>();
            for (ResourceLocation file : files.keySet()) {
                // 配方 id = 文件相对 data/<ns>/recipe/ 的完整路径（**子目录也算进 id**）：
                // 所以只去掉最外层的 "recipe/"，不能只取最后一段（crystal_assembler/xxx.json 的 id 是
                // mi_ae2_pattern_provider:crystal_assembler/xxx，写成 mi_ae2_pattern_provider:xxx 会误报）。
                String name = file.getPath();
                int slash = name.indexOf('/');
                if (slash >= 0) {
                    name = name.substring(slash + 1);
                }
                if (name.endsWith(".json")) {
                    name = name.substring(0, name.length() - ".json".length());
                }
                ResourceLocation recipeId = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), name);
                if (server.getRecipeManager().byKey(recipeId).isEmpty()) {
                    missing.add(recipeId + "（文件 " + file + " 未解析成功）");
                }
            }
            if (files.isEmpty()) {
                LOGGER.error("冒烟测试：❌ 配方加载自检失败——资源管理器里找不到 {} 的任何配方文件"
                                + "（data/{}/recipe/ 这个路径是不是变了？）",
                        com.miae2.MiAe2PatternProvider.MOD_ID, com.miae2.MiAe2PatternProvider.MOD_ID);
            } else if (missing.isEmpty()) {
                LOGGER.info("冒烟测试：✅ 配方加载自检通过——本 mod 的 {} 个配方文件全部解析成功并进入配方管理器",
                        files.size());
            } else {
                LOGGER.error("冒烟测试：❌ 配方加载自检失败——{} 个配方文件里有 {} 个没进配方管理器"
                                + "（解析失败在游戏里只留一行 ERROR 日志，物品就是合不出来）：{}",
                        files.size(), missing.size(), missing);
            }
        } catch (Throwable t) {
            LOGGER.error("冒烟测试：❌ 配方加载自检抛异常", t);
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
     * 所以按产量成比例分配是最优解。这里用「往最低天花板加水」的贪心复核：产量 2 / 4 / 8、当前输出格数
     * 应当得到近正比于产量的格数（36 格时 6 / 10 / 20，天花板 160~192），而不是「每个 1 格 + 剩下全给某一个」。
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
     * 冒烟测试：确认 GUI 高度装得下所有槽位行、不超出背景贴图、且槽位数与常量一致。
     *
     * <p>MI 把玩家背包首行放在 {@code backgroundHeight - 82}、背包标题放在 {@code backgroundHeight - 94}；
     * 我们的槽位行从 y=20 起、每行 18px。高度写小<b>不会有任何报错</b>，只会让最下面几行槽位被背包压住
     * （表现为「格子看不见」），所以这里按同一套公式复核一遍。
     *
     * <p>上限是 MI 背景贴图高度 256：{@code MachineScreen#renderBg} 用
     * {@code v = 256 - backgroundHeight + 4}，超过 256 会画到贴图外（顶部/底部错位）。
     *
     * <p>顺带把关「瞬时输出空间」：整合包的并行仓（如 bingxing 并行仓 elite 档 = 256 并行）会在配方
     * 完成的那一 tick 里额外重跑最多 255 遍配方，<b>且不检查输出空间</b>（MI 的 {@code putItemOutputs}
     * 放不下只置 {@code ok=false}，调用方丢弃返回值 ⇒ 产物静默销毁）。所以这里断言
     * {@code 输出格数 × 每格容量 ≥ 256 × 8}（elite 档 + 每批 8 件产物），保证「4 排而不是更少」这个选择
     * 是有依据的、且不会被后续改动悄悄改回去。
     */
    private static void assertGuiLayoutFitsSlots(MePatternProviderBlockEntity provider) {
        int needed = MePatternProviderBlockEntity.requiredGuiHeight(
                ModHatches.ITEM_INPUT_SLOTS, ModHatches.ITEM_OUTPUT_SLOTS,
                ModHatches.FLUID_INPUT_SLOTS, ModHatches.FLUID_OUTPUT_SLOTS);
        int actual = provider.guiParams.backgroundHeight;

        List<ConfigurableItemStack> outputs = new java.util.ArrayList<>();
        provider.appendItemOutputs(outputs);
        boolean slotsMatch = outputs.size() == ModHatches.ITEM_OUTPUT_SLOTS;

        long outputSpace = 0L;
        for (ConfigurableItemStack stack : outputs) {
            outputSpace += stack.getCapacity();
        }
        long spaceNeeded = 256L * 8L;

        boolean heightFitsTexture = actual <= 256;
        boolean spaceEnough = outputSpace >= spaceNeeded;

        if (actual >= needed && slotsMatch && heightFitsTexture && spaceEnough) {
            LOGGER.info("冒烟测试：✅ GUI 槽位布局自检通过——GUI 高 {}（需要 ≥ {}、贴图上限 256）、"
                            + "物品输出 {} 格、瞬时输出空间 {} 件（≥ 256 并行 × 8 件/批）",
                    actual, needed, outputs.size(), outputSpace);
        } else {
            LOGGER.error("冒烟测试：❌ GUI 槽位布局自检失败——GUI 高 {}（需要 ≥ {}、贴图上限 256）、"
                            + "物品输出 {} 格（应为 {}）、瞬时输出空间 {} 件（应 ≥ {}）。"
                            + "高度不够最下面几行槽位会被玩家背包压住、超过 256 会画到贴图外；"
                            + "输出空间不够时并行仓（bingxing elite 档 256 并行）会把装不下的产物静默销毁",
                    actual, needed, outputs.size(), ModHatches.ITEM_OUTPUT_SLOTS,
                    outputSpace, spaceNeeded);
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
     * 冒烟测试：<b>极端巨量</b>下的接纳边界与「全有或全无」语义。
     *
     * <p>动机：AE 生态里已经出现能处理 int/long 之上数量的拓展（例如 OmniSequence: Transfinite +
     * Applied Enhancements 的精确 BigInteger 账本，界面上会出现 10^21「Z」、10^24「Y」级数字）。
     * 但 {@code IPatternDetails}/{@code KeyCounter}/{@code MEStorage} 这条原生 ABI 始终是 {@code long}
     * ⇒ 大数订单只会被「饱和到 {@code Long.MAX_VALUE} 后按 long 分窗」或「逐份派发」，
     * <b>第三方供应器永远收不到超过 long 的数量</b>（源级证据见 PROJECT_MEMORY「极端巨量」一节）。
     * 而本仓每个输入槽的真实容量上限来自 MI 的 <b>int</b> 字段 {@code adjustedCapacity}
     * （我们把它顶到 {@link Integer.MAX_VALUE}，见 {@code ConfigurableItemStackMixin#miae2$setUnbounded}）
     * ⇒ 单次 push 每个键的上限是「输入槽数 × {@code Integer.MAX_VALUE}」。
     *
     * <p>这条自检钉住两件事，它们正是「巨量下单是否安全」的核心：
     * <ol>
     *   <li>恰好装满能被<b>完整</b>接纳 —— 全程 long，不会在途中被 int 截断成小数值；</li>
     *   <li>再多 1 个单位时<b>整体拒绝</b>且槽内容分毫不动 —— 只收一部分会让 AE 认为已发配，
     *       剩下的料既不在网络里也不在仓里（静默丢料）。</li>
     * </ol>
     * 超限时 AE2 只会每 tick 重试（{@code CraftingCpuLogic#executeCrafting} 里 push 返回 false 就
     * {@code continue}）⇒ 合成任务挂起，但绝不损坏、不复制。
     */
    private static void assertExtremeAmountPush(MePatternProviderBlockEntity provider) {
        try {
            List<ConfigurableItemStack> itemInputs = provider.miae2$itemInputs();
            List<ConfigurableFluidStack> fluidInputs = provider.miae2$fluidInputs();
            if (itemInputs.isEmpty() || fluidInputs.isEmpty()) {
                LOGGER.error("冒烟测试：❌ 极端量自检未覆盖——输入槽为空（物品 {} 个、流体 {} 个）",
                        itemInputs.size(), fluidInputs.size());
                return;
            }

            clearExtremeAmountCheckSlots(itemInputs, fluidInputs);

            long itemTotal = 0L;
            for (ConfigurableItemStack stack : itemInputs) {
                itemTotal += stack.getCapacity();
            }
            long fluidTotal = 0L;
            for (ConfigurableFluidStack stack : fluidInputs) {
                fluidTotal += stack.getCapacity();
            }

            AEItemKey itemKey = AEItemKey.of(Items.IRON_INGOT);
            boolean itemFits = provider.insertPatternInputs(
                    singleKeyCounter(itemKey, itemTotal), IActionSource.ofMachine(provider));
            long itemStored = sumItemAmounts(itemInputs);
            boolean itemOverflowRejected = !provider.insertPatternInputs(
                    singleKeyCounter(itemKey, 1L), IActionSource.ofMachine(provider));
            long itemStoredAfter = sumItemAmounts(itemInputs);

            AEFluidKey fluidKey = AEFluidKey.of(Fluids.WATER);
            boolean fluidFits = provider.insertPatternInputs(
                    singleKeyCounter(fluidKey, fluidTotal), IActionSource.ofMachine(provider));
            long fluidStored = sumFluidAmounts(fluidInputs);
            boolean fluidOverflowRejected = !provider.insertPatternInputs(
                    singleKeyCounter(fluidKey, 1L), IActionSource.ofMachine(provider));
            long fluidStoredAfter = sumFluidAmounts(fluidInputs);

            boolean ok = itemFits && itemStored == itemTotal
                    && itemOverflowRejected && itemStoredAfter == itemStored
                    && fluidFits && fluidStored == fluidTotal
                    && fluidOverflowRejected && fluidStoredAfter == fluidStored;
            if (ok) {
                LOGGER.info("冒烟测试：✅ 极端量自检通过——单次 push 物品 {} 个（{} 槽 × {}）与流体 {} mB"
                                + "（{} 槽 × {}）都被完整接纳；再多 1 个单位被整体拒绝且槽内容分毫不动"
                                + "（不部分接收 = 不静默丢料）",
                        itemTotal, itemInputs.size(), Integer.MAX_VALUE,
                        fluidTotal, fluidInputs.size(), Integer.MAX_VALUE);
            } else {
                LOGGER.error("冒烟测试：❌ 极端量自检失败——物品[恰好装满={}，存入 {}/{}；多 1 个被拒={}，存入 {}]、"
                                + "流体[恰好装满={}，存入 {}/{}；多 1 个被拒={}，存入 {}]。"
                                + "「多 1 个被拒=false」或「两次存入数不同」都说明发生了部分接收 = 静默丢料",
                        itemFits, itemStored, itemTotal, itemOverflowRejected, itemStoredAfter,
                        fluidFits, fluidStored, fluidTotal, fluidOverflowRejected, fluidStoredAfter);
            }

            clearExtremeAmountCheckSlots(itemInputs, fluidInputs);
        } catch (Throwable t) {
            LOGGER.error("冒烟测试：❌ 极端量自检抛异常", t);
        }
    }

    /**
     * 冒烟测试：<b>输出格容量守卫</b>（{@code MePatternProviderBlockEntity#miae2$outputCapacityIsTruthful}）。
     *
     * <p>要钉住的是「下单时先核对容量口径，不一致就拒收发配材料」。它对应的真实 bug：输出槽被标成"无上限"
     * （{@code adjustedCapacity = MAX} 叠加重定向容量查询路径）时，阵列的<b>模拟</b>说"装得下"、
     * <b>真插入</b>却只装 64 个 ⇒ 并行倍率被抬到机器数、超出的产物被 {@code putItemOutputs} 静默销毁、
     * AE 的 CPU 永远等不到足量产。三步：
     * <ol>
     *   <li>正常状态：谓词为真、{@code pushPattern} 正常收料（守卫不能误拒）；</li>
     *   <li>把第 1 个输出格标成无上限（= 人为复现那个口径分歧）：谓词必须为假、
     *       {@code pushPattern} 必须<b>拒收</b>且输入格分毫不动（不发配材料 &gt; 发了被吞）；</li>
     *   <li>同一次 {@code pushPattern} 还会顺手把那个标志夹回普通容量 ⇒ 紧接着再推一次必须成功
     *       （守卫只让 AE 多等一 tick，不会把合成永久卡死）。</li>
     * </ol>
     * 注意这里要先把供应仓 {@code link} 上一个外壳：{@code pushPattern} 的第一道检查是
     * {@code isMatched()}（独立放置的仓永远为 false），链接只是喂给那一道检查，测完立刻 {@code unlink}。
     */
    private static void assertOutputCapacityGuard(MePatternProviderBlockEntity provider) {
        try {
            List<ConfigurableItemStack> itemInputs = provider.miae2$itemInputs();
            List<ConfigurableItemStack> itemOutputs = new java.util.ArrayList<>();
            provider.appendItemOutputs(itemOutputs);
            List<IPatternDetails> patterns = provider.getLogic().getAvailablePatterns();
            IPatternDetails pattern = null;
            for (IPatternDetails candidate : patterns) {
                if (candidate.getOutputs().stream().anyMatch(output -> output.what() instanceof AEItemKey)) {
                    pattern = candidate;
                    break;
                }
            }
            if (itemInputs.isEmpty() || itemOutputs.isEmpty() || pattern == null) {
                LOGGER.error("冒烟测试：❌ 输出格守卫自检未覆盖——输入格 {} 个、输出格 {} 个、含物品产物的样板 {} 个",
                        itemInputs.size(), itemOutputs.size(), patterns.size());
                return;
            }

            clearExtremeAmountCheckSlots(itemInputs, provider.miae2$fluidInputs());
            provider.link(MachineCasings.STEEL);
            try {
                boolean truthfulBefore = provider.miae2$outputCapacityIsTruthful(pattern);
                boolean pushedBefore = provider.pushPattern(
                        pattern, singleKeyCounter(AEItemKey.of(Items.IRON_INGOT), 1L));
                long storedAfterPush = sumItemAmounts(itemInputs);

                ConfigurableItemStack firstOutput = itemOutputs.get(0);
                IUnboundedItemAccessor outputAccessor = (IUnboundedItemAccessor) firstOutput;
                outputAccessor.miae2$setUnbounded(true);
                long inflatedCapacity = firstOutput.getCapacity();

                boolean truthfulAfterTamper = provider.miae2$outputCapacityIsTruthful(pattern);
                boolean pushedAfterTamper = provider.pushPattern(
                        pattern, singleKeyCounter(AEItemKey.of(Items.IRON_INGOT), 1L));
                long storedAfterTamper = sumItemAmounts(itemInputs);

                boolean revertedByGuard = !outputAccessor.miae2$isUnbounded() && firstOutput.getCapacity() <= 64L;
                boolean pushedAfterRevert = provider.pushPattern(
                        pattern, singleKeyCounter(AEItemKey.of(Items.IRON_INGOT), 1L));

                boolean ok = truthfulBefore && pushedBefore && storedAfterPush == 1L
                        && inflatedCapacity > 64L
                        && !truthfulAfterTamper && !pushedAfterTamper && storedAfterTamper == storedAfterPush
                        && revertedByGuard && pushedAfterRevert;
                if (ok) {
                    LOGGER.info("冒烟测试：✅ 输出格守卫自检通过——正常时口径一致、正常收料；把输出格标成无上限"
                                    + "（容量 {}）后谓词为假、pushPattern 拒收且输入格分毫不动（仍 {} 个）；"
                                    + "同一次调用把标志夹回普通容量（现在 {}），下一次 pushPattern 立刻成功",
                            inflatedCapacity, storedAfterTamper, firstOutput.getCapacity());
                } else {
                    LOGGER.error("冒烟测试：❌ 输出格守卫自检失败——正常[谓词={}、收料={}、存入 {}]、"
                                    + "人为膨胀[容量 {}、谓词={}、拒收={}、存入 {}]、夹回[生效={}、容量 {}、再推成功={}]。"
                                    + "「谓词=false 却仍然收料」说明守卫没接进 pushPattern；"
                                    + "「拒收=false」说明产物会被静默销毁的路径没有被拦住",
                            truthfulBefore, pushedBefore, storedAfterPush,
                            inflatedCapacity, truthfulAfterTamper, pushedAfterTamper, storedAfterTamper,
                            revertedByGuard, firstOutput.getCapacity(), pushedAfterRevert);
                }
            } finally {
                clearExtremeAmountCheckSlots(itemInputs, provider.miae2$fluidInputs());
                provider.unlink();
            }
        } catch (Throwable t) {
            LOGGER.error("冒烟测试：❌ 输出格守卫自检抛异常", t);
        }
    }

    /**
     * 冒烟测试：「量子超频模块」的两组不变量。
     *
     * <ol>
     *   <li><b>家族判定一致</b>：高级模块与量子模块都被 {@code OverclockModules.isOverclockModule}
     *       认可（两者共用超频槽、共用效率 hook），MI 原版超频模块与空物品不被认可；
     *       只有量子模块 {@code grantsParallel}。这条能抓住「新物品只改了一处判定」的漏改
     *       （历史上「能右键装、GUI 拖不进去」就是这么来的）；</li>
     *   <li><b>并行上限的算术与「偏保守」的承诺</b>：{@code scaleMaxMultiplier(机器数)} = 机器数 × 配置倍率，
     *       配置必须落在 [{@code QUANTUM_PARALLEL_MIN}, {@code QUANTUM_PARALLEL_MAX}]；
     *       并断言这个上限确实装得进输出格空间（36 格 × 64 = 2304 件）：
     *       配置上限 16× 时按「每批 1 件物品产物」算 16×64×1 ≤ 2304，
     *       默认 4× 时按「每批 9 件」（覆盖 95% 的 MI/EI 配方）算 4×64×9 = 2304。
     *       这条把「为什么上限是 16、默认 4」这个结论钉在了代码里：谁改大常量都会立刻红。</li>
     * </ol>
     */
    private static void assertQuantumModule() {
        try {
            ItemStack advanced = new ItemStack(ModItems.ADVANCED_OVERCLOCK_MODULE.get());
            ItemStack quantum = new ItemStack(ModItems.QUANTUM_OVERCLOCK_MODULE.get());
            ItemStack vanilla = MIItem.OVERDRIVE_MODULE.stack();

            boolean familyOk = OverclockModules.isOverclockModule(advanced)
                    && OverclockModules.isOverclockModule(quantum)
                    && !OverclockModules.isOverclockModule(vanilla)
                    && !OverclockModules.isOverclockModule(ItemStack.EMPTY);
            boolean parallelOk = OverclockModules.grantsParallel(quantum)
                    && !OverclockModules.grantsParallel(advanced)
                    && !OverclockModules.grantsParallel(vanilla);

            int configured = MiAe2Config.quantumParallelMultiplier();
            int machineCount = 64; // EI ProcessingArrayBlockEntity 的形状上限（8/16/32/64）
            int scaled = OverclockModules.scaleMaxMultiplier(machineCount);
            boolean configInRange = configured >= MiAe2Config.QUANTUM_PARALLEL_MIN
                    && configured <= MiAe2Config.QUANTUM_PARALLEL_MAX;
            boolean arithmeticOk = scaled == machineCount * configured
                    && OverclockModules.scaleMaxMultiplier(0) == 0
                    && OverclockModules.scaleMaxMultiplier(1) == configured;

            long outputSpace = 36L * 64L; // 供应仓输出格：9 列 × 4 排，每格 64 件
            boolean conservativeOk =
                    (long) MiAe2Config.QUANTUM_PARALLEL_MAX * machineCount <= outputSpace
                            && (long) MiAe2Config.QUANTUM_PARALLEL_DEFAULT * machineCount * 9L <= outputSpace;
            // 配置系统必须真的加载过：否则 quantumParallelMultiplier() 静默回落默认值，
            // 玩家改了 config 却毫无效果，而且不会有任何错误日志——只能靠断言钉住。
            boolean configLoaded = MiAe2Config.SPEC.isLoaded();
            // 「保存后立即生效、不用重启游戏」这句承诺必须可验证：游戏内配置界面
            // （MiAe2ConfigScreen 的 setSaveConsumer）保存时调的就是 setQuantumParallelMultiplier，
            // 而读路径是每次算倍率现读 —— 这里改一下再读回来，谁把读路径改成启动时缓存都会立刻红。
            // 写完立刻还原（连 toml 一起写回原值，避免测试改变 dev 环境状态）。
            int probe = configured == MiAe2Config.QUANTUM_PARALLEL_MIN
                    ? MiAe2Config.QUANTUM_PARALLEL_MAX : configured - 1;
            MiAe2Config.setQuantumParallelMultiplier(probe);
            boolean liveConfigured = MiAe2Config.quantumParallelMultiplier() == probe;
            MiAe2Config.setQuantumParallelMultiplier(configured);
            boolean configLive = liveConfigured && MiAe2Config.quantumParallelMultiplier() == configured;

            if (familyOk && parallelOk && configInRange && arithmeticOk && conservativeOk && configLoaded
                    && configLive) {
                LOGGER.info("冒烟测试：✅ 量子超频模块自检通过——家族判定[高级={}、量子={}、原版={}、空={}]、"
                                + "并行倍率 = 机器数 {} × 配置 {} = {}（配置范围 {}-{}，config 已加载={}，"
                                + "改成 {} 后立刻读到新值={} 且已还原）；"
                                + "上限 {}× 与默认 {}× 都装得进输出格空间（{} 件）",
                        OverclockModules.isOverclockModule(advanced), OverclockModules.isOverclockModule(quantum),
                        OverclockModules.isOverclockModule(vanilla), OverclockModules.isOverclockModule(ItemStack.EMPTY),
                        machineCount, configured, scaled,
                        MiAe2Config.QUANTUM_PARALLEL_MIN, MiAe2Config.QUANTUM_PARALLEL_MAX, configLoaded,
                        probe, configLive,
                        MiAe2Config.QUANTUM_PARALLEL_MAX, MiAe2Config.QUANTUM_PARALLEL_DEFAULT, outputSpace);
            } else {
                LOGGER.error("冒烟测试：❌ 量子超频模块自检失败——家族判定[高级={}、量子={}、原版={}、空={}] 期望[true,true,false,false]、"
                                + "grantsParallel[量子={}、高级={}、原版={}] 期望[true,false,false]、"
                                + "配置 {} 在 [{}, {}] 内={}、算术 {}×{}={}（期望 {}）且 scale(0)=0、scale(1)={}、"
                                + "保守性[max×机器数={} ≤ {} 且 默认×机器数×9={} ≤ {}]={}、config 已加载={}、"
                                + "改成 {} 后立刻读到新值且已还原={}",
                        OverclockModules.isOverclockModule(advanced), OverclockModules.isOverclockModule(quantum),
                        OverclockModules.isOverclockModule(vanilla), OverclockModules.isOverclockModule(ItemStack.EMPTY),
                        OverclockModules.grantsParallel(quantum), OverclockModules.grantsParallel(advanced),
                        OverclockModules.grantsParallel(vanilla),
                        configured, MiAe2Config.QUANTUM_PARALLEL_MIN, MiAe2Config.QUANTUM_PARALLEL_MAX, configInRange,
                        machineCount, configured, scaled, machineCount * configured, OverclockModules.scaleMaxMultiplier(1),
                        (long) MiAe2Config.QUANTUM_PARALLEL_MAX * machineCount, outputSpace,
                        (long) MiAe2Config.QUANTUM_PARALLEL_DEFAULT * machineCount * 9L, outputSpace, conservativeOk,
                        configLoaded, probe, configLive);
            }
        } catch (Throwable t) {
            LOGGER.error("冒烟测试：❌ 量子超频模块自检抛异常", t);
        }
    }

    /**
     * 冒烟测试：<b>真实 EI 处理阵列</b>上的并行倍率端到端验证。
     *
     * <p>动机：{@code assertQuantumModule()} 只验证了纯函数的算术与家族判定，它<b>证明不了</b>
     * {@code ProcessingArrayParallelMixin} 真的贴到了 {@code ProcessingArrayBlockEntity#getMaxMultiplier()}
     * 上 —— mixin 目标改名、被别的 mod 抢先、注入点被 inline 掉，都会让功能静默失效而算术自检照旧全绿。
     * 这里放一个<b>真实</b>阵列控制器方块（不需要成型多方块：{@code getMaxMultiplier()} 只读
     * {@code ProcessingArrayMachineComponent#getMachineCount()}，见
     * {@code ei-src\...\blockentity\multiblock\ProcessingArrayBlockEntity.java:100-102}），
     * 往它的超频槽依次放 空 / MI 原版超频模块 / 本 mod 高级超频模块 / 本 mod 量子超频模块，
     * 每一步都读同一个 {@code getMaxMultiplier()}：只有量子模块那一次必须变成「机器数 × 配置倍率」。
     *
     * <p>钉住的语义（用户 m03941 定稿的口径）：
     * <ol>
     *   <li>量子模块是<b>高级超频模块的升级版</b>：装了它就同时拥有高级超频的全部效果（由
     *       {@code AdvancedOverclockHook} 的家族判定覆盖），本自检不重复验证；</li>
     *   <li>它是唯一会抬并行天花板的模块 —— 原版与高级模块都必须<b>保持原倍率</b>
     *       （高级模块只归零效率 tesseract hook 的消耗，不加并行）；</li>
     *   <li>倍率来自 config（默认 4×、范围 2–16×），不是硬编码；</li>
     *   <li>抬的是「阵列机器数 × 倍率」这个<b>天花板</b>，而不是算完的结果 —— 真实并行倍率仍由 tesseract 的
     *       {@code MultipliedCrafterComponent#calculateMultiplier} 按输入料量与输出格空间反推
     *       （装不下时自动降倍率，绝不吞产物）。</li>
     * </ol>
     */
    private static void assertQuantumParallel(ServerLevel level, MePatternProviderBlockEntity provider) {
        try {
            Block arrayBlock = findBlock("extended_industrialization", "processing_array");
            Item machineItem = findProcessingArrayMachineItem();
            if (arrayBlock == null || machineItem == null) {
                LOGGER.error("冒烟测试：❌ 量子并行（真实阵列）自检未覆盖——{}",
                        arrayBlock == null ? "找不到 EI 处理阵列方块" : "找不到可用的工作方块物品");
                return;
            }
            BlockPos arrayPos = provider.getBlockPos().offset(5, 0, 0);
            level.setBlock(arrayPos, arrayBlock.defaultBlockState(), 3);
            try {
                if (!(level.getBlockEntity(arrayPos) instanceof ProcessingArrayBlockEntity array)) {
                    LOGGER.error("冒烟测试：❌ 量子并行（真实阵列）自检未覆盖——处理阵列方块的方块实体不是 "
                            + "ProcessingArrayBlockEntity（{}）", level.getBlockEntity(arrayPos));
                    return;
                }
                ProcessingArrayMachineComponent machines =
                        array.components.getNullable(ProcessingArrayMachineComponent.class);
                ComponentStackHolder overdriveSlot = null;
                for (ComponentStackHolder holder : array.components.getAll(ComponentStackHolder.class)) {
                    if (holder instanceof OverdriveComponent) {
                        overdriveSlot = holder;
                        break;
                    }
                }
                if (machines == null || overdriveSlot == null) {
                    LOGGER.error("冒烟测试：❌ 量子并行（真实阵列）自检未覆盖——阵列上{}",
                            machines == null ? "没有 EI 的 ProcessingArrayMachineComponent" : "没有超频槽（OverdriveComponent）");
                    return;
                }
                machines.setStack(new ItemStack(machineItem));
                int configured = MiAe2Config.quantumParallelMultiplier();

                overdriveSlot.setStack(ItemStack.EMPTY);
                int base = array.getMaxMultiplier();
                overdriveSlot.setStack(MIItem.OVERDRIVE_MODULE.stack());
                int withVanilla = array.getMaxMultiplier();
                overdriveSlot.setStack(new ItemStack(ModItems.ADVANCED_OVERCLOCK_MODULE.get()));
                int withAdvanced = array.getMaxMultiplier();
                overdriveSlot.setStack(new ItemStack(ModItems.QUANTUM_OVERCLOCK_MODULE.get()));
                int withQuantum = array.getMaxMultiplier();
                overdriveSlot.setStack(ItemStack.EMPTY);

                String detail = String.format("机器数 %d：空=%d、原版超频=%d、高级超频=%d、量子超频=%d（期望 %d，配置 %d×）",
                        base, base, withVanilla, withAdvanced, withQuantum, base * configured, configured);
                if (base > 0 && withVanilla == base && withAdvanced == base && withQuantum == base * configured) {
                    LOGGER.info("冒烟测试：✅ 量子并行（真实阵列）自检通过——{}", detail);
                } else {
                    LOGGER.error("冒烟测试：❌ 量子并行（真实阵列）自检失败——{}；"
                                    + "量子模块没抬起天花板=并行 mixin 没贴上（或注入点失效），"
                                    + "非量子模块也抬了=家族判定漏了",
                            detail);
                }
            } finally {
                level.setBlock(arrayPos, Blocks.AIR.defaultBlockState(), 3);
            }
        } catch (Throwable t) {
            LOGGER.error("冒烟测试：❌ 量子并行（真实阵列）自检抛异常", t);
        }
    }

    /**
     * 冒烟测试：量子超频模块「只许装进处理阵列控制器」的准入限制（用户 m04850 要求）。
     *
     * <p>三层断言，缺一层都会留下一个「看着生效、其实漏了」的口子：
     * <ol>
     *   <li><b>判定表</b>：量子模块在非宿主机器上是白装（拒收）、在宿主上放行；原版与高级模块到处都放行；
     *       {@code ItemStack.EMPTY} 到处都放行（空手不能触发拒收）；</li>
     *   <li><b>标记接口的覆盖面</b>：EI 处理阵列<b>是</b>宿主，MI 单方块与 MI 多方块<b>不是</b>
     *       （装了 IO 的话它的多处理阵列也必须是）—— 这条钉住「可插 = 并行真的会生效」这个等价关系；</li>
     *   <li><b>真实槽位</b>：拿真实机器的真实 {@code SlotPanel} 真建一次菜单，读超频槽的 {@code mayPlace} ——
     *       这是唯一能证明 {@code OverdriveSlotGateMixin} 的 {@code @Redirect} 真贴上了的断言
     *       （它失效时前两层纯函数断言照旧全绿）。</li>
     * </ol>
     *
     * <p>已知取舍（写进 PROJECT_MEMORY）：拦在<b>服务端</b>的槽位谓词上，所以物品不会离开玩家光标（不丢件）；
     * 客户端预览仍会短暂把模块画进槽里，服务端回滚后重同步。
     */
    private static void assertQuantumInsertRestriction(ServerLevel level, MePatternProviderBlockEntity provider) {
        BlockPos hostPos = provider.getBlockPos().offset(6, 0, 0);
        BlockPos plainPos = provider.getBlockPos().offset(7, 0, 0);
        try {
            ItemStack quantum = new ItemStack(ModItems.QUANTUM_OVERCLOCK_MODULE.get());
            ItemStack advanced = new ItemStack(ModItems.ADVANCED_OVERCLOCK_MODULE.get());
            ItemStack vanilla = MIItem.OVERDRIVE_MODULE.stack();

            boolean blocksQuantum = OverclockModules.blockedIn(false, quantum);
            boolean allowsQuantum = !OverclockModules.blockedIn(true, quantum);
            boolean othersPass = !OverclockModules.blockedIn(false, advanced)
                    && !OverclockModules.blockedIn(true, advanced)
                    && !OverclockModules.blockedIn(false, vanilla)
                    && !OverclockModules.blockedIn(true, vanilla)
                    && !OverclockModules.blockedIn(false, ItemStack.EMPTY)
                    && !OverclockModules.blockedIn(true, ItemStack.EMPTY);
            boolean tableOk = blocksQuantum && allowsQuantum && othersPass;

            boolean eiIsHost = QuantumParallelHost.class.isAssignableFrom(ProcessingArrayBlockEntity.class);
            boolean singleIsNotHost = !QuantumParallelHost.class.isAssignableFrom(ElectricCraftingMachineBlockEntity.class);
            boolean multiIsNotHost = !QuantumParallelHost.class.isAssignableFrom(ElectricCraftingMultiblockBlockEntity.class);
            boolean ioIsHost = !ModList.get().isLoaded("industrialization_overdrive")
                    || isParallelHostClass("dev.wp.industrialization_overdrive.machines.blockentities.multiblock."
                            + "MultiProcessingArrayBlockEntity");
            boolean hostsOk = eiIsHost && singleIsNotHost && multiIsNotHost && ioIsHost;

            Boolean hostVerdict = null;
            Block arrayBlock = findBlock("extended_industrialization", "processing_array");
            if (arrayBlock != null) {
                level.setBlock(hostPos, arrayBlock.defaultBlockState(), 3);
                hostVerdict = overdriveSlotVerdict(level.getBlockEntity(hostPos), quantum, advanced, vanilla);
                level.setBlock(hostPos, Blocks.AIR.defaultBlockState(), 3);
            }
            BlockEntity plainMachine = placePlainMachine(level, plainPos);
            String plainName = plainMachine == null
                    ? "（没扫到可用的普通机器方块）"
                    : BuiltInRegistries.BLOCK.getKey(plainMachine.getBlockState().getBlock()).toString();
            Boolean plainVerdict = overdriveSlotVerdict(plainMachine, quantum, advanced, vanilla);

            boolean slotsOk = Boolean.TRUE.equals(hostVerdict) && Boolean.FALSE.equals(plainVerdict);
            if (tableOk && hostsOk && slotsOk) {
                LOGGER.info("冒烟测试：✅ 量子插槽限制自检通过——判定表[非宿主拒收量子={}、宿主放行量子={}、"
                                + "原版/高级到处放行={}]、宿主标记[EI 处理阵列={}、MI 单方块={}、MI 多方块={}、IO 多处理阵列={}]；"
                                + "真实槽位[EI 处理阵列收量子={}、{} 收量子={}（非宿主应为 false）]",
                        blocksQuantum, allowsQuantum, othersPass, eiIsHost, !singleIsNotHost, !multiIsNotHost, ioIsHost,
                        hostVerdict, plainName, plainVerdict);
            } else {
                LOGGER.error("冒烟测试：❌ 量子插槽限制自检失败——判定表={}（非宿主拒收量子={}、宿主放行量子={}、"
                                + "原版/高级到处放行={}）、宿主标记={}（EI 处理阵列={}、MI 单方块={}、MI 多方块={}、IO={}）、"
                                + "真实槽位={}（EI 处理阵列收量子={}、{} 收量子={}；null=该槽连原版/高级模块都不收，说明没找到超频槽）；"
                                + "宿主标记不对=并行 mixin 的 implements 漏了；真实槽位不对=OverdriveSlotGateMixin 的 "
                                + "@Redirect 没贴上（或 MenuFacade 换了实现）",
                        tableOk, blocksQuantum, allowsQuantum, othersPass, hostsOk, eiIsHost, singleIsNotHost,
                        multiIsNotHost, ioIsHost, slotsOk, hostVerdict, plainName, plainVerdict);
            }
        } catch (Throwable t) {
            LOGGER.error("冒烟测试：❌ 量子插槽限制自检抛异常", t);
        } finally {
            level.setBlock(hostPos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(plainPos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    /** 某个类名（可能缺席，比如可选的 IO）有没有被打上 {@link QuantumParallelHost} 标记接口。 */
    private static boolean isParallelHostClass(String className) {
        try {
            return QuantumParallelHost.class.isAssignableFrom(Class.forName(className));
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /**
     * 读一次真实超频槽的口径：{@code true} = 宿主口径（收量子），{@code false} = 非宿主口径（拒收量子），
     * {@code null} = 这条槽不符合两者中任何一个（连原版/高级模块都不收，或压根没找到槽）。
     */
    private static Boolean overdriveSlotVerdict(
            BlockEntity be, ItemStack quantum, ItemStack advanced, ItemStack vanilla) {
        Slot slot = firstOverdriveSlot(be);
        if (slot == null || !slot.mayPlace(vanilla) || !slot.mayPlace(advanced)) {
            return null;
        }
        return slot.mayPlace(quantum);
    }

    /**
     * 拿机器真实的 {@code SlotPanel} 真建一次菜单，取回它的超频槽 —— 走的正是
     * {@code OverdriveSlotGateMixin} 注入的那条 {@code SlotPanel#setupMenu} 路径，
     * 所以它拿到的槽和游戏里玩家拖物品时判定的槽是同一个包装。
     */
    private static Slot firstOverdriveSlot(BlockEntity be) {
        if (!(be instanceof MachineBlockEntity machine)) {
            return null;
        }
        SlotPanel panel = machine.guiComponents.getNullable(SlotPanel.class);
        if (panel == null) {
            return null;
        }
        List<Slot> captured = new ArrayList<>();
        panel.setupMenu(new GuiComponent.MenuFacade() {
            @Override
            public void addSlotToMenu(Slot slot, SlotGroup group) {
                if (OverclockSlotGate.isOverdriveSlot(group)) {
                    captured.add(slot);
                }
            }

            @Override
            public MachineGuiParameters getGuiParams() {
                return machine.guiParams;
            }

            @Override
            public MIInventory getMachineInventory() {
                return null;
            }
        });
        return captured.isEmpty() ? null : captured.get(0);
    }

    /**
     * 在世界里放一个「有超频槽、但不是并行宿主」的 MI 机器（用于验证 GUI 路径的拒收）。
     *
     * <p>不写死机器 id（MI 的机器方块没有常量，见 {@code MIBlock}）：扫注册表，拿 MI 命名空间下
     * 「是方块实体方块」的方块挨个试放，要求它给出的方块实体是带 {@code OverdriveComponent} 的
     * {@code MachineBlockEntity}、且不是 {@link QuantumParallelHost}；不合适就立刻清掉换下一个。
     * 一个都找不到时返回 {@code null}（调用方据此报「未覆盖」，不静默通过）。
     */
    private static BlockEntity placePlainMachine(ServerLevel level, BlockPos pos) {
        int tried = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (!"modern_industrialization".equals(id.getNamespace()) || !(block instanceof EntityBlock)) {
                continue;
            }
            if (++tried > MAX_PLAIN_MACHINE_TRIES) {
                break;
            }
            level.setBlock(pos, block.defaultBlockState(), 3);
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MachineBlockEntity machine
                    && !(machine instanceof QuantumParallelHost)
                    && machine.components.getNullable(OverdriveComponent.class) != null) {
                return be;
            }
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        return null;
    }

    /** 造一个只含单个键的 {@link KeyCounter} 数组 —— AE2 交给样板供应器的输入就是这个形状。 */
    private static KeyCounter[] singleKeyCounter(AEKey key, long amount) {
        KeyCounter counter = new KeyCounter();
        counter.add(key, amount);
        return new KeyCounter[]{counter};
    }

    private static long sumItemAmounts(List<ConfigurableItemStack> stacks) {
        long total = 0L;
        for (ConfigurableItemStack stack : stacks) {
            total += stack.getAmount();
        }
        return total;
    }

    private static long sumFluidAmounts(List<ConfigurableFluidStack> stacks) {
        long total = 0L;
        for (ConfigurableFluidStack stack : stacks) {
            total += stack.getAmount();
        }
        return total;
    }

    private static void clearExtremeAmountCheckSlots(
            List<ConfigurableItemStack> itemInputs, List<ConfigurableFluidStack> fluidInputs) {
        for (ConfigurableItemStack stack : itemInputs) {
            stack.setAmount(0L);
        }
        for (ConfigurableFluidStack stack : fluidInputs) {
            stack.setAmount(0L);
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
     * 且存档里的 {@code adjCap} 不得是 MAX；③ 拷贝构造同样不许动别人的容量。另外②那个槽里会塞进
     * {@link Integer.MAX_VALUE} 级的巨量，确认「AE 一次性推进来的大批料」也能原样过档（量走 long）。
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
            // ③ 无上限槽里塞进「远超一格 64」的巨量（Integer.MAX_VALUE ≈ 21 亿），确认这个量也能
            //    原样过档、读档后容量仍是无上限 —— AE 拓展能把整批料推进来，量必须以 long 存住。
            unbounded.setKey(ItemVariant.of(Items.IRON_INGOT));
            unbounded.setAmount(Integer.MAX_VALUE);
            CompoundTag unboundedSaved = unbounded.toNbt(registries);
            ConfigurableItemStack unboundedReloaded = new ConfigurableItemStack(unboundedSaved, registries);
            boolean stillUnbounded = ((IUnboundedItemAccessor) unboundedReloaded).miae2$isUnbounded();
            boolean copyUnbounded = ((IUnboundedItemAccessor) new ConfigurableItemStack(unbounded))
                    .miae2$isUnbounded();
            int savedAdjCap = unboundedSaved.getInt("adjCap");
            long unboundedCapacity = unboundedReloaded.getCapacity();
            long unboundedAmount = unboundedReloaded.getAmount();

            boolean ok = afterReload == 1024
                    && copyOfConfigured == 1024
                    && stillUnbounded
                    && copyUnbounded
                    && savedAdjCap == 64
                    && unboundedCapacity == Integer.MAX_VALUE
                    && unboundedAmount == Integer.MAX_VALUE;
            if (ok) {
                LOGGER.info("冒烟测试：✅ 槽位容量保护自检通过——别人的 1024 容量活过读档（={}）、拷贝构造保留（={}）、"
                                + "本 mod 无上限槽往返后仍无上限（容量={}、巨量内容={} 个也原样活过存档）、"
                                + "存档 adjCap 未被写成 MAX（={}）",
                        afterReload, copyOfConfigured, unboundedCapacity, unboundedAmount, savedAdjCap);
            } else {
                LOGGER.error("冒烟测试：❌ 槽位容量保护自检失败——读档后别人的容量={}（应为 1024）、拷贝后={}（应为 1024）、"
                                + "本 mod 无上限槽往返后 unbounded={}（应为 true）、拷贝后 unbounded={}（应为 true）、"
                                + "存档 adjCap={}（应为 64）、无上限槽容量={}（应为 MAX）、巨量内容={}（应为 MAX）",
                        afterReload, copyOfConfigured, stillUnbounded, copyUnbounded, savedAdjCap,
                        unboundedCapacity, unboundedAmount);
            }
        } catch (Throwable t) {
            LOGGER.error("冒烟测试：❌ 槽位容量保护自检执行出错", t);
        }
    }
}
