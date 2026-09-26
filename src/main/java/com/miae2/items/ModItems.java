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

    private ModItems() {
    }
}
