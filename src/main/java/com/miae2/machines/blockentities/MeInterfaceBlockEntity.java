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
import com.miae2.util.IUnboundedItemAccessor;
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

    /** 玩家塞进 MI 槽、但 AE storage 收不下的余量（覆盖时加回去，避免丢物）。 */
    private final long[] spillItemAmount;
    private final long[] spillFluidAmount;

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
        this.spillItemAmount = new long[this.slots];
        this.spillFluidAmount = new long[this.slots];
        this.hatchType = hatchType;
        this.iconSupplier = iconSupplier;

        this.itemInputs = new ArrayList<>();
        this.itemOutputs = new ArrayList<>();
        this.fluidInputs = new ArrayList<>();
        this.fluidOutputs = new ArrayList<>();

        // 输入槽：普通输入槽（容量由 AE 侧 config/storage 决定，MI 侧只做视图 —— TODO ②）
        for (int i = 0; i < itemInSlots; i++) {
            ConfigurableItemStack slot = ConfigurableItemStack.standardInputSlot();
            // 输入槽只是 AE storage 的镜像容器：容量必须**等于** AE 侧每槽容量（64 / 64 / 1024）。
            // 不设成「无上限」的原因（用户 2026-11-07 提问点破）：无上限会让玩家能在 MI 侧超塞，
            // 超出的部分 AE 收不下、只能留在 MI 槽里，就得再写一套 spill 兜底；给具体容量则 MI 自己挡住超塞，
            // 语义更简单，也不会出现「AE 显示 64 / MI 显示 200」这种两套数字。
            // 输入不参与阵列并行推算，所以这个容量不影响倍率。
            ((IUnboundedItemAccessor) slot).miae2$setCapacity((int) Math.min(Integer.MAX_VALUE, tier.itemCapacity()));
            this.itemInputs.add(slot);
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
        this.logic.miae2$ensureCapacity();
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
        // 与样板仓一致：读档后补写节点 owner（未绑定的 EAEP 频道卡按 owner 回退匹配），
        // 再请求 EAEP 的频道卡控制器重建无线链路。缺了这两步，频道卡会「插进去但必须手动绑定玩家才生效」。
        this.miae2$ensureOwner();
        this.miae2$restoreChannelCard();
    }

    /**
     * 补写 AE 节点 owner（读档 / 旧存档兼容）。
     *
     * <p>{@code onPlaced} 只在放置那一刻设 owner；读档后节点可能没有 owner ⇒ EAEP 的频道卡
     * 拿不到回退所有者（{@code getOwningPlayerProfileId()} 为 null）⇒ 未绑定的卡连不上。
     * 这里用 MI 自己持久化的放置者 {@code placedBy.placerId} 回填（找不到人就算了，无副作用）。
     */
    private void miae2$ensureOwner() {
        var node = this.mainNode.getNode();
        if (node == null || node.getOwningPlayerProfileId() != null || this.level == null) {
            return;
        }
        java.util.UUID placer = this.placedBy.placerId;
        if (placer == null || this.level.getServer() == null) {
            return;
        }
        var player = this.level.getServer().getPlayerList().getPlayer(placer);
        if (player != null) {
            this.mainNode.setOwningPlayer(player);
            LOGGER.info("[节点] 已按放置者补写 AE 节点 owner @ {}", this.getBlockPos());
        }
    }

    /** 反射请求 EAEP 的频道卡控制器重建无线链路；没装 EAEP 时静默跳过。 */
    private void miae2$restoreChannelCard() {
        try {
            java.lang.reflect.Method getController = this.logic.getClass().getMethod("eap$getChannelCardController");
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
                // 让位 / 超出配置槽：清空 MI 侧视图与快照（**不**动 AE storage）。
                // 但若有 spill 余量，必须先塞回网络；塞不掉就保留这格、跳过镜像（宁可不同步，也不丢物）。
                boolean itemOk = this.miae2$releaseItemSpill(i);
                boolean fluidOk = this.miae2$releaseFluidSpill(i);
                if (!itemOk || !fluidOk) {
                    continue;
                }
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
                // 先对账（被吃掉就扣 AE、玩家塞进来就收进 AE），拿到「该显示多少」
                if (!this.miae2$releaseFluidSpill(i)) {
                    // 流体侧还有余量没地方去：这一格先别动（物品侧同样跳过，避免半格状态）
                    continue;
                }
                long itemTarget = this.miae2$reconcileItem(i, itemKey, storage);
                fluidSlot.empty();
                this.mirrorFluidKey[i] = null;
                this.mirrorFluidAmount[i] = 0L;
                if (itemTarget <= 0) {
                    itemSlot.empty();
                } else {
                    itemSlot.setKey(ItemVariant.of(itemKey.toStack(1)));
                    itemSlot.setAmount(itemTarget);
                }
                this.mirrorItemKey[i] = itemKey;
                this.mirrorItemAmount[i] = itemSlot.getAmount();
            } else if (key instanceof AEFluidKey fluidKey) {
                if (!this.miae2$releaseItemSpill(i)) {
                    continue;
                }
                long fluidTarget = this.miae2$reconcileFluid(i, fluidKey, storage);
                itemSlot.empty();
                this.mirrorItemKey[i] = null;
                this.mirrorItemAmount[i] = 0L;
                if (fluidTarget <= 0) {
                    fluidSlot.empty();
                } else {
                    fluidSlot.setKey(FluidVariant.of(fluidKey.getFluid()));
                    fluidSlot.setAmount(fluidTarget);
                }
                this.mirrorFluidKey[i] = fluidKey;
                this.mirrorFluidAmount[i] = fluidSlot.getAmount();
            } else {
                // 该槽没有配置内容：两边都清空（同样先救 spill 余量）
                if (!this.miae2$releaseItemSpill(i) || !this.miae2$releaseFluidSpill(i)) {
                    continue;
                }
                itemSlot.empty();
                fluidSlot.empty();
                this.mirrorItemKey[i] = null;
                this.mirrorItemAmount[i] = 0L;
                this.mirrorFluidKey[i] = null;
                this.mirrorFluidAmount[i] = 0L;
            }
        }
    }

    /**
     * 对账：MI 物品槽与上一 tick 的镜像值比较，处理两个方向的差额。
     *
     * @return 覆盖后该槽应该显示的数量（AE 侧数量 + 塞不进去的余量）
     */
    private long miae2$reconcileItem(int i, AEItemKey key, GenericStackInv storage) {
        if (!key.equals(this.mirrorItemKey[i])) {
            return storage.getAmount(i);
        }
        long now = this.itemInputs.get(i).getAmount();
        long prev = this.mirrorItemAmount[i];
        if (now < prev) {
            // 被多方块吃掉 / 被玩家取走 ⇒ 从 AE storage 扣
            storage.extract(i, key, prev - now, Actionable.MODULATE);
        } else if (now > prev) {
            // 玩家手动塞进来的 ⇒ 尽量塞进 AE storage，塞不下的留在槽里（绝不丢）
            long extra = now - prev;
            long accepted = storage.insert(i, key, extra, Actionable.MODULATE);
            this.spillItemAmount[i] = extra - accepted;
        }
        // spill 持久保留（不在这里清零）：改配置/清空槽/让位时还要用它把余量救回来
        return storage.getAmount(i) + this.spillItemAmount[i];
    }

    /** 流体的同上。 */
    private long miae2$reconcileFluid(int i, AEFluidKey key, GenericStackInv storage) {
        if (!key.equals(this.mirrorFluidKey[i])) {
            return storage.getAmount(i);
        }
        long now = this.fluidInputs.get(i).getAmount();
        long prev = this.mirrorFluidAmount[i];
        if (now < prev) {
            storage.extract(i, key, prev - now, Actionable.MODULATE);
        } else if (now > prev) {
            long extra = now - prev;
            long accepted = storage.insert(i, key, extra, Actionable.MODULATE);
            this.spillFluidAmount[i] = extra - accepted;
        }
        return storage.getAmount(i) + this.spillFluidAmount[i];
    }

    /**
     * 把 MI 侧某格的内容尽量塞回 AE 网络（用于「改配置 / 清空 / 让位」前保住余量）。
     *
     * @return 实际塞回去的量
     */
    private long miae2$dumpItemSlotToNetwork(ConfigurableItemStack slot, long limit) {
        IGrid grid = this.mainNode.getGrid();
        if (grid == null || slot.isEmpty() || limit <= 0) {
            return 0L;
        }
        long amount = Math.min(limit, slot.getAmount());
        if (amount <= 0) {
            return 0L;
        }
        MEStorage network = grid.getStorageService().getInventory();
        long inserted = network.insert(AEItemKey.of(slot.toStack()), amount, Actionable.MODULATE, IActionSource.empty());
        if (inserted > 0) {
            slot.decrement(inserted);
        }
        return inserted;
    }

    private long miae2$dumpFluidSlotToNetwork(ConfigurableFluidStack slot, long limit) {
        IGrid grid = this.mainNode.getGrid();
        if (grid == null || slot.isEmpty() || limit <= 0) {
            return 0L;
        }
        long amount = Math.min(limit, slot.getAmount());
        if (amount <= 0) {
            return 0L;
        }
        MEStorage network = grid.getStorageService().getInventory();
        long inserted = network.insert(AEFluidKey.of(slot.getVariant().getFluid()), amount, Actionable.MODULATE,
                IActionSource.empty());
        if (inserted > 0) {
            slot.decrement(inserted);
        }
        return inserted;
    }

    /**
     * 该槽即将被清空/覆盖：先把 **spill 余量**塞回网络，塞不掉的留下。
     *
     * @return true = 余量已清掉，可以安全清空/覆盖该槽；false = 还有余量没地方去，**不要动这一格**
     */
    private boolean miae2$releaseItemSpill(int i) {
        if (this.spillItemAmount[i] <= 0) {
            return true;
        }
        long dumped = this.miae2$dumpItemSlotToNetwork(this.itemInputs.get(i), this.spillItemAmount[i]);
        this.spillItemAmount[i] -= dumped;
        return this.spillItemAmount[i] <= 0;
    }

    private boolean miae2$releaseFluidSpill(int i) {
        if (this.spillFluidAmount[i] <= 0) {
            return true;
        }
        long dumped = this.miae2$dumpFluidSlotToNetwork(this.fluidInputs.get(i), this.spillFluidAmount[i]);
        this.spillFluidAmount[i] -= dumped;
        return this.spillFluidAmount[i] <= 0;
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

    /**
     * 为 Jade 悬浮提示生成「有内容的槽」摘要（36 槽档位用：MI 自带的容器预览对它们不输出）。
     *
     * <p>只在槽里真的有东西时才列，最多各 8 条 —— 比一屏「空 4B」可读得多。
     */
    public void miae2$appendContentsPreview(net.minecraft.nbt.ListTag items, net.minecraft.nbt.ListTag fluids) {
        // 物品：逐条列非空（含输入/输出），最多 8 条
        int itemLines = 0;
        int itemUsed = 0;
        for (ConfigurableItemStack stack : this.itemInputs) {
            if (stack.isEmpty()) {
                continue;
            }
            itemUsed++;
            if (itemLines < 8) {
                items.add(net.minecraft.nbt.StringTag.valueOf(
                        "物品：" + stack.getVariant().toStack().getHoverName().getString() + " ×" + stack.getAmount()));
                itemLines++;
            }
        }
        for (ConfigurableItemStack stack : this.itemOutputs) {
            if (stack.isEmpty()) {
                continue;
            }
            itemUsed++;
            if (itemLines < 8) {
                items.add(net.minecraft.nbt.StringTag.valueOf(
                        "产物：" + stack.getVariant().toStack().getHoverName().getString() + " ×" + stack.getAmount()));
                itemLines++;
            }
        }
        // 恒定一行概况：让「全空」时也能看出槽位规模
        items.add(net.minecraft.nbt.StringTag.valueOf(
                "物品槽：已用 " + itemUsed + " / " + (this.itemInputs.size() + this.itemOutputs.size())));

        // 流体：逐条列非空，最多 8 条；并恒定给一行概况（用户要的「流体预览」）
        int fluidLines = 0;
        int fluidUsed = 0;
        long fluidTotal = 0L;
        for (ConfigurableFluidStack stack : this.fluidInputs) {
            if (stack.isEmpty()) {
                continue;
            }
            fluidUsed++;
            fluidTotal += stack.getAmount();
            if (fluidLines < 8) {
                fluids.add(net.minecraft.nbt.StringTag.valueOf(
                        "流体：" + stack.getVariant().getFluid().getFluidType().getDescription().getString()
                                + " " + stack.getAmount() + " mB"));
                fluidLines++;
            }
        }
        for (ConfigurableFluidStack stack : this.fluidOutputs) {
            if (stack.isEmpty()) {
                continue;
            }
            fluidUsed++;
            fluidTotal += stack.getAmount();
            if (fluidLines < 8) {
                fluids.add(net.minecraft.nbt.StringTag.valueOf(
                        "产物流体：" + stack.getVariant().getFluid().getFluidType().getDescription().getString()
                                + " " + stack.getAmount() + " mB"));
                fluidLines++;
            }
        }
        long perSlot = this.fluidInputs.isEmpty() ? 0L : this.fluidInputs.get(0).getCapacity();
        fluids.add(net.minecraft.nbt.StringTag.valueOf(
                "流体槽：已用 " + fluidUsed + " / " + (this.fluidInputs.size() + this.fluidOutputs.size())
                        + "（每格 " + perSlot + " mB，共 " + fluidTotal + " mB）"));
    }

    /** 网格节点是否在线（供 Jade / TOP 显示「设备在线 / 设备离线」，与样板仓一致）。 */
    public boolean miae2$isNodeOnline() {
        var node = this.mainNode.getNode();
        return node != null && node.isActive();
    }

    /** 配置槽数量（9 = 原版档；36 = 扩展/超大档）。供菜单守卫判断该走哪套界面。 */
    public int miae2$configSlots() {
        return this.logic.getConfig().size();
    }

    @Override
    public void openMenu(ServerPlayer player) {
        boolean opened = false;
        try {
            LOGGER.info("[界面] 打开 AE 接口界面 tier={} configSlots={} @ {}",
                    this.logic.tier(), this.logic.getConfig().size(), this.getBlockPos());
            switch (this.logic.tier()) {
                case BASE -> opened = MenuOpener.open(InterfaceMenu.TYPE, player, MenuLocators.forBlockEntity(this));
                case EXTENDED -> opened = com.miae2.ae.ExtendedAeInterfaceMenuCompat.openExtended(
                        player, MenuLocators.forBlockEntity(this));
                case OVERSIZE -> opened = com.miae2.ae.ExtendedAeInterfaceMenuCompat.openOversize(
                        player, MenuLocators.forBlockEntity(this));
            }
            LOGGER.info("[界面] 打开结果 opened={} tier={}", opened, this.logic.tier());
            if (opened) {
                return;
            }
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
