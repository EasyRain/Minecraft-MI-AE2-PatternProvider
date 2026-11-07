package com.miae2.ae;

import appeng.helpers.InterfaceLogicHost;
import appeng.menu.MenuOpener;
import appeng.menu.implementations.InterfaceMenu;
import appeng.menu.locator.MenuHostLocator;
import com.miae2.machines.blockentities.MeInterfaceBlockEntity;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import org.slf4j.Logger;

/**
 * 把「原生接口菜单」的重开请求，在我们 36 槽的接口仓上改写成 ExtendedAE 的扩展接口菜单。
 *
 * <p><b>为什么要这么做</b>（2026-11-07 实测，扩展档与超大档都复现）：
 * 在 AE 界面里设置保持量并点确认后，会**有别处**用 AE2 原生的 {@code InterfaceMenu} 重新打开界面，
 * 而原生界面按「配置槽数量」去样式里取 {@code amtButton{i+1}} 控件（{@code InterfaceScreen} 构造里那个循环），
 * 36 个槽 → 需要 {@code amtButton10..36}，原生样式只有 9 个 ⇒
 * {@code IllegalStateException: Screen is missing required widget: amtButton10} ⇒ 客户端被踢出世界。
 *
 * <p>AE2 允许用 {@link MenuOpener#addOpener} 覆盖某个菜单类型的打开逻辑，于是这里：
 * <ul>
 *   <li>打开目标是我们自己的 36 槽接口仓 → 改成 ExtendedEA 的 {@code ContainerExInterface.TYPE}（界面带 36 个控件，正常显示）；</li>
 *   <li>其它任何接口（AE2 原版的方块/部件）→ 按原样构造 {@link InterfaceMenu} 并打开，行为与默认一致。</li>
 * </ul>
 *
 * <p>顺带打一条带调用栈的日志：把「是谁在重开界面」记录下来，便于以后判断是否该在上游修。
 */
public final class NativeInterfaceMenuGuard {

    private static final Logger LOGGER = LogUtils.getLogger();

    private NativeInterfaceMenuGuard() {
    }

    /** 在 mod 初始化（common setup）时调用一次。 */
    public static void install() {
        MenuOpener.addOpener(InterfaceMenu.TYPE, (player, locator, force) -> miae2$openInterface(player, locator));
    }

    private static boolean miae2$openInterface(net.minecraft.world.entity.player.Player player, MenuHostLocator locator) {
        InterfaceLogicHost host = locator.locate(player, InterfaceLogicHost.class);
        if (host == null) {
            return false;
        }
        // 我们自己 36 槽的接口仓：原生界面必崩，改走 ExtendedAE 的扩展菜单
        if (host instanceof MeInterfaceBlockEntity be && be.miae2$configSlots() > 9 && player instanceof ServerPlayer serverPlayer) {
            LOGGER.info("[接口守卫] 拦截到原生接口菜单的重开，改用扩展菜单（{} 槽）@ {}；调用方：{}",
                    be.miae2$configSlots(), be.getBlockPos(), miae2$caller());
            return ExtendedAeInterfaceMenuCompat.openExtended(serverPlayer, locator);
        }
        // 其它接口：与 AE2 默认行为一致。**必须把 locator 写进 open-menu 的附加数据**：
        // AE2 的菜单在客户端要从包里 `MenuLocators.readFromPacket(buf)` 还原宿主，
        // 不带数据的 `player.openMenu(provider)` 会让客户端拿到 null buf ⇒ NPE ⇒ 网络协议错误
        // （2026-11-07 实测：基础档 9 槽接口一打开就断线）。
        if (player instanceof ServerPlayer serverPlayer) {
            ((net.neoforged.neoforge.common.extensions.IPlayerExtension) serverPlayer).openMenu(
                    new SimpleMenuProvider(
                            (id, inventory, p) -> new InterfaceMenu(InterfaceMenu.TYPE, id, inventory, host),
                            host.getMainMenuIcon().getHoverName()),
                    buf -> appeng.menu.locator.MenuLocators.writeToPacket(buf, locator));
            return true;
        }
        return false;
    }

    /** 取一层「谁调用的」调用栈（跳过本类与 AE2 的菜单框架）。 */
    private static String miae2$caller() {
        StackTraceElement[] trace = new Throwable().getStackTrace();
        StringBuilder sb = new StringBuilder();
        for (int i = 2; i < trace.length && i < 12; i++) {
            String frame = trace[i].toString();
            if (frame.contains("appeng.menu.MenuOpener") || frame.contains("NativeInterfaceMenuGuard")) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" <- ");
            }
            sb.append(frame);
            if (sb.length() > 240) {
                break;
            }
        }
        return sb.toString();
    }
}
