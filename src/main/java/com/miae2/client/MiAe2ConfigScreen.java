package com.miae2.client;

import com.miae2.config.MiAe2Config;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Cloth Config 的游戏内配置界面（「Mods 界面 → 本 mod → Config」按钮）。
 *
 * <p><b>这个类只在客户端允许被加载</b>：{@link IConfigScreenFactory} 位于
 * {@code net.neoforged.neoforge.client.gui}，是客户端专属类，专用服上加载它会直接
 * {@code NoClassDefFoundError}。所以主类里用 {@code FMLEnvironment.dist.isClient() &&
 * ModList.get().isLoaded("cloth_config")} 双重守护之后<b>才调用</b> {@link #register(ModContainer)}
 * （仅仅在方法体里引用这个类不会加载它，调用才会）。
 *
 * <p>字段刻意<b>没有</b>调 {@code requireRestart()}：倍率在
 * {@code MiAe2Config.quantumParallelMultiplier()} ← {@code OverclockModules.scaleMaxMultiplier()}
 * ← {@code ProcessingArrayParallelMixin} 这条链上是<b>每次计算倍率时现读</b>的，保存后下一次配方
 * 计算就用新值，给玩家挂「需要重启」的徽章是错误暗示。{@code SmokeTestAutoStop#assertQuantumModule}
 * 里有一条活性断言（改配置 → 立刻读到新值 → 还原）钉住这件事，所以哪天有人把读路径改成启动时缓存，
 * 冒烟测试会立刻红。
 */
public final class MiAe2ConfigScreen {

    private static final String TITLE = "config.mi_ae2_pattern_provider.title";
    private static final String CATEGORY = "config.mi_ae2_pattern_provider.category.quantum_overclock";
    private static final String PARALLEL_MULTIPLIER = "config.mi_ae2_pattern_provider.parallel_multiplier";
    private static final String PARALLEL_MULTIPLIER_TOOLTIP =
            "config.mi_ae2_pattern_provider.parallel_multiplier.tooltip";

    /** 登记 NeoForge 的配置界面扩展点（Cloth Config 只是我们用来画界面的那个库）。 */
    public static void register(ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (container, parent) -> create(parent));
    }

    private static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable(TITLE));
        ConfigEntryBuilder entries = builder.entryBuilder();
        ConfigCategory category = builder.getOrCreateCategory(Component.translatable(CATEGORY));
        category.addEntry(entries
                .startIntField(Component.translatable(PARALLEL_MULTIPLIER), MiAe2Config.quantumParallelMultiplier())
                .setDefaultValue(MiAe2Config.QUANTUM_PARALLEL_DEFAULT)
                .setMin(MiAe2Config.QUANTUM_PARALLEL_MIN)
                .setMax(MiAe2Config.QUANTUM_PARALLEL_MAX)
                .setTooltip(Component.translatable(PARALLEL_MULTIPLIER_TOOLTIP))
                .setSaveConsumer(MiAe2Config::setQuantumParallelMultiplier)
                .build());
        return builder.build();
    }

    private MiAe2ConfigScreen() {
    }
}
