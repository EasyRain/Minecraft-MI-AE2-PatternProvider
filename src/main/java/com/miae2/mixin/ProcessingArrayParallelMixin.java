package com.miae2.mixin;

import aztech.modern_industrialization.machines.MachineBlockEntity;
import com.miae2.api.QuantumParallelHost;
import com.miae2.items.OverclockModules;
import net.swedz.extended_industrialization.machines.blockentity.multiblock.ProcessingArrayBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让「量子超频模块」把 EI 处理阵列的并行上限放大到 {@code 机器数 × 配置倍率}。
 *
 * <h2>为什么改这一个方法就够了</h2>
 * EI 的 {@code ProcessingArrayBlockEntity#getMaxMultiplier()} 只是
 * {@code return this.machines.getMachineCount();}（上限 64 台机器）。tesseract 的
 * {@code MultipliedCrafterComponent} 每 tick 通过构造时传入的 getter 拉这个值，把它当作
 * <b>倍率上限</b>，再用 min(输入料量能撑的倍率, 输出格空间装得下的倍率, 这个上限) 得到真正执行的倍率。
 * 所以：
 * <ul>
 *   <li>放大这里 = 放宽上限，输入/输出不满足时自动降下来，<b>不会出现「模拟够、真扣不够」</b>；</li>
 *   <li>EU 与耗时的语义完全不动，保持「与真塞进那么多台机器等价」，不做免费产能；</li>
 *   <li>机器数上限（{@code MAX_MACHINES = 64}）保持原样 —— 这是「额外并行」，不是「塞更多机器」。</li>
 * </ul>
 *
 * <p>目标类是 {@code final}，所以本 mixin 不继承它，只用 {@code (MachineBlockEntity) (Object) this}
 * 拿到 MI 的组件容器（{@code components} 是 {@code MachineBlockEntity} 上的 public final 字段）。
 *
 * <p>顺带把 {@link QuantumParallelHost} 贴在这个类上：超频槽那条「量子模块只许阵列」的规则
 * （{@code OverclockModules#blockedIn}）正是按这个标记判断「装了到底有没有用」的。
 */
@Mixin(value = ProcessingArrayBlockEntity.class, remap = false)
public abstract class ProcessingArrayParallelMixin implements QuantumParallelHost {

    @Inject(method = "getMaxMultiplier", at = @At("RETURN"), cancellable = true, remap = false)
    private void miae2$applyQuantumParallel(CallbackInfoReturnable<Integer> cir) {
        MachineBlockEntity machine = (MachineBlockEntity) (Object) this;
        if (!OverclockModules.grantsParallel(OverclockModules.moduleIn(machine))) {
            return; // 没装量子模块（或装的是高级模块）→ 原样返回
        }
        int original = cir.getReturnValue();
        int scaled = OverclockModules.scaleMaxMultiplier(original);
        if (scaled != original) {
            cir.setReturnValue(scaled);
        }
    }
}
