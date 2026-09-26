package com.miae2.client;

import com.miae2.MiAe2PatternProvider;
import com.miae2.util.SmokeTestAutoStop;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;

/**
 * 客户端侧的冒烟测试自动收尾（对应 {@link SmokeTestAutoStop}）。
 * 单独一个类是为了让 {@code Minecraft} 只出现在 {@code Dist.CLIENT} 的注册器里。
 */
@EventBusSubscriber(modid = MiAe2PatternProvider.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class SmokeTestAutoStopClient {

    private static final Logger LOGGER = LogUtils.getLogger();

    private SmokeTestAutoStopClient() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (SmokeTestAutoStop.tickAndShouldStop()) {
            LOGGER.info("冒烟测试结束：主动关闭客户端（日志无异常即视为通过）");
            Minecraft.getInstance().stop();
        }
    }
}
