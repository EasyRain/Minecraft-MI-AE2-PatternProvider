package com.miae2.items;

import com.miae2.MiAe2PatternProvider;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 本 mod 自己命名空间下的物品。 */
public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MiAe2PatternProvider.MOD_ID);

    /**
     * 高级超频模块：装进 MI 电动机器（含 EI 处理阵列控制器）的「超频模块」槽。
     *
     * <p>与 MI 原版超频模块的区别见 {@code AdvancedOverclockHook}：
     * 原版靠「空转也持续耗电」把效率钉住，代价是<b>锁死配方</b>；本模块直接用官方
     * {@code MIHookEfficiency} 把效率设为上限（开局即满速），且做完立刻释放配方。
     */
    public static final DeferredItem<Item> ADVANCED_OVERCLOCK_MODULE = ITEMS.register(
            "advanced_overclock_module",
            () -> new AdvancedOverclockModuleItem(new Item.Properties()));

    /**
     * 量子超频模块：高级超频模块的<b>升级版</b>，占同一个超频槽（因此两者天然互斥）。
     *
     * <p>既有高级模块的全部行为（满效率 + 做完立刻释放配方），又额外把 EI 处理阵列的并行上限
     * 放大到「机器数 × 配置倍率」（{@code config/mi_ae2_pattern_provider-common.toml} 里的
     * {@code quantum_overclock.parallel_multiplier}，默认 4×）。
     */
    public static final DeferredItem<Item> QUANTUM_OVERCLOCK_MODULE = ITEMS.register(
            "quantum_overclock_module",
            () -> new QuantumOverclockModuleItem(new Item.Properties()));

    /**
     * 「使用手册」：右键打开本 mod 自己的 GuideME 指南书（正文在
     * {@code assets/mi_ae2_pattern_provider/guides/mi_ae2_pattern_provider/guide/} 下）。
     *
     * <p>之所以要一个自己的物品、而不是把页面塞进 MI / AE2 的指南：MI 与 AE2 的指南是各自的
     * {@code Guide}，本 mod 的页面自成一本（与 EI 的做法一致），玩家拿到的入口也就明确。
     * 指南 id 与物品 id 同名（{@code mi_ae2_pattern_provider:guide}），两者是不同的注册表，不冲突
     * —— AE2 的 {@code ae2:guide} 也是这个写法。
     */
    public static final DeferredItem<Item> GUIDE = ITEMS.register(
            "guide",
            () -> new GuideBookItem(new Item.Properties()));

    private ModItems() {
    }
}
