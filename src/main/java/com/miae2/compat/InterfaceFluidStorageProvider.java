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
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.ViewGroup;

/**
 * 给 36 槽接口仓提供**流体存储**（Jade 标准管道），这样悬浮提示里的流体行就是原版 AE 那套样式：
 * 进度条填充 + 当前量，按住 Shift 才显示 `/ 容量`，空余部分聚合成一行「空 xxxB」。
 *
 * <p>为什么接标准管道而不是自己画元素（2026-11-07 连续返工后的结论）：AE / ExtendedAE 的悬浮提示
 * 用的就是 Jade 的 {@code FluidView} 渲染器（{@link FluidView#writeDefault} 生成数据，
 * Jade 客户端统一渲染）。自己用 {@code IElementHelper.progress/fluid} 拼是拼不出同样效果的
 * （文字颜色、流体材质、空槽聚合都不一样）。
 *
 * <p>只处理 {@code >9} 槽的档位：原版档由 MI 自己的 provider 显示，避免重复。
 */
public final class InterfaceFluidStorageProvider
        implements IServerExtensionProvider<CompoundTag>,
        snownee.jade.api.view.IClientExtensionProvider<CompoundTag, FluidView> {

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(MiAe2PatternProvider.MOD_ID, "interface_fluids");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public List<ViewGroup<CompoundTag>> getGroups(Accessor<?> accessor) {
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
        // 空余部分聚合成一行（AE 的 `空 2.17kB` 就是这么来的）
        if (free > 0) {
            views.add(FluidView.writeDefault(JadeFluidObject.empty(), free));
        }
        return views.isEmpty() ? List.of() : List.of(new ViewGroup<>(views));
    }

    /**
     * 客户端侧：把服务端给的 `CompoundTag` 解成 {@link FluidView} 交给 Jade 的标准渲染器。
     *
     * <p>**两边都要注册**（`registerFluidStorage` + `registerFluidStorageClient`），
     * 只注册服务端时数据会取到但没人渲染 —— 表现就是「流体条完全不显示」（2026-11-07 实测）。
     */
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

    /** 仅用于日志/调试：确认 AEFluidKey 的引用不会带来类加载问题。 */
    static boolean selfCheck() {
        return AEFluidKey.class != null;
    }
}
