package com.miae2.ae;

import appeng.helpers.InterfaceLogicHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.MenuOpener;
import appeng.menu.implementations.InterfaceMenu;
import appeng.menu.locator.MenuHostLocator;
import com.miae2.machines.blockentities.MeInterfaceBlockEntity;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import org.slf4j.Logger;

/**
 * 只把「我们 36 槽接口仓」的原生接口菜单打开请求，改写成 ExtendedAE 的扩展接口菜单。
 *
 * <p><b>为什么需要</b>：36 个配置槽的接口在设置保持量并点确认后，会**有别处**用 AE2 原生的
 * {@code InterfaceMenu} 重新打开界面；原生界面按配置槽数量取 {@code amtButton{i+1}} 控件
 * （36 槽要 amtButton10..36，原生样式只有 9 个）⇒
 * {@code IllegalStateException: Screen is missing required widget: amtButton10} ⇒ 客户端被踢。
 *
 * <p><b>为什么必须委托给 AE2 原本的 opener</b>：{@link MenuOpener#addOpener} 是**替换**语义。
 * 我们只关心自己那几个方块，其它接口（AE2 原版方块/部件、ExtendedAE 的接口等）必须原样走 AE2 的默认实现 ——
 * 自己重写一遍开菜单是行不通的：AE2 的菜单在客户端要按它自己的格式从 open-menu 包里读数据，
 * 手写的附加数据一旦不一致就会「网络协议错误 / IndexOutOfBounds」（2026-11-07 连续踩了两次）。
 * 所以这里先用反射把**原来注册的 opener 抓下来**，兜底时原样调用。
 */
public final class NativeInterfaceMenuGuard {

    private static final Logger LOGGER = LogUtils.getLogger();

    private NativeInterfaceMenuGuard() {
    }

    /** 在 mod 初始化时调用一次（此时 AE2 已给自己的菜单类型注册过 opener）。 */
    public static void install() {
        MenuOpener.Opener original = miae2$lookupOriginal();
        if (original == null) {
            LOGGER.warn("[接口守卫] 没能取到 AE2 原本的 InterfaceMenu opener，守卫不安装（36 槽确认时可能崩）");
            return;
        }
        MenuOpener.addOpener(InterfaceMenu.TYPE, (player, locator, force) -> {
            InterfaceLogicHost host = locator.locate(player, InterfaceLogicHost.class);
            if (host instanceof MeInterfaceBlockEntity be && be.miae2$configSlots() > 9
                    && player instanceof ServerPlayer serverPlayer) {
                LOGGER.info("[接口守卫] 拦截到原生接口菜单的重开，改用扩展菜单（{} 槽）@ {}；调用方：{}",
                        be.miae2$configSlots(), be.getBlockPos(), miae2$caller());
                return ExtendedAeInterfaceMenuCompat.openExtended(serverPlayer, locator);
            }
            // 其它一切接口：原样交回 AE2 的默认实现（含它自己的 open-menu 数据格式）
            return original.open(player, locator, force);
        });
        LOGGER.info("[接口守卫] 已接管 InterfaceMenu 的打开请求（仅改写 36 槽接口仓，其余委托给 AE2）");
    }

    /** 反射取 {@code MenuOpener.registry} 里当前的 InterfaceMenu opener。 */
    private static MenuOpener.Opener miae2$lookupOriginal() {
        try {
            Field field = MenuOpener.class.getDeclaredField("registry");
            field.setAccessible(true);
            Object value = field.get(null);
            if (!(value instanceof Map<?, ?> map)) {
                return null;
            }
            Object opener = map.get(InterfaceMenu.TYPE);
            if (opener instanceof MenuOpener.Opener o) {
                return o;
            }
            return null;
        } catch (Throwable t) {
            LOGGER.warn("[接口守卫] 读取 MenuOpener.registry 失败", t);
            return null;
        }
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
