package com.miae2.mixin;

import appeng.client.gui.AEBaseScreen;
import appeng.menu.AEBaseMenu;
import com.miae2.machines.blockentities.MePatternProviderBlockEntity;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * AE2 的 PatternProviderScreen 把标题文本 dialog_title 写死为 gui.ae2.CraftingInterface（"样板供应器"），
 * 并不像 MEChestScreen 那样用菜单标题覆盖。这里改从 AEBaseScreen（所有 AE2 界面的基类）入手，
 * 只针对我方块的界面把标题覆盖成方块名"ME样板供应仓"。
 */
@Mixin(AEBaseScreen.class)
public abstract class AEBaseScreenMixin {

    @Invoker("setTextContent")
    public abstract void invokeSetTextContent(String id, Component content);

    @Inject(method = "updateBeforeRender", at = @At("TAIL"))
    private void miae2$setDialogTitle(CallbackInfo ci) {
        AbstractContainerMenu menu = ((AbstractContainerScreen) (Object) this).getMenu();
        if (menu instanceof AEBaseMenu aeMenu && aeMenu.getBlockEntity() instanceof MePatternProviderBlockEntity be) {
            // 用方块自身名称（普通「ME样板供应仓」/ 扩展「ME扩展样板供应仓」）
            this.invokeSetTextContent("dialog_title", be.getName());
        }
    }
}
