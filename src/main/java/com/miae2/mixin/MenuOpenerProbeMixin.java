package com.miae2.mixin;

import appeng.menu.MenuOpener;
import appeng.menu.implementations.InterfaceMenu;
import appeng.menu.locator.MenuHostLocator;
import java.util.Arrays;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 诊断用探针：记录**谁**打开了 AE2 原版接口菜单（{@code InterfaceMenu.TYPE}）。
 *
 * <p>背景（2026-11-07）：打开我们「超大接口仓」的 AE 界面本身正常，但在界面里设置保持量并点确认后，
 * 客户端崩溃：
 * <pre>
 * IllegalStateException: Screen is missing required widget: amtButton10
 *   at appeng.client.gui.implementations.InterfaceScreen.&lt;init&gt;
 * </pre>
 * AE2 的 {@code InterfaceScreen} 按「配置槽数量」取 {@code amtButton{i+1}} 控件，36 个槽就要
 * {@code amtButton10..36}，而原版 {@code interface.json} 只有 9 个 ⇒ 崩。
 * 但我们的 36 槽档位走的是 ExtendedAE 的 {@code ContainerExInterface}（界面是 {@code GuiExInterface}），
 * 所以**一定有别处**用 {@code InterfaceMenu.TYPE} + 我们 36 槽的逻辑打开了界面。本探针把它揪出来。
 *
 * <p>只记录、不改行为；确认元凶后可以删掉这个类（或只在 debug 日志下保留）。
 */
@Mixin(value = MenuOpener.class, remap = false)
public abstract class MenuOpenerProbeMixin {

    @Inject(method = "open", at = @At("HEAD"), remap = false, require = 0)
    private static void miae2$logInterfaceOpen(MenuType<?> menuType, Player player, MenuHostLocator locator,
                                               CallbackInfoReturnable<Boolean> cir) {
        // 只关心接口系菜单，避免刷屏
        String name = menuType == null ? "null" : String.valueOf(menuType);
        if (!name.contains("interface") && !name.contains("Interface")) {
            return;
        }
        Logger logger = com.mojang.logging.LogUtils.getLogger();
        StringBuilder sb = new StringBuilder("[菜单探针] open ").append(name).append(" locator=")
                .append(locator == null ? "null" : locator.getClass().getSimpleName());
        StackTraceElement[] trace = new Throwable().getStackTrace();
        for (int i = 1; i < Math.min(trace.length, 9); i++) {
            String frame = trace[i].toString();
            if (frame.contains("net.minecraft") || frame.contains("net.neoforged")) {
                continue;
            }
            sb.append("\n    at ").append(frame);
        }
        logger.info(sb.toString());
    }
}
