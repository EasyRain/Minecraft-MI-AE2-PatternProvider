package com.miae2.util;

import net.minecraft.core.BlockPos;

/** 供 ShapeMatcherMixin 在多方块成形时把控制器位置写回仓方块。 */
public interface IControllerPosHolder {
    void miae2$setControllerPos(BlockPos pos);
}
