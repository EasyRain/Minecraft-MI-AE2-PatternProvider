package com.miae2.ae;

import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.implementations.MenuTypeBuilder;
import appeng.menu.implementations.PatternProviderMenu;
import com.miae2.MiAe2PatternProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

/** 扩展样板供应仓的菜单：与 PatternProviderMenu 同逻辑，只是样板槽更多（36 = 4×9）。 */
public class ContainerExtendedPatternProvider extends PatternProviderMenu {

    public static final MenuType<ContainerExtendedPatternProvider> TYPE = MenuTypeBuilder
            .create(ContainerExtendedPatternProvider::new, PatternProviderLogicHost.class)
            .build(MiAe2PatternProvider.id("me_extended_pattern_provider_hatch"));

    protected ContainerExtendedPatternProvider(int id, Inventory playerInventory, PatternProviderLogicHost host) {
        super(TYPE, id, playerInventory, host);
    }
}
