package com.miae2.machines.init;

import aztech.modern_industrialization.MI;
import aztech.modern_industrialization.machines.MachineBlockEntity;
import aztech.modern_industrialization.machines.gui.MachineGuiParameters;
import aztech.modern_industrialization.machines.init.MachineDefinition;
import aztech.modern_industrialization.machines.init.MachineRegistrationHelper;
import aztech.modern_industrialization.machines.models.MachineCasings;
import aztech.modern_industrialization.machines.multiblocks.HatchType;
import aztech.modern_industrialization.machines.multiblocks.HatchTypes;
import com.miae2.MiAe2PatternProvider;
import com.miae2.machines.blockentities.MePatternProviderBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

/**
 * 注册 ME 样板供应仓（普通 9 样板）与 ME 扩展样板供应仓（36 样板）的自定义 HatchType 与方块。
 * 方块经 MI 的 MachineRegistrationHelper 注册（因此落在 modern_industrialization 命名空间下）。
 * 扩展版仅在安装了 ExtendedAE 时注册。
 */
public final class ModHatches {

    public static final HatchType ME_PATTERN_PROVIDER = HatchTypes.register(
            ResourceLocation.fromNamespaceAndPath(MiAe2PatternProvider.MOD_ID, "me_pattern_provider"),
            MI.id("me_pattern_provider_hatch")
    );

    public static final HatchType ME_EXTENDED_PATTERN_PROVIDER = HatchTypes.register(
            ResourceLocation.fromNamespaceAndPath(MiAe2PatternProvider.MOD_ID, "me_extended_pattern_provider"),
            MI.id("me_extended_pattern_provider_hatch")
    );

    public static final int ITEM_INPUT_SLOTS = 9;
    /**
     * 物品输出槽：<b>4 排 = 36 格</b>（2026-10-04 从 1 排 → 3 排 → 4 排）。
     *
     * <p>为什么不能只有 1 排：EI 的处理阵列按「输出格一共还装得下多少」反推并行倍率，而它<b>真</b>插入时
     * 一格最多 {@code ItemVariant.getMaxStackSize()} = 64（见 {@code MePatternProviderBlockEntity#lockOutputs}）。
     * 1 排（9 格）时一个产物最多锁 1~9 格 ⇒ 倍率上限 {@code 64 * 格数 / 单次产量}；4 排后上限变成
     * {@code 64 * 36 / 单次产量}，同时给产物留出 36×64 = 2304 件的缓冲。
     *
     * <p>为什么是 4 排而不是更多：MI 的机器 GUI 背景贴图只有 256 高（
     * {@code MachineScreen#renderBg} 用 {@code v = 256 - backgroundHeight + 4}），而槽位区高度 =
     * {@code 20 + 18 * 行数 + 100}。4 排物品输出 ⇒ 共 7 行 = 246 ≤ 256 已接近上限，再加一排（8 行 = 264）
     * 就会超出贴图（症状是顶部/底部错位），要更多只能换更大的贴图或分页。
     *
     * <p>为什么 4 排值得：整合包的并行仓（如 bingxing「并行仓」）会在配方完成的那一 tick 里额外重跑
     * 最多 2047 遍配方，且<b>不检查输出空间</b>（MI 的 {@code putItemOutputs} 放不下只置 {@code ok=false}，
     * 调用方丢弃返回值 ⇒ 产物静默销毁）。输出格越多，这种「一批合成瞬时产出」越装得下。
     */
    public static final int ITEM_OUTPUT_SLOTS = 9 * 4;
    public static final int FLUID_INPUT_SLOTS = 9;
    public static final int FLUID_OUTPUT_SLOTS = 9;
    // 超堆叠直接到上限（单等级、无升级）
    public static final long FLUID_CAPACITY = Integer.MAX_VALUE;
    /**
     * 扩展仓的样板槽数：底层库存按 <b>4 页 = 144</b> 预留，实际对外暴露几页由
     * ExtendedAE-Plus 的扩容卡决定（见 {@code MePatternProviderLogic#getPatternInv}）。
     * 无 EAEP / 取不到升级槽时会保守地只暴露 1 页（36）。
     */
    public static final int EXTENDED_PATTERN_SLOTS = 36 * 4;

    public static MachineDefinition<MePatternProviderBlockEntity> ME_PATTERN_PROVIDER_BLOCK;
    public static MachineDefinition<MePatternProviderBlockEntity> ME_EXTENDED_PATTERN_PROVIDER_BLOCK;

    private ModHatches() {
    }

    public static void init() {
        // 自定义机壳（基础方块材质），模型见 assets/mi_ae2_pattern_provider/models/machine_casing/*.json
        MachineCasings.create(
                ResourceLocation.fromNamespaceAndPath(MiAe2PatternProvider.MOD_ID, "me_pattern_provider_hatch"),
                "ME Pattern Provider Hatch"
        );
        MachineCasings.create(
                ResourceLocation.fromNamespaceAndPath(MiAe2PatternProvider.MOD_ID, "me_extended_pattern_provider_hatch"),
                "ME Extended Pattern Provider Hatch"
        );

        ME_PATTERN_PROVIDER_BLOCK = MachineRegistrationHelper.registerMachine(
                "ME Pattern Provider",
                "me_pattern_provider_hatch",
                bet -> new MePatternProviderBlockEntity(
                        bet,
                        guiParams("me_pattern_provider_hatch"),
                        ITEM_INPUT_SLOTS, ITEM_OUTPUT_SLOTS, FLUID_INPUT_SLOTS, FLUID_OUTPUT_SLOTS, FLUID_CAPACITY,
                        ME_PATTERN_PROVIDER, 9, false,
                        () -> ME_PATTERN_PROVIDER_BLOCK.blockDefinition().asItem()
                ),
                MachineBlockEntity::registerItemApi,
                MachineBlockEntity::registerFluidApi
        );

        // 扩展版：仅在安装了 ExtendedAE 时注册方块（无 ExtendedAE 则方块不存在，进存档即消失）。
        if (ModList.get().isLoaded("extendedae")) {
            ME_EXTENDED_PATTERN_PROVIDER_BLOCK = MachineRegistrationHelper.registerMachine(
                    "ME Extended Pattern Provider",
                    "me_extended_pattern_provider_hatch",
                    bet -> new MePatternProviderBlockEntity(
                            bet,
                            guiParams("me_extended_pattern_provider_hatch"),
                            ITEM_INPUT_SLOTS, ITEM_OUTPUT_SLOTS, FLUID_INPUT_SLOTS, FLUID_OUTPUT_SLOTS, FLUID_CAPACITY,
                            ME_EXTENDED_PATTERN_PROVIDER, EXTENDED_PATTERN_SLOTS, true,
                            () -> ME_EXTENDED_PATTERN_PROVIDER_BLOCK.blockDefinition().asItem()
                    ),
                    MachineBlockEntity::registerItemApi,
                    MachineBlockEntity::registerFluidApi
            );
        }
    }

    /**
     * GUI 参数：高度按槽位行数<b>算出来</b>（{@link MePatternProviderBlockEntity#requiredGuiHeight}）。
     *
     * <p>以前这里写死 {@code backgroundHeight(190)}，正好只够 1+1+1+1 四行；物品输出加到 4 排（共 7 行 = 246）后
     * 若不同步加高，最下面两行槽位会被玩家背包压住（不会有任何报错，只表现为「格子看不见」）。
     * 高度上限是贴图高度 256（见 {@link #ITEM_OUTPUT_SLOTS}），自检里有断言把关。
     */
    private static MachineGuiParameters guiParams(String blockId) {
        return new MachineGuiParameters.Builder(blockId, true)
                .backgroundHeight(MePatternProviderBlockEntity.requiredGuiHeight(
                        ITEM_INPUT_SLOTS, ITEM_OUTPUT_SLOTS, FLUID_INPUT_SLOTS, FLUID_OUTPUT_SLOTS))
                .build();
    }
}
