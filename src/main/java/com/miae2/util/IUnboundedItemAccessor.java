package com.miae2.util;

/**
 * 供 mixin 给 ConfigurableItemStack 打「无上限容量」标记的访问器接口。
 */
public interface IUnboundedItemAccessor {
    boolean miae2$isUnbounded();

    void miae2$setUnbounded(boolean unbounded);

    /**
     * 把该槽容量设成具体值（用于接口仓输入槽：容量必须与 AE 侧每槽容量一致）。
     *
     * <p>与 {@link #miae2$setUnbounded(boolean)} 的区别：这是「有限但大于默认 64」的容量，
     * 玩家在 MI 界面里塞不进更多 ⇒ 不需要 spill 兜底逻辑。
     */
    void miae2$setCapacity(int capacity);
}
