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
    private static final int ROWS_VISIBLE = 4;
    private static final int COLS = 9;
    private static final int FIRST_ROW_Y = 20;
    private static final int ROW_HEIGHT = 18;
    private static final int FIRST_COL_X = 8;
    /** 隐藏槽的坐标：挪到画布外，既不渲染也点不到。 */
    private static final int HIDDEN = -100_000;

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
        // 只有 36 槽的两个档位需要翻页；原版档（9 槽）保持 MI 原生界面
        if (!path.equals("me_extended_interface_hatch") && !path.equals("me_oversize_interface_hatch")) {
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
            int clamped = Math.max(0, Math.min(3, requested));
            if (clamped == this.page) {
                return;
            }
            this.page = clamped;
            layout();
        }

        /** 按当前页把 144 个机器槽重新摆位（其余挪出画布）。 */
        private void layout() {
            List<Slot> slots = this.screen.getMenu().slots;
            if (slots.size() < GROUP * 4) {
                return;
            }
            // 先全部隐藏，再摆当前页
            for (int i = 0; i < GROUP * 4; i++) {
                setPos(slots.get(i), HIDDEN, HIDDEN);
            }

            // 每页两组：输入组 + 输出组；组内偏移由页决定（0 或 18）
            int half = (this.page % 2) * 18;
            int inputGroup = this.page < 2 ? 0 : 2 * GROUP;      // 页 1/2 = 物品，页 3/4 = 流体
            int outputGroup = inputGroup + GROUP;

            place(slots, inputGroup, half, 0);        // 输入：第 0、1 行
            place(slots, outputGroup, half, 2);       // 输出：第 2、3 行
        }

        private void place(List<Slot> slots, int groupStart, int offsetInGroup, int startRow) {
            for (int n = 0; n < 18; n++) {
                int index = groupStart + offsetInGroup + n;
                if (index >= groupStart + GROUP) {
                    return;
                }
                int row = startRow + n / COLS;
                int col = n % COLS;
                if (row >= startRow + 2) {
                    return;
                }
                setPos(slots.get(index), FIRST_COL_X + col * ROW_HEIGHT, FIRST_ROW_Y + row * ROW_HEIGHT);
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
