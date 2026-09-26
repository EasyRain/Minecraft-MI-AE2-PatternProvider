package com.miae2.ae;

import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import com.miae2.machines.init.ModHatches;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * 把「原版样板供应器能用的升级卡」<b>自动</b>嫁接到本 mod 的两个供应仓上。
 *
 * <h2>为什么以前插不进任何升级</h2>
 * AE2 的升级准入是一张<b>静态支持表</b>（{@link Upgrades#add}）：{@code UpgradeInventory}
 * 的插入过滤器只问 {@code Upgrades.getMaxInstallable(card, machineItem)}，而后者按「机器物品」
 * 精确匹配（{@code ==}），查不到就返回 0 → 一切卡片都被拒绝。这里的 machineItem 取的是
 * {@code host.getTerminalIcon().getItem()}，也就是我们的方块物品——而它从来没被登记过。
 * （AE2 本体对 {@code PATTERN_PROVIDER} 是<b>零登记</b>，原版供应器的升级能力全部来自附加 mod，
 * 所以不登记就等于插不了任何东西。）
 *
 * <h2>槽位不用我们操心</h2>
 * AppliedFlux 与 ExtendedAE-Plus 的 mixin 直接作用在 {@code PatternProviderLogic} /
 * {@code PatternProviderLogicHost} / {@code PatternProviderMenu} / {@code PatternProviderScreen} 上
 * （把升级库存挂在逻辑对象上、在菜单构造时 setupUpgrades、在屏幕加升级面板）。本 mod 的
 * {@code MePatternProviderLogic} 正是 {@code PatternProviderLogic} 的子类、方块实体也实现了
 * {@code PatternProviderLogicHost}，所以升级槽本来就会出现，缺的只是「登记进支持表」。
 *
 * <h2>为什么用扫描而不是写死卡片清单</h2>
 * 大型整合包里的附加 mod 远不止测试客户端那几个，写死清单必然漏。这里反过来做：
 * <b>凡是原版样板供应器能用的卡，我们就自动跟着支持</b>——判据就是「这张卡在 AE2 的静态表里
 * 对 {@code PATTERN_PROVIDER}（方块或部件）登记了 maxCount &gt; 0」，最大数量沿用原值。
 * 这样任何现有/将来新增的附加 mod 都自动兼容，只有明确不兼容的才进 {@link #EXCLUDED} 黑名单。
 *
 * <p>刻意的边界：只对齐<b>原版</b>供应器（{@code AEBlocks/AEParts.PATTERN_PROVIDER}），
 * 不跟 ExtendedAE 扩展供应器（{@code EX_PATTERN_PROVIDER}）。后者独有的「超级扩展卡」是
 * 「解锁更多样板页」的意思，需要本仓自己实现分页与动态样板槽，属于独立功能。
 *
 * <p>必须在 {@code FMLLoadCompleteEvent} 里跑：其它 mod 都在 {@code FMLCommonSetupEvent} 里登记，
 * 而 NeoForge 的 {@code CommonModLoader.begin} 保证 LoadComplete 是<b>之后</b>才派发的。
 */
public final class MePatternProviderUpgrades {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 卡片提示里把它们归到同一组「样板供应器」，与原版供应器共用一条 tooltip（附加 mod 的惯例）。 */
    private static final String PATTERN_PROVIDER_GROUP = "group.pattern_provider.name";

    /**
     * 明确剔除的卡片（{@code modId:path}）。不登记 = 插不进来，
     * 对方 mod 读取升级槽得到的「已装该卡」状态也就永远不会被打开。
     *
     * <ul>
     *   <li>{@code extendedae_plus:virtual_crafting_card} —— 虚拟合成卡：让供应仓走「虚拟合成」路径，
     *       与本仓「材料推进自身库存、由处理阵列实际制作」的机制冲突。</li>
     * </ul>
     */
    private static final List<ResourceLocation> EXCLUDED = List.of(
            ResourceLocation.fromNamespaceAndPath("extendedae_plus", "virtual_crafting_card")
    );

    private MePatternProviderUpgrades() {
    }

    public static void register() {
        List<Item> hatches = hatchItems();
        if (hatches.isEmpty()) {
            return;
        }

        // 黑名单按物品实例比对（mod 没装时注册表查不到，自然也不会命中）
        Set<Item> excluded = new HashSet<>(EXCLUDED.size());
        for (ResourceLocation id : EXCLUDED) {
            Item item = BuiltInRegistries.ITEM.get(id);
            if (item != Items.AIR) {
                excluded.add(item);
                LOGGER.info("升级支持：刻意剔除 {}", id);
            }
        }

        // 扫描全物品表，挑出「对原版样板供应器登记过升级」的卡片
        Map<Item, Integer> cards = new LinkedHashMap<>();
        for (Item card : BuiltInRegistries.ITEM) {
            if (card == Items.AIR || excluded.contains(card)) {
                continue;
            }
            int max = Math.max(
                    Upgrades.getMaxInstallable(card, AEBlocks.PATTERN_PROVIDER),
                    Upgrades.getMaxInstallable(card, AEParts.PATTERN_PROVIDER));
            if (max > 0) {
                cards.put(card, max);
            }
        }

        if (cards.isEmpty()) {
            LOGGER.info("升级支持：当前没有找到任何「原版供应器可用」的升级卡，跳过登记");
            return;
        }

        List<String> names = new ArrayList<>(cards.size());
        for (Map.Entry<Item, Integer> entry : cards.entrySet()) {
            for (Item hatch : hatches) {
                Upgrades.add(entry.getKey(), hatch, entry.getValue(), PATTERN_PROVIDER_GROUP);
            }
            names.add(BuiltInRegistries.ITEM.getKey(entry.getKey()) + "(max " + entry.getValue() + ")");
        }
        LOGGER.info("升级支持：已把 {} 张原版供应器可用的升级卡嫁接到 {} 个供应仓 —— {}",
                cards.size(), hatches.size(), String.join(", ", names));

        // ExtendedAE-Plus 的扩容卡只对扩展仓有意义（增加样板页数），因此只登记给扩展仓。
        // 它本身是登记给 EAE 扩展供应器的卡，不在上面那张「原版供应器可用」的表里。
        Item extendedHatch = extendedHatchItem();
        Item expansionCard = BuiltInRegistries.ITEM.get(MePatternProviderLogic.EXPANSION_CARD_ID);
        if (extendedHatch != null && expansionCard != Items.AIR) {
            Upgrades.add(expansionCard, extendedHatch, MePatternProviderLogic.MAX_EXPANSION_CARDS,
                    PATTERN_PROVIDER_GROUP);
            LOGGER.info("升级支持：扩容卡 {} 已登记给扩展仓（max {}，每张 +1 页 36 槽）",
                    MePatternProviderLogic.EXPANSION_CARD_ID, MePatternProviderLogic.MAX_EXPANSION_CARDS);
        }
    }

    /** 本 mod 已注册的供应仓方块物品（扩展仓在无 ExtendedAE 时为 null）。 */
    private static List<Item> hatchItems() {
        List<Item> items = new ArrayList<>(2);
        if (ModHatches.ME_PATTERN_PROVIDER_BLOCK != null) {
            items.add(ModHatches.ME_PATTERN_PROVIDER_BLOCK.blockDefinition().asItem());
        }
        if (ModHatches.ME_EXTENDED_PATTERN_PROVIDER_BLOCK != null) {
            items.add(ModHatches.ME_EXTENDED_PATTERN_PROVIDER_BLOCK.blockDefinition().asItem());
        }
        return items;
    }

    /** 扩展仓的方块物品；未注册（无 ExtendedAE）时返回 null。 */
    @Nullable
    private static Item extendedHatchItem() {
        return ModHatches.ME_EXTENDED_PATTERN_PROVIDER_BLOCK == null
                ? null
                : ModHatches.ME_EXTENDED_PATTERN_PROVIDER_BLOCK.blockDefinition().asItem();
    }
}
