package com.miae2.ae;

import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import com.glodblock.github.extendedae.container.ContainerExPatternProvider;
import net.minecraft.server.level.ServerPlayer;

/**
 * 隔离对 ExtendedAE 类的引用。
 *
 * <p>扩展仓的界面直接用 ExtendedAE 自己的「扩展样板供应器」菜单类型，而不是自建一个：
 * ExtendedAE-Plus 的<b>样板分页</b>（页码同步、翻页按钮、按页启用槽位）是注入在
 * {@code ContainerExPatternProvider} / {@code GuiExPatternProvider} 上的，只有用它的菜单类型
 * 才能连带获得整套分页界面。屏幕也由 ExtendedAE 自己注册（同一个 ex_pattern_provider 样式）。
 *
 * <p>本类只在「扩展仓」路径上被触达，而扩展仓只在 ExtendedAE 存在时才会注册（见 {@code ModHatches}），
 * 因此这些引用不会在缺少 ExtendedAE 的环境里被解析。
 */
public final class ExtendedAeMenuCompat {

    private ExtendedAeMenuCompat() {
    }

    public static void openExtendedPatternProvider(ServerPlayer player, MenuHostLocator locator) {
        MenuOpener.open(ContainerExPatternProvider.TYPE, player, locator);
    }
}
