package com.miae2.client;

import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.ScreenStyle;
import com.miae2.ae.ContainerExtendedPatternProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** 扩展样板供应仓的界面：沿用 PatternProviderScreen，用 4×9 的屏幕样式。 */
public class GuiExtendedPatternProvider extends PatternProviderScreen<ContainerExtendedPatternProvider> {
    public GuiExtendedPatternProvider(ContainerExtendedPatternProvider menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }
}
