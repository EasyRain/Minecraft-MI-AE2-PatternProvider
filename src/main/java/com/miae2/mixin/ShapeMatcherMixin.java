package com.miae2.mixin;

import aztech.modern_industrialization.machines.multiblocks.HatchBlockEntity;
import aztech.modern_industrialization.machines.multiblocks.HatchTypes;
import aztech.modern_industrialization.machines.multiblocks.ShapeMatcher;
import com.miae2.util.IControllerPosHolder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 多方块成形后，把控制器位置与阵列内「能源输入仓」的位置写回匹配到的仓。
 * <ul>
 *   <li>控制器位置：供自动命名查询处理阵列的工作方块；</li>
 *   <li>能源输入仓位置：供 {@code AppfluxEnergyTargetMixin} 把感应卡的能量定向送进阵列。</li>
 * </ul>
 */
@Mixin(ShapeMatcher.class)
public abstract class ShapeMatcherMixin {

    @Shadow
    @Final
    protected BlockPos controllerPos;

    @Shadow
    public abstract List<HatchBlockEntity> getMatchedHatches();

    @Inject(method = "rematch", at = @At("TAIL"))
    private void miae2$onRematch(Level world, CallbackInfo ci) {
        List<HatchBlockEntity> matched = this.getMatchedHatches();

        List<BlockPos> energyInputs = new ArrayList<>();
        for (HatchBlockEntity hatch : matched) {
            if (hatch.getHatchType() == HatchTypes.ENERGY_INPUT) {
                energyInputs.add(hatch.getBlockPos());
            }
        }
        List<BlockPos> energyHatches = List.copyOf(energyInputs);

        for (HatchBlockEntity hatch : matched) {
            if (hatch instanceof IControllerPosHolder holder) {
                holder.miae2$setControllerPos(this.controllerPos);
                holder.miae2$setEnergyInputHatchPositions(energyHatches);

                // AppliedFlux 的感应卡会把「能量送不进去」的边记为 blocked，只有该位置的能力失效回调
                // 才会解除。阵列成形/改动的这一刻主动戳一遍相邻方块，避免它一直卡在 blocked 而永不再试。
                BlockPos pos = hatch.getBlockPos();
                for (Direction dir : Direction.values()) {
                    world.invalidateCapabilities(pos.relative(dir));
                }
            }
        }
    }
}
