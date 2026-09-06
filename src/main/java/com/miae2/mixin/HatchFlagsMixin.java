package com.miae2.mixin;

import aztech.modern_industrialization.machines.multiblocks.HatchFlags;
import aztech.modern_industrialization.machines.multiblocks.HatchType;
import aztech.modern_industrialization.machines.multiblocks.HatchTypes;
import com.miae2.machines.init.ModHatches;
import java.util.Set;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让自定义 HatchType（me_pattern_provider）在物品/流体的输入/输出仓位置都被接受。
 * 沿用 mi_itemfluid_hatch 的思路：mixin MI 的 HatchFlags.allows。
 */
@Mixin(HatchFlags.class)
public class HatchFlagsMixin {

    @Shadow
    @Final
    private Set<HatchType> allowed;

    @Inject(method = "allows", at = @At("HEAD"), cancellable = true, require = 1)
    public void miae2$allows(HatchType type, CallbackInfoReturnable<Boolean> cir) {
        if (type == ModHatches.ME_PATTERN_PROVIDER || type == ModHatches.ME_EXTENDED_PATTERN_PROVIDER) {
            cir.setReturnValue(
                    this.allowed.contains(HatchTypes.ITEM_INPUT)
                            || this.allowed.contains(HatchTypes.FLUID_INPUT)
                            || this.allowed.contains(HatchTypes.ITEM_OUTPUT)
                            || this.allowed.contains(HatchTypes.FLUID_OUTPUT)
            );
        }
    }
}
