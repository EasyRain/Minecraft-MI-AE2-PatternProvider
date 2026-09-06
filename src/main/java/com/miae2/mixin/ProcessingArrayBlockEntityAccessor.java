package com.miae2.mixin;

import net.swedz.extended_industrialization.machines.blockentity.multiblock.ProcessingArrayBlockEntity;
import net.swedz.extended_industrialization.machines.component.craft.processingarray.ProcessingArrayMachineComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 暴露 EI 处理阵列的机器组件（用于读取工作方块 ItemStack 的名字）。 */
@Mixin(ProcessingArrayBlockEntity.class)
public interface ProcessingArrayBlockEntityAccessor {

    @Accessor("machines")
    ProcessingArrayMachineComponent getMachinesComponent();
}
