package com.miae2.util;

/**
 * 供 mixin 给 ConfigurableItemStack 打「无上限容量」标记的访问器接口。
 */
public interface IUnboundedItemAccessor {
    boolean miae2$isUnbounded();

    void miae2$setUnbounded(boolean unbounded);
}
