package com.miae2.mixin;

import aztech.modern_industrialization.machines.guicomponents.SlotPanel;
import com.miae2.items.ModItems;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让机器界面右侧那条「超频模块」槽也接受本 mod 的高级超频模块。
 *
 * <p>{@code SlotPanel.SlotType.OVERDRIVE_MODULE} 的准入判定写死为 {@code MIItem.OVERDRIVE_MODULE::is}，
 * 只靠 {@code OverdriveComponent#onUse} 那条右键路径的话，玩家把模块<b>拖</b>进槽位会被静默拒绝。
 * 这里只放宽这一个槽，其它槽（升级/外壳/红石）不受影响。
 */
@Mixin(value = SlotPanel.SlotType.class, remap = false)
public abstract class OverdriveSlotTypeMixin {

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true, remap = false)
    private void miae2$acceptAdvancedModule(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this == SlotPanel.SlotType.OVERDRIVE_MODULE
                && stack.is(ModItems.ADVANCED_OVERCLOCK_MODULE.get())) {
            cir.setReturnValue(true);
        }
    }
}
