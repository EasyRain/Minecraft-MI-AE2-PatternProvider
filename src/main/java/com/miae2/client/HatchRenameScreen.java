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
 * <p>AE2 的切割刀在本版本只能重命名**物品**（名称压印模板那套），没有「重命名已放置方块」的路径；
 * 所以这里自己补一个：切割刀右键我们的仓 → 服务端发 {@link HatchNamePayloads.OpenEditor} → 本界面。
 * 提交时空串表示清除自定义名。
 */
@OnlyIn(Dist.CLIENT)
public final class HatchRenameScreen extends Screen {

    private final BlockPos pos;
    private EditBox field;

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
        int cx = this.width / 2;
        int cy = this.height / 2;
        this.field = new EditBox(this.font, cx - 100, cy - 20, 200, 20,
                Component.translatable("gui.mi_ae2_pattern_provider.rename_hint"));
        this.field.setMaxLength(64);
        this.field.setHint(Component.translatable("gui.mi_ae2_pattern_provider.rename_hint"));
        this.addRenderableWidget(this.field);
        this.setInitialFocus(this.field);

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> submit())
                .bounds(cx - 100, cy + 10, 98, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(cx + 2, cy + 10, 98, 20).build());
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
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 44, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
