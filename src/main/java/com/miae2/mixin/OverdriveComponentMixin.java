package com.miae2.mixin;

import aztech.modern_industrialization.MIItem;
import aztech.modern_industrialization.machines.MachineBlockEntity;
import aztech.modern_industrialization.machines.components.OverdriveComponent;
import com.miae2.items.ModItems;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让 MI 的「超频模块」槽也接受本 mod 的<b>高级超频模块</b>，并阻止它触发原版那套「锁死配方」。
 *
 * <p>MI 的 {@code OverdriveComponent} 只认 {@code MIItem.OVERDRIVE_MODULE}，
 * 且 {@code shouldOverdrive()} 一旦为真就会让机器空转也持续耗电（从而钉住效率、不释放配方）。
 * 我们的模块走的是 {@code AdvancedOverclockHook} 那条官方 hook 路线，因此这里要：
 * <ol>
 *   <li>把它认成可插入的模块；</li>
 *   <li>让 {@code shouldOverdrive()} 对<b>非原版</b>模块返回 false —— 否则就会出现
 *       「空转耗电 + 锁配方」和「满速 + 立即释放」两套机制互相打架。</li>
 * </ol>
 * MI 原版模块的行为完全不变。
 */
@Mixin(value = OverdriveComponent.class, remap = false)
public abstract class OverdriveComponentMixin {

    @Shadow
    private ItemStack overdriveModule;

    /** 手持高级超频模块右键机器时，插进这个槽（对齐 MI 原版那 5 行插入逻辑）。 */
    @Inject(method = "onUse", at = @At("HEAD"), cancellable = true, remap = false)
    private void miae2$acceptAdvancedModule(MachineBlockEntity be, Player player, InteractionHand hand,
                                            CallbackInfoReturnable<ItemInteractionResult> cir) {
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty() || !held.is(ModItems.ADVANCED_OVERCLOCK_MODULE.get())) {
            return; // 不是我们的模块 → 交回原逻辑
        }
        if (this.overdriveModule != null && !this.overdriveModule.isEmpty()) {
            return; // 槽里已有东西 → 也让原逻辑去处理
        }
        this.overdriveModule = held.copyWithCount(1);
        held.consume(1, player);
        be.setChanged();
        cir.setReturnValue(ItemInteractionResult.sidedSuccess(player.level().isClientSide));
    }

    /** 原版的「空转耗电 + 锁配方」只对 MI 自己的超频模块生效。 */
    @Inject(method = "shouldOverdrive", at = @At("HEAD"), cancellable = true, remap = false)
    private void miae2$onlyVanillaModuleOverdrives(CallbackInfoReturnable<Boolean> cir) {
        ItemStack module = this.overdriveModule;
        if (module != null && !module.isEmpty() && !MIItem.OVERDRIVE_MODULE.is(module)) {
            cir.setReturnValue(false);
        }
    }
}
