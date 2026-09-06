package com.miae2.mixin;

import aztech.modern_industrialization.inventory.ConfigurableItemStack;
import aztech.modern_industrialization.thirdparty.fabrictransfer.api.item.ItemVariant;
import com.miae2.util.IUnboundedItemAccessor;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 给 ConfigurableItemStack 加「无上限容量」标记（默认关闭，只对本 mod 的槽生效）。
 * 无上限时把容量与物品 maxStackSize 都视为 Integer.MAX_VALUE，从而允许 AE 一次性推入百万级物品。
 * 沿用 mi_stack_upgrade 的思路，但把 multiplier 换成固定无上限（单等级、无升级）。
 */
@Mixin(ConfigurableItemStack.class)
public class ConfigurableItemStackMixin implements IUnboundedItemAccessor {

    @Shadow
    private int adjustedCapacity;

    @Unique
    private boolean miae2$unbounded = false;

    @Unique
    @Override
    public boolean miae2$isUnbounded() {
        return this.miae2$unbounded;
    }

    @Unique
    @Override
    public void miae2$setUnbounded(boolean unbounded) {
        this.miae2$unbounded = unbounded;
        this.adjustedCapacity = unbounded ? Integer.MAX_VALUE : 64;
    }

    @Inject(method = "<init>(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V", at = @At("TAIL"))
    private void miae2$init(CompoundTag compound, Provider registries, CallbackInfo ci) {
        this.miae2$setUnbounded(compound.getBoolean("miae2$unbounded"));
    }

    @Inject(method = "<init>(Laztech/modern_industrialization/inventory/ConfigurableItemStack;)V", at = @At("TAIL"))
    private void miae2$initFromOther(ConfigurableItemStack other, CallbackInfo ci) {
        if (other instanceof IUnboundedItemAccessor accessor) {
            this.miae2$setUnbounded(accessor.miae2$isUnbounded());
        }
    }

    @Inject(
            method = "toNbt(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;",
            at = @At("RETURN")
    )
    private void miae2$toNbt(Provider registries, CallbackInfoReturnable<CompoundTag> cir) {
        if (this.miae2$unbounded) {
            cir.getReturnValue().putBoolean("miae2$unbounded", true);
        }
    }

    @Redirect(
            method = "getCapacity",
            at = @At(value = "INVOKE", target = "Laztech/modern_industrialization/thirdparty/fabrictransfer/api/item/ItemVariant;getMaxStackSize()I")
    )
    private int miae2$getCapacityMaxStack(ItemVariant itemVariant) {
        return this.miae2$unbounded ? Integer.MAX_VALUE : itemVariant.getMaxStackSize();
    }

    @Redirect(
            method = "getTotalCapacityFor",
            at = @At(value = "INVOKE", target = "Laztech/modern_industrialization/thirdparty/fabrictransfer/api/item/ItemVariant;getMaxStackSize()I")
    )
    private int miae2$getTotalCapacityMaxStack(ItemVariant itemVariant) {
        return this.miae2$unbounded ? Integer.MAX_VALUE : itemVariant.getMaxStackSize();
    }

    @Redirect(
            method = "getRemainingCapacityFor",
            at = @At(value = "INVOKE", target = "Laztech/modern_industrialization/thirdparty/fabrictransfer/api/item/ItemVariant;getMaxStackSize()I")
    )
    private int miae2$getRemainingCapacityMaxStack(ItemVariant itemVariant) {
        return this.miae2$unbounded ? Integer.MAX_VALUE : itemVariant.getMaxStackSize();
    }
}
