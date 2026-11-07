package com.miae2.client;

import aztech.modern_industrialization.machines.multiblocks.HatchBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * 只在本仓<b>已并入成型多方块</b>时绘制「侧面着色 overlay」。
 *
 * <p>为什么不用 MI 自带的 {@code default_overlays}：那是<b>烘进模型</b>的（每个 casing 一套），
 * 运行时没有开关，会一直画在最上层 —— 未成型时把仓中间的图案挡住，很难看。
 * MI 的 {@link HatchBlockEntity#isMatched()} 恰好就是「这个仓属于一个已成型的结构」，
 * 所以改成由本渲染器按这个状态画。
 *
 * <p>对应的模型 json 里已经<b>删掉</b> {@code default_overlays}，否则会画出两层。
 */
public class MeHatchOverlayRenderer implements BlockEntityRenderer<HatchBlockEntity> {

    public static final ResourceLocation OVERLAY = ResourceLocation.fromNamespaceAndPath(
            "mi_ae2_pattern_provider", "block/me_pattern_provider_hatch_overlay_side");

    /** 稍微外移，避免与方块表面 Z-fighting。 */
    private static final float OUT = 0.002f;
    private static final float LO = -OUT;
    private static final float HI = 1.0f + OUT;

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static final java.util.concurrent.atomic.AtomicBoolean LOGGED =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    @Override
    public void render(HatchBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        if (!be.isMatched()) {
            // 未成型：仓还是它自己的贴图，overlay 只会挡住中间图案 ⇒ 不画
            return;
        }
        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(OVERLAY);
        // 用 entityCutoutNoCull：上面四组顶点的绕序是「从方块外侧看顺时针」，
        // 若用带剔除的 RenderType 会被当背面整面剔掉（实测表现为「成型后完全看不见」）。
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        PoseStack.Pose pose = poseStack.last();
        if (LOGGED.compareAndSet(false, true)) {
            LOGGER.info("[overlay] 首次绘制成型仓 overlay @ {}（sprite={}）", be.getBlockPos(), sprite.contents().name());
        }

        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();

        // 四个水平面（贴图是方框图案，UV 统一铺满即可，方向差异看不出来）
        face(pose, vc, packedLight, u0, v0, u1, v1,
                0, 0, LO, 1, 0, LO, 1, 1, LO, 0, 1, LO, 0, 0, -1);   // NORTH
        face(pose, vc, packedLight, u0, v0, u1, v1,
                1, 0, HI, 0, 0, HI, 0, 1, HI, 1, 1, HI, 0, 0, 1);    // SOUTH
        face(pose, vc, packedLight, u0, v0, u1, v1,
                LO, 0, 0, LO, 0, 1, LO, 1, 1, LO, 1, 0, -1, 0, 0);   // WEST
        face(pose, vc, packedLight, u0, v0, u1, v1,
                HI, 0, 1, HI, 0, 0, HI, 1, 0, HI, 1, 1, 1, 0, 0);    // EAST
    }

    /** 画一个垂直面：四个角按「左下 → 右下 → 右上 → 左上」给。 */
    private static void face(PoseStack.Pose pose, VertexConsumer vc, int light,
                             float u0, float v0, float u1, float v1,
                             float x0, float y0, float z0,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float nx, float ny, float nz) {
        vertex(pose, vc, x0, y0, z0, u0, v1, light, nx, ny, nz);
        vertex(pose, vc, x1, y1, z1, u1, v1, light, nx, ny, nz);
        vertex(pose, vc, x2, y2, z2, u1, v0, light, nx, ny, nz);
        vertex(pose, vc, x3, y3, z3, u0, v0, light, nx, ny, nz);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer vc, float x, float y, float z,
                               float u, float v, int light, float nx, float ny, float nz) {
        vc.addVertex(pose, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
