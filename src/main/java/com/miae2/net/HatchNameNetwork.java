package com.miae2.net;

import com.miae2.MiAe2PatternProvider;
import com.miae2.machines.blockentities.MeInterfaceBlockEntity;
import com.miae2.machines.blockentities.MePatternProviderBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** 注册 {@link HatchNamePayloads} 的两个包，并处理「提交名字」那一侧。 */
@EventBusSubscriber(modid = MiAe2PatternProvider.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class HatchNameNetwork {

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private HatchNameNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // 打开界面：只发给客户端，客户端若无对应 handler 会忽略（不会崩）
        registrar.playToClient(HatchNamePayloads.OpenEditor.TYPE, HatchNamePayloads.OpenEditor.CODEC,
                (payload, context) -> context.enqueueWork(
                        () -> com.miae2.client.HatchRenameScreen.open(payload.pos())));
        // 提交名字：服务端校验距离与方块类型后写入
        registrar.playToServer(HatchNamePayloads.SetName.TYPE, HatchNamePayloads.SetName.CODEC,
                (payload, context) -> context.enqueueWork(() -> applyName(payload, context.player())));
    }

    private static void applyName(HatchNamePayloads.SetName payload, net.minecraft.world.entity.player.Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.level().isClientSide) {
            return;
        }
        var level = serverPlayer.level();
        if (!level.isLoaded(payload.pos())
                || serverPlayer.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(payload.pos())) > 64.0) {
            return;
        }
        String raw = payload.name();
        Component name = raw == null || raw.isBlank() ? null : Component.literal(raw.trim());
        var be = level.getBlockEntity(payload.pos());
        if (be instanceof MePatternProviderBlockEntity provider) {
            provider.miae2$setCustomName(name);
            level.sendBlockUpdated(payload.pos(), be.getBlockState(), be.getBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_ALL);
        } else if (be instanceof MeInterfaceBlockEntity hatch) {
            hatch.miae2$setCustomName(name);
            level.sendBlockUpdated(payload.pos(), be.getBlockState(), be.getBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_ALL);
        } else {
        }
    }
}
