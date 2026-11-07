package com.miae2.client;

import com.miae2.MiAe2PatternProvider;
import com.miae2.machines.init.ModHatches;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * 给三个仓注册「成型时才画 overlay」的渲染器（见 {@link MeHatchOverlayRenderer}）。
 *
 * <p>注册时机：模组总线上的 {@link EntityRenderersEvent.RegisterRenderers}。
 * 扩展仓/接口仓可能是条件注册的（扩展仓需要 ExtendedAE），所以各自判空。
 */
@EventBusSubscriber(modid = MiAe2PatternProvider.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MeHatchRenderers {

    private MeHatchRenderers() {
    }

    @SubscribeEvent
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        if (ModHatches.ME_PATTERN_PROVIDER_BLOCK != null) {
            event.registerBlockEntityRenderer(
                    ModHatches.ME_PATTERN_PROVIDER_BLOCK.blockEntityType().get(),
                    ctx -> new MeHatchOverlayRenderer());
        }
        if (ModHatches.ME_EXTENDED_PATTERN_PROVIDER_BLOCK != null) {
            event.registerBlockEntityRenderer(
                    ModHatches.ME_EXTENDED_PATTERN_PROVIDER_BLOCK.blockEntityType().get(),
                    ctx -> new MeHatchOverlayRenderer());
        }
        if (ModHatches.ME_INTERFACE_BLOCK != null) {
            event.registerBlockEntityRenderer(
                    ModHatches.ME_INTERFACE_BLOCK.blockEntityType().get(),
                    ctx -> new MeHatchOverlayRenderer());
        }
    }

}
