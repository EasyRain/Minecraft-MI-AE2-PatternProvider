package com.miae2.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.miae2.machines.blockentities.MePatternProviderBlockEntity;

/**
 * 基于原版 PatternProviderLogic，把 push 目标从「相邻方块」改为「自身仓库存」，
 * 排队/解锁由 MePatternProviderBlockEntity 的 activePattern 状态机控制（不强制 LOCK_UNTIL_RESULT，
 * 这样同一任务的材料可以一次性累计发配完，而不是做完一份才发下一份）。
 */
public class MePatternProviderLogic extends PatternProviderLogic {

    private final MePatternProviderBlockEntity be;

    public MePatternProviderLogic(IManagedGridNode mainNode, PatternProviderLogicHost host, MePatternProviderBlockEntity be) {
        super(mainNode, host);
        this.be = be;
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        if (this.getGrid() == null || !this.getAvailablePatterns().contains(patternDetails)) {
            return false;
        }
        return this.be.pushPattern(patternDetails, inputHolder);
    }
}
