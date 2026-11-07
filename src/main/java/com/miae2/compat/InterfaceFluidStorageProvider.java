package com.miae2.compat;

import appeng.api.stacks.AEFluidKey;
import com.miae2.MiAe2PatternProvider;
import com.miae2.machines.blockentities.MeInterfaceBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.Accessor;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;

/**
 * 给 36 槽接口仓提供**流体存储**（Jade 标准管道），渲染出来就是原版 AE 那套样式。
 *
 * <p><b>为什么必须「延迟到最后再注册」</b>：Jade 的存储 provider 表是
 * 「一个类 → 一个 provider」（后注册的覆盖先注册的）。MI 给 {@code MachineBlockEntity} 注册了自己的
 * 流体 provider，而我们的 {@code MeInterfaceBlockEntity} 是它的子类；只要 MI 比我们晚注册，
 * 我们的 provider 就永远不会被调用（2026-11-07 实测：`getGroups` 一行日志都没有）。
 * 所以 {@link MeProviderJadePlugin} 会在 {@code FMLLoadCompleteEvent} 再注册一次，确保最后写入的是我们。
 *
 * <p>同时保留一个「是否真的被调用」的静态标记：如果没有被调用，插件会退回自己渲染流体行
 * （见 {@code MeProviderJadePlugin}），保证任何情况下都能看到流体信息。
 */
public final class InterfaceFluidStorageProvider
        implements IServerExtensionProvider<CompoundTag>,
        snownee.jade.api.view.IClientExtensionProvider<CompoundTag, FluidView> {

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(MiAe2PatternProvider.MOD_ID, "interface_fluids");

    /** 服务端：本 provider 是否真的被 Jade 调用过（用于决定要不要走兜底渲染）。 */
    private static boolean called;

    public static boolean wasCalled() {
        return called;
    }

    /** 测试/热重载用：清掉标记，让下一次悬停重新判定。 */
    public static void resetCalled() {
        called = false;
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public List<ViewGroup<CompoundTag>> getGroups(Accessor<?> accessor) {
        called = true;
        if (!(accessor.getTarget() instanceof MeInterfaceBlockEntity be) || be.miae2$configSlots() <= 9) {
            return List.of();
        }
        List<CompoundTag> views = new ArrayList<>();
        long free = 0L;

        for (var stack : be.miae2$fluidInputsAll()) {
            long capacity = stack.getCapacity();
            free += Math.max(0L, capacity - stack.getAmount());
            if (!stack.isEmpty()) {
                views.add(FluidView.writeDefault(
                        JadeFluidObject.of(stack.getVariant().getFluid(), stack.getAmount()), capacity));
            }
        }
        for (var stack : be.miae2$fluidOutputsAll()) {
            long capacity = stack.getCapacity();
            free += Math.max(0L, capacity - stack.getAmount());
            if (!stack.isEmpty()) {
                views.add(FluidView.writeDefault(
                        JadeFluidObject.of(stack.getVariant().getFluid(), stack.getAmount()), capacity));
            }
        }
        if (free > 0) {
            views.add(FluidView.writeDefault(JadeFluidObject.empty(), free));
        }
        return views.isEmpty() ? List.of() : List.of(new ViewGroup<>(views));
    }

    /** 客户端：把服务端数据解成 Jade 标准视图。 */
    @Override
    public List<snownee.jade.api.view.ClientViewGroup<FluidView>> getClientGroups(
            Accessor<?> accessor, List<ViewGroup<CompoundTag>> groups) {
        List<snownee.jade.api.view.ClientViewGroup<FluidView>> out = new ArrayList<>();
        for (ViewGroup<CompoundTag> group : groups) {
            List<FluidView> views = new ArrayList<>();
            for (CompoundTag tag : group.views) {
                views.add(FluidView.readDefault(tag));
            }
            if (!views.isEmpty()) {
                out.add(new snownee.jade.api.view.ClientViewGroup<>(views));
            }
        }
        return out;
    }

    static boolean selfCheck() {
        return AEFluidKey.class != null;
    }
}
