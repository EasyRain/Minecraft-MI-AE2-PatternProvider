package com.miae2.client;

import com.miae2.net.HatchNamePayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 仓的重命名界面：底图用我们自己画的 AE 风格面板（{@code textures/gui/hatch_rename.png}）。
 *
 * <p>底图尺寸 **176×186**，输入框已经画在图上（内区 x 19–157、y 45–58），标题与两个按钮由代码补：
 * <ul>
 *   <li>标题：左上内缩 8px（AE 对话框的习惯）；</li>
 *   <li>输入框：正好盖住底图那格；</li>
 *   <li>按钮：底图里输入框与背包格之间那段空白（y 62–100）放「完成 / 取消」，各 50×20。</li>
 * </ul>
 *
 * <p>为什么不用 AE2 的 {@code QuartzKnifeMenu}：它的服务端 action 只把名字存进 `currentName`，
 * 真正的方块改名落点是 `AEBaseBlockEntity#setCustomName`，我们是 MI 的方块实体，投不过去。
 */
@OnlyIn(Dist.CLIENT)
public final class HatchRenameScreen extends Screen {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(com.miae2.MiAe2PatternProvider.MOD_ID, "textures/gui/hatch_rename.png");

    /** 底图尺寸（与 hatch_rename.png 的可视面板一致）。 */
    private static final int IMAGE_W = 176;
    private static final int IMAGE_H = 186;

    /** 底图上输入框的内区（1px 边界留给描边）。 */
    private static final int FIELD_X = 20;
    private static final int FIELD_Y = 45;
    private static final int FIELD_W = 137;
    private static final int FIELD_H = 13;

    /** 按钮：放在输入框与背包格之间的空白里。 */
    private static final int BUTTON_Y = 70;
    private static final int BUTTON_W = 50;
    private static final int BUTTON_H = 20;

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
        this.left = (this.width - IMAGE_W) / 2;
        this.top = (this.height - IMAGE_H) / 2;

        this.field = new EditBox(this.font,
                this.left + FIELD_X, this.top + FIELD_Y, FIELD_W, FIELD_H, Component.empty());
        this.field.setMaxLength(64);
        this.field.setBordered(false);              // 底图上已经画了边框
        this.field.setTextColor(0x000000);          // 浅色面板上用黑字（对比度才够）
        this.field.setHint(Component.translatable("gui.mi_ae2_pattern_provider.rename_hint"));
        this.addRenderableWidget(this.field);
        this.setInitialFocus(this.field);

        int totalW = BUTTON_W * 2 + 8;
        int x = this.left + (IMAGE_W - totalW) / 2;
        // 直接复用 AE 自己的按钮类：它按 AE 的样式表取贴图（浅灰面板 + 深色 1px 描边），
        // 和我们自绘的“暗色扁平”不是一回事（2026-11-09 用户指出）。
        this.addRenderableWidget(new appeng.client.gui.widgets.AE2Button(
                x, this.top + BUTTON_Y, BUTTON_W, BUTTON_H,
                Component.translatable("gui.done"), b -> submit()));
        this.addRenderableWidget(new appeng.client.gui.widgets.AE2Button(
                x + BUTTON_W + 8, this.top + BUTTON_Y, BUTTON_W, BUTTON_H,
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
        graphics.fill(0, 0, this.width, this.height, 0x80000000);
        graphics.blit(TEXTURE, this.left, this.top, 0, 0, IMAGE_W, IMAGE_H);
        // 标题：AE 对话框习惯放在左上角（内缩 8）
        graphics.drawString(this.font, this.title, this.left + 8, this.top + 8, 0x000000, true);
        if (this.field != null && this.field.getValue().isEmpty()) {
            graphics.drawString(this.font,
                    Component.translatable("gui.mi_ae2_pattern_provider.rename_hint"),
                    this.left + FIELD_X + 1, this.top + FIELD_Y + 2, 0x707070, true);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

}
