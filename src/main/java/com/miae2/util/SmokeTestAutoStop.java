package com.miae2.util;

import aztech.modern_industrialization.api.energy.EnergyApi;
import aztech.modern_industrialization.machines.multiblocks.HatchBlockEntity;
import aztech.modern_industrialization.machines.multiblocks.HatchTypes;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.miae2.machines.blockentities.MePatternProviderBlockEntity;
import com.miae2.machines.init.ModHatches;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
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
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if ("modern_industrialization".equals(id.getNamespace()) && id.getPath().endsWith("_energy_input_hatch")) {
                return block;
            }
        }
        return null;
    }

    /** 专用服务端自动收尾（客户端时不注册本监听，改由客户端类收尾）。 */
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld != null && !selfCheckDone) {
            selfCheckDone = true;
            runInductionRetargetSelfCheck(server);
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
            level.setBlock(patternCheckPos, Blocks.AIR.defaultBlockState(), 3);
        } catch (Throwable t) {
            LOGGER.error("冒烟测试：❌ 样板注册自检的断言阶段失败", t);
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
}
