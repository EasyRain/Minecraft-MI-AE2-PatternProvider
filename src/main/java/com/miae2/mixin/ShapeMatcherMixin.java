package com.miae2.mixin;

import aztech.modern_industrialization.machines.multiblocks.HatchBlockEntity;
import aztech.modern_industrialization.machines.multiblocks.HatchTypes;
import aztech.modern_industrialization.machines.multiblocks.ShapeMatcher;
import com.miae2.machines.blockentities.MePatternProviderBlockEntity;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import com.miae2.util.IControllerPosHolder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 多方块成形后：
 * <ul>
 *   <li><b>「一仓一供应器」约束</b>：同一个多方块里出现 2 个及以上的本 mod 供应仓时，直接把这次匹配判为
 *       失败（并解除各仓的链接）—— 于是控制器显示「结构无效」。见 {@link #miae2$enforceSingleProvider}。</li>
 *   <li>把控制器位置与阵列内「能源输入仓」的位置写回匹配到的仓（自动命名 / 感应卡能量定向）。</li>
 * </ul>
 */
@Mixin(ShapeMatcher.class)
public abstract class ShapeMatcherMixin {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Shadow
    @Final
    protected BlockPos controllerPos;

    /** MI 的匹配成功标志（private）；置 false 即可让控制器把结构判为无效。 */
    @Shadow
    private boolean matchSuccessful;

    @Shadow
    public abstract List<HatchBlockEntity> getMatchedHatches();

    @Shadow
    public abstract void unlinkHatches();

    @Inject(method = "rematch", at = @At("TAIL"))
    private void miae2$onRematch(Level world, CallbackInfo ci) {
        this.miae2$enforceSingleProvider();

        List<HatchBlockEntity> matched = this.getMatchedHatches();

        List<BlockPos> energyInputs = new ArrayList<>();
        for (HatchBlockEntity hatch : matched) {
            if (hatch.getHatchType() == HatchTypes.ENERGY_INPUT) {
                energyInputs.add(hatch.getBlockPos());
            }
        }
        List<BlockPos> energyHatches = List.copyOf(energyInputs);

        for (HatchBlockEntity hatch : matched) {
            if (hatch instanceof IControllerPosHolder holder) {
                holder.miae2$setControllerPos(this.controllerPos);
                holder.miae2$setEnergyInputHatchPositions(energyHatches);

                // AppliedFlux 的感应卡会把「能量送不进去」的边记为 blocked，只有该位置的能力失效回调
                // 才会解除。阵列成形/改动的这一刻主动戳一遍相邻方块，避免它一直卡在 blocked 而永不再试。
                BlockPos pos = hatch.getBlockPos();
                for (Direction dir : Direction.values()) {
                    world.invalidateCapabilities(pos.relative(dir));
                }
            }
        }
    }

    /**
     * 同一个多方块里只允许存在**一个**本 mod 的样板供应仓；多于一个就把这次匹配判为失败。
     *
     * <p>原因：阵列的合成产物会被写进**聚合库存里任意一个可接纳的输出格**，并不保证落回"发料的那一仓"。
     * 而本 mod 每个仓的锁定、回收、`LOCK_UNTIL_RESULT`（等自己的产物回网）都是**按仓独立**的 ——
     * 产物落到别的仓，那一仓的 returnInv 回调会解锁它自己的逻辑，发料仓却永远等不到自己的产物 → 卡死。
     * 与其引入跨仓归属的复杂度，不如直接不允许：多放一个供应仓 = 结构无效，控制器会显示「结构无效」。
     *
     * <p>置 false 后 MI 的 {@code MultiblockMachineBlockEntity#link()} 不会把 shapeValid 置真；随后
     * {@code onRematch} 走的是"匹配失败"分支（不 rebuild 库存、状态停在 NOT_MATCHED），各仓也已 unlink。
     */
    private void miae2$enforceSingleProvider() {
        if (!this.matchSuccessful) {
            return;
        }
        List<HatchBlockEntity> matched = this.getMatchedHatches();
        int providers = 0;
        for (HatchBlockEntity hatch : matched) {
            if (hatch instanceof MePatternProviderBlockEntity) {
                providers++;
            }
        }
        if (providers <= 1) {
            return;
        }
        LOGGER.warn("该多方块里有 {} 个 ME 样板供应仓（控制器 {}）：同一个阵列只允许一个，已把结构判为无效",
                providers, this.controllerPos);
        this.matchSuccessful = false;
        this.unlinkHatches();
    }
}
