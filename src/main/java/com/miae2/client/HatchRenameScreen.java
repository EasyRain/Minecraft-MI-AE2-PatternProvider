package com.miae2.client;

import com.miae2.net.HatchNamePayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 仓的重命名界面（纯客户端 Screen，不占菜单）。
 *
 * <p>视觉上模仿 AE2 的原生对话框：深色面板 + 一圈浅色描边 + 居中标题 + 输入框 + 两个按钮。
 * 不复用 AE2 的 `QuartzKnifeMenu`/`ScreenStyle` 的原因（2026-11-09）：那条路的写入点最终落到
 * `AEBaseBlockEntity#setCustomName`，我们是 MI 的方块实体，投不过去；而复用它的界面要给 AE 的
 * `AETextField` 准备一份 `ScreenStyle` 资源，等于把界面绑死在 AE2 内部实现上。
 */
@OnlyIn(Dist.CLIENT)
public final class HatchRenameScreen extends Screen {

    // AE2 对话框配色
    private static final int PANEL_BG = 0xE61A1A1A;
    private static final int PANEL_BORDER = 0xFF6E6E6E;
    private static final int PANEL_INNER = 0xFF2B2B2B;
    private static final int TEXT_COLOR = 0xFFE0E0E0;

    private static final int PANEL_W = 240;
    private static final int PANEL_H = 116;

    private final BlockPos pos;
    private EditBox field;
    private int panelX;
    private int panelY;

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
        this.panelX = (this.width - PANEL_W) / 2;
        this.panelY = (this.height - PANEL_H) / 2;

        this.field = new EditBox(this.font,
                this.panelX + 20, this.panelY + 44, PANEL_W - 40, 20,
                Component.translatable("gui.mi_ae2_pattern_provider.rename_hint"));
        this.field.setMaxLength(64);
        this.field.setHint(Component.translatable("gui.mi_ae2_pattern_provider.rename_hint"));
        this.addRenderableWidget(this.field);
        this.setInitialFocus(this.field);

        int buttonY = this.panelY + PANEL_H - 34;
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> submit())
                .bounds(this.panelX + 20, buttonY, 96, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(this.panelX + PANEL_W - 116, buttonY, 96, 20).build());
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
        // 半透明遮罩 + AE 风格面板
        graphics.fill(0, 0, this.width, this.height, 0x80000000);
        int x = this.panelX;
        int y = this.panelY;
        graphics.fill(x - 1, y - 1, x + PANEL_W + 1, y + PANEL_H + 1, PANEL_BORDER);
        graphics.fill(x, y, x + PANEL_W, y + PANEL_H, PANEL_BG);
        graphics.fill(x + 1, y + 1, x + PANEL_W - 1, y + 2, PANEL_INNER);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, y + 12, TEXT_COLOR);
        graphics.drawCenteredString(this.font,
                Component.translatable("gui.mi_ae2_pattern_provider.rename_hint"),
                this.width / 2, y + 28, 0xFF9A9A9A);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
