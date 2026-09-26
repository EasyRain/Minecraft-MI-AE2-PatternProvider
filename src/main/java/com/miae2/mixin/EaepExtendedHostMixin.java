package com.miae2.mixin;

import com.miae2.machines.blockentities.MePatternProviderBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让 ExtendedAE-Plus 把本 mod 的「扩展样板供应仓」当成它认定的扩展型样板供应器。
 *
 * <p>EAEP 的整套扩展能力（5 个升级槽、样板库存的动态视图、扩容卡的页数语义）都门控在
 * {@code UpgradeSlotCompat.isExtendedPatternProviderHost(host)} 上，而它只认 ExtendedAE 自己的
 * {@code TileExPatternProvider} / {@code PartExPatternProvider}。我们的方块实体是 MI
 * {@code HatchBlockEntity} 的子类，所以在这里把这个判定补上。
 *
 * <p>刻意做成「坏了也只是不能用」，而不是崩游戏：
 * <ul>
 *   <li>用字符串 {@code targets} 而不是类字面量 → 编译期不依赖 EAEP，产物里也没有它的代码；</li>
 *   <li>{@code @Pseudo} → EAEP 缺席时 Mixin 不会因为找不到目标类而报错；</li>
 *   <li>{@code require = 0} → EAEP 改了方法名/签名/删了这个方法时，只是这条注入不生效。</li>
 * </ul>
 * 注入失效的后果：扩展仓退回 1 页样板、升级槽回到 2 个——功能不可用，但一切正常
 * （样板槽的对外规模另有 {@code MePatternProviderLogic#getPatternInv()} 兜底，不会露出 144 个空槽）。
 */
@Pseudo
@Mixin(targets = "com.extendedae_plus.compat.UpgradeSlotCompat", remap = false)
public abstract class EaepExtendedHostMixin {

    @Inject(method = "isExtendedPatternProviderHost", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private static void miae2$alsoAcceptOurExtendedHatch(Object host, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            return;
        }
        if (host instanceof MePatternProviderBlockEntity be && be.isExtended()) {
            cir.setReturnValue(true);
        }
    }
}
