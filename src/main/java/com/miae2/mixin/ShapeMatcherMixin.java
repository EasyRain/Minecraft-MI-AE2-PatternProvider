package com.miae2.mixin;

import aztech.modern_industrialization.machines.multiblocks.HatchBlockEntity;
import aztech.modern_industrialization.machines.multiblocks.ShapeMatcher;
import com.miae2.util.IControllerPosHolder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 多方块成形后，把控制器位置写回匹配到的仓（供自动命名查询处理阵列的工作方块）。 */
@Mixin(ShapeMatcher.class)
public abstract class ShapeMatcherMixin {

    @Shadow
    @Final
    protected BlockPos controllerPos;

    @Shadow
    public abstract List<HatchBlockEntity> getMatchedHatches();

    @Inject(method = "rematch", at = @At("TAIL"))
    private void miae2$onRematch(Level world, CallbackInfo ci) {
        for (HatchBlockEntity hatch : this.getMatchedHatches()) {
            if (hatch instanceof IControllerPosHolder holder) {
                holder.miae2$setControllerPos(this.controllerPos);
            }
        }
    }
}
