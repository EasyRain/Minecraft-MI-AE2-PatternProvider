package com.miae2.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 本 mod 的配置（生成 {@code config/mi_ae2_pattern_provider-common.toml}）。
 *
 * <p>目前只有一个键：量子超频模块的并行倍率。做成配置而非常量，是因为「并行多少才合适」取决于
 * 具体整合包（机器配方产物量、能源上限、玩家想不想牺牲产量换速度），服务端整合包作者可以自己调。
 *
 * <p>配置类型选 {@code COMMON} 而不是 {@code SERVER}：这个值只会被服务端的机器 tick 读到，
 * 但 {@code COMMON} 在两端都一定处于「已加载」状态，读取路径不存在任何时序风险
 * （见 {@link #quantumParallelMultiplier()} 的兜底）。
 */
public final class MiAe2Config {

    /** 并行倍率下限：低于 2× 就没有意义了（那正是高级超频模块的领域）。 */
    public static final int QUANTUM_PARALLEL_MIN = 2;

    /** 默认 4×：64 台机器 → 256 并行。 */
    public static final int QUANTUM_PARALLEL_DEFAULT = 4;

    /**
     * 上限 16×（64 台机器 → 1024 并行）。
     *
     * <p>取值依据：供应仓输出侧是 36 格 × 64 = <b>2304 件</b>瞬时空间，而一次 craft 需要
     * {@code 机器数 × 倍率 × 每批物品产物 P ≤ 2304}，即 {@code 倍率 ≤ 36 / P}：
     * <ul>
     *   <li>P = 1（MI+EI 里 52.6% 的配方）理论上限 36×；</li>
     *   <li>P ≤ 2（66.1%）18×；P ≤ 4（77.2%）9×；P ≤ 9（95.1%）4×。</li>
     * </ul>
     * 16× 取在「单产物配方理论上限 36×」的一半以下，属于偏保守的档位；P ≥ 3 的配方即使设到 16×
     * 也吃不满 —— 那不是问题，tesseract 会按真实输出空间把倍率夹回来（<b>夹低而不是丢产物</b>），
     * 所以调高这个值最坏的结果只是「没达到设定值」，不会有任何物品被销毁。
     *
     * <p>整包作者若自行扩大了输出仓容量，可以自行改大这个上限（源码里的这个常量）。
     */
    public static final int QUANTUM_PARALLEL_MAX = 16;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue QUANTUM_PARALLEL_MULTIPLIER;

    /**
     * 是否让本 mod 的仓**不参与** ExtendedAE-Plus 的智能翻倍（默认开）。
     *
     * <p>EAEP 的翻倍会把「多次合成」合并成一次材料包，材料必须一次性备齐；碰上**自回收配方**
     * （产物又是原料）或**跨机器循环**（A 的副产物喂 B、B 的产物又回 A）时，整条链会互相等待，
     * 合成任务一直挂起（**原版 AE 供应仓同样会卡**，2026-11-13 实测）。
     *
     * <p>关掉本项 = 允许 EAEP 翻倍我们的样板（可能复现上述卡死，但小批量、无循环的配方会更快）。
     */
    public static final ModConfigSpec.BooleanValue SKIP_EAEP_DOUBLING_FOR_RECURSIVE_PATTERNS;

    /**
     * 单次「合并发配」最多合并几批（1 = 关闭合并，恢复 AE 原生一批一发）。
     *
     * <p>AE 每次只推 1 批材料；开启后本仓会看网络里还能凑出几批，一次性塞给机器，
     * 缺的等下一轮。产物记账仍按 AE 那边（不会超发：上限取「AE 还在等的量」）。
     */



    public static final ModConfigSpec SPEC;

    static {
        // 分区（两级菜单）：**保持 [quantum_overclock] 这一层**，这样旧配置里的值天然继续可读
        // （2026-11-10 用户口径：宁愿配置界面按钮带 NeoForge 的「…」，也不破坏已有配置）。
        BUILDER.comment("量子超频模块：在高级超频模块的基础上，再给处理阵列提供「机器数 × 倍率」的并行批次。")
                .push("quantum_overclock");
        QUANTUM_PARALLEL_MULTIPLIER = BUILDER
                .comment("并行倍率（乘在处理阵列的机器数上；机器数上限 64）。",
                        "范围 " + QUANTUM_PARALLEL_MIN + " ~ " + QUANTUM_PARALLEL_MAX + "，默认 " + QUANTUM_PARALLEL_DEFAULT + "（64 台机器 → 256 并行）。",
                        "这只是倍率上限：实际并行还会被输入料量与输出格空间二次夹紧，",
                        "所以调高不会丢产物，只会在产物太多的配方上「吃不满」。")
                .defineInRange("parallel_multiplier", QUANTUM_PARALLEL_DEFAULT,
                        QUANTUM_PARALLEL_MIN, QUANTUM_PARALLEL_MAX);
        BUILDER.pop();
        SKIP_EAEP_DOUBLING_FOR_RECURSIVE_PATTERNS = BUILDER
                .comment("本 mod 的仓不参与 ExtendedAE-Plus 的智能翻倍（默认开）。",
                        "EAEP 的翻倍会把多次合成合并成一次材料包，遇到「产物又是原料」的自回收配方",
                        "或跨机器的循环配方时会互相等待、任务卡死（原版 AE 供应仓同样会卡）。",
                        "关掉本项则恢复由 EAEP 翻倍（可能复现卡死）。")
                .define("skip_eaep_doubling_for_recursive_patterns", true);
        SPEC = BUILDER.build();
    }

    /**
     * 读并行倍率；配置尚未加载时退回默认值。
     *
     * <p>我们的调用点是一个 mixin（{@code getMaxMultiplier}），它可能在配置加载之前就被调到
     * （例如某些整合包在注册阶段就触碰机器），因此这里必须有兜底，绝不能抛异常。
     */
    public static int quantumParallelMultiplier() {
        try {
            if (SPEC.isLoaded()) {
                return QUANTUM_PARALLEL_MULTIPLIER.get();
            }
        } catch (IllegalStateException ignored) {
            // 配置未加载 → 用默认值
        }
        return QUANTUM_PARALLEL_DEFAULT;
    }

    /**
     * 写并行倍率（游戏内配置界面 {@code MiAe2ConfigScreen} 保存时调用），并立刻写回 toml。
     *
     * <p>值先夹进 [{@link #QUANTUM_PARALLEL_MIN}, {@link #QUANTUM_PARALLEL_MAX}]：{@code ConfigValue#set}
     * 不做范围校正，越界值会在下次启动被静默改回默认，那对玩家来说就是「改了没保住」。
     *
     * <p><b>不需要重启游戏</b>：读路径 {@link #quantumParallelMultiplier()} 是在每次计算并行倍率时
     * 现读的（{@code OverclockModules.scaleMaxMultiplier} ← {@code ProcessingArrayParallelMixin}），
     * 保存后下一次配方计算就用新值。{@code SmokeTestAutoStop#assertQuantumModule} 里有一条活性断言
     * 钉住这一点（改配置 → 立刻读到新值 → 还原）。
     */
    public static void setQuantumParallelMultiplier(int value) {
        int clamped = Math.max(QUANTUM_PARALLEL_MIN, Math.min(QUANTUM_PARALLEL_MAX, value));
        QUANTUM_PARALLEL_MULTIPLIER.set(clamped);
        if (SPEC.isLoaded()) {
            SPEC.save();
        }
    }

    private MiAe2Config() {
    }

    /** 自回收样板是否跳过 EAEP 智能翻倍（配置未加载时按默认 true）。 */
    public static boolean skipEaepDoublingForRecursivePatterns() {
        try {
            return SKIP_EAEP_DOUBLING_FOR_RECURSIVE_PATTERNS.get();
        } catch (Throwable notLoaded) {
            return true;
        }
    }




}
