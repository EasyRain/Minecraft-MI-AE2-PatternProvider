package com.miae2.compat;

import aztech.modern_industrialization.machines.MachineBlockEntity;
import aztech.modern_industrialization.machines.components.OverdriveComponent;
import com.miae2.items.ModItems;
import net.minecraft.world.item.ItemStack;
import net.swedz.tesseract.neoforge.compat.mi.hook.MIHookEfficiency;
import net.swedz.tesseract.neoforge.compat.mi.hook.MIHookEntrypoint;
import net.swedz.tesseract.neoforge.compat.mi.hook.context.machine.EfficiencyMIHookContext;

/**
 * 「高级超频模块」的行为实现——用 MI 官方（经 tesseract 暴露）的 {@code MIHookEfficiency} 钩子，
 * <b>不碰 MI 内部逻辑、也不需要 mixin MI 的效率代码</b>（tesseract 自己已经 mixin 了
 * {@code CrafterComponent} 并分发这些回调）。
 *
 * <h2>MI 原本的效率机制（为什么要另做一个模块）</h2>
 * {@code CrafterComponent#tickRecipe} 里：
 * <ul>
 *   <li>{@code efficiencyTicks} 是「超频档位」：做完一个配方 +1，没跑满 {@code recipeMaxEu} 则 -1，
 *       档位越高 {@code recipeMaxEu} 越大 → 越快；<b>从 0 开始慢慢爬</b>；</li>
 *   <li>配方只有在 {@code efficiencyTicks == 0 && usedEnergy == 0}（{@code clearActiveRecipeIfPossible}）
 *       才会被释放 —— 所以做完之后要<b>等效率慢慢归零</b>才能接下一个配方。</li>
 * </ul>
 * MI 自己的「超频模块」靠 {@code isOverdriving()} 在空转时继续按 {@code recipeMaxEu} 耗电，
 * 从而既不让效率掉、也就不释放配方 → 代价就是<b>锁死配方</b>。
 *
 * <h2>本模块的做法</h2>
 * 直接把「效率」钉到上限、并且做完立刻归零：
 * <ul>
 *   <li>{@code onTickStart} / {@code onReadNbt}：有配方就 {@code efficiencyTicks = maxEfficiencyTicks}
 *       → <b>开局就是最高速度</b>；</li>
 *   <li>{@code onTickEnd(eu == 0)}：归零 → 下一 tick 就能释放配方 → <b>做完立刻接下一个</b>。</li>
 * </ul>
 * 只在装了我们模块的机器上生效（{@code shouldAlwaysRun} 保证没有配方时也会被调到）。
 *
 * <p>思路参考 MIT 授权的 MI-Tweaks（作者 Swedz）的 {@code ALWAYS_MAX} 模式；本文件为独立实现，
 * 面向的是 MI 的公开 hook API。
 */
@MIHookEntrypoint
public final class AdvancedOverclockHook implements MIHookEfficiency {

    /**
     * tesseract 把各个 mod 的效率 hook 放在一个<b>按优先级升序</b>的 {@code TreeSet} 里依次调用，
     * 每个 hook 改的是**同一个 context**，最终由最后跑的那个写回 {@code efficiencyTicks} ——
     * 也就是说 <b>优先级越高 = 跑得越晚 = 越能决定结果</b>。
     *
     * <p>MI-Tweaks（MIT，作者 Swedz）的同类 hook 用的是 {@code Integer.MIN_VALUE}（最先跑），
     * 本 mod 的模块是「走正常游戏流程的道具」，应当压过它的全局配置，因此用一个较高的值。
     *
     * <p>⚠️ 该 TreeSet 是按 {@code getPriority()} 比较去重的：<b>与别的 mod 优先级撞值会让其中一个被静默丢弃</b>，
     * 所以这里刻意用一个不那么容易撞的值，而不是 {@code Integer.MAX_VALUE}。
     */
    @Override
    public int getPriority() {
        return 10_000;
    }

    @Override
    public boolean shouldAlwaysRun() {
        return true;
    }

    @Override
    public void onTickStart(EfficiencyMIHookContext context) {
        if (hasAdvancedModule(context)) {
            context.setEfficiencyTicks(context.hasActiveRecipe() ? context.getMaxEfficiencyTicks() : 0);
        }
    }

    @Override
    public void onTickEnd(EfficiencyMIHookContext context, long eu) {
        // eu == 0 表示这一 tick 没在按 recipeMaxEu 工作（做完/停转/断电）→ 立刻归零，好让配方马上被释放
        if (eu == 0L && hasAdvancedModule(context)) {
            context.setEfficiencyTicks(0);
        }
    }

    @Override
    public void onReadNbt(EfficiencyMIHookContext context) {
        // 读档后也立刻回到满档，避免重进世界要重新爬
        if (hasAdvancedModule(context)) {
            context.setEfficiencyTicks(context.hasActiveRecipe() ? context.getMaxEfficiencyTicks() : 0);
        }
    }

    /** 该机器是否装着本 mod 的高级超频模块。 */
    private static boolean hasAdvancedModule(EfficiencyMIHookContext context) {
        MachineBlockEntity machine = context.getMachineBlockEntity();
        if (machine == null) {
            return false;
        }
        ItemStack module = machine.components.mapOrDefault(
                OverdriveComponent.class, OverdriveComponent::getDrop, ItemStack.EMPTY);
        return !module.isEmpty() && module.is(ModItems.ADVANCED_OVERCLOCK_MODULE.get());
    }
}
