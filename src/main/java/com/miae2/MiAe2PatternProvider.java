package com.miae2;

import appeng.api.AECapabilities;
import appeng.api.networking.IInWorldGridNodeHost;
import com.miae2.machines.blockentities.MePatternProviderBlockEntity;
import com.miae2.machines.init.ModHatches;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(MiAe2PatternProvider.MOD_ID)
public class MiAe2PatternProvider {

    public static final String MOD_ID = "mi_ae2_pattern_provider";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public MiAe2PatternProvider(IEventBus modEventBus) {
        ModHatches.init();
        modEventBus.addListener(MiAe2PatternProvider::registerCapabilities);
        NeoForge.EVENT_BUS.addListener(MiAe2PatternProvider::onRightClickBlock);
        LOGGER.info("MI AE2 Pattern Provider initialized");
    }

    /** 手持 ExtendedAE 的「样板供应器升级」+ shift 右键：把普通供应仓原位升级为扩展供应仓。 */
    private static void onRightClickBlock(RightClickBlock event) {
        Player player = event.getEntity();
        InteractionHand hand = event.getHand();
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (player.isSpectator() || !level.mayInteract(player, pos)) {
            return;
        }
        if (!player.isShiftKeyDown()) {
            return;
        }
        if (!MePatternProviderBlockEntity.isPatternProviderUpgrade(player.getItemInHand(hand))) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof MePatternProviderBlockEntity be)) {
            return;
        }
        if (!level.isClientSide()) {
            // 服务端：真正替换方块并消耗升级物品
            if (be.upgradeToExtended() && !player.isCreative()) {
                player.getItemInHand(hand).shrink(1);
            }
        }
        // 两端都取消默认交互（避免 ExtendedAE 升级物品的 useOn / MI 的 useItemOn 再跑一遍）
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide()));
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModHatches.ME_PATTERN_PROVIDER_BLOCK.blockEntityType().get(),
                (be, ctx) -> (IInWorldGridNodeHost) be
        );
        if (ModHatches.ME_EXTENDED_PATTERN_PROVIDER_BLOCK != null) {
            event.registerBlockEntity(
                    AECapabilities.IN_WORLD_GRID_NODE_HOST,
                    ModHatches.ME_EXTENDED_PATTERN_PROVIDER_BLOCK.blockEntityType().get(),
                    (be, ctx) -> (IInWorldGridNodeHost) be
            );
        }
    }
}
