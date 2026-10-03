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
 *
 * <p><b>⚠️ 绝不碰别人的 adjustedCapacity。</b>这个 mixin 挂在 MI 的公共类上，会作用于**全游戏**每一个
 * {@code ConfigurableItemStack}（每台机器、每个仓、以及其它 mod 的槽）。而 {@code adjustedCapacity}
 * 是**别人也会写的持久化字段**：Extended Industrialization 的「机器配置」物品就通过
 * {@code ConfigurableItemStackAccessor.setAdjustedCapacity(...)} 把它设成 &gt;64 的值
 * （见 {@code ei-src/net/swedz/extended_industrialization/item/machineconfig/MachineConfigSlots.java:42,77}）。
 *
 * <p>之前这里是 `adjustedCapacity = unbounded ? MAX : 64;` —— 于是**每次读档**（NBT 构造函数的 TAIL）
 * 都会把所有槽位的容量硬写成 64，把机器配置设的大容量静默抹掉，症状是「机器做满一组（64）就再也不动」。
 * 现在改成：
 * <ul>
 *   <li>只有 NBT 里真的带本 mod 的标记时才动容量（别人的槽是"零操作"）；</li>
 *   <li>开无上限前先备份原值，关掉时原样还回去；</li>
 *   <li>写 NBT 时把 {@code adjCap} 还原成原值，别把 MAX 带进存档。</li>
 * </ul>
 *
 * <p><b>⚠️ 只许给「输入槽」开无上限，输出槽绝不能开</b>（2026-10-04 修的大型合成卡死）：EI 的处理阵列会按
 * 「输出槽还装得下多少」反推并行倍率（{@code MultipliedCrafterComponent#calculateItemOutputRecipeMultiplier}
 * → {@code canItemOutputsAllFit} → {@code MIStorage.insert} → {@code getRemainingCapacityFor}），
 * 无上限的输出槽让它永远返回「装得下」⇒ 倍率被抬到阵列机器数；而 tesseract 的真插入
 * （{@code CrafterComponentHelper#putItemOutputs}）真实路径算的是
 * {@code variant.getMaxStackSize() - amount}（一格 64），多出来的产物只把返回值置 false、
 * <b>调用方根本不看</b> ⇒ 产物被静默销毁 ⇒ AE 的 CPU 永远等不到足量产、任务永久挂起。
 * 详见 {@code MePatternProviderBlockEntity#miae2$enforceSlotCapacityPolicy()}。
 */
@Mixin(ConfigurableItemStack.class)
public class ConfigurableItemStackMixin implements IUnboundedItemAccessor {

    /** 本 mod 的槽位是否处于「无上限」状态；只有它为 true 时我们才有资格动容量。 */
    @Unique
    private static final String MIAE2_UNBOUNDED_KEY = "miae2$unbounded";

    @Shadow
    private int adjustedCapacity;

    @Unique
    private boolean miae2$unbounded = false;

    /** 开无上限之前的 adjustedCapacity（别人的配置），关掉时要原样还回去。 */
    @Unique
    private int miae2$savedCapacity = -1;

    @Unique
    @Override
    public boolean miae2$isUnbounded() {
        return this.miae2$unbounded;
    }

    @Unique
    @Override
    public void miae2$setUnbounded(boolean unbounded) {
        if (unbounded) {
            if (!this.miae2$unbounded) {
                this.miae2$savedCapacity = this.adjustedCapacity;
            }
            this.miae2$unbounded = true;
            this.adjustedCapacity = Integer.MAX_VALUE;
        } else if (this.miae2$unbounded) {
            this.miae2$unbounded = false;
            if (this.miae2$savedCapacity >= 0) {
                this.adjustedCapacity = this.miae2$savedCapacity;
                this.miae2$savedCapacity = -1;
            }
        }
        // 已经是 false 且本来就不是无上限 → 什么都不做：
        // 绝不能在这里写 adjustedCapacity（会抹掉其它 mod / 玩家机器配置设定的容量）。
    }

    @Inject(method = "<init>(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V", at = @At("TAIL"))
    private void miae2$init(CompoundTag compound, Provider registries, CallbackInfo ci) {
        // MI 已经读过 adjCap（别人的容量在这里已经就位）—— 只有本 mod 自己的槽才动它。
        if (compound.getBoolean(MIAE2_UNBOUNDED_KEY)) {
            this.miae2$setUnbounded(true);
        }
    }

    @Inject(method = "<init>(Laztech/modern_industrialization/inventory/ConfigurableItemStack;)V", at = @At("TAIL"))
    private void miae2$initFromOther(ConfigurableItemStack other, CallbackInfo ci) {
        // 拷贝构造：MI 已经把 adjustedCapacity 抄过来了，只在源是无上限槽时才接管。
        if (other instanceof IUnboundedItemAccessor accessor && accessor.miae2$isUnbounded()) {
            this.miae2$setUnbounded(true);
        }
    }

    @Inject(
            method = "toNbt(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;",
            at = @At("RETURN")
    )
    private void miae2$toNbt(Provider registries, CallbackInfoReturnable<CompoundTag> cir) {
        if (this.miae2$unbounded) {
            cir.getReturnValue().putBoolean(MIAE2_UNBOUNDED_KEY, true);
            // MI 刚把 adjustedCapacity(=MAX) 写进 adjCap —— 换成原值，别把 MAX 带到存档里。
            if (this.miae2$savedCapacity >= 0) {
                cir.getReturnValue().putInt("adjCap", this.miae2$savedCapacity);
            }
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
