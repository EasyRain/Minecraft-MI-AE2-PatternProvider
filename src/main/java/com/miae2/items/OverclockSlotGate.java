package com.miae2.items;

import aztech.modern_industrialization.inventory.HackySlot;
import aztech.modern_industrialization.inventory.MIInventory;
import aztech.modern_industrialization.inventory.SlotGroup;
import aztech.modern_industrialization.machines.MachineBlockEntity;
import aztech.modern_industrialization.machines.gui.GuiComponent;
import aztech.modern_industrialization.machines.gui.MachineGuiParameters;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 把 MI 的「超频模块槽」包一层，让它知道自己在<b>哪台机器</b>上 —— 这是「量子超频模块只许处理阵列」
 * 这条规则在 GUI 路径上的落点。
 *
 * <h2>为什么必须包一层</h2>
 * MI 的槽位准入判定是 {@code SlotPanel.SlotType#mayPlace(ItemStack)}，只拿到物品、<b>拿不到机器</b>
 * （{@code SlotPanel} 里那个匿名 {@code HackySlot} 的 {@code mayPlace} 就是直接转调它）。
 * 而 {@code OverdriveComponent#onUse} / {@code setStackServer} 虽然拿得到机器，却分别在
 * 「右键」与「已经放进去之后」两个阶段 —— 后者退回物品会让玩家丢件（服务端已经把物品从光标扣走了）。
 * 所以正确的落点是<b>服务端建槽时</b>：{@code OverdriveSlotGateMixin} 把
 * {@code SlotPanel#setupMenu} 收到的 {@link GuiComponent.MenuFacade} 换成这里包过的版本，
 * 超频槽在加入菜单前就被包成知道机器的那一个。
 *
 * <h2>只动准入判定，别的什么都不动</h2>
 * <ul>
 *   <li>只处理 {@link SlotGroup#OVERDRIVE_MODULE} 这一格；升级槽、红石槽、外壳槽原样透传；</li>
 *   <li>槽位数量、位置（x/y）、{@code getMaxStackSize}、内容的读/写、{@code DropableComponent} 掉落全都不变
 *       —— 覆写的方法只有 {@code mayPlace}，其余全部委托给 MI 原本那个槽；</li>
 *   <li>只在<b>服务端</b>生效（客户端那套槽是 {@code SlotPanelClient} 自己建的，这里不碰），
 *       所以判定为假时是「服务端拒收」，物品不会离开玩家光标。</li>
 * </ul>
 */
public final class OverclockSlotGate {

    /**
     * 包装服务端建槽用的 {@link GuiComponent.MenuFacade}：只有超频模块槽会被换成
     * {@link MachineAwareOverdriveSlot 知道机器的那一个}，其它槽一律原样注册。
     */
    public static GuiComponent.MenuFacade gateFacade(GuiComponent.MenuFacade delegate, MachineBlockEntity machine) {
        return new GatedMenuFacade(delegate, machine);
    }

    private static final class GatedMenuFacade implements GuiComponent.MenuFacade {

        private final GuiComponent.MenuFacade delegate;
        private final MachineBlockEntity machine;

        private GatedMenuFacade(GuiComponent.MenuFacade delegate, MachineBlockEntity machine) {
            this.delegate = delegate;
            this.machine = machine;
        }

        @Override
        public void addSlotToMenu(Slot slot, SlotGroup group) {
            this.delegate.addSlotToMenu(wrap(slot, group, this.machine), group);
        }

        @Override
        public MachineGuiParameters getGuiParams() {
            return this.delegate.getGuiParams();
        }

        @Override
        public MIInventory getMachineInventory() {
            return this.delegate.getMachineInventory();
        }
    }

    /** 非超频槽、或不是 MI 那套 {@link HackySlot} 的槽，一律原样返回。 */
    private static Slot wrap(Slot slot, SlotGroup group, MachineBlockEntity machine) {
        if (group != SlotGroup.OVERDRIVE_MODULE || !(slot instanceof HackySlot hackySlot)) {
            return slot;
        }
        return new MachineAwareOverdriveSlot(hackySlot, machine);
    }

    /**
     * 知道机器的超频槽：只覆写 {@code mayPlace}，其余全部委托。
     *
     * <p>委托走的是 {@link HackySlot} 那两个 {@code public final} 的 {@code getItem()}/{@code set()}：
     * 它们内部会转到 MI 原本那个匿名槽的 {@code getRealStack()}/{@code setRealStack()}
     * （即组件取值 / {@code setStackServer}），所以读写路径与没包的时候逐字节一致。
     */
    private static final class MachineAwareOverdriveSlot extends HackySlot {

        private final HackySlot delegate;
        private final MachineBlockEntity machine;

        private MachineAwareOverdriveSlot(HackySlot delegate, MachineBlockEntity machine) {
            super(delegate.x, delegate.y);
            this.delegate = delegate;
            this.machine = machine;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            // 先问我们自己的规则（量子模块在白装的地方直接拒收），再问 MI 原本的规则
            return !OverclockModules.blockedIn(this.machine, stack) && this.delegate.mayPlace(stack);
        }

        @Override
        public int getMaxStackSize() {
            return this.delegate.getMaxStackSize();
        }

        @Override
        protected ItemStack getRealStack() {
            return this.delegate.getItem();
        }

        @Override
        protected void setRealStack(ItemStack stack) {
            this.delegate.set(stack);
        }
    }

    private OverclockSlotGate() {
    }

    /** 该槽组是不是超频模块槽（自检里也用这个值断言，避免两处各写一份判断）。 */
    public static boolean isOverdriveSlot(SlotGroup group) {
        return group == SlotGroup.OVERDRIVE_MODULE;
    }
}
