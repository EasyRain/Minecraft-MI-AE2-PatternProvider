package com.miae2.client;

import appeng.client.gui.widgets.AE2Button;
import com.miae2.net.HatchNamePayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 仓的重命名界面：一个**紧凑**的 AE 风格对话框（没有背包区）。
 *
 * <p>做法与 AE 一致（2026-11-09 用户指出）：AE 的对话框不是一整张图，而是
 * 「`background.png` 那种 1px 描边 + 纯色底拉伸成小框」+ 输入框 + 文字 + 按钮。
 * 这里就用 AE 的实测配色直接画：
 * <ul>
 *   <li>面板：描边 {@code (65,63,84)}、底 {@code (203,204,212)}；</li>
 *   <li>输入框：描边 {@code (105,109,136)}、内里 {@code (154,159,180)}、底边压深 {@code (77,77,103)}；</li>
 *   <li>按钮：直接用 AE 自己的 {@link AE2Button}（它会按 AE 样式表取贴图）。</li>
 * </ul>
 *
 * <p><b>不要高斯虚化</b>：见 {@link #renderBackground} 的空覆写。
 */
@OnlyIn(Dist.CLIENT)
public final class HatchRenameScreen extends Screen {

    /** 我们自己的对话框底图（176×96，用户手改）。 */
    private static final net.minecraft.resources.ResourceLocation TEXTURE =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    com.miae2.MiAe2PatternProvider.MOD_ID, "textures/gui/rename_dialog_bg.png");

    // AE2 实测配色（仅作参考，实际由底图提供）
    private static final int OVERLAY = 0x80000000;
    private static final int PANEL_BG = 0xFFCBCED4;
    private static final int PANEL_EDGE = 0xFF413F54;
    private static final int FIELD_EDGE = 0xFF696D88;
    private static final int FIELD_BG = 0xFF9A9FB4;
    private static final int FIELD_EDGE_BOTTOM = 0xFF4D4D67;

    /** 小框尺寸：标题 + 输入框 + 一排按钮，就这些。 */
    private static final int PANEL_W = 176;
    private static final int PANEL_H = 96;

    private static final int FIELD_X = 12;
    private static final int FIELD_Y = 30;
    private static final int FIELD_W = 152;
    private static final int FIELD_H = 16;

    private static final int BUTTON_W = 50;
    private static final int BUTTON_H = 20;
    private static final int BUTTON_Y = 58;

    private final BlockPos pos;
    private EditBox field;
    private int left;
    private int top;

    private HatchRenameScreen(BlockPos pos) {
        super(Component.translatable("gui.mi_ae2_pattern_provider.rename_title"));
        this.pos = pos;
    }

    /** 由网络包调用（客户端线程）。 */
    public static void open(BlockPos pos) {
        Minecraft.getInstance().setScreen(new HatchRenameScreen(pos));
    }

    @Override
    protected void init() {
        this.left = (this.width - PANEL_W) / 2;
        this.top = (this.height - PANEL_H) / 2;

        this.field = new EditBox(this.font,
                this.left + FIELD_X + 3, this.top + FIELD_Y + 4, FIELD_W - 6, 10, Component.empty());
        this.field.setMaxLength(64);
        this.field.setBordered(false);       // 边框用 render() 里画的那圈
        this.field.setTextColor(0xFFFFFF);   // AE 的输入框里是白字
        this.field.setHint(Component.translatable("gui.mi_ae2_pattern_provider.rename_hint"));
        this.addRenderableWidget(this.field);
        this.setInitialFocus(this.field);

        int x = this.left + (PANEL_W - (BUTTON_W * 2 + 8)) / 2;
        this.addRenderableWidget(new AE2Button(x, this.top + BUTTON_Y, BUTTON_W, BUTTON_H,
                Component.translatable("gui.done"), b -> submit()));
        this.addRenderableWidget(new AE2Button(x + BUTTON_W + 8, this.top + BUTTON_Y, BUTTON_W, BUTTON_H,
                Component.translatable("gui.cancel"), b -> onClose()));
    }

    private void submit() {
        String name = this.field == null ? "" : this.field.getValue();
        PacketDistributor.sendToServer(new HatchNamePayloads.SetName(this.pos, name));
        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter / 小键盘 Enter
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, OVERLAY);

        int x = this.left;
        int y = this.top;

        // 底图（面板描边 + 底色 + 输入框框都由它提供）
        graphics.blit(TEXTURE, x, y, 0, 0, PANEL_W, PANEL_H);

        // 标题（不加阴影：像素汉字加阴影容易糊在一起，AE 原版也没有）
        graphics.drawString(this.font, this.title, x + 8, y + 8, 0x000000, false);

        super.render(graphics, mouseX, mouseY, partialTick);

        // 空输入时把提示画在框里（白字太亮，用半透明灰）
        int fx = x + FIELD_X + 1;
        int fy = y + FIELD_Y + 1;
        if (this.field != null && this.field.getValue().isEmpty()) {
            graphics.drawString(this.font,
                    Component.translatable("gui.mi_ae2_pattern_provider.rename_hint"),
                    fx + 4, fy + 4, 0xB0B4C4, false);
        }
    }

    /**
     * **必须空覆写**：1.21.1 的 `Screen#render()` 会自动调 4 参 `renderBackground(...)`，
     * 内部走 `renderBlurredBackground()` → 高斯虚化整个帧缓冲。我们的面板先画、`super.render()`
     * 后触发虚化 ⇒ 背景糊、按钮清晰（2026-11-09 实机症状，与 Hojojutsu Fix #31 同因）。
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 故意留空：不要高斯虚化
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
