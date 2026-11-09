package com.miae2.compat;

import appeng.api.integrations.igtooltip.ClientRegistration;
import appeng.api.integrations.igtooltip.CommonRegistration;
import appeng.api.integrations.igtooltip.TooltipContext;
import appeng.api.integrations.igtooltip.TooltipProvider;
import appeng.api.integrations.igtooltip.providers.NameProvider;
import com.miae2.MiAe2PatternProvider;
import com.miae2.machines.blockentities.MeInterfaceBlockEntity;
import com.miae2.machines.blockentities.MePatternProviderBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

/**
 * 通过 AE 的 IGT（Jade / The One Probe 共用）提供**自定义名作为标题**。
 *
 * <p>AE 原版仓改名后，悬浮提示的第一行直接就是新名字（不是单独一行「名称: xxx」）。
 * 要做到这个必须注册 {@code NameProvider}；AE2 用 {@link java.util.ServiceLoader} 发现
 * {@link TooltipProvider} 实现 —— jar 里放
 * {@code META-INF/services/appeng.api.integrations.igtooltip.TooltipProvider} 指向本类即可。
 */
public final class MeHatchIgtooltipProvider implements TooltipProvider {

    /** 数据 UID：服务端把名字写进这份数据，客户端读它。 */
    public static final ResourceLocation NAME_UID =
            ResourceLocation.fromNamespaceAndPath(MiAe2PatternProvider.MOD_ID, "hatch_name");

    private static final String KEY_NAME = "name";

    @Override
    public void registerCommon(CommonRegistration registration) {
        registration.addBlockEntityData(NAME_UID, MePatternProviderBlockEntity.class,
                (player, be, tag) -> writeName(be.getCustomName(), tag));
        registration.addBlockEntityData(NAME_UID, MeInterfaceBlockEntity.class,
                (player, be, tag) -> writeName(be.getCustomName(), tag));
    }

    private static void writeName(Component custom, CompoundTag tag) {
        if (custom != null && !custom.getString().isBlank()) {
            tag.putString(KEY_NAME, custom.getString());
        }
    }

    @Override
    public void registerClient(ClientRegistration registration) {
        NameProvider<MePatternProviderBlockEntity> provider = MeHatchIgtooltipProvider::readName;
        registration.addBlockEntityName(MePatternProviderBlockEntity.class, Block.class, NAME_UID, provider);
        registration.addBlockEntityName(MeInterfaceBlockEntity.class, Block.class, NAME_UID,
                (be, ctx) -> readName(be, ctx));
    }

    private static Component readName(net.minecraft.world.level.block.entity.BlockEntity be, TooltipContext ctx) {
        CompoundTag data = ctx.serverData();
        if (data != null && data.contains(KEY_NAME)) {
            String name = data.getString(KEY_NAME);
            if (!name.isBlank()) {
                return Component.literal(name);
            }
        }
        // 没设名字：回落到方块默认名（交给原版逻辑时会显示默认名，这里显式给）
        return be.getBlockState().getBlock().getName();
    }
}
