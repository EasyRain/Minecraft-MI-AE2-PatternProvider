package com.miae2.mixin;

import aztech.modern_industrialization.machines.MachineBlockEntity;
import aztech.modern_industrialization.machines.gui.GuiComponent;
import aztech.modern_industrialization.machines.guicomponents.SlotPanel;
import com.miae2.items.OverclockSlotGate;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 给「超频模块槽」的服务端准入判定补上机器上下文，从而拒收白装的量子超频模块
 * （用户 m04850：「既然只有阵列里才生效，是否可以让量子的升级无法插入到普通的机器里」）。
 *
 * <h2>为什么改这里</h2>
 * {@code SlotPanel#setupMenu(MenuFacade)} 是 MI 把一整套槽位注册进机器菜单的唯一入口，
 * 而 {@code SlotPanel} 自己持有 {@code machine} 字段。把传进去的 {@link GuiComponent.MenuFacade}
 * 换成 {@link OverclockSlotGate#gateFacade} 包过的版本，超频槽在加入菜单之前就拿到了机器 ——
 * 之后无论玩家是拖拽、点击还是 shift 快速移动，服务端都会先问过我们那条规则。
 *
 * <p>为什么不去改 {@code SlotPanel.SlotType#mayPlace}：那个谓词只有一个 {@code ItemStack} 参数，
 * 天生不知道机器；而 {@code OverdriveComponent#setStackServer} 又太晚（物品已经被从光标扣走，
 * 这时拒收就是让玩家丢件）。详见 {@link OverclockSlotGate} 的类注释。
 *
 * <p>范围：只包超频槽那一格，其它槽（升级、红石、外壳…）行为零变化；客户端那套槽由
 * {@code SlotPanelClient} 自己建，本 mixin 不涉及。
 */
@Mixin(value = SlotPanel.class, remap = false)
public abstract class OverdriveSlotGateMixin {

    @Shadow
    @Final
    private MachineBlockEntity machine;

    /** {@code setupMenu} 里只有一处 {@code Consumer#accept}：就是逐个跑槽位工厂那一次。 */
    @Redirect(method = "setupMenu", at = @At(value = "INVOKE",
            target = "Ljava/util/function/Consumer;accept(Ljava/lang/Object;)V"), remap = false)
    private void miae2$gateOverdriveSlot(Consumer<GuiComponent.MenuFacade> factory, Object menu) {
        factory.accept(OverclockSlotGate.gateFacade((GuiComponent.MenuFacade) menu, this.machine));
    }
}
