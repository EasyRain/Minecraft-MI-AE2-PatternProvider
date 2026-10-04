package com.miae2.mixin;

import aztech.modern_industrialization.machines.MachineBlockEntity;
import com.miae2.api.QuantumParallelHost;
import com.miae2.items.OverclockModules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 同一件事，针对可选 mod Industrialization Overdrive 的「多处理阵列」（{@code MultiProcessingArrayBlockEntity}，
 * 它同样继承 tesseract 的 multiplied 基类、同样有 64 台上限与超频槽）。
 *
 * <p>IO <b>不是</b>本 mod 的依赖（{@code build.gradle} 与 {@code neoforge.mods.toml} 里都没有它），
 * 所以这里严格照 {@code AppfluxEnergyTargetMixin} 的做法：{@code @Pseudo} + 字符串 {@code targets}
 * + 注入点 {@code require = 0}，代码里也绝不出现 IO 的类字面量。IO 缺席或改签名时，
 * 这个 mixin 只是**不生效**（写一条日志），不会导致加载失败。
 *
 * <p>注意范围：只覆盖「多处理阵列」。IO 的 {@code PyrolyseOvenBlockEntity} 也有同名的
 * {@code getMaxMultiplier()}，但它不是处理阵列（没有机器槽），本 mod 不碰它。
 *
 * <p>顺带把 {@link QuantumParallelHost} 贴在这个类上（同 EI 那边）：超频槽的「量子模块只许阵列」
 * 判定就是按这个标记判断「装了到底有没有用」。IO 缺席时本 mixin 整体不生效，
 * 那个类自然也不是宿主 —— 语义正确。
 */
@Pseudo
@Mixin(targets = "dev.wp.industrialization_overdrive.machines.blockentities.multiblock.MultiProcessingArrayBlockEntity", remap = false)
public abstract class MultiProcessingArrayParallelMixin implements QuantumParallelHost {

    @Inject(method = "getMaxMultiplier", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void miae2$applyQuantumParallel(CallbackInfoReturnable<Integer> cir) {
        MachineBlockEntity machine = (MachineBlockEntity) (Object) this;
        if (!OverclockModules.grantsParallel(OverclockModules.moduleIn(machine))) {
            return;
        }
        int original = cir.getReturnValue();
        int scaled = OverclockModules.scaleMaxMultiplier(original);
        if (scaled != original) {
            cir.setReturnValue(scaled);
        }
    }
}
