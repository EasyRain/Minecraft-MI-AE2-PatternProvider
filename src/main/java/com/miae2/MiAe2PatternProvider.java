package com.miae2;

import appeng.api.AECapabilities;
import appeng.api.networking.IInWorldGridNodeHost;
import com.miae2.machines.init.ModHatches;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(MiAe2PatternProvider.MOD_ID)
public class MiAe2PatternProvider {

    public static final String MOD_ID = "mi_ae2_pattern_provider";
    private static final Logger LOGGER = LogUtils.getLogger();

    public MiAe2PatternProvider(IEventBus modEventBus) {
        ModHatches.init();
        modEventBus.addListener(MiAe2PatternProvider::registerCapabilities);
        LOGGER.info("MI AE2 Pattern Provider initialized");
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                AECapabilities.IN_WORLD_GRID_NODE_HOST,
                ModHatches.ME_PATTERN_PROVIDER_BLOCK.blockEntityType().get(),
                (be, ctx) -> (IInWorldGridNodeHost) be
        );
    }
}
