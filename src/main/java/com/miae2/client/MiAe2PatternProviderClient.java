package com.miae2.client;

import appeng.init.client.InitScreens;
import com.miae2.MiAe2PatternProvider;
import com.miae2.ae.ContainerExtendedPatternProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** 客户端注册：扩展样板供应仓的界面（沿用 PatternProviderScreen + 4×9 屏幕样式）。 */
@EventBusSubscriber(modid = MiAe2PatternProvider.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MiAe2PatternProviderClient {

    private MiAe2PatternProviderClient() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        // 复用 ExtendedAE 的 36 槽屏幕样式（扩展仓依赖 ExtendedAE），无需自带背景贴图。
        InitScreens.register(
                event,
                ContainerExtendedPatternProvider.TYPE,
                GuiExtendedPatternProvider::new,
                "/screens/ex_pattern_provider.json"
        );
    }
}
