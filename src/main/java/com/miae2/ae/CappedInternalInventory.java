package com.miae2.ae;

import appeng.api.inventories.InternalInventory;
import net.minecraft.world.item.ItemStack;

/**
 * 把底层库存<b>只对外暴露前 {@code limit} 个槽位</b>的尺寸视图，读写照常转发。
 *
 * <p>本类为本项目<b>原创实现</b>：只做「槽位号越界检查 + 转发」这一件事，
 * 未复制任何第三方（尤其是 LGPL-3.0 授权）mod 的代码。
 *
 * <p>用途见 {@link MePatternProviderLogic#getPatternInv()}：扩展仓的底层样板库存按 4 页（144）
 * 预留，但真正能用的页数由扩容卡决定，对外只应暴露已解锁的部分。
 */
final class CappedInternalInventory implements InternalInventory {

    private final InternalInventory delegate;
    private final int limit;

    CappedInternalInventory(InternalInventory delegate, int limit) {
        this.delegate = delegate;
        this.limit = Math.max(0, Math.min(limit, delegate.size()));
    }

    @Override
    public int size() {
        return this.limit;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        this.requireInRange(slot);
        return this.delegate.getStackInSlot(slot);
    }

    @Override
    public void setItemDirect(int slot, ItemStack stack) {
        this.requireInRange(slot);
        this.delegate.setItemDirect(slot, stack);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        this.requireInRange(slot);
        return this.delegate.isItemValid(slot, stack);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        this.requireInRange(slot);
        return this.delegate.extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        this.requireInRange(slot);
        return this.delegate.getSlotLimit(slot);
    }

    @Override
    public void sendChangeNotification(int slot) {
        this.requireInRange(slot);
        this.delegate.sendChangeNotification(slot);
    }

    private void requireInRange(int slot) {
        if (slot < 0 || slot >= this.limit) {
            throw new IndexOutOfBoundsException("slot " + slot + " outside capped inventory size " + this.limit);
        }
    }
}
