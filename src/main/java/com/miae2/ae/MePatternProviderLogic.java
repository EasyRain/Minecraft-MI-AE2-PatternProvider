package com.miae2.ae;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import aztech.modern_industrialization.inventory.ConfigurableFluidStack;
import aztech.modern_industrialization.inventory.ConfigurableItemStack;
import java.util.HashSet;
import java.util.Set;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.helpers.patternprovider.UnlockCraftingEvent;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import org.slf4j.Logger;
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
        // 原版「锁定合成模式」（LOCK_WHILE_LOW / LOCK_WHILE_HIGH / LOCK_UNTIL_PULSE / LOCK_UNTIL_RESULT）：
        // AE2 基类就是在这一句上拦的。解锁侧不用我们管 —— 红石由基类 tick 的 updateRedstoneState() 处理，
        // 「直到产物返回网络」由 PatternProviderReturnInventory 的回调处理（我们的产物正是经 returnInv 回网）。
        if (this.getCraftingLockedReason() != LockCraftingMode.NONE) {
            return false;
        }
        // 原版「阻挡模式」：本仓输入表里已经存有样板输入时就不再接收，等机器消耗完再发下一份。
        // 判据对齐 AE2 基类那句 `!this.isBlocking() || !adapter.containsPatternInput(this.patternInputs)`
        // —— 的"目标"在我们这里就是本仓自己的输入表。
        if (this.isBlocking() && this.miae2$inputTableHasAnyPatternInput()) {
            return false;
        }
        if (!this.be.pushPattern(patternDetails, inputHolder)) {
            return false;
        }
        // 复刻 AE2 私有的 onPushPatternSuccess()：把解锁事件挂上。不这么做的话，
        // LOCK_UNTIL_PULSE / LOCK_UNTIL_RESULT 永远不会进入"已锁定"状态
        // （getCraftingLockedReason 只看 unlockEvent），等于这两个模式没用。
        this.miae2$onPushPatternSuccess(patternDetails);
        return true;
    }

    // ---------- 复刻 AE2 的私有钩子（我们覆写了 pushPattern，拿不到基类的 private 方法） ----------

    private static final Logger LOGGER = LogUtils.getLogger();

    private static Field aeUnlockEvent;
    private static Field aeUnlockStack;
    private static Field aeRedstoneState;
    private static Method aeGetRedstoneState;
    private static boolean aePrivateResolved;

    private static void miae2$resolveAePrivate() {
        if (aePrivateResolved) {
            return;
        }
        aePrivateResolved = true;
        try {
            aeUnlockEvent = PatternProviderLogic.class.getDeclaredField("unlockEvent");
            aeUnlockEvent.setAccessible(true);
            aeUnlockStack = PatternProviderLogic.class.getDeclaredField("unlockStack");
            aeUnlockStack.setAccessible(true);
            aeRedstoneState = PatternProviderLogic.class.getDeclaredField("redstoneState");
            aeRedstoneState.setAccessible(true);
            aeGetRedstoneState = PatternProviderLogic.class.getDeclaredMethod("getRedstoneState");
            aeGetRedstoneState.setAccessible(true);
        } catch (Throwable t) {
            LOGGER.warn("无法解析 AE2 PatternProviderLogic 的私有锁定状态，锁定合成模式将不可用", t);
        }
    }

    /**
     * 与 AE2 {@code PatternProviderLogic#onPushPatternSuccess} 逐行对应（原方法 private，只能自己来）：
     *
     * <pre>
     * resetCraftingLock();
     * switch (LOCK_CRAFTING_MODE) {
     *   LOCK_UNTIL_PULSE  → unlockEvent = 有红石 ? REDSTONE_PULSE : REDSTONE_POWER；redstoneState = UNDECIDED
     *   LOCK_UNTIL_RESULT → unlockEvent = RESULT；unlockStack = 样板主产物
     * }
     * </pre>
     */
    private void miae2$onPushPatternSuccess(IPatternDetails pattern) {
        this.resetCraftingLock();
        LockCraftingMode mode = this.getConfigManager().getSetting(Settings.LOCK_CRAFTING_MODE);
        if (mode != LockCraftingMode.LOCK_UNTIL_PULSE && mode != LockCraftingMode.LOCK_UNTIL_RESULT) {
            return;
        }
        miae2$resolveAePrivate();
        try {
            if (mode == LockCraftingMode.LOCK_UNTIL_PULSE) {
                boolean powered = aeGetRedstoneState != null
                        && Boolean.TRUE.equals(aeGetRedstoneState.invoke(this));
                if (aeUnlockEvent != null) {
                    aeUnlockEvent.set(this, powered
                            ? UnlockCraftingEvent.REDSTONE_PULSE : UnlockCraftingEvent.REDSTONE_POWER);
                }
                if (aeRedstoneState != null) {
                    aeRedstoneState.set(this, YesNo.UNDECIDED);
                }
            } else {
                if (aeUnlockEvent != null) {
                    aeUnlockEvent.set(this, UnlockCraftingEvent.RESULT);
                }
                if (aeUnlockStack != null) {
                    aeUnlockStack.set(this, pattern.getPrimaryOutput());
                }
            }
            this.saveChanges();
        } catch (Throwable t) {
            LOGGER.warn("挂载 AE2 锁定合成事件失败（模式 {}）", mode, t);
        }
    }

    /**
     * 原版阻挡模式的判据：本仓输入表里是否已经有本供应器**任一**样板的输入。
     *
     * <p>语义严格对齐 AE2 的 {@code PatternProviderTarget#containsPatternInput(patternInputs)} ——
     * 它的入参 {@code this.patternInputs} 是"本供应器所有样板输入键的并集"，判定是"目标里是否存在其中
     * 任意一个键"，即"这仓是不是正拿着料"。
     *
     * <p>⚠️ 这里刻意**只做原版语义**、不复刻 EAEP 的「智能阻挡」：EAEP 是用
     * {@code @WrapOperation} 改写 AE2 <b>基类</b> {@code pushPattern} 里那句
     * {@code containsPatternInput} 调用来实现"同配方不阻挡"的；而我们的 {@code pushPattern}
     * 不调 {@code super}，那条 mixin 天然轮不到 —— 于是「原版阻挡可用、EAEP 智能阻挡被屏蔽」。
     */
    private boolean miae2$inputTableHasAnyPatternInput() {
        Set<AEKey> patternInputs = new HashSet<>();
        for (IPatternDetails details : this.getAvailablePatterns()) {
            for (IPatternDetails.IInput input : details.getInputs()) {
                for (GenericStack candidate : input.getPossibleInputs()) {
                    patternInputs.add(candidate.what());
                }
            }
        }
        if (patternInputs.isEmpty()) {
            return false;
        }
        for (ConfigurableItemStack stack : this.be.miae2$itemInputs()) {
            if (stack.isEmpty()) {
                continue;
            }
            AEKey key = AEItemKey.of(stack.getResource().getItem());
            if (patternInputs.contains(key.dropSecondary())) {
                return true;
            }
        }
        for (ConfigurableFluidStack stack : this.be.miae2$fluidInputs()) {
            if (stack.isEmpty()) {
                continue;
            }
            AEKey key = AEFluidKey.of(stack.getResource().getFluid());
            if (patternInputs.contains(key.dropSecondary())) {
                return true;
            }
        }
        return false;
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
