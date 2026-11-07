package com.miae2.ae;

import appeng.api.stacks.AEKeyTypes;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.util.ConfigInventory;
import com.glodblock.github.extendedae.common.inventory.OversizeConfigInv;
import com.glodblock.github.extendedae.util.Ae2Reflect;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * 把 {@link InterfaceLogic} 内部的 {@code config} / {@code storage} 换成 ExtendedAE 的
 * {@link OversizeConfigInv} —— **完全照抄 ExtendedAE 自己 Oversize 接口的做法**。
 *
 * <p>为什么必须照抄：AE2 的 {@code ConfigInventory} 对物品槽的容量被堆叠大小（64）锁死，
 * {@code setCapacity()} 改不动；ExtendedAE 是在 {@code OversizeConfigInv#getMaxAmount} 里返回
 * {@code 堆叠大小 × EAEConfig.getOversizeMultiplier()} 才做到 16 倍（1024）。
 *
 * <p>照抄的好处（用户 2026-11-07 的判断）：EAE 已定型，以后附属（例如 EAEP）对 EAE 的改动
 * （界面分页、数量按钮、菜单类型检查等）会以同样方式作用于我们的方块 —— 因为我们的逻辑对象
 * 在类型与内部结构上与 EAE 的 Oversize 接口**一模一样**。
 *
 * <p>用到的公开工具（同样是 EAE 自己的）：
 * {@code Ae2Reflect.setInterfaceConfig/setInterfaceStorage}（换字段）、
 * {@code Ae2Reflect.isInterfaceSlotAllowed}（槽位过滤器）、
 * {@code Ae2Reflect.onInterfaceConfigChange/onInterfaceStorageChange}（变更回调）。
 */
public final class OversizeInterfaceInventories {

    private static final Logger LOGGER = LogUtils.getLogger();

    private OversizeInterfaceInventories() {
    }

    /**
     * 把给定接口逻辑的两个 inventory 换成 Oversize 版本。
     *
     * @param logic 我们的 {@link MeInterfaceLogic}
     * @param slots 配置槽数量（36）
     */
    public static void apply(InterfaceLogic logic, int slots) {
        try {
            // 配置槽：EAE 对 config 传的是 null 过滤器（配置由玩家自由填）
            ConfigInventory config = new OversizeConfigInv(
                    AEKeyTypes.getAll(),
                    null,
                    GenericStackInv.Mode.CONFIG_STACKS,
                    slots,
                    () -> Ae2Reflect.onInterfaceConfigChange(logic),
                    false);
            Ae2Reflect.setInterfaceConfig(logic, config);

            // 库存：过滤器沿用 EAE 的「该槽是否允许该键」
            ConfigInventory storage = new OversizeConfigInv(
                    AEKeyTypes.getAll(),
                    (slot, key) -> Ae2Reflect.isInterfaceSlotAllowed(logic, slot, key),
                    GenericStackInv.Mode.STORAGE,
                    slots,
                    () -> Ae2Reflect.onInterfaceStorageChange(logic),
                    false);
            Ae2Reflect.setInterfaceStorage(logic, storage);

            // 换完之后必须再摁一次容量（与 EAE 的 TileOversizeInterface 构造尾部一致）
            logic.getConfig().useRegisteredCapacities();
            logic.getStorage().useRegisteredCapacities();
            LOGGER.info("[接口仓] 超大档已切换为 ExtendedAE 的 Oversize 库存实现（{} 槽，物品上限 {}）",
                    slots, logic.getConfig().getMaxAmount(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.STONE)));
        } catch (Throwable t) {
            LOGGER.warn("[接口仓] 切换 Oversize 库存失败，超大档将退化为普通容量（每槽 64）", t);
        }
    }
}
