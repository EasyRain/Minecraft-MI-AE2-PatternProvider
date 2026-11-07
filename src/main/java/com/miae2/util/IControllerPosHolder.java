package com.miae2.util;

import java.util.List;
import net.minecraft.core.BlockPos;

/** 供 ShapeMatcherMixin 在多方块成形时，把控制器位置与阵列内能源输入仓的位置写回仓方块。 */
public interface IControllerPosHolder {

    void miae2$setControllerPos(BlockPos pos);

    /** 阵列（多方块）里能源输入仓的位置列表，供感应卡定向供电。 */
    void miae2$setEnergyInputHatchPositions(List<BlockPos> positions);

    /**
     * 同一阵列里<b>其它</b> ME 样板供应仓的位置（多仓单活仲裁用，见 {@code ArrayArbiter}）。
     *
     * <p>做成 default 空实现：接口仓等其它仓不需要关心这条。
     */
    default void miae2$setProviderSiblingPositions(List<BlockPos> positions) {
    }
}
