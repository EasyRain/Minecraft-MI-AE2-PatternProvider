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

/**
 * 注册 ME 样板供应仓的自定义 HatchType 与方块。
 * 方块经 MI 的 MachineRegistrationHelper 注册（因此落在 modern_industrialization 命名空间下）。
 */
public final class ModHatches {

    public static final HatchType ME_PATTERN_PROVIDER = HatchTypes.register(
            ResourceLocation.fromNamespaceAndPath(MiAe2PatternProvider.MOD_ID, "me_pattern_provider"),
            MI.id("me_pattern_provider_hatch")
    );

    public static final int ITEM_INPUT_SLOTS = 9;
    public static final int ITEM_OUTPUT_SLOTS = 9;
    public static final int FLUID_INPUT_SLOTS = 9;
    public static final int FLUID_OUTPUT_SLOTS = 9;
    // 超堆叠直接到上限（单等级、无升级）
    public static final long FLUID_CAPACITY = Integer.MAX_VALUE;

    public static MachineDefinition<MePatternProviderBlockEntity> ME_PATTERN_PROVIDER_BLOCK;

    private ModHatches() {
    }

    public static void init() {
        // 自定义机壳（基础方块材质），模型见 assets/mi_ae2_pattern_provider/models/machine_casing/me_pattern_provider_hatch.json
        MachineCasings.create(
                ResourceLocation.fromNamespaceAndPath(MiAe2PatternProvider.MOD_ID, "me_pattern_provider_hatch"),
                "ME Pattern Provider Hatch"
        );

        ME_PATTERN_PROVIDER_BLOCK = MachineRegistrationHelper.registerMachine(
                "ME Pattern Provider",
                "me_pattern_provider_hatch",
                bet -> new MePatternProviderBlockEntity(
                        bet,
                        new MachineGuiParameters.Builder("me_pattern_provider_hatch", true).backgroundHeight(190).build(),
                        ITEM_INPUT_SLOTS, ITEM_OUTPUT_SLOTS, FLUID_INPUT_SLOTS, FLUID_OUTPUT_SLOTS, FLUID_CAPACITY
                ),
                MachineBlockEntity::registerItemApi,
                MachineBlockEntity::registerFluidApi
        );
    }
}
