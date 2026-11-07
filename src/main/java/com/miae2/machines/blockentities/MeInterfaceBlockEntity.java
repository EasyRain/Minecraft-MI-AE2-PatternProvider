package com.miae2.machines.blockentities;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.api.util.AECableType;
import appeng.helpers.InterfaceLogicHost;
import appeng.menu.MenuOpener;
import appeng.menu.implementations.InterfaceMenu;
import appeng.menu.locator.MenuLocators;
import aztech.modern_industrialization.inventory.ConfigurableFluidStack;
import aztech.modern_industrialization.inventory.ConfigurableItemStack;
import aztech.modern_industrialization.inventory.MIInventory;
import aztech.modern_industrialization.inventory.SlotPositions;
import aztech.modern_industrialization.thirdparty.fabrictransfer.api.fluid.FluidVariant;
import aztech.modern_industrialization.thirdparty.fabrictransfer.api.item.ItemVariant;
import aztech.modern_industrialization.machines.BEP;
import aztech.modern_industrialization.machines.MachineBlockEntity;
import aztech.modern_industrialization.machines.MachineComponent;
import aztech.modern_industrialization.machines.components.OrientationComponent;
import aztech.modern_industrialization.machines.gui.MachineGuiParameters;
import aztech.modern_industrialization.machines.multiblocks.HatchBlockEntity;
import aztech.modern_industrialization.machines.multiblocks.HatchType;
import com.miae2.ae.MeInterfaceLogic;
import com.miae2.util.IControllerPosHolder;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * ME 接口仓（骨架，2026-11-07）。
 *
 * <p>结构照抄样板仓那套已验证的骨架：{@code extends HatchBlockEntity} + AE2 节点 +
 * 一个 AE 侧逻辑类 + MI 的槽位组件。区别只在于 AE 侧逻辑换成 {@link MeInterfaceLogic}
 * （{@code InterfaceLogic} 的子类）。
 *
 * <p><b>当前进度</b>：核心语义已实现（镜像同步 / 输出抽回 / 让位 / NBT / AE 界面）。槽数按档位（9 或 36）。
 * <b>尚未实现</b>（都在下面的 TODO 里）：
 * <ol>
 *   <li>AE 配置槽 ⇄ MI 物品/流体输入槽的<b>按索引映射视图</b>（设计 §4.5「映射方案」）</li>
 *   <li>输出缓冲 → AE 网络（{@code MEStorage.insert}）的抽回循环</li>
 *   <li>多仓共存时的<b>输出让位规则</b>（复用样板仓的 {@code CraftingInFlight} 判定）</li>
 *   <li>MI 侧界面（3 页翻页 / 原版档 1 页）与 AE 侧界面（{@code InterfaceMenu} 复用）</li>
 *   <li>NBT 存档（配置与库存持久化）</li>
 * </ol>
 */
public class MeInterfaceBlockEntity extends HatchBlockEntity
        implements IInWorldGridNodeHost, IGridNodeListener<MeInterfaceBlockEntity>, InterfaceLogicHost,
        IActionHost, Nameable, IControllerPosHolder {

    /** 槽位区几何，与样板仓保持一致（第一行 y、行距、底部余量）。 */
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    /** 配置槽数量（按档位：原版 9 / 扩展与超大 36）。构造时确定，之后只读。 */
    private final int slots;

    public static final int SLOT_FIRST_ROW_Y = 20;
    public static final int SLOT_ROW_HEIGHT = 18;
    public static final int GUI_BOTTOM_MARGIN = 100;

    private final List<ConfigurableItemStack> itemInputs;
    private final List<ConfigurableItemStack> itemOutputs;
    private final List<ConfigurableFluidStack> fluidInputs;
    private final List<ConfigurableFluidStack> fluidOutputs;
    private final MIInventory inventory;

    private final IManagedGridNode mainNode;
    private final MeInterfaceLogic logic;

    private final HatchType hatchType;
    private final Supplier<Item> iconSupplier;

    /**
     * 同一阵列里所有 ME 样板供应仓的位置（由 {@code ShapeMatcherMixin} 在成形时写入）。
     *
     * <p>接口仓的让位规则：**任一样板仓有订单在飞** ⇒ 屏蔽自己的输入（多方块读不到、抽不走），
     * 否则阵列会拿接口缓存顶替订单材料。详见设计文档 §4.5「多仓共存的让位规则」。
     */
    private List<BlockPos> providerPositions = List.of();

    /** 多方块控制器位置（成形时由 ShapeMatcherMixin 写入；接口仓暂时只记录）。 */
    private BlockPos controllerPos;

    /**
     * 上一 tick 镜像到 MI 侧的「键 + 数量」，用于识别**被多方块吃掉 / 被玩家取走**的量。
     *
     * <p>AE 侧 {@code InterfaceLogic.getStorage()} 是唯一真相，但多方块只认 MI 的
     * {@code ConfigurableItemStack}/{@code ConfigurableFluidStack}（按 {@code StorageView} 读写）。
     * 所以每 tick 把 AE 内容镜像到 MI 槽位；发现 MI 侧变少 = 被消耗 ⇒ 从 AE storage 扣掉等量，
     * 随后 AE 自己按配置补货，等于「多方块用掉的从网络补」。
     */
    private final AEKey[] mirrorItemKey;
    private final long[] mirrorItemAmount;
    private final AEKey[] mirrorFluidKey;
    private final long[] mirrorFluidAmount;

    /** 读档初始化只做一次（见 tick()）。 */
    private boolean loadInitDone;

    /** 让位状态（同阵列样板仓有订单在飞）：MI 侧输入要「看不见、抽不走」。 */
    private boolean yielding;

    /** 让位锁是否已施加（接口仓按用户口径：让位时清空后全锁 AIR，结束后还原）。 */
    private boolean yieldLocked;

    public MeInterfaceBlockEntity(
            BEP bep,
            MachineGuiParameters guiParams,
            int itemInSlots,
            int itemOutSlots,
            int fluidInSlots,
            int fluidOutSlots,
            long fluidCapacity,
            MeInterfaceLogic.Tier tier,
            HatchType hatchType,
            Supplier<Item> iconSupplier
    ) {
        super(bep, guiParams, OrientationComponent.Params.noFacingNoOutput());
        this.slots = tier.configSlots();
        this.mirrorItemKey = new AEKey[this.slots];
        this.mirrorItemAmount = new long[this.slots];
        this.mirrorFluidKey = new AEKey[this.slots];
        this.mirrorFluidAmount = new long[this.slots];
        this.hatchType = hatchType;
        this.iconSupplier = iconSupplier;

        this.itemInputs = new ArrayList<>();
        this.itemOutputs = new ArrayList<>();
        this.fluidInputs = new ArrayList<>();
        this.fluidOutputs = new ArrayList<>();

        // 输入槽：普通输入槽（容量由 AE 侧 config/storage 决定，MI 侧只做视图 —— TODO ②）
        for (int i = 0; i < itemInSlots; i++) {
            this.itemInputs.add(ConfigurableItemStack.standardInputSlot());
        }
        // 输出槽：物品<b>普通容量 64</b>，绝不能无上限（阵列按输出空间反推并行，虚报会销毁产物）
        for (int i = 0; i < itemOutSlots; i++) {
            this.itemOutputs.add(ConfigurableItemStack.standardOutputSlot());
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

        int itemInY = SLOT_FIRST_ROW_Y;
        int fluidInY = itemInY + SLOT_ROW_HEIGHT * rowsOf(itemInSlots);
        int itemOutY = fluidInY + SLOT_ROW_HEIGHT * rowsOf(fluidInSlots);
        int fluidOutY = itemOutY + SLOT_ROW_HEIGHT * rowsOf(itemOutSlots);

        SlotPositions itemPositions = new SlotPositions.Builder()
                .addSlots(8, itemInY, 9, rowsOf(itemInSlots))
                .addSlots(8, itemOutY, 9, rowsOf(itemOutSlots))
                .build();
        SlotPositions fluidPositions = new SlotPositions.Builder()
                .addSlots(8, fluidInY, 9, rowsOf(fluidInSlots))
                .addSlots(8, fluidOutY, 9, rowsOf(fluidOutSlots))
                .build();

        this.inventory = new MIInventory(allItems, allFluids, itemPositions, fluidPositions);
        this.registerComponents(this.inventory);

        // AE2 节点 + 接口逻辑（复用 AE2 的 InterfaceLogic）
        this.mainNode = GridHelper.createManagedNode(this, this)
                .setVisualRepresentation(iconSupplier.get())
                .setInWorldNode(true)
                .setTagName("me_interface");
        this.logic = new MeInterfaceLogic(this.mainNode, this, iconSupplier.get(), tier);
        this.registerComponents(new InterfaceNbtComponent());
    }

    // ---------- 生命周期 ----------

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        this.mainNode.destroy();
    }

    /**
     * 放置时把放置者设为 AE 节点 owner —— 与样板仓同一理由：EAEP 的频道卡对「未绑定」的卡会用
     * {@code IGridNode#getOwningPlayerProfileId()} 作为回退所有者做匹配，没有 owner 就连不上。
     */
    @Override
    public void onPlaced(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.item.ItemStack stack) {
        super.onPlaced(entity, stack);
        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            this.mainNode.setOwningPlayer(player);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level == null || this.level.isClientSide()) {
            return;
        }
        this.miae2$ensureNode();
        if (!this.loadInitDone) {
            this.loadInitDone = true;
            this.miae2$onLoadInit();
        }
        this.yielding = this.miae2$providerOrderActive();
        this.miae2$syncInputsFromInterface();
        this.miae2$drainOutputsToNetwork();
        this.miae2$updateYieldLocks();
    }

    /** 节点创建兜底（样板仓里是同样的写法，理由见那里的注释）。 */
    private void miae2$ensureNode() {
        if (this.mainNode.getNode() == null && !this.mainNode.isReady()) {
            this.mainNode.create(this.level, this.worldPosition);
        }
    }

    /**
     * 读档 / 区块重载后的一次性初始化：**把 MIInventory 里的 stack 对象重新对齐到我们自己那批**。
     *
     * <p>MI 的 {@code MIInventory.readNbt} 每次读档都会 {@code new} 一批 {@code ConfigurableItemStack}
     * 塞回自己的列表，并随后把**锁**留在我们原有对象上（内容在新对象、锁在旧对象）。于是出现「两个缓存」：
     * {@code appendItemInputs/Outputs}（阵列读的）用我们的列表，而 GUI / 方块能力 / 镜像同步用 MI 的列表。
     *
     * <p>接口仓的症状就是用户报的「加工中输出槽看不到产物」「重进存档后行为变样」。
     * 修法与样板仓一致：把内容搬回我们的对象，再让 MIInventory 的列表反过来指向我们的对象
     * —— **我们的对象永不更换**，阵列手里的引用才不会悬空。
     */
    private void miae2$onLoadInit() {
        this.miae2$rebindInventoryStacks();
    }

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

        miae2$copyItemContents(miItems.subList(0, itemIn), this.itemInputs);
        miae2$copyItemContents(miItems.subList(itemIn, itemIn + itemOut), this.itemOutputs);
        miae2$copyFluidContents(miFluids.subList(0, fluidIn), this.fluidInputs);
        miae2$copyFluidContents(miFluids.subList(fluidIn, fluidIn + fluidOut), this.fluidOutputs);

        miItems.clear();
        miItems.addAll(this.itemInputs);
        miItems.addAll(this.itemOutputs);
        miFluids.clear();
        miFluids.addAll(this.fluidInputs);
        miFluids.addAll(this.fluidOutputs);

        if ((!miItems.isEmpty() && !this.itemInputs.isEmpty() && miItems.get(0) != this.itemInputs.get(0))
                || (!miFluids.isEmpty() && !this.fluidInputs.isEmpty()
                        && miFluids.get(0) != this.fluidInputs.get(0))) {
            LOGGER.warn("[读档初始化] 库存重绑定后 MIInventory 与本地列表仍不是同一批对象，"
                    + "GUI 与处理阵列可能各看一套");
        }
    }

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

    // ---------- AE ⇄ MI 同步（接口语义的核心） ----------


    /**
     * 把 AE 侧配置槽的内容镜像到 MI 侧输入槽位，并按索引一一对应（AE 第 i 槽 ⇄ MI 物品/流体第 i 槽）。
     *
     * <p>一个 AE 槽同时只能是一种键（物品或流体），所以「物品第 i 槽」与「流体第 i 槽」互斥生效。
     * 让位期间把两边都清空 ⇒ 多方块读不到、也抽不走（正是用户要的「屏蔽输入」）。
     */
    private void miae2$syncInputsFromInterface() {
        GenericStackInv storage = this.logic.getStorage();
        int storageSlots = Math.min(storage.size(), this.slots);
        for (int i = 0; i < this.slots; i++) {
            ConfigurableItemStack itemSlot = this.itemInputs.get(i);
            ConfigurableFluidStack fluidSlot = this.fluidInputs.get(i);

            if (this.yielding || i >= storageSlots) {
                // 让位 / 超出配置槽：清空 MI 侧视图与快照（**不**动 AE storage）
                itemSlot.empty();
                fluidSlot.empty();
                this.mirrorItemKey[i] = null;
                this.mirrorItemAmount[i] = 0L;
                this.mirrorFluidKey[i] = null;
                this.mirrorFluidAmount[i] = 0L;
                continue;
            }

            AEKey key = storage.getKey(i);
            long target = storage.getAmount(i);
            if (key instanceof AEItemKey itemKey) {
                // 先把上一 tick 镜像后被消耗掉的量从 AE storage 扣掉
                this.miae2$reconcileItem(i, itemKey, storage);
                fluidSlot.empty();
                this.mirrorFluidKey[i] = null;
                this.mirrorFluidAmount[i] = 0L;
                // 再把目标量写进 MI 槽位（键不同就换键）
                if (target <= 0) {
                    itemSlot.empty();
                } else {
                    itemSlot.setKey(ItemVariant.of(itemKey.toStack(1)));
                    itemSlot.setAmount(Math.min(target, itemSlot.getCapacity()));
                }
                this.mirrorItemKey[i] = itemKey;
                this.mirrorItemAmount[i] = itemSlot.getAmount();
            } else if (key instanceof AEFluidKey fluidKey) {
                this.miae2$reconcileFluid(i, fluidKey, storage);
                itemSlot.empty();
                this.mirrorItemKey[i] = null;
                this.mirrorItemAmount[i] = 0L;
                if (target <= 0) {
                    fluidSlot.empty();
                } else {
                    fluidSlot.setKey(FluidVariant.of(fluidKey.getFluid()));
                    fluidSlot.setAmount(Math.min(target, fluidSlot.getCapacity()));
                }
                this.mirrorFluidKey[i] = fluidKey;
                this.mirrorFluidAmount[i] = fluidSlot.getAmount();
            } else {
                // 该槽没有配置内容：两边都清空
                itemSlot.empty();
                fluidSlot.empty();
                this.mirrorItemKey[i] = null;
                this.mirrorItemAmount[i] = 0L;
                this.mirrorFluidKey[i] = null;
                this.mirrorFluidAmount[i] = 0L;
            }
        }
    }

    /** MI 物品槽变少 = 被多方块吃掉（或被玩家取走）⇒ 从 AE storage 扣等量。 */
    private void miae2$reconcileItem(int i, AEItemKey key, GenericStackInv storage) {
        if (!key.equals(this.mirrorItemKey[i])) {
            return;
        }
        long now = this.itemInputs.get(i).getAmount();
        long consumed = this.mirrorItemAmount[i] - now;
        if (consumed > 0) {
            storage.extract(i, key, consumed, Actionable.MODULATE);
        }
    }

    /** 流体的同上。 */
    private void miae2$reconcileFluid(int i, AEFluidKey key, GenericStackInv storage) {
        if (!key.equals(this.mirrorFluidKey[i])) {
            return;
        }
        long now = this.fluidInputs.get(i).getAmount();
        long consumed = this.mirrorFluidAmount[i] - now;
        if (consumed > 0) {
            storage.extract(i, key, consumed, Actionable.MODULATE);
        }
    }

    /**
     * 输出缓冲抽回 AE 网络。
     *
     * <p>接口仓的输出槽**不接 AE 配置**（用户口径）：机器产出的东西直接塞回网络，塞不进的留在缓冲里
     * （断网时内容不丢，且能通过扳手界面手动取出）。
     */
    private void miae2$drainOutputsToNetwork() {
        IGrid grid = this.mainNode.getGrid();
        if (grid == null) {
            return;
        }
        MEStorage network = grid.getStorageService().getInventory();
        IActionSource source = IActionSource.empty();
        boolean changed = false;

        for (ConfigurableItemStack out : this.itemOutputs) {
            if (out.isEmpty()) {
                continue;
            }
            long amount = out.getAmount();
            if (amount <= 0) {
                continue;
            }
            long inserted = network.insert(AEItemKey.of(out.toStack()), amount, Actionable.MODULATE, source);
            if (inserted > 0) {
                out.decrement(inserted);
                changed = true;
            }
        }
        for (ConfigurableFluidStack out : this.fluidOutputs) {
            if (out.isEmpty()) {
                continue;
            }
            long amount = out.getAmount();
            if (amount <= 0) {
                continue;
            }
            long inserted = network.insert(AEFluidKey.of(out.getVariant().getFluid()), amount, Actionable.MODULATE, source);
            if (inserted > 0) {
                out.decrement(inserted);
                changed = true;
            }
        }
        if (changed) {
            // 诊断日志（每 200 tick 最多一条）：确认产物确实被抽回网络，而不是「凭空消失」。
            if (this.level != null && this.level.getGameTime() % 200L == 0L) {
                LOGGER.info("[接口仓] 产物已抽回网络 @ {}", this.getBlockPos());
            }
            this.setChanged();
        }
    }

    /**
     * 让位时锁输出（用户口径：接口仓也是**先清空全部锁（含玩家锁）再全锁 AIR**，结束后还原）。
     *
     * <p>还原暂时退化为「全解」——等 MI 侧 GUI 做出来、玩家能真正设锁之后，再补快照还原。
     */
    private void miae2$updateYieldLocks() {
        if (this.yielding && !this.yieldLocked) {
            this.yieldLocked = true;
            for (ConfigurableItemStack out : this.itemOutputs) {
                if (out.isPlayerLocked()) {
                    out.togglePlayerLock();
                }
            }
            for (ConfigurableFluidStack out : this.fluidOutputs) {
                if (out.isPlayerLocked()) {
                    out.togglePlayerLock();
                }
            }
            for (ConfigurableItemStack out : this.itemOutputs) {
                // 关键：连**机器锁**一起清掉。订单期间阵列可能已经先把产物锁在接口的输出格上
                // （machine lock，显示成「绿宝石板 + 数量」的占位），只加玩家锁是压不住它的。
                if (out.isMachineLocked()) {
                    out.disableMachineLock();
                }
                out.togglePlayerLock();
            }
            for (ConfigurableFluidStack out : this.fluidOutputs) {
                if (out.isMachineLocked()) {
                    out.disableMachineLock();
                }
                out.togglePlayerLock();
            }
            this.setChanged();
        } else if (!this.yielding && this.yieldLocked) {
            this.yieldLocked = false;
            for (ConfigurableItemStack out : this.itemOutputs) {
                if (out.isPlayerLocked()) {
                    out.togglePlayerLock();
                }
            }
            for (ConfigurableFluidStack out : this.fluidOutputs) {
                if (out.isPlayerLocked()) {
                    out.togglePlayerLock();
                }
            }
            this.setChanged();
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
    public void onSaveChanges(MeInterfaceBlockEntity nodeOwner, IGridNode node) {
        this.setChanged();
    }

    @Override
    public void onGridChanged(MeInterfaceBlockEntity nodeOwner, IGridNode node) {
        this.logic.gridChanged();
        this.setChanged();
    }

    @Override
    public void onStateChanged(MeInterfaceBlockEntity nodeOwner, IGridNode node, State state) {
        this.setChanged();
    }

    /**
     * 把 {@code InterfaceLogic} 的配置槽 / 库存 / 优先级 / 升级卡挂到 MI 的组件存档系统上。
     *
     * <p>MI 的 {@code saveAdditional}/{@code loadAdditional} 是 final，只能走 {@link MachineComponent}；
     * 好消息是 AE2 的 {@code InterfaceLogic#writeToNBT/readFromNBT} 签名与组件接口完全一致。
     */
    private final class InterfaceNbtComponent implements MachineComponent {
        private static final String KEY = "miae2_interface";

        @Override
        public void writeNbt(CompoundTag tag, Provider registries) {
            CompoundTag sub = new CompoundTag();
            MeInterfaceBlockEntity.this.logic.writeToNBT(sub, registries);
            tag.put(KEY, sub);
        }

        @Override
        public void readNbt(CompoundTag tag, Provider registries, boolean client) {
            if (tag.contains(KEY)) {
                MeInterfaceBlockEntity.this.logic.readFromNBT(tag.getCompound(KEY), registries);
            }
        }
    }

    /**
     * 右键打开 AE2 原版 ME 接口界面（配置槽 / 保持量 / 优先级）。
     *
     * <p>AE2 的 {@code InterfaceMenu} 泛型参数是 {@code InterfaceLogicHost}（不是它自己的方块实体），
     * 所以只要能提供 host 就能直接复用它的菜单 —— 与样板仓复用 ExtendedAE 菜单同一手法。
     * 打不开时退回 MI 的槽位界面，绝不让服务端线程崩（可选类加载失败也不能崩服）。
     */
    /**
     * 扳手右键 → MI 自己的槽位界面（四组槽位；玩家手动取放用）。
     *
     * <p>与样板仓同一分工：**右键 = AE 界面，扳手右键 = MI 界面**。
     * 不覆写这个的话 MI 的默认扳手行为不会打开界面（2026-11-07 用户实测「扳手打不开」）。
     */
    @Override
    public boolean useWrench(net.minecraft.world.entity.player.Player player,
                            net.minecraft.world.InteractionHand hand,
                            net.minecraft.world.phys.BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer) {
            super.openMenu(serverPlayer);
        }
        return true;
    }

    @Override
    public void openMenu(ServerPlayer player) {
        try {
            switch (this.logic.tier()) {
                case BASE -> MenuOpener.open(InterfaceMenu.TYPE, player, MenuLocators.forBlockEntity(this));
                case EXTENDED -> com.miae2.ae.ExtendedAeInterfaceMenuCompat.openExtended(player, MenuLocators.forBlockEntity(this));
                case OVERSIZE -> com.miae2.ae.ExtendedAeInterfaceMenuCompat.openOversize(player, MenuLocators.forBlockEntity(this));
            }
            return;
        } catch (LinkageError | RuntimeException e) {
            LOGGER.warn("[界面] AE 接口界面打开失败，退回 MI 槽位界面 @ {}", this.getBlockPos(), e);
        }
        super.openMenu(player);
    }

    // ---------- InterfaceLogicHost ----------


    @NotNull
    @Override
    public MeInterfaceLogic getInterfaceLogic() {
        return this.logic;
    }

    @Override
    public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity() {
        return this;
    }

    @Override
    public void saveChanges() {
        this.setChanged();
    }

    @Override
    public void miae2$setProviderSiblingPositions(List<BlockPos> positions) {
        this.providerPositions = List.copyOf(positions);
    }

    @Override
    public void miae2$setControllerPos(BlockPos pos) {
        this.controllerPos = pos;
    }

    @Override
    public void miae2$setEnergyInputHatchPositions(List<BlockPos> positions) {
        // 接口仓不参与感应卡的能量定向（那是样板仓/阵列的事），忽略
    }

    /**
     * 同阵列里的样板仓是否有订单在飞 —— 接口仓据此让位（屏蔽输入 / 锁输出）。
     *
     * <p>TODO ⓪：等索引映射视图实现后，把本方法接到「输入视图报 0 + 拒绝抽取」上；
     * 输出缓冲的锁也走同一判定（订单进行中不在本仓接产物）。
     */
    public boolean miae2$providerOrderActive() {
        if (this.level == null || this.providerPositions.isEmpty()) {
            return false;
        }
        for (BlockPos pos : this.providerPositions) {
            if (this.level.getBlockEntity(pos) instanceof MePatternProviderBlockEntity provider
                    && provider.miae2$hasActiveOrder()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(this.iconSupplier.get());
    }

    // TODO ④ 打开界面：原版档复用 AE2 的 InterfaceMenu，扩展/超大复用 ContainerExInterface.TYPE / TYPE_OVERSIZE

    // ---------- Nameable ----------

    @Override
    public Component getName() {
        return this.getDisplayName();
    }

    @Override
    public Component getCustomName() {
        return this.getDisplayName();
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

    // ⚠️ 四组槽位都要交出去：只交输入的话，阵列看不到接口的输出槽 ⇒ 没有可用输出空间 ⇒
    // 「接口有料但机器不加工」（2026-11-07 用户实测）。流体侧同理。
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

    /** 与样板仓同一公式：四组槽位行数算出的最低 GUI 高度（上限 256）。 */
    public static int requiredGuiHeight(int itemInSlots, int itemOutSlots, int fluidInSlots, int fluidOutSlots) {
        int rows = rowsOf(itemInSlots) + rowsOf(itemOutSlots) + rowsOf(fluidInSlots) + rowsOf(fluidOutSlots);
        return SLOT_FIRST_ROW_Y + SLOT_ROW_HEIGHT * rows + GUI_BOTTOM_MARGIN;
    }

    /** 终端图标（AE2 的菜单/终端会用到）。 */
    public AEItemKey miae2$terminalIcon() {
        return AEItemKey.of(this.iconSupplier.get());
    }
}
