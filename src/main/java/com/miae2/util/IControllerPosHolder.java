package com.miae2.util;

import java.util.List;
import net.minecraft.core.BlockPos;

/** 供 ShapeMatcherMixin 在多方块成形时，把控制器位置与阵列内能源输入仓的位置写回仓方块。 */
public interface IControllerPosHolder {

    void miae2$setControllerPos(BlockPos pos);

    /** 阵列（多方块）里能源输入仓的位置列表，供感应卡定向供电。 */
    void miae2$setEnergyInputHatchPositions(List<BlockPos> positions);
}
