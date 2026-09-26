package com.miae2.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.KeyCounter;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.miae2.machines.blockentities.MePatternProviderBlockEntity;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * 基于原版 PatternProviderLogic，把 push 目标从「相邻方块」改为「自身仓库存」，
 * 排队/解锁由 MePatternProviderBlockEntity 的 activePattern 状态机控制（不强制 LOCK_UNTIL_RESULT，
 * 这样同一任务的材料可以一次性累计发配完，而不是做完一份才发下一份）。
 *
 * <p>另外负责扩展仓的「样板分页」：底层库存按 4 页（144）预留，对外只暴露已解锁的页数。
 */
public class MePatternProviderLogic extends PatternProviderLogic {

    /** 每页 36 个样板槽（与 ExtendedAE / ExtendedAE-Plus 的约定一致）。 */
    private static final int SLOTS_PER_PAGE = 36;
    /** ExtendedAE-Plus 扩容卡每台机器最多 3 张（对齐它给扩展供应器的登记值）。 */
    static final int MAX_EXPANSION_CARDS = 3;

    static final ResourceLocation EXPANSION_CARD_ID = ResourceLocation.fromNamespaceAndPath(
            "extendedae_plus", "extended_pattern_provider_expansion_card_plus");

    /**
     * ExtendedAE-Plus 在 appflux 缺席时把自己那份升级库存挂在逻辑对象上
     * （mixin 加的方法）。这里用反射取，**避免编译期依赖 EAEP**，它改版/删功能都只是取不到。
     */
    private static Method eaepCompatUpgrades;
    private static boolean eaepCompatUpgradesResolved;

    private final MePatternProviderBlockEntity be;

    /** clamp 视图缓存：EAEP 自己已经在管时（size 已等于 cap）根本不创建，保持它的对象身份。 */
    @Nullable
    private InternalInventory cappedPatternInv;

    public MePatternProviderLogic(IManagedGridNode mainNode, PatternProviderLogicHost host, MePatternProviderBlockEntity be, int patternInventorySize) {
        super(mainNode, host, patternInventorySize);
        this.be = be;
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        if (this.getGrid() == null || !this.miae2$isAcceptablePattern(patternDetails)) {
            return false;
        }
        return this.be.pushPattern(patternDetails, inputHolder);
    }

    /**
     * 样板是否可接受。
     *
     * <p>AE2 原版只接受 {@code patterns} 列表里的实例（{@code patterns.contains(details)}）。
     * 但 ExtendedAE-Plus 的「智能翻倍」在 AE2 的合成模拟阶段会推一个**包装过的缩放样板**
     * （{@code ScaledProcessingPattern}；它的 {@code equals} 把倍率也算进去，所以必然不在列表里）。
     *
     * <p>EAEP 自己的适配是 {@code PatternProviderLogicContainsModifyMixin}：用 {@code @WrapOperation}
     * 改写 AE2 {@code pushPattern} 里那句 {@code List.contains}，遇到缩放样板就用它的
     * {@code getOriginal()} **退回原始样板**去匹配。
     *
     * <p>⚠️ 我们覆写了 {@code pushPattern} 且从不调 {@code super} —— 那条 mixin 就完全管不到我们，
     * 缩放样板会被原版那句判断直接拒掉。表现出来正是用户报的
     * **「打开智能翻倍 → 下单不发配材料；关掉就正常」**。所以这里必须自己复刻同样的放行规则。
     *
     * <p>EAEP 不是编译期依赖，故用反射读 {@code getOriginal()}
     * （对 {@code ScaledProcessingPattern} / {@code ScaledMolecularAssemblerPattern} 都适用）。
     */
    private boolean miae2$isAcceptablePattern(IPatternDetails patternDetails) {
        List<IPatternDetails> available = this.getAvailablePatterns();
        if (available.contains(patternDetails)) {
            return true;
        }
        IPatternDetails original = miae2$unwrapScaledPattern(patternDetails);
        return original != null && available.contains(original);
    }

    /** 缩放样板包装 → 包装前的原始样板；不是包装则返回 null。 */
    @Nullable
    private static IPatternDetails miae2$unwrapScaledPattern(IPatternDetails patternDetails) {
        try {
            Object original = patternDetails.getClass().getMethod("getOriginal").invoke(patternDetails);
            return original instanceof IPatternDetails unwrapped && unwrapped != patternDetails ? unwrapped : null;
        } catch (Throwable ignored) {
            return null; // 不是缩放样板（或 EAEP 改了实现）→ 老老实实走原版判断
        }
    }

    /**
     * 扩展仓：底层样板库存预留 4 页，但对外只暴露「1 + 已装扩容卡数」页。
     *
     * <p>这样即使 ExtendedAE-Plus 缺席（或它以后改了内部实现），也不会把 144 个空槽一次性摊到界面上——
     * 拿不到升级槽信息时保守地只开 1 页（36），即「功能不可用但一切正常」。
     */
    @Override
    public InternalInventory getPatternInv() {
        InternalInventory base = super.getPatternInv();
        // be 在 super(...) 构造期间还是 null，此处必须守护
        if (this.be == null || !this.be.isExtended()) {
            return base;
        }

        int cap = this.miae2$getUnlockedSlots();
        if (base.size() <= cap) {
            // ExtendedAE-Plus 的动态视图已经在生效（或本来就不超过上限），原样返回
            return base;
        }
        if (this.cappedPatternInv == null || this.cappedPatternInv.size() != cap) {
            this.cappedPatternInv = new CappedInternalInventory(base, cap);
        }
        return this.cappedPatternInv;
    }

    /** 已解锁的样板槽数 = （1 + 扩容卡数）× 36；取不到升级槽时只开 1 页。 */
    private int miae2$getUnlockedSlots() {
        IUpgradeInventory upgrades = this.miae2$findUpgrades();
        if (upgrades == null) {
            return SLOTS_PER_PAGE;
        }
        Item card = BuiltInRegistries.ITEM.get(EXPANSION_CARD_ID);
        if (card == Items.AIR) {
            return SLOTS_PER_PAGE;
        }

        int cards = 0;
        for (ItemStack stack : upgrades) {
            if (!stack.isEmpty() && stack.getItem() == card && ++cards >= MAX_EXPANSION_CARDS) {
                break;
            }
        }
        return (1 + cards) * SLOTS_PER_PAGE;
    }

    /** 找到本供应仓的升级库存：优先 appflux 提供的，其次 EAEP 自带的（反射，零编译依赖）。 */
    @Nullable
    private IUpgradeInventory miae2$findUpgrades() {
        try {
            // appflux 的 mixin 让 PatternProviderLogic 实现了 IUpgradeableObject
            if (this instanceof IUpgradeableObject upgradeable) {
                IUpgradeInventory inv = upgradeable.getUpgrades();
                if (inv != null && inv.size() > 0) {
                    return inv;
                }
            }
        } catch (Throwable ignored) {
            // 第三方 mixin 缺失/改版时静默降级
        }

        try {
            if (!eaepCompatUpgradesResolved) {
                eaepCompatUpgradesResolved = true;
                try {
                    eaepCompatUpgrades = this.getClass().getMethod("eap$getCompatUpgrades");
                } catch (NoSuchMethodException e) {
                    eaepCompatUpgrades = null;
                }
            }
            if (eaepCompatUpgrades != null) {
                Object result = eaepCompatUpgrades.invoke(this);
                if (result instanceof IUpgradeInventory inv && inv.size() > 0) {
                    return inv;
                }
            }
        } catch (Throwable ignored) {
            // EAEP 不在或改版 → 降级为 1 页
        }
        return null;
    }
}
