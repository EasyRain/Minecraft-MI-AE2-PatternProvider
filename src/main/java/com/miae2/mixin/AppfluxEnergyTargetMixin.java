package com.miae2.mixin;

import appeng.api.networking.IGrid;
import aztech.modern_industrialization.machines.multiblocks.HatchBlockEntity;
import aztech.modern_industrialization.machines.multiblocks.HatchTypes;
import com.miae2.machines.blockentities.MePatternProviderBlockEntity;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把 AppliedFlux「感应卡」的能量投送目标，从「供应仓周围的 6 个方块」改成「处理阵列的能源输入仓」。
 *
 * <p>原理：AppliedFlux 的 {@code EnergyTicker} 每 tick 对 6 个方向各调一次
 * {@code EnergyHandler.send(cache, side, storage, source)}，而后者最终只通过
 * {@code EnergyCapCache#getEnergyCap(cap, side)} 去取「侧邻方块」的能量能力。
 * <b>所有能道路径（MI 的 EU 能力、Mekanism、grandpower、FluxNetworks，以及最后兜底的通用 FE 能力）
 * 都汇聚在这一个方法上</b>，所以只要在这里把「查哪个位置」换掉，能量就会定向送进阵列能源仓——
 * 而 AppliedFlux 自己的速率限制（{@code EnergyTickRecord}）、失效标记（{@code blocked}）、
 * 能力失效监听、FE→EU 换算全部原样复用，我们一行能量换算代码都不用写。
 *
 * <p>本 mixin <b>不依赖 AppliedFlux 编译期</b>（字符串 {@code targets}），也不用 {@code @Shadow}
 * （构造参数自己存下来，避免对方改字段名时 mixin 直接报错），并且 {@code require = 0}：
 * AppliedFlux 缺席或改签名时只是不再重定向，一切照旧、不会崩。
 */
@Pseudo
@Mixin(targets = "com.glodblock.github.appflux.common.me.energy.EnergyCapCache", remap = false)
public abstract class AppfluxEnergyTargetMixin {

    /** 自己记下构造参数（避免 @Shadow）。注入没贴上时保持 null，此时一律走原逻辑。 */
    @Unique
    private ServerLevel miae2$level;
    @Unique
    private BlockPos miae2$pos;

    /**
     * 按能力类型缓存「指向能源仓」的查询句柄，避免每 tick 重建。
     *
     * <p>⚠️ <b>不要给 {@code @Unique} 字段写初始化器</b>：实测 Mixin 不会执行它
     * （曾因此 NPE 崩服）。一律在下面那个构造注入里显式赋值——那条路径是验证过可靠的。
     */
    @Unique
    private Map<BlockCapability<?, Direction>, BlockCapabilityCache<?, Direction>[]> miae2$caches;
    /** 缓存所对应的能源仓位置集合；集合变了就整体清空重建。 */
    @Unique
    private List<BlockPos> miae2$targets;

    @Inject(
            method = "<init>(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Ljava/util/function/Supplier;)V",
            at = @At("TAIL"),
            remap = false,
            require = 0
    )
    private void miae2$captureCtorArgs(ServerLevel world, BlockPos pos, Supplier<IGrid> gridSupplier, CallbackInfo ci) {
        this.miae2$level = world;
        this.miae2$pos = pos;
        this.miae2$caches = new IdentityHashMap<>();
        this.miae2$targets = List.of();
    }

    @Inject(method = "getEnergyCap", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private <T> void miae2$retargetToArrayEnergyHatch(BlockCapability<T, Direction> cap, Direction side,
                                                      CallbackInfoReturnable<T> cir) {
        if (this.miae2$level == null || this.miae2$pos == null) {
            return; // 构造注入没生效 → 交回原逻辑
        }
        if (!(this.miae2$level.getBlockEntity(this.miae2$pos) instanceof MePatternProviderBlockEntity provider)) {
            return; // 不是本 mod 的供应仓 → 原版行为
        }

        List<BlockPos> hatches = provider.miae2$getEnergyInputHatchPositions();
        int index = side.get3DDataValue();
        if (index >= hatches.size()) {
            // 方向数多于能源仓数：多出来的方向不供能（保持「给阵列供电」的语义，而不是回头喂邻居）
            cir.setReturnValue(null);
            return;
        }

        BlockPos target = hatches.get(index);
        if (!(this.miae2$level.getBlockEntity(target) instanceof HatchBlockEntity hatch)
                || hatch.getHatchType() != HatchTypes.ENERGY_INPUT) {
            // 阵列已散架 / 能源仓被拆：不供能。ShapeMatcher 成形时会戳一次相邻方块的能力失效回调，
            // 让 AppliedFlux 把内侧记下的 blocked 清掉，所以这里不会把它永久锁死。
            cir.setReturnValue(null);
            return;
        }

        // 用 null 作为查询方向：能源仓是机器而不是线缆，任意面都接受
        cir.setReturnValue(this.miae2$resolve(cap, index, target, hatches));
    }

    @Unique
    @SuppressWarnings("unchecked")
    private <T> T miae2$resolve(BlockCapability<T, Direction> cap, int index, BlockPos target, List<BlockPos> targets) {
        // 双保险：即使构造注入没生效（那也意味着走不到这里），也绝不 NPE
        if (this.miae2$caches == null) {
            this.miae2$caches = new IdentityHashMap<>();
        }
        if (this.miae2$targets == null || !targets.equals(this.miae2$targets)) {
            this.miae2$caches.clear();
            this.miae2$targets = targets;
        }
        BlockCapabilityCache<?, Direction>[] perSide = this.miae2$caches.computeIfAbsent(
                cap, ignored -> new BlockCapabilityCache[6]);
        if (perSide[index] == null) {
            perSide[index] = BlockCapabilityCache.create(cap, this.miae2$level, target, null);
        }
        return (T) perSide[index].getCapability();
    }
}
