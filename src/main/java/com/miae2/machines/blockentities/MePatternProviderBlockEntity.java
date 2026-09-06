package com.miae2.machines.blockentities;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import appeng.helpers.patternprovider.PatternProviderTarget;
import appeng.menu.MenuOpener;
import appeng.menu.implementations.PatternProviderMenu;
import appeng.menu.locator.MenuLocators;
import aztech.modern_industrialization.inventory.AbstractConfigurableStack;
import aztech.modern_industrialization.inventory.ConfigurableFluidStack;
import aztech.modern_industrialization.inventory.ConfigurableItemStack;
import aztech.modern_industrialization.inventory.MIInventory;
import aztech.modern_industrialization.inventory.SlotPositions;
import aztech.modern_industrialization.machines.BEP;
import aztech.modern_industrialization.machines.MachineComponent;
import aztech.modern_industrialization.machines.components.OrientationComponent;
import aztech.modern_industrialization.machines.gui.MachineGuiParameters;
import aztech.modern_industrialization.machines.multiblocks.HatchBlockEntity;
import aztech.modern_industrialization.machines.multiblocks.HatchType;
import com.miae2.ae.MePatternProviderLogic;
import com.miae2.machines.init.ModHatches;
import com.miae2.mixin.ProcessingArrayBlockEntityAccessor;
import com.miae2.util.IControllerPosHolder;
import com.miae2.util.IUnboundedItemAccessor;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.swedz.extended_industrialization.machines.blockentity.multiblock.ProcessingArrayBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * ME 样板供应仓：单方块同时承担物品输入/输出 + 流体输入/输出四种接口，并且是 AE2 网格节点 +
 * 样板供应器。合成请求把输入推进自身仓库存，成品从自身仓库存抽回 ME 网络。
 */
public class MePatternProviderBlockEntity extends HatchBlockEntity
        implements IInWorldGridNodeHost, IGridNodeListener<MePatternProviderBlockEntity>, PatternProviderLogicHost, IActionHost, Nameable, IControllerPosHolder {

    private final List<ConfigurableItemStack> itemInputs;
    private final List<ConfigurableItemStack> itemOutputs;
    private final List<ConfigurableFluidStack> fluidInputs;
    private final List<ConfigurableFluidStack> fluidOutputs;
    private final MIInventory inventory;

    private final IManagedGridNode mainNode;
    private final MePatternProviderLogic logic;

    // 当前正在处理（累计发配材料中 / 机器制作中）的样板；null 表示空闲
    private IPatternDetails activePattern;

    // 多方块控制器（处理阵列）的位置，由 ShapeMatcherMixin 在成形时写入
    private BlockPos controllerPos;

    public MePatternProviderBlockEntity(
            BEP bep,
            MachineGuiParameters guiParams,
            int itemInSlots,
            int itemOutSlots,
            int fluidInSlots,
            int fluidOutSlots,
            long fluidCapacity
    ) {
        super(bep, guiParams, OrientationComponent.Params.noFacingNoOutput());

        this.itemInputs = new ArrayList<>();
        this.itemOutputs = new ArrayList<>();
        this.fluidInputs = new ArrayList<>();
        this.fluidOutputs = new ArrayList<>();

        for (int i = 0; i < itemInSlots; i++) {
            this.itemInputs.add(unboundedItemInputSlot());
        }
        for (int i = 0; i < itemOutSlots; i++) {
            this.itemOutputs.add(unboundedItemOutputSlot());
        }
        for (int i = 0; i < fluidInSlots; i++) {
            this.fluidInputs.add(ConfigurableFluidStack.standardInputSlot(fluidCapacity));
        }
        for (int i = 0; i < fluidOutSlots; i++) {
            this.fluidOutputs.add(ConfigurableFluidStack.standardOutputSlot(fluidCapacity));
        }

        List<ConfigurableItemStack> allItems = new ArrayList<>(this.itemInputs);
        allItems.addAll(this.itemOutputs);
        List<ConfigurableFluidStack> allFluids = new ArrayList<>(this.fluidInputs);
        allFluids.addAll(this.fluidOutputs);

        SlotPositions itemPositions = new SlotPositions.Builder()
                .addSlots(8, 20, 9, rowsOf(itemInSlots))
                .addSlots(8, 56, 9, rowsOf(itemOutSlots))
                .build();
        SlotPositions fluidPositions = new SlotPositions.Builder()
                .addSlots(8, 38, 9, rowsOf(fluidInSlots))
                .addSlots(8, 74, 9, rowsOf(fluidOutSlots))
                .build();

        this.inventory = new MIInventory(allItems, allFluids, itemPositions, fluidPositions);
        this.registerComponents(this.inventory);

        // AE2 节点与样板供应器逻辑
        this.mainNode = GridHelper.createManagedNode(this, this)
                .setVisualRepresentation(ModHatches.ME_PATTERN_PROVIDER_BLOCK.blockDefinition().asItem())
                .setInWorldNode(true)
                .setTagName("me_pattern_provider");
        this.logic = new MePatternProviderLogic(this.mainNode, this, this);
        this.registerComponents(new LogicComponent());
    }

    // ---------- AE2 网格节点生命周期 ----------

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        GridHelper.onFirstTick(this, be -> this.mainNode.create(be.getLevel(), be.getBlockPos()));
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        this.mainNode.destroy();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        this.mainNode.destroy();
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level != null && !this.level.isClientSide()) {
            this.drainOutputsToReturnInv();
            this.updateActivePattern();
        }
    }

    // ---------- IInWorldGridNodeHost ----------

    @Nullable
    @Override
    public IGridNode getGridNode(Direction dir) {
        return this.mainNode.getNode();
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        return AECableType.SMART;
    }

    // ---------- IActionHost ----------

    @Override
    public IGridNode getActionableNode() {
        return this.mainNode.getNode();
    }

    // ---------- IGridNodeListener ----------

    @Override
    public void onSaveChanges(MePatternProviderBlockEntity nodeOwner, IGridNode node) {
        this.setChanged();
    }

    @Override
    public void onGridChanged(MePatternProviderBlockEntity nodeOwner, IGridNode node) {
        this.setChanged();
    }

    @Override
    public void onStateChanged(MePatternProviderBlockEntity nodeOwner, IGridNode node, State state) {
        this.setChanged();
    }

    // ---------- PatternProviderLogicHost ----------

    @NotNull
    @Override
    public MePatternProviderLogic getLogic() {
        return this.logic;
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity() {
        return this;
    }

    @Override
    public EnumSet<Direction> getTargets() {
        // 自身库存即 push 目标，无相邻目标
        return EnumSet.noneOf(Direction.class);
    }

    @Override
    public void saveChanges() {
        this.setChanged();
    }

    @Override
    public AEItemKey getTerminalIcon() {
        return AEItemKey.of(ModHatches.ME_PATTERN_PROVIDER_BLOCK.blockDefinition().asItem());
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(ModHatches.ME_PATTERN_PROVIDER_BLOCK.blockDefinition().asItem());
    }

    /** 右键打开 AE2 的样板供应器界面（放样板），而不是 MI 的槽位界面。 */
    @Override
    public void openMenu(ServerPlayer player) {
        MenuOpener.open(PatternProviderMenu.TYPE, player, MenuLocators.forBlockEntity(this));
    }

    /** 手持任意扳手右键：打开 MI 原生槽位界面（用于手动取出卡住的物品/流体）。 */
    @Override
    public boolean useWrench(Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer) {
            super.openMenu(serverPlayer);
        }
        return true;
    }

    /** 挖掘掉落：额外掉落 AE2 样板、返回区物品与在途输出（否则这些会直接消失）。 */
    @Override
    public List<ItemStack> dropExtra() {
        List<ItemStack> drops = super.dropExtra();
        this.logic.addDrops(drops);
        return drops;
    }

    // ---------- Nameable / 自动命名 ----------

    @Override
    public Component getName() {
        // 方块本身名称（"ME样板供应仓"），避免 GUI 标题落到 AE2 默认的"样板供应器"。
        return this.getDisplayName();
    }

    @Override
    public Component getCustomName() {
        // hasCustomName() 因此恒为 true，AE2 的 GUI 标题（getDefaultMenuTitle）会落到这里。
        return this.getDisplayName();
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        // 终端里保留带工作方块名的完整命名：xxx处理阵列样板供应仓。
        return new PatternContainerGroup(this.getTerminalIcon(), this.computeAutoName(), List.of());
    }

    @Override
    public void miae2$setControllerPos(BlockPos pos) {
        this.controllerPos = pos;
    }

    /** 根据控制器（处理阵列）里放的工作方块自动命名。 */
    private Component computeAutoName() {
        ProcessingArrayBlockEntity controller = this.findController();
        if (controller != null) {
            ItemStack machines = ((ProcessingArrayBlockEntityAccessor) (Object) controller).getMachinesComponent().getMachines();
            if (!machines.isEmpty()) {
                Component workBlockName = machines.getHoverName();
                if (machines.has(DataComponents.CUSTOM_NAME)) {
                    return workBlockName;
                }
                return workBlockName.copy().append(Component.translatable("text.mi_ae2_pattern_provider.processing_array_suffix"));
            }
        }
        return Component.translatable("text.mi_ae2_pattern_provider.hatch_name");
    }

    private ProcessingArrayBlockEntity findController() {
        if (this.controllerPos == null || this.level == null) {
            return null;
        }
        BlockEntity be = this.level.getBlockEntity(this.controllerPos);
        return be instanceof ProcessingArrayBlockEntity pa ? pa : null;
    }

    // ---------- 样板 push/pull ----------

    /** 把样板的输入（物品+流体）推进自身输入槽。返回是否全部推进成功。 */
    public boolean insertPatternInputs(KeyCounter[] inputHolder, IActionSource src) {
        Level level = this.getLevel();
        if (level == null) {
            return false;
        }
        PatternProviderTarget target = PatternProviderTarget.get(level, this.getBlockPos(), this, Direction.NORTH, src);
        if (target == null) {
            return false;
        }

        // 先模拟，确认所有输入都能被接纳
        for (KeyCounter counter : inputHolder) {
            for (it.unimi.dsi.fastutil.objects.Object2LongMap.Entry<AEKey> entry : counter) {
                long inserted = target.insert(entry.getKey(), entry.getLongValue(), Actionable.SIMULATE);
                if (inserted <= 0L) {
                    return false;
                }
            }
        }

        // 全部可接纳，实际推进
        for (KeyCounter counter : inputHolder) {
            for (it.unimi.dsi.fastutil.objects.Object2LongMap.Entry<AEKey> entry : counter) {
                target.insert(entry.getKey(), entry.getLongValue(), Actionable.MODULATE);
            }
        }
        return true;
    }

    /** 把自身输出槽里的成品搬进 returnInv，由 PatternProviderLogic 的 doWork 回注网络。 */
    private void drainOutputsToReturnInv() {
        PatternProviderReturnInventory returnInv = this.logic.getReturnInv();
        IActionSource src = IActionSource.ofMachine(this);

        for (ConfigurableItemStack stack : this.itemOutputs) {
            if (stack.isEmpty()) {
                continue;
            }
            AEItemKey key = AEItemKey.of(stack.getResource().getItem());
            long amount = stack.getAmount();
            long inserted = returnInv.insert(key, amount, Actionable.MODULATE, src);
            if (inserted > 0L) {
                stack.decrement(inserted);
            }
        }

        for (ConfigurableFluidStack stack : this.fluidOutputs) {
            if (stack.isEmpty()) {
                continue;
            }
            AEFluidKey key = AEFluidKey.of(stack.getResource().getFluid());
            long amount = stack.getAmount();
            long inserted = returnInv.insert(key, amount, Actionable.MODULATE, src);
            if (inserted > 0L) {
                stack.decrement(inserted);
            }
        }
    }

    /** 把输出格锁定为样板的产物、没用到的格锁为空（让处理阵列走确定性配方匹配）。 */
    public void lockOutputs(IPatternDetails patternDetails) {
        this.unlockAllOutputs();

        for (GenericStack output : patternDetails.getOutputs()) {
            if (output.what() instanceof AEItemKey itemKey) {
                AbstractConfigurableStack.playerLockNoOverride(itemKey.getItem(), output.amount(), this.itemOutputs);
            } else if (output.what() instanceof AEFluidKey fluidKey) {
                AbstractConfigurableStack.playerLockNoOverride(fluidKey.getFluid(), output.amount(), this.fluidOutputs);
            }
        }

        this.lockAllEmpty(this.itemOutputs);
        this.lockAllEmpty(this.fluidOutputs);
    }

    private void unlockAllOutputs() {
        for (ConfigurableItemStack stack : this.itemOutputs) {
            if (stack.isPlayerLocked()) {
                stack.togglePlayerLock();
            }
        }
        for (ConfigurableFluidStack stack : this.fluidOutputs) {
            if (stack.isPlayerLocked()) {
                stack.togglePlayerLock();
            }
        }
    }

    private static void lockAllEmpty(List<? extends AbstractConfigurableStack<?, ?>> stacks) {
        for (AbstractConfigurableStack<?, ?> stack : stacks) {
            if (stack.isEmpty() && !stack.isPlayerLocked()) {
                stack.togglePlayerLock();
            }
        }
    }

    /** 把尚未被阵列消耗的输入（仍在输入槽里的）导回 ME 网络。 */
    private void returnInputsToNetwork() {
        IGrid grid = this.logic.getGrid();
        if (grid == null) {
            return;
        }
        MEStorage networkInv = grid.getStorageService().getInventory();
        if (networkInv == null) {
            return;
        }
        IActionSource src = IActionSource.ofMachine(this);

        for (ConfigurableItemStack stack : this.itemInputs) {
            if (stack.isEmpty()) {
                continue;
            }
            AEItemKey key = AEItemKey.of(stack.getResource().getItem());
            long amount = stack.getAmount();
            long inserted = networkInv.insert(key, amount, Actionable.MODULATE, src);
            if (inserted > 0L) {
                stack.decrement(inserted);
            }
        }
        for (ConfigurableFluidStack stack : this.fluidInputs) {
            if (stack.isEmpty()) {
                continue;
            }
            AEFluidKey key = AEFluidKey.of(stack.getResource().getFluid());
            long amount = stack.getAmount();
            long inserted = networkInv.insert(key, amount, Actionable.MODULATE, src);
            if (inserted > 0L) {
                stack.decrement(inserted);
            }
        }
    }

    /** push 入口（供 MePatternProviderLogic 调用）：同一样板累计发配，不同样板排队。 */
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        if (this.activePattern != null && this.activePattern != patternDetails) {
            return false;
        }
        if (!this.insertPatternInputs(inputHolder, IActionSource.ofMachine(this))) {
            return false;
        }
        if (this.activePattern == null) {
            this.activePattern = patternDetails;
            this.lockOutputs(patternDetails);
        }
        return true;
    }

    /** 每个 tick 检查：机器空闲且任务已结束（完成或取消）时，回收残留材料并解锁。 */
    private void updateActivePattern() {
        if (this.activePattern == null) {
            return;
        }
        boolean isCrafting = this.isMachineCrafting();
        boolean requested = this.isPatternRequested();
        if (!isCrafting && !requested) {
            // 机器没在制作 && 网格已不再请求本样板的产物 → 解锁 + 回收未处理材料
            this.returnInputsToNetwork();
            this.activePattern = null;
            this.unlockAllOutputs();
        }
    }

    /** 处理阵列的合成器在制作期间会给输出槽打机器锁，这里用「有机器锁」判断正在制作。 */
    private boolean isMachineCrafting() {
        for (ConfigurableItemStack stack : this.itemOutputs) {
            if (stack.isMachineLocked()) {
                return true;
            }
        }
        for (ConfigurableFluidStack stack : this.fluidOutputs) {
            if (stack.isMachineLocked()) {
                return true;
            }
        }
        return false;
    }

    /** 网格是否仍在请求本样板的产物（用于区分「正常排队」与「任务已取消」）。 */
    private boolean isPatternRequested() {
        IGrid grid = this.logic.getGrid();
        if (grid == null) {
            return false;
        }
        for (GenericStack output : this.activePattern.getOutputs()) {
            if (grid.getCraftingService().getRequestedAmount(output.what()) > 0L) {
                return true;
            }
        }
        return false;
    }

    // ---------- MI 仓接口 ----------

    @NotNull
    @Override
    public HatchType getHatchType() {
        return ModHatches.ME_PATTERN_PROVIDER;
    }

    @Override
    public boolean upgradesToSteel() {
        return false;
    }

    @NotNull
    @Override
    public MIInventory getInventory() {
        return this.inventory;
    }

    @Override
    public void appendItemInputs(List<ConfigurableItemStack> list) {
        list.addAll(this.itemInputs);
    }

    @Override
    public void appendItemOutputs(List<ConfigurableItemStack> list) {
        list.addAll(this.itemOutputs);
    }

    @Override
    public void appendFluidInputs(List<ConfigurableFluidStack> list) {
        list.addAll(this.fluidInputs);
    }

    @Override
    public void appendFluidOutputs(List<ConfigurableFluidStack> list) {
        list.addAll(this.fluidOutputs);
    }

    // ---------- 工具 ----------

    private static int rowsOf(int slots) {
        return Math.max(1, (slots + 8) / 9);
    }

    private static ConfigurableItemStack unboundedItemInputSlot() {
        ConfigurableItemStack stack = ConfigurableItemStack.standardInputSlot();
        ((IUnboundedItemAccessor) stack).miae2$setUnbounded(true);
        return stack;
    }

    private static ConfigurableItemStack unboundedItemOutputSlot() {
        ConfigurableItemStack stack = ConfigurableItemStack.standardOutputSlot();
        ((IUnboundedItemAccessor) stack).miae2$setUnbounded(true);
        return stack;
    }

    /** 把 PatternProviderLogic 的 NBT 读写挂到 MI 的组件系统上（saveAdditional/loadAdditional 是 final）。 */
    private final class LogicComponent implements MachineComponent {
        @Override
        public void writeNbt(CompoundTag tag, Provider registries) {
            MePatternProviderBlockEntity.this.logic.writeToNBT(tag, registries);
        }

        @Override
        public void readNbt(CompoundTag tag, Provider registries, boolean isUpgradingMachine) {
            MePatternProviderBlockEntity.this.logic.readFromNBT(tag, registries);
        }
    }
}
