package com.miae2.client;

import java.util.function.Supplier;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * 「Mods 界面 → 本 mod → Config」按钮的登记（客户端专属），用 **NeoForge 自带的
 * {@link ConfigurationScreen}**，因此**不再需要 Cloth Config**。
 *
 * <p>为什么单独一个类：{@link IConfigScreenFactory} 是客户端专属类型，lambda 的合成方法描述符里会带
 * {@code Screen}，专用服上 RuntimeDistCleaner 剥不掉描述符里的客户端类 → 加载失败。
 * 主类里只引用本类（描述符 {@code (ModContainer)void}），并在 {@code Dist.CLIENT} 判断后才调用。
 *
 * <p>界面里的文案键（NeoForge 约定）：{@code <modid>.configuration.<配置路径>} 与同名 {@code .tooltip}，
 * 标题 {@code <modid>.configuration.title}；没写也不崩，只是显示原始键名。
 */
public final class MiAe2ConfigScreens {

    private MiAe2ConfigScreens() {
    }

    public static void register(ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (Supplier<IConfigScreenFactory>) () -> ConfigurationScreen::new);
    }
}
