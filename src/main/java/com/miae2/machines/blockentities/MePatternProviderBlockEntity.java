package com.miae2.machines.blockentities;

import appeng.api.config.Actionable;
import appeng.api.AECapabilities;
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
import aztech.modern_industrialization.thirdparty.fabrictransfer.api.fluid.FluidVariant;
import aztech.modern_industrialization.thirdparty.fabrictransfer.api.item.ItemVariant;
import aztech.modern_industrialization.inventory.ConfigurableItemStack;
import aztech.modern_industrialization.inventory.MIInventory;
import aztech.modern_industrialization.inventory.SlotPositions;
import aztech.modern_industrialization.machines.BEP;
import aztech.modern_industrialization.machines.MachineComponent;
import aztech.modern_industrialization.machines.components.OrientationComponent;
import aztech.modern_industrialization.machines.gui.MachineGuiParameters;
import aztech.modern_industrialization.machines.multiblocks.HatchBlockEntity;
import aztech.modern_industrialization.machines.multiblocks.HatchType;
import com.miae2.ae.ExtendedAeMenuCompat;
import com.miae2.ae.MePatternProviderLogic;
import com.miae2.machines.init.ModHatches;
import com.miae2.mixin.ProcessingArrayBlockEntityAccessor;
import com.miae2.util.IControllerPosHolder;
import com.miae2.util.IUnboundedItemAccessor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.Nameable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.swedz.extended_industrialization.machines.blockentity.multiblock.ProcessingArrayBlockEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * ME 样板供应仓：单方块同时承担物品输入/输出 + 流体输入/输出四种接口，并且是 AE2 网格节点 +
 * 样板供应器。合成请求把输入推进自身仓库存，成品从自身仓库存抽回 ME 网络。
 */
public class MePatternProviderBlockEntity extends HatchBlockEntity
        implements IInWorldGridNodeHost, IGridNodeListener<MePatternProviderBlockEntity>, PatternProviderLogicHost, IActionHost, Nameable, IControllerPosHolder {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final List<ConfigurableItemStack> itemInputs;
    private final List<ConfigurableItemStack> itemOutputs;
    private final List<ConfigurableFluidStack> fluidInputs;
    private final List<ConfigurableFluidStack> fluidOutputs;
    private final MIInventory inventory;

    private final IManagedGridNode mainNode;
    private final MePatternProviderLogic logic;

    // 本仓的仓类型（普通 vs 扩展）与样板槽数量、方块图标
    private final HatchType hatchType;
    private final int patternSlots;
    private final boolean extended;
    private final Supplier<Item> iconSupplier;

    // 当前正在处理（累计发配材料中 / 机器制作中）的样板；null 表示空闲
    private IPatternDetails activePattern;

    /** 读档初始化只做一次（见 tick()）。 */
    private boolean loadInitDone;

    /** 「网络不再请求」需要连续这么多 tick 才判定任务结束（避开两次 push 之间的空隙）。 */
    private static final int IDLE_CLEANUP_TICKS = 40;

    /** 空闲计时（见 updateActivePattern()）。 */
    private int idleTicks;

    // 多方块控制器（处理阵列）的位置，由 ShapeMatcherMixin 在成形时写入
    private BlockPos controllerPos;

    // 阵列内「能源输入仓」的位置，由 ShapeMatcherMixin 在成形时写入；供感应卡定向供电
    private List<BlockPos> energyInputHatchPositions = List.of();

    public MePatternProviderBlockEntity(
            BEP bep,
            MachineGuiParameters guiParams,
            int itemInSlots,
            int itemOutSlots,
            int fluidInSlots,
            int fluidOutSlots,
            long fluidCapacity,
            HatchType hatchType,
            int patternSlots,
            boolean extended,
            Supplier<Item> iconSupplier
    ) {
        super(bep, guiParams, OrientationComponent.Params.noFacingNoOutput());
        this.hatchType = hatchType;
        this.patternSlots = patternSlots;
        this.extended = extended;
        this.iconSupplier = iconSupplier;

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
        this.miae2$rebindInventoryStacks();

        // AE2 节点与样板供应器逻辑
        this.mainNode = GridHelper.createManagedNode(this, this)
                .setVisualRepresentation(iconSupplier.get())
                .setInWorldNode(true)
                .setTagName("me_pattern_provider");
        this.logic = new MePatternProviderLogic(this.mainNode, this, this, patternSlots);
        this.registerComponents(new LogicComponent());
    }

    // ---------- AE2 网格节点生命周期 ----------

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        GridHelper.onFirstTick(this, be -> this.miae2$ensureNode());
    }

    /**
     * 确保 AE 网格节点已创建。
     *
     * <p>不只在 {@code GridHelper.onFirstTick} 里做（那次回调在某些加载/替换方块实体的场景下不触发，
     * 我们已经在读档初始化上踩过一次），{@code tick()} 每帧也会兜一次，创建成功后是个纯判断、几乎零开销。
     *
     * <p>创建成功后补一次 {@code miae2$restoreChannelCard()}：EAEP 的频道卡是在
     * {@code PatternProviderLogic.readFromNBT} 的 TAIL 就 {@code onLoaded()} 的，那时我们的节点还不存在，
     * 它内部的 {@code wakeNode} 会落空 → 链路再也建不起来（表现为「频道卡连接，重进存档后一直断开」，
     * 而电缆连接不受影响）。节点就绪后重新请它恢复一次即可。
     */
    private void miae2$ensureNode() {
        if (this.mainNode.getNode() != null || this.level == null) {
            return;
        }
        this.mainNode.create(this.level, this.getBlockPos());
        this.miae2$restoreChannelCard();
    }

    /** 反射请求 EAEP 的频道卡控制器重新建立无线链路；没装 EAEP 时静默跳过。 */
    private void miae2$restoreChannelCard() {
        try {
            Method getController = this.logic.getClass().getMethod("eap$getChannelCardController");
            Object controller = getController.invoke(this.logic);
            if (controller == null) {
                return;
            }
            controller.getClass().getMethod("onLoaded").invoke(controller);
            LOGGER.info("[节点] 网格节点就绪，已请求 EAEP 重新建立频道卡连接 @ {}", this.getBlockPos());
        } catch (NoSuchMethodException ignored) {
            // 未安装 ExtendedAE-Plus（或它没有频道卡功能）→ 正常
        } catch (Throwable t) {
            LOGGER.warn("[节点] 重建频道卡连接失败", t);
        }
    }

    /**
     * 读档 / 区块重载后的一次性初始化。
     *
     * <p>AE2 自己的供应器在 {@code PatternProviderBlockEntity#onReady()} 里做这些事，而我们的方块实体
     * 继承的是 MI 的 {@code HatchBlockEntity}，没有那个钩子，必须自己补——这两步缺失正是
     * 「重进存档后失效 / 阵列不工作 / 重放才好」那一组 bug 的根因。
     */
    private void miae2$onLoadInit() {
        // ⓪ MIInventory.readNbt 会**换掉**它库存里的那批 stack 对象（每读一次档换一批），
        //    所以每次读档后都必须重新绑定，否则我们手里的又变成旧对象 → 阵列与 GUI 各看一套。
        this.miae2$rebindInventoryStacks();

        // ① 把样板库存重建进 patterns 列表，并通知合成服务。没有这一步，读档后
        //    getAvailablePatterns() 是空的 —— 终端里看得见供应器与样板，但样板完全不参与合成。
        //    （手动把样板拿出来重插会触发 AE2 的库存变更回调，于是又「活」过来，正是这个原因。）
        ItemStack slot0 = this.logic.getPatternInv().getStackInSlot(0);
        this.logic.updatePatterns();
        LOGGER.debug("[读档初始化] 样板槽0={}，重建后 patterns={}",
                slot0.isEmpty() ? "空" : slot0.getItem(), this.logic.getAvailablePatterns().size());

        // ② 读档后**绝不能**去动输出格的玩家锁。
        //
        //    MI 的处理阵列靠 CrafterComponent#updateActiveRecipe 里的
        //    `boolean outputsLocked = this.areAllOutputSlotsLocked();` 来消除配方歧义：
        //    输出格**全被玩家锁住**时它遇到第一个匹配配方就 break（否则遇到重叠配方会设
        //    matchesMultipleRecipes 并直接放弃开工）。我们的 lockOutputs 正是靠「锁产物格 +
        //    lockAllEmpty 锁掉其余空格」来命中这个机制，让阵列走确定性配方匹配。
        //
        //    我上一版在这里调了 unlockAllOutputs()（想「等同于打掉重放」）——那是错的：
        //    把全锁解掉之后阵列重新看到多个重叠配方 → matchesMultipleRecipes → 拒绝开工，
        //    表现就是「材料发配到缓存里但阵列不工作，打掉重放才好」。玩家重放后重新下单会走一次
        //    pushPattern → lockOutputs 重新上锁 → 于是又正常。
        //
        //    残留输入也不再主动退回网络：那是臆测的修复，反而可能把阵列正在用的料抽走。
        //    锁的清理由 lockOutputs 自己保证（它开头就会 unlockAllOutputs），无需读档时插手。
        this.activePattern = null;
    }

    /**
     * 把四个列表重新指向 {@link MIInventory} <b>当前</b>真正持有的那批 stack。
     *
     * <p>为什么必须这么做：{@code MIInventory} 构造时会对传入的列表做 {@code new ArrayList<>(...)}，
     * 但真正的问题是 {@code MIInventory.readNbt} —— 它 {@code new ConfigurableItemStack(...)} 造一批
     * <b>新对象</b>再 {@code SlotConfig.readSlotList} 塞回自己的列表里。于是：
     * <ul>
     *   <li>构造后、读档前：MI 手里可能是另一批对象（{@code MIItemStorage} 侧也会另建）；</li>
     *   <li><b>每次读档后</b>：MI 手里<b>一定</b>是一批新对象，我们手上的旧引用就悬空了。</li>
     * </ul>
     * 而 {@code appendItemInputs/Outputs}（处理阵列读的）用的是我们的列表，GUI / Jade / 方块能力
     * 用的却是 MI 的那份 —— 两边一分家就会出现「阵列能合成但 GUI 看不到」或反过来的症状。
     * 用对象身份探针实测过：{@code 同一对象=false}。
     *
     * <p>顺序与我们传入 MI 时一致（先输入后输出），所以按当前槽数切分即可；槽数不变，可重复调用。
     */
    private void miae2$rebindInventoryStacks() {
        List<ConfigurableItemStack> miItems = this.inventory.getItemStacks();
        List<ConfigurableFluidStack> miFluids = this.inventory.getFluidStacks();
        int itemIn = this.itemInputs.size();
        int itemOut = this.itemOutputs.size();
        int fluidIn = this.fluidInputs.size();
        int fluidOut = this.fluidOutputs.size();
        if (miItems.size() < itemIn + itemOut || miFluids.size() < fluidIn + fluidOut) {
            LOGGER.warn("[读档初始化] MIInventory 槽数与预期不符（物品 {}/{}，流体 {}/{}），跳过库存重绑定",
                    miItems.size(), itemIn + itemOut, miFluids.size(), fluidIn + fluidOut);
            return;
        }

        // ① MIInventory.readNbt 每次读档都会 new 一批 ConfigurableItemStack 并把内容读进**它们**，
        //    同时用 SlotConfig.readSlotList 把**锁**留在原有对象上（内容在新、锁在旧）。
        //    所以先把内容从 MI 的新对象搬回**我们自始至终不变的那批对象**（锁本来就在我们这儿）。
        miae2$copyItemContents(miItems.subList(0, itemIn), this.itemInputs);
        miae2$copyItemContents(miItems.subList(itemIn, itemIn + itemOut), this.itemOutputs);
        miae2$copyFluidContents(miFluids.subList(0, fluidIn), this.fluidInputs);
        miae2$copyFluidContents(miFluids.subList(fluidIn, fluidIn + fluidOut), this.fluidOutputs);

        // ② 再让 MIInventory 的列表反过来指向**我们的对象**。
        //
        //    ★ 这一步是整套修复的关键：处理阵列在 MultiblockInventoryComponent.rebuild() 里会把我们
        //    （经 appendItemInputs 交出）的 stack **收集进它自己的列表**，而重匹配发生在区块加载时
        //    —— 早于我们这次初始化。所以只要我们的对象**永不更换**，阵列手里的引用就永不悬空；
        //    反过来（改成采用 MI 的新对象）则必然让阵列那边指向一批被抛弃的空对象，症状就是
        //    「读档后阵列能把持久化的旧配方做完，但那配方一结束就再也匹配不到新配方，永久空闲」。
        miItems.clear();
        miItems.addAll(this.itemInputs);
        miItems.addAll(this.itemOutputs);
        miFluids.clear();
        miFluids.addAll(this.fluidInputs);
        miFluids.addAll(this.fluidOutputs);

        // 不变量自检：重绑定后 MIInventory 的列表必须就是我们的对象。一旦分家，
        // GUI/Jade/方块能力 与 处理阵列 会各看一套库存（这正是「能合成但 GUI 看不到」那类 bug）。
        if ((!miItems.isEmpty() && !this.itemInputs.isEmpty() && miItems.get(0) != this.itemInputs.get(0))
                || (!miFluids.isEmpty() && !this.fluidInputs.isEmpty()
                        && miFluids.get(0) != this.fluidInputs.get(0))) {
            LOGGER.warn("[读档初始化] 库存重绑定后 MIInventory 与本地列表仍不是同一批对象，"
                    + "GUI 与处理阵列可能各看一套");
        }
    }

    /** 把 src 的内容拷进 dst（保留 dst 自身的锁/配置）。 */
    private static void miae2$copyItemContents(List<ConfigurableItemStack> src, List<ConfigurableItemStack> dst) {
        for (int i = 0; i < dst.size() && i < src.size(); i++) {
            ConfigurableItemStack from = src.get(i);
            ConfigurableItemStack to = dst.get(i);
            if (from.isEmpty()) {
                to.setAmount(0);
            } else {
                to.setKey(from.getResource());
                to.setAmount(from.getAmount());
            }
        }
    }

    private static void miae2$copyFluidContents(List<ConfigurableFluidStack> src, List<ConfigurableFluidStack> dst) {
        for (int i = 0; i < dst.size() && i < src.size(); i++) {
            ConfigurableFluidStack from = src.get(i);
            ConfigurableFluidStack to = dst.get(i);
            if (from.isEmpty()) {
                to.setAmount(0);
            } else {
                to.setKey(from.getResource());
                to.setAmount(from.getAmount());
            }
        }
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
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        // 读档初始化走这里、而不是 AE2 的 GridHelper.onFirstTick：后者在「手动创建/替换方块实体」
        // 的场景（我们的升级路径、以及单元测试复现读档）下不一定触发，挂在它上面会静默不执行。
        // 节点创建也兜在这里（不只靠 GridHelper.onFirstTick）：创建成功后是纯判断，几乎零开销。
        this.miae2$ensureNode();
        if (!this.loadInitDone) {
            this.loadInitDone = true;
            this.miae2$onLoadInit();
        }
        this.drainOutputsToReturnInv();
        this.updateActivePattern();
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
        return AEItemKey.of(this.iconSupplier.get());
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(this.iconSupplier.get());
    }

    /** 右键打开 AE2 的样板供应器界面（放样板），而不是 MI 的槽位界面。 */
    @Override
    public void openMenu(ServerPlayer player) {
        if (this.extended) {
            // 用 ExtendedAE 自己的扩展供应器菜单：ExtendedAE-Plus 的样板分页注入在那两个类上
            ExtendedAeMenuCompat.openExtendedPatternProvider(player, MenuLocators.forBlockEntity(this));
        } else {
            MenuOpener.open(PatternProviderMenu.TYPE, player, MenuLocators.forBlockEntity(this));
        }
    }

    /** 是否是扩展仓（4 页样板容量）。 */
    public boolean isExtended() {
        return this.extended;
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

    /** 把当前普通供应仓原位替换为扩展供应仓，保留库存与样板数据（由 RightClickBlock 事件触发）。 */
    public boolean upgradeToExtended() {
        if (this.extended || this.level == null || this.level.isClientSide() || ModHatches.ME_EXTENDED_PATTERN_PROVIDER_BLOCK == null) {
            return false;
        }
        BlockPos pos = this.getBlockPos();
        BlockState extState = ModHatches.ME_EXTENDED_PATTERN_PROVIDER_BLOCK.asBlock().defaultBlockState();
        CompoundTag contents = this.saveWithFullMetadata(this.level.registryAccess());
        BlockEntity newTile = ModHatches.ME_EXTENDED_PATTERN_PROVIDER_BLOCK.blockEntityType().get().create(pos, extState);
        if (newTile == null) {
            return false;
        }
        this.level.removeBlockEntity(pos);
        this.level.removeBlock(pos, false);
        this.level.setBlock(pos, extState, 3);
        this.level.setBlockEntity(newTile);
        newTile.loadWithComponents(contents, this.level.registryAccess());
        newTile.setChanged();
        return true;
    }

    public static boolean isPatternProviderUpgrade(ItemStack stack) {
        return ResourceLocation.fromNamespaceAndPath("extendedae", "pattern_provider_upgrade")
                .equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
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

    @Override
    public void miae2$setEnergyInputHatchPositions(List<BlockPos> positions) {
        this.energyInputHatchPositions = positions;
    }

    /** 阵列内能源输入仓的位置（可能为空：阵列未成形 / 没放能源仓）。供感应卡重定向使用。 */
    public List<BlockPos> miae2$getEnergyInputHatchPositions() {
        return this.energyInputHatchPositions;
    }

    /** 根据控制器（处理阵列）里放的工作方块自动命名（扩展版带「扩展」前缀）。 */
    private Component computeAutoName() {
        ProcessingArrayBlockEntity controller = this.findController();
        if (controller != null) {
            ItemStack machines = ((ProcessingArrayBlockEntityAccessor) (Object) controller).getMachinesComponent().getMachines();
            if (!machines.isEmpty()) {
                Component workBlockName = machines.getHoverName();
                if (machines.has(DataComponents.CUSTOM_NAME)) {
                    return workBlockName;
                }
                return workBlockName.copy().append(Component.translatable(this.extended
                        ? "text.mi_ae2_pattern_provider.extended_processing_array_suffix"
                        : "text.mi_ae2_pattern_provider.processing_array_suffix"));
            }
        }
        return Component.translatable(this.extended
                ? "text.mi_ae2_pattern_provider.extended_hatch_name"
                : "text.mi_ae2_pattern_provider.hatch_name");
    }

    private ProcessingArrayBlockEntity findController() {
        if (this.controllerPos == null || this.level == null) {
            return null;
        }
        BlockEntity be = this.level.getBlockEntity(this.controllerPos);
        return be instanceof ProcessingArrayBlockEntity pa ? pa : null;
    }

    // ---------- 样板 push/pull ----------

    /**
     * 把样板的输入（物品+流体）推进自身输入槽。返回是否全部推进成功。
     *
     * <p>⚠️ 这里**不再用** {@code PatternProviderTarget.get(...)} 让 AE2 去解析目标存储。那条路会：
     * <ol>
     *   <li>先问本方块有没有 {@code ME_STORAGE} 能力 —— 有的话目标直接变成「ME 网络本身」，
     *       材料插回网络、仓里永远是空的（按设计就是错的）；</li>
     *   <li>否则回退到 {@code StackWorldBehaviors} 的「外部容器」包装 —— 按<b>方块+面</b>解析出
     *       MI 的库存句柄，具体落到哪些槽取决于 MI 的槽位/面配置，**不可控**。</li>
     * </ol>
     * 实测（诊断日志，两种情况的 {@code ME_STORAGE} 都是"无"，即走的第 2 条）：读档前材料确实落进
     * 本仓 {@code itemInputs}（{@code 物品1/9}），读档后 125 次 push 之后<b>输入表与输出表全为 0</b>，
     * 而 Jade 显示材料就在仓里 —— 也就是说材料被插到了我们校验之外的地方，处理阵列读的却是
     * {@code itemInputs}，于是永远没料 → 「重进存档后发配正常但阵列不工作」。
     *
     * <p>所以改成<b>直接插进自己的输入表</b>：去处唯一、可控，且与"新放置"路径行为完全一致。
     * 单位方面 AE 液滴与 MI 的 mB 都是 1/1000 桶，数值可直接互换。
     */
    public boolean insertPatternInputs(KeyCounter[] inputHolder, IActionSource src) {
        if (this.level == null) {
            return false;
        }

        // 先模拟，确认所有输入都能被**完整**接纳（只接受一部分会造成静默丢料：
        // AE 认为已发配、我们只收下一半，剩下的既不在网络里也不在缓存里）
        for (KeyCounter counter : inputHolder) {
            for (it.unimi.dsi.fastutil.objects.Object2LongMap.Entry<AEKey> entry : counter) {
                long amount = entry.getLongValue();
                if (amount > 0L && this.miae2$insertOwnInput(entry.getKey(), amount, true) < amount) {
                    return false;
                }
            }
        }

        // 全部可接纳，实际推进
        boolean changed = false;
        for (KeyCounter counter : inputHolder) {
            for (it.unimi.dsi.fastutil.objects.Object2LongMap.Entry<AEKey> entry : counter) {
                long amount = entry.getLongValue();
                if (amount > 0L) {
                    this.miae2$insertOwnInput(entry.getKey(), amount, false);
                    changed = true;
                }
            }
        }

        // 通知 MI 这一层：内容变了。setKey/setAmount 只会触发 stack 自身的 ChangeListener，
        // 而 GUI/Jade 与存档看的是**方块实体**的「已修改 / 需同步」状态 —— 不标就会出现
        // 「阵列正常合成，但扳手 GUI 与 Jade 里看不到材料」。MI 自己的仓也是这么做的
        // （如 LargeTankHatch 改完调 setChanged()）。
        if (changed) {
            this.setChanged();
            this.sync();
        }
        return true;
    }

    /** 往自身输入槽插一份 AE 键值；返回实际能插入的数量。不支持的键类型返回 0（→ push 被拒）。 */
    private long miae2$insertOwnInput(AEKey key, long amount, boolean simulate) {
        if (key instanceof AEItemKey itemKey) {
            ItemVariant variant = ItemVariant.of(itemKey.getItem());
            long remaining = amount;
            for (ConfigurableItemStack stack : this.itemInputs) {
                if (remaining <= 0L) {
                    break;
                }
                if (!stack.isEmpty() && !stack.getResource().equals(variant)) {
                    continue;
                }
                long toAdd = Math.min(remaining, stack.getRemainingCapacityFor(variant));
                if (toAdd <= 0L) {
                    continue;
                }
                if (!simulate) {
                    if (stack.isEmpty()) {
                        stack.setKey(variant);
                    }
                    stack.setAmount(stack.getAmount() + toAdd);
                }
                remaining -= toAdd;
            }
            return amount - remaining;
        }

        if (key instanceof AEFluidKey fluidKey) {
            FluidVariant variant = FluidVariant.of(fluidKey.getFluid());
            long remaining = amount;
            for (ConfigurableFluidStack stack : this.fluidInputs) {
                if (remaining <= 0L) {
                    break;
                }
                if (!stack.isEmpty() && !stack.getResource().equals(variant)) {
                    continue;
                }
                // 流体槽的 getRemainingCapacityFor 是 protected，改用公开的 getRemainingSpace()
                long toAdd = Math.min(remaining, stack.getRemainingSpace());
                if (toAdd <= 0L) {
                    continue;
                }
                if (!simulate) {
                    if (stack.isEmpty()) {
                        stack.setKey(variant);
                    }
                    stack.setAmount(stack.getAmount() + toAdd);
                }
                remaining -= toAdd;
            }
            return amount - remaining;
        }

        return 0L; // 化学品等其它键类型本仓没有对应槽位
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
        // 没被任何多方块匹配上就不收料：可能是还没成形，也可能是「同一阵列放了多个供应仓 → 结构被判无效」。
        // 否则 AE 会把材料灌进一个永远不会被消耗的仓里，任务就一直挂着。
        if (!this.isMatched()) {
            return false;
        }
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


    /**
     * 每个 tick 检查：任务已结束（完成或被取消）时，回收残留材料并解锁输出格。
     *
     * <p>判据只看「**ME 网络是否还在请求本样板的产物**」，<b>不再</b>看输出槽的机器锁：
     * 机器锁（{@code isMachineLocked()}）可能一直挂着，会让清理条件永远为假 ——
     * 表现就是用户报的「合成中途取消后材料不回收、格子锁也不重置」。
     *
     * <p>连续 {@link #IDLE_CLEANUP_TICKS} tick 没有请求才动手，是为了避开两次 push 之间的瞬时空隙；
     * 也正因为有"还有请求就不动"这层门，读档时（AE 的任务还在排队）不会误清 —— 而上一版无条件解锁
     * 正是踩了这个坑。{@code activePattern == null} 时也能清理，用来收尾上个会话遗留的锁/残料。
     */
    private void updateActivePattern() {
        // ⚠️ activePattern 是瞬态字段（不进 NBT），**读档后必为 null**。此时绝不能当成"没有请求"，
        //    否则会把 AE 排队中、正在用的材料退回网络（AE 的合成队列是持久化的，读档后依然挂着）。
        //    所以没有 activePattern 时，改问"本供应器**任何**样板的产物还有没有被请求"。
        boolean requested = this.activePattern != null
                ? this.isPatternRequested()
                : this.miae2$isAnyPatternRequested();
        if (requested) {
            this.idleTicks = 0; // 还有任务（含读档后恢复的任务），什么都不做
            return;
        }
        if (++this.idleTicks < IDLE_CLEANUP_TICKS) {
            return;
        }
        this.idleTicks = 0;
        this.returnInputsToNetwork();
        this.activePattern = null;
        this.unlockAllOutputs();
        this.setChanged();
        this.sync(); // 让 GUI/Jade 立刻反映回收结果
    }

    /** 本供应器上任意一张样板的产物是否仍被 ME 网络请求（读档后 activePattern 为空时用）。 */
    private boolean miae2$isAnyPatternRequested() {
        IGrid grid = this.logic.getGrid();
        if (grid == null) {
            return false;
        }
        for (IPatternDetails details : this.logic.getAvailablePatterns()) {
            for (GenericStack output : details.getOutputs()) {
                if (grid.getCraftingService().getRequestedAmount(output.what()) > 0L) {
                    return true;
                }
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
        return this.hatchType;
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

    /** 供 MePatternProviderLogic 实现原版阻挡模式的判据（本仓输入表里是否还存着料）。 */
    public List<ConfigurableItemStack> miae2$itemInputs() {
        return this.itemInputs;
    }

    /** 同上，流体侧。 */
    public List<ConfigurableFluidStack> miae2$fluidInputs() {
        return this.fluidInputs;
    }

    /** 网格节点是否在线（供悬浮提示显示「设备在线/离线」，与 AE2 自己的机器一致）。 */
    public boolean miae2$isNodeOnline() {
        var node = this.mainNode.getNode();
        return node != null && node.isActive();
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
