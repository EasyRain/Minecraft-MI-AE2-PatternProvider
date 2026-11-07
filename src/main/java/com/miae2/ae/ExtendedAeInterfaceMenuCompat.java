package com.miae2.ae;

import appeng.menu.MenuOpener;
import appeng.menu.locator.MenuHostLocator;
import com.glodblock.github.extendedae.container.ContainerExInterface;
import net.minecraft.server.level.ServerPlayer;

/**
 * 隔离对 ExtendedAE 类的引用：扩展/超大接口仓直接复用它的 {@link ContainerExInterface} 菜单。
 *
 * <p>为什么必须复用它的菜单（而不是自建）：**翻页**（页码同步、翻页按钮、按页启用槽位）是注入在
 * {@code ContainerExInterface} / 它对应的 Screen 上的，只有用它的 MenuType 才能连带拿到整套分页界面。
 * 手法与我们样板仓复用 {@code ContainerExPatternProvider} 完全一致。
 *
 * <p>本类只在「扩展/超大接口仓」路径被触达，而那两个档位只在 ExtendedAE 在场时才注册，
 * 所以缺少 ExtendedAE 的环境不会解析到这里的类。
 */
public final class ExtendedAeInterfaceMenuCompat {

    private ExtendedAeInterfaceMenuCompat() {
    }

    /** 扩展接口仓（36 槽，4 页 × 9）。 */
    public static void openExtended(ServerPlayer player, MenuHostLocator locator) {
        MenuOpener.open(ContainerExInterface.TYPE, player, locator);
    }

    /** 超大接口仓（同样 36 槽，只是每槽容量更大）。 */
    public static void openOversize(ServerPlayer player, MenuHostLocator locator) {
        MenuOpener.open(ContainerExInterface.TYPE_OVERSIZE, player, locator);
    }
}
