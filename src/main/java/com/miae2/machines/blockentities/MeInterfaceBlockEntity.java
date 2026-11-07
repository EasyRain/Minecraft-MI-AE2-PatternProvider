package com.miae2.machines.blockentities;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEItemKey;
import appeng.api.util.AECableType;
import appeng.helpers.InterfaceLogicHost;
import aztech.modern_industrialization.inventory.ConfigurableFluidStack;
import aztech.modern_industrialization.inventory.ConfigurableItemStack;
import aztech.modern_industrialization.inventory.MIInventory;
import aztech.modern_industrialization.inventory.SlotPositions;
import aztech.modern_industrialization.machines.BEP;
import aztech.modern_industrialization.machines.MachineBlockEntity;
import aztech.modern_industrialization.machines.components.OrientationComponent;
import aztech.modern_industrialization.machines.gui.MachineGuiParameters;
import aztech.modern_industrialization.machines.multiblocks.HatchBlockEntity;
import aztech.modern_industrialization.machines.multiblocks.HatchType;
import com.miae2.ae.MeInterfaceLogic;
import com.miae2.util.IControllerPosHolder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
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
 * <p><b>当前进度：只有骨架</b>。已具备：注册、AE 节点、9+9 输入 + 9+9 输出槽位、AE 接口逻辑实例。
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
        // TODO ② 输出缓冲抽回 AE（MEStorage.insert），塞不进的留在缓冲
        // TODO ③ 让位判定：同阵列样板仓是否还有任务 ⇒ 锁/解锁输出（复用样板仓的 CraftingInFlight 判定）
    }

    /** 节点创建兜底（样板仓里是同样的写法，理由见那里的注释）。 */
    private void miae2$ensureNode() {
        if (this.mainNode.getNode() == null && !this.mainNode.isReady()) {
            this.mainNode.create(this.level, this.worldPosition);
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
