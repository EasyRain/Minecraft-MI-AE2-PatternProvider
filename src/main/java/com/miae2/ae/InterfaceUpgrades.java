package com.miae2.ae;

import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import com.miae2.machines.init.ModHatches;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * 给三个 ME 接口仓嫁接 AE2 升级卡支持 —— 做法与样板供应仓（{@code MePatternProviderUpgrades}）完全一致。
 *
 * <p><b>为什么要嫁接</b>：AE2 的升级槽准入是一张全局静态表 {@link Upgrades#add}，键是「机器物品」。
 * AE2 / ExtendedAE 只给自己的方块登记过，我们的方块是 MI 物品 ⇒ 界面里 4 个升级槽**什么都插不进**。
 *
 * <p><b>为什么用「扫描 + 嫁接」而不是手写清单</b>：手写只能覆盖我们已知的卡；扫描能自动带上
 * ExtendedAE、EAEP 乃至以后任何装饰了「原版接口」的附属卡 —— 玩家装什么卡，我们就支持什么卡。
 * 供应仓一直就是这么干的。
 *
 * <p>数据源：对 {@code ae2:interface}（方块与部件）以及 ExtendedEA 的扩展/超大接口物品登记过的卡。
 */
public final class InterfaceUpgrades {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String INTERFACE_GROUP = "group.interface.name";

    /** 刻意剔除的卡：这些是「供应仓专用」语义，装到接口上毫无意义。 */
    private static final List<ResourceLocation> EXCLUDED = List.of(
            ResourceLocation.fromNamespaceAndPath("extendedae_plus", "virtual_crafting_card")
    );

    private static final List<ResourceLocation> EXTENDED_INTERFACE_ITEMS = List.of(
            ResourceLocation.fromNamespaceAndPath("extendedae", "ex_interface"),
            ResourceLocation.fromNamespaceAndPath("extendedae", "oversize_interface")
    );

    private InterfaceUpgrades() {
    }

    /** 在 common setup（方块与物品已注册完后）调用一次。 */
    public static void register() {
        List<Item> hatches = hatchItems();
        if (hatches.isEmpty()) {
            return;
        }

        // 数据源：原版接口（方块 + 部件）+ ExtendedEA 的两种接口物品
        List<ItemLike> sources = new ArrayList<>(4);
        sources.add(AEBlocks.INTERFACE);
        sources.add(AEParts.INTERFACE);
        if (ModList.get().isLoaded("extendedae")) {
            for (ResourceLocation id : EXTENDED_INTERFACE_ITEMS) {
                Item item = BuiltInRegistries.ITEM.get(id);
                if (item != Items.AIR) {
                    sources.add(item);
                }
            }
        }

        // 黑名单（按物品实例比对：mod 没装时查不到，自然不会命中）
        List<Item> excluded = new ArrayList<>(EXCLUDED.size());
        for (ResourceLocation id : EXCLUDED) {
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item != Items.AIR) {
                excluded.add(item);
            }
        }

        Map<Item, Integer> cards = new LinkedHashMap<>();
        for (Item card : BuiltInRegistries.ITEM) {
            if (card == Items.AIR || excluded.contains(card)) {
                continue;
            }
            int max = 0;
            for (ItemLike source : sources) {
                max = Math.max(max, Upgrades.getMaxInstallable(card, source));
            }
            if (max > 0) {
                cards.put(card, max);
            }
        }

        if (cards.isEmpty()) {
            LOGGER.info("[接口仓] 没有找到任何「接口可用」的升级卡，跳过登记");
            return;
        }

        List<String> names = new ArrayList<>(cards.size());
        for (Map.Entry<Item, Integer> entry : cards.entrySet()) {
            for (Item hatch : hatches) {
                Upgrades.add(entry.getKey(), hatch, entry.getValue(), INTERFACE_GROUP);
            }
            names.add(BuiltInRegistries.ITEM.getKey(entry.getKey()) + "(max " + entry.getValue() + ")");
        }
        LOGGER.info("[接口仓] 已把 {} 张接口可用的升级卡嫁接到 {} 个接口仓 —— {}",
                cards.size(), hatches.size(), String.join(", ", names));
    }

    /** 本 mod 已注册的接口仓方块物品（扩展/超大档在无 ExtendedAE 时为 null）。 */
    private static List<Item> hatchItems() {
        List<Item> items = new ArrayList<>(3);
        if (ModHatches.ME_INTERFACE_BLOCK != null) {
            items.add(ModHatches.ME_INTERFACE_BLOCK.blockDefinition().asItem());
        }
        if (ModHatches.ME_EXTENDED_INTERFACE_BLOCK != null) {
            items.add(ModHatches.ME_EXTENDED_INTERFACE_BLOCK.blockDefinition().asItem());
        }
        if (ModHatches.ME_OVERSIZE_INTERFACE_BLOCK != null) {
            items.add(ModHatches.ME_OVERSIZE_INTERFACE_BLOCK.blockDefinition().asItem());
        }
        return items;
    }

    @Nullable
    private static Item optionalItem(ResourceLocation id) {
        Item item = BuiltInRegistries.ITEM.get(id);
        return item == Items.AIR ? null : item;
    }
}
