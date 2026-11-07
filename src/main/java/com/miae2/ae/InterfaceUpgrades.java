package com.miae2.ae;

import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import com.miae2.machines.init.ModHatches;
import com.mojang.logging.LogUtils;
import net.minecraft.world.level.ItemLike;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

/**
 * 给三个 ME 接口仓注册 AE2 升级卡支持。
 *
 * <p><b>为什么必须显式注册</b>：AE2 的升级槽是否接受某张卡，由全局注册表
 * {@link Upgrades#add(ItemLike, ItemLike, int)} 决定 —— 键是「机器物品」。AE2 / ExtendedAE 只给
 * 自己的方块登记过，我们的方块是 MI 的物品，注册表里没有条目 ⇒ 界面里 4 个升级槽**什么都插不进去**
 * （2026-11-07 用户实测）。
 *
 * <p>注册的门槛是「机器物品」而不是方块实体类型，所以只要照抄 AE2 对自家接口的登记即可：
 * 合成卡 ×1、模糊卡 ×1（与 {@code InitUpgrades} 里 {@code AEBlocks.INTERFACE} 的条目一致）。
 */
public final class InterfaceUpgrades {

    private static final Logger LOGGER = LogUtils.getLogger();

    private InterfaceUpgrades() {
    }

    /** 在 common setup（方块与物品已注册完后）调用一次。 */
    public static void register() {
        registerFor(ModHatches.ME_INTERFACE_BLOCK == null ? null
                : ModHatches.ME_INTERFACE_BLOCK.blockDefinition().asItem());
        if (ModList.get().isLoaded("extendedae")) {
            registerFor(ModHatches.ME_EXTENDED_INTERFACE_BLOCK == null ? null
                    : ModHatches.ME_EXTENDED_INTERFACE_BLOCK.blockDefinition().asItem());
            registerFor(ModHatches.ME_OVERSIZE_INTERFACE_BLOCK == null ? null
                    : ModHatches.ME_OVERSIZE_INTERFACE_BLOCK.blockDefinition().asItem());
        }
    }

    private static void registerFor(ItemLike machineItem) {
        if (machineItem == null) {
            return;
        }
        try {
            // 与 AE2 原版接口一致：合成卡 1 张、模糊卡 1 张
            Upgrades.add(AEItems.CRAFTING_CARD.asItem(), machineItem, 1);
            Upgrades.add(AEItems.FUZZY_CARD.asItem(), machineItem, 1);
            LOGGER.info("[接口仓] 已注册升级卡支持：{}（合成卡 ×1、模糊卡 ×1）",
                    machineItem.asItem().getDescriptionId());
        } catch (Throwable t) {
            LOGGER.warn("[接口仓] 注册升级卡失败（不影响其它功能）", t);
        }
    }
}
