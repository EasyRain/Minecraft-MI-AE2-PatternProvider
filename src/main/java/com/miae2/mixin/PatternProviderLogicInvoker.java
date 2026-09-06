package com.miae2.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.helpers.patternprovider.PatternProviderLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 暴露 PatternProviderLogic 私有的 onPushPatternSuccess（用于 push 成功后的锁定逻辑）。
 */
@Mixin(PatternProviderLogic.class)
public interface PatternProviderLogicInvoker {

    @Invoker("onPushPatternSuccess")
    void invokeOnPushPatternSuccess(IPatternDetails pattern);
}
