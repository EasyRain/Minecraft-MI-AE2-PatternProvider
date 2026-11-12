package com.miae2.compat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * ExtendedAE-Plus「频道卡」的接线兼容层。
 *
 * <p>EAEP 的频道卡通过 mixin 挂在 **AE2 的方块实体**（`AEBaseBlockEntity` / 各种 host mixin）上，
 * 读档时由 {@code ChannelCardConnectionController#onLoaded()} 重新建立无线链路。我们的仓是
 * **MI 的方块实体**，那些 mixin 挂不上 ⇒ 读档后频道卡断链、显示离线，重插卡也不管用
 * （只有挖掉重放、走一遍"新放上去"的流程才会重新注册）。
 *
 * <p>这里按 EAEP 自己的构造方式补一份控制器：
 * <pre>
 * new ChannelCardConnectionController(
 *     upgrades,        // 升级卡库存
 *     fallbackOwner,   // 节点 owner 的 UUID（我们读档后已从 placedBy 恢复）
 *     endpoint,        // GenericNodeEndpointImpl(方块实体, IGridNode)
 *     stateChanged,    // 保存/同步
 *     wakeNode,        // 唤醒节点
 *     clientSide)
 * ChannelCardConnectionController.register(be, controller)
 * controller.onLoaded()
 * </pre>
 *
 * <p>全程反射：没装 EAEP、或它改版改了签名，都只是 {@code null} / 静默跳过，不影响其它功能。
 */
public final class ChannelCardCompat {

    private static boolean probed;
    private static Constructor<?> controllerCtor;
    private static Constructor<?> endpointCtor;
    private static Method registerMethod;
    private static Method onLoadedMethod;
    private static Method onChangedMethod;
    private static Method tickMethod;

    private ChannelCardCompat() {
    }

    /** 控制器句柄（EAEP 的对象，用 Object 保存避免编译期依赖）。 */
    public static final class Handle {
        private final Object controller;

        private Handle(Object controller) {
            this.controller = controller;
        }
    }

    /**
     * 给一个 MI 方块实体接上 EAEP 的频道卡控制器；EAEP 不在场时返回 null。
     *
     * @param be         方块实体本体（EAEP 用它当注册键）
     * @param upgrades   升级卡库存的 supplier
     * @param owner      节点 owner UUID 的 supplier（可为 null）
     * @param node       该方块实体的 {@code IGridNode} supplier
     * @param stateChanged 保存/同步回调
     * @param wakeNode   唤醒节点回调
     * @param clientSide 是否逻辑客户端
     */
    public static Handle attach(BlockEntity be,
                                Supplier<?> upgrades,
                                Supplier<UUID> owner,
                                Supplier<?> node,
                                Runnable stateChanged,
                                Runnable wakeNode,
                                BooleanSupplier clientSide) {
        probe();
        if (controllerCtor == null || endpointCtor == null || registerMethod == null) {
            return null;
        }
        try {
            Supplier<?> endpoint = () -> {
                try {
                    return endpointCtor.newInstance((Supplier<BlockEntity>) () -> be, node);
                } catch (Throwable t) {
                    return null;
                }
            };
            Object controller = controllerCtor.newInstance(upgrades, owner, endpoint, stateChanged, wakeNode, clientSide);
            registerMethod.invoke(null, be, controller);
            if (onLoadedMethod != null) {
                onLoadedMethod.invoke(controller);
            }
            return new Handle(controller);
        } catch (Throwable t) {
            return null;
        }
    }

    /** 读档后重新建立无线链路（等同 AE2 方块实体的 onLoaded 钩子）。 */
    public static void onLoaded(Handle handle) {
        if (handle == null || onLoadedMethod == null) {
            return;
        }
        try {
            onLoadedMethod.invoke(handle.controller);
        } catch (Throwable ignored) {
            // 上游改签名也不能影响启动
        }
    }

    /** 升级卡变化 / 节点变化时让 EAEP 重新评估链路。 */
    public static void onChanged(Handle handle) {
        if (handle == null || onChangedMethod == null) {
            return;
        }
        try {
            onChangedMethod.invoke(handle.controller);
        } catch (Throwable ignored) {
        }
    }

    /** 每 tick 调用（EAEP 内部自己判断要不要干活）。 */
    public static void tick(Handle handle) {
        if (handle == null || tickMethod == null) {
            return;
        }
        try {
            tickMethod.invoke(handle.controller);
        } catch (Throwable ignored) {
        }
    }

    private static void probe() {
        if (probed) {
            return;
        }
        probed = true;
        try {
            Class<?> controller = Class.forName("com.extendedae_plus.util.wireless.ChannelCardConnectionController");
            Class<?> endpoint = Class.forName("com.extendedae_plus.ae.wireless.endpoint.GenericNodeEndpointImpl");
            controllerCtor = controller.getConstructor(
                    Supplier.class, Supplier.class, Supplier.class,
                    Runnable.class, Runnable.class, BooleanSupplier.class);
            endpointCtor = endpoint.getConstructor(Supplier.class, Supplier.class);
            registerMethod = controller.getMethod("register", BlockEntity.class, controller);
            onLoadedMethod = controller.getMethod("onLoaded");
            onChangedMethod = controller.getMethod("onUpgradesChanged");
            tickMethod = controller.getMethod("tick");
        } catch (Throwable absentOrChanged) {
            controllerCtor = null;
            endpointCtor = null;
            registerMethod = null;
            onLoadedMethod = null;
            onChangedMethod = null;
            tickMethod = null;
        }
    }
}
