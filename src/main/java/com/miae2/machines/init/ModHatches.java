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
import com.miae2.ae.ContainerExtendedPatternProvider;
import com.miae2.machines.blockentities.MePatternProviderBlockEntity;
import net.minecraft.resources.ResourceLocation;

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
    public static final int ITEM_OUTPUT_SLOTS = 9;
    public static final int FLUID_INPUT_SLOTS = 9;
    public static final int FLUID_OUTPUT_SLOTS = 9;
    // 超堆叠直接到上限（单等级、无升级）
    public static final long FLUID_CAPACITY = Integer.MAX_VALUE;

    public static MachineDefinition<MePatternProviderBlockEntity> ME_PATTERN_PROVIDER_BLOCK;
    public static MachineDefinition<MePatternProviderBlockEntity> ME_EXTENDED_PATTERN_PROVIDER_BLOCK;

    private ModHatches() {
    }

    public static void init() {
        // 强制初始化扩展菜单类型：否则 MenuTypeBuilder.build 的 queueRegistration 会晚于 AE2 的 RegisterEvent，
        // 菜单类型注册不上 → 右键打开扩展仓 GUI 时服务端写开屏包抛异常 → 断开连接。
        ContainerExtendedPatternProvider.TYPE.getClass();

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
                        new MachineGuiParameters.Builder("me_pattern_provider_hatch", true).backgroundHeight(190).build(),
                        ITEM_INPUT_SLOTS, ITEM_OUTPUT_SLOTS, FLUID_INPUT_SLOTS, FLUID_OUTPUT_SLOTS, FLUID_CAPACITY,
                        ME_PATTERN_PROVIDER, 9, false,
                        () -> ME_PATTERN_PROVIDER_BLOCK.blockDefinition().asItem()
                ),
                MachineBlockEntity::registerItemApi,
                MachineBlockEntity::registerFluidApi
        );

        // 扩展版：无条件注册方块（否则挖掘标签会引用不存在的方块导致 mineable/pickaxe 整表失效）。
        // 获取渠道（升级物品 shift 右键 / 水晶装配器配方）由 ExtendedAE 提供，配方本身用 neoforge:conditions 门控。
        ME_EXTENDED_PATTERN_PROVIDER_BLOCK = MachineRegistrationHelper.registerMachine(
                "ME Extended Pattern Provider",
                "me_extended_pattern_provider_hatch",
                bet -> new MePatternProviderBlockEntity(
                        bet,
                        new MachineGuiParameters.Builder("me_extended_pattern_provider_hatch", true).backgroundHeight(190).build(),
                        ITEM_INPUT_SLOTS, ITEM_OUTPUT_SLOTS, FLUID_INPUT_SLOTS, FLUID_OUTPUT_SLOTS, FLUID_CAPACITY,
                        ME_EXTENDED_PATTERN_PROVIDER, 36, true,
                        () -> ME_EXTENDED_PATTERN_PROVIDER_BLOCK.blockDefinition().asItem()
                ),
                MachineBlockEntity::registerItemApi,
                MachineBlockEntity::registerFluidApi
        );
    }
}
