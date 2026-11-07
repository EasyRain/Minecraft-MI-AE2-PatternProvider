package com.miae2.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 让 {@link Slot} 的 {@code x}/{@code y} 可写。
 *
 * <p>1.21.1 里这两个字段是 <b>final</b>，而接口仓的翻页界面要把 144 个槽按页重新摆位
 * （其余挪出画布）—— 纯客户端改坐标即可，因为槽位点击走的是**槽位索引**，服务端不靠坐标找槽。
 */
@Mixin(Slot.class)
public interface SlotPositionAccessor {

    @Mutable
    @Accessor("x")
    void miae2$setX(int x);

    @Mutable
    @Accessor("y")
    void miae2$setY(int y);
}
