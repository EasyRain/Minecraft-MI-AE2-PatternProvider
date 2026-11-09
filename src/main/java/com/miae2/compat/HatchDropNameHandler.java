package com.miae2.compat;

import com.miae2.machines.blockentities.MeInterfaceBlockEntity;
import com.miae2.machines.blockentities.MePatternProviderBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Nameable;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * 挖掘时让掉落的方块物品**继承自定义名**（与 AE 一致：AE 的仓改名后挖下来，手里那件也是新名字）。
 *
 * <p>为什么不用 loot table：掉落表是静态 json，带不了运行时数据；也不好在 block 上覆写
 * `playerDestroy`（方块是 MI 注册的 `MachineBlock`，不是我们的类）。所以挂 NeoForge 的
 * {@link BlockDropsEvent}，在掉落生成的最后一步改名。
 */
@EventBusSubscriber(modid = com.miae2.MiAe2PatternProvider.MOD_ID)
public final class HatchDropNameHandler {

    private HatchDropNameHandler() {
    }

    @SubscribeEvent
    public static void onDrops(BlockDropsEvent event) {
        var be = event.getBlockEntity();
        if (!(be instanceof MePatternProviderBlockEntity) && !(be instanceof MeInterfaceBlockEntity)) {
            return;
        }
        if (!(be instanceof Nameable nameable)) {
            return;
        }
        Component custom = nameable.getCustomName();
        if (custom == null || custom.getString().isBlank()) {
            return;
        }
        var blockItem = be.getBlockState().getBlock().asItem();
        for (var entity : event.getDrops()) {
            var stack = entity.getItem();
            if (stack.is(blockItem)) {
                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, custom);
                entity.setItem(stack);
            }
        }
    }
}
