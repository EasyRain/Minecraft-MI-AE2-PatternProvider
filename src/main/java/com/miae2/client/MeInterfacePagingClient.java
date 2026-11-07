package com.miae2.client;

import aztech.modern_industrialization.client.machines.gui.MachineMenuClient;
import aztech.modern_industrialization.client.machines.gui.MachineScreen;
import com.miae2.MiAe2PatternProvider;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * 接口仓（36 槽档位）的 MI 侧翻页界面 —— **纯客户端**实现。
 *
 * <p>为什么能只做客户端：MI 的机器界面是内建的 {@link MachineScreen}（MenuType 由 MI 注册，我们插不进去），
 * 但它是 public 的、{@code addButton(...)} 也是 public，并且 `ScreenEvent.Init.Post` 能拿到构造好的实例；
 * 而槽位点击走的是**槽位索引**（1.21 的 `AbstractContainerMenu#clicked(int slotId, ...)`），
 * 服务端不靠坐标找槽 ⇒ 只在客户端重摆 `Slot#x/#y` 就能实现翻页，服务端完全无感。
 *
 * <p>页码因此是**每个玩家各自**的（存在本类的界面实例里），两个人同时看可以各翻各的 —— 比存到方块实体更好。
 *
 * <p>槽位编址（与 {@code MeInterfaceBlockEntity} 构造里的 {@code SlotPositions} 顺序一致）：
 * 物品入 0–35 → 物品出 36–71 → 流体入 72–107 → 流体出 108–143；之后才是玩家背包。
 *
 * <p>4 页布局（每页 4 行 × 9 = 36 格，GUI 高度仍是 192）：
 * <ol>
 *   <li>物品入 0–17（2 行）+ 物品出 0–17（2 行）</li>
 *   <li>物品入 18–35 + 物品出 18–35</li>
 *   <li>流体入 0–17 + 流体出 0–17</li>
 *   <li>流体入 18–35 + 流体出 18–35</li>
 * </ol>
 * 同一页里「第 i 格输入」与「第 i 格输出」上下相邻：配置料、取产物都在一屏内。
 */
@EventBusSubscriber(modid = MiAe2PatternProvider.MOD_ID, value = Dist.CLIENT)
public final class MeInterfacePagingClient {

    /** 每个槽组 36 个（扩展 / 超大档）。 */
    private static final int GROUP = 36;
    /** 每类输出槽数（2 排）。 */
    private static final int OUT_GROUP = 18;
    private static final int ROWS_VISIBLE = 4;
    private static final int COLS = 9;
    private static final int FIRST_ROW_Y = 20;
    private static final int ROW_HEIGHT = 18;
    private static final int FIRST_COL_X = 8;
    /** 隐藏槽的坐标：挪到画布外，既不渲染也点不到。 */
    private static final int HIDDEN = -100_000;

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private MeInterfacePagingClient() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof MachineScreen screen)) {
            return;
        }
        MachineMenuClient menu = screen.getMenu();
        if (menu == null || menu.getGuiParams() == null) {
            return;
        }
        String path = menu.getGuiParams().blockId.getPath();
        // 机器槽数 = 机器库存里的 stack 总数（物品 + 流体），玩家背包槽不算
        int machineSlots = menu.getMachineInventory().getItemStacks().size()
                + menu.getMachineInventory().getFluidStacks().size();
        // 诊断：把「打开的是哪个方块、多少个机器槽」打出来，方便判断是不是跑到了预期分支
        var mi = menu.getMachineInventory();
        LOGGER.info("[接口翻页] 打开界面 blockId={} menu.slots={} 机器槽(库存)={} 物品栈={} 流体栈={}",
                path, menu.slots.size(), machineSlots, mi.getItemStacks().size(), mi.getFluidStacks().size());
        // 判据用**机器槽数**而不是 blockId：原版档 36 个（9×4），扩展/超大档 108 个（36+18 输入各两组 + 18+18 输出）。
        // ⚠️ 这两处数字必须与 ModHatches 的注册参数保持一致 —— 2026-11-07 把输出从 36 改 18 后，
        //    这里残留的旧阈值（≥144）让翻页直接不生效、界面乱套。
        int expectedTierSlots = 2 * GROUP + 2 * OUT_GROUP; // 36+36 输入 + 18+18 输出
        if (machineSlots != expectedTierSlots) {
            return;
        }
        new Pager(screen, event::addListener).install();
    }

    /** 一个界面实例对应一个翻页控制器（页码是玩家私有的）。 */
    private static final class Pager {
        private final MachineScreen screen;
        private final java.util.function.Consumer<net.minecraft.client.gui.components.events.GuiEventListener> addListener;
        private int page;

        Pager(MachineScreen screen,
              java.util.function.Consumer<net.minecraft.client.gui.components.events.GuiEventListener> addListener) {
            this.screen = screen;
            this.addListener = addListener;
        }

        void install() {
            layout();

            int xSize = this.screen.getXSize();
            int baseX = this.screen.x() + xSize - 68;
            int baseY = this.screen.y() + 4;

            Button prev = Button.builder(Component.literal("◀"), b -> setPage(this.page - 1))
                    .bounds(baseX, baseY, 20, 20)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.translatable("gui.mi_ae2_pattern_provider.page_prev")))
                    .build();
            Button next = Button.builder(Component.literal("▶"), b -> setPage(this.page + 1))
                    .bounds(baseX + 22, baseY, 20, 20)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                            Component.translatable("gui.mi_ae2_pattern_provider.page_next")))
                    .build();
            this.addListener.accept(prev);
            this.addListener.accept(next);
        }

        private void setPage(int requested) {
            int clamped = Math.max(0, Math.min(2, requested));
            if (clamped == this.page) {
                return;
            }
            this.page = clamped;
            layout();
        }

        /** 按当前页把 144 个机器槽重新摆位（其余挪出画布）。 */
        private void layout() {
            // 分类而**不依赖菜单里的索引顺序**：
            // - 玩家背包槽（container 是 player Inventory）一律不动；
            // - 机器槽里，MFluidSlot 是流体槽，其余是物品槽；
            // - 每组内部按「先输入后输出」切分（与 BE 构造 SlotPositions 的顺序一致）。
            List<Slot> itemSlots = new java.util.ArrayList<>();
            List<Slot> fluidSlots = new java.util.ArrayList<>();
            for (Slot slot : this.screen.getMenu().slots) {
                if (slot.container instanceof net.minecraft.world.entity.player.Inventory) {
                    continue;
                }
                if (slot.getClass().getSimpleName().contains("Fluid")) {
                    fluidSlots.add(slot);
                } else {
                    itemSlots.add(slot);
                }
            }
            // 每组 = 输入 36 + 输出 18 = 54
            int perType = GROUP + OUT_GROUP;
            if (itemSlots.size() < perType || fluidSlots.size() < perType) {
                LOGGER.warn("[接口翻页] 槽位分组不符预期（物品 {} / 流体 {}，每组需要 {}），跳过布局",
                        itemSlots.size(), fluidSlots.size(), perType);
                return;
            }
            // 先全部隐藏
            for (int i = 0; i < perType; i++) {
                setPos(itemSlots.get(i), HIDDEN, HIDDEN);
                setPos(fluidSlots.get(i), HIDDEN, HIDDEN);
            }

            // 3 页（每页 4 排 = 36 格，输出集中在最后一页）：
            //   页 0 = 物品入 0–35（4 排）
            //   页 1 = 流体入 0–35（4 排）
            //   页 2 = 物品出 0–17（2 排）+ 流体出 0–17（2 排）
            switch (this.page) {
                case 0 -> place(itemSlots.subList(0, GROUP), 0);
                case 1 -> place(fluidSlots.subList(0, GROUP), 0);
                default -> {
                    place(itemSlots.subList(GROUP, GROUP + OUT_GROUP), 0);
                    place(fluidSlots.subList(GROUP, GROUP + OUT_GROUP), 2);
                }
            }
        }

        /** 把一整组 36 个槽摆成 4 排 × 9。 */
        private void place(List<Slot> group, int startRow) {
            for (int n = 0; n < group.size() && n < COLS * ROWS_VISIBLE; n++) {
                int row = startRow + n / COLS;
                int col = n % COLS;
                setPos(group.get(n), FIRST_COL_X + col * ROW_HEIGHT, FIRST_ROW_Y + row * ROW_HEIGHT);
            }
        }

        private void setPos(Slot slot, int x, int y) {
            // Slot#x/y 在 1.21.1 是 final ⇒ 走 SlotPositionAccessor
            com.miae2.mixin.SlotPositionAccessor accessor = (com.miae2.mixin.SlotPositionAccessor) slot;
            accessor.miae2$setX(x);
            accessor.miae2$setY(y);
        }
    }
}
