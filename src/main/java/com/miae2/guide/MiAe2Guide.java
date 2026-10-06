package com.miae2.guide;

import aztech.modern_industrialization.guidebook.MultiblockShapeCompiler;
import com.miae2.MiAe2PatternProvider;
import com.mojang.logging.LogUtils;
import guideme.Guide;
import guideme.GuideBuilder;
import guideme.Guides;
import guideme.GuidesCommon;
import guideme.PageAnchor;
import guideme.scene.element.SceneElementTagCompiler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.swedz.tesseract.neoforge.compat.guideme.tags.TesseractGuideMETags;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * 本 mod 的指南页面：注册方式、以及「使用手册」打开哪一本。
 *
 * <h2>页面放在哪</h2>
 * 正文只维护一份（{@code guide/} 目录），构建时由 {@code build.gradle} 的 {@code processResources} 镜像到
 * {@code assets/mi_ae2_pattern_provider/mi_guidebook/} —— MI 的指南书用
 * {@code Guide.builder(modern_industrialization:book).folder("mi_guidebook")} 注册，而 GuideME 读页面是
 * 「<b>任意命名空间</b>下的这个目录」（官方文档：Pages for a guidebook are read from all resource packs
 * across all namespace）。Industrialization Overdrive 就是这么并进 MI 指南书的。
 *
 * <p>这里同时也用 {@link #init()} 注册一本**我们自己的**指南书：它是**兜底**用的 ——
 * 手册默认打开 MI 的那本（侧栏与 MI 的章节在同一处，用户反馈「和 overdrive 一样打开也直接在 MI 的 GuideME 里」），
 * 但万一 MI 改了内容目录名、并入失败，手册至少还能打开我们自己这本看内容。
 *
 * <h2>两个扩展</h2>
 * <ul>
 *   <li>{@link MultiblockShapeCompiler}（MI 的类）—— EI 的指南也是这么挂的，挂上之后页面里就能写
 *       {@code <MultiblockShape controller="extended_industrialization:processing_array" />}
 *       直接渲染多方块结构；</li>
 *   <li>{@link TesseractGuideMETags#includeIn}（tesseract 提供的标签包）—— 与 EI 指南保持一致。</li>
 * </ul>
 */
public final class MiAe2Guide {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** 我们自己的指南书 id（**兜底**那一本；手册默认打开的是 MI 的指南书，见 {@link #open}）。 */
    public static final ResourceLocation ID = MiAe2PatternProvider.id("guide");

    /**
     * 根页面 id —— 手册深链用的就是它。
     *
     * <p>页面 id 由 GuideME 按「命名空间 + 相对该指南目录的路径」生成，**带 {@code .md}**；
     * 两个落点里都是 {@code mi_ae2_pattern_provider:index.md}（放在我们自己的命名空间下是刻意的：
     * 若学 IO 放进 MI 的命名空间，{@code index.md} 之类会与 MI 自己的页面撞 id）。
     */
    public static final ResourceLocation INDEX_PAGE = MiAe2PatternProvider.id("index.md");

    /** MI 指南书的内容目录名（{@code MIGuide} 用它会注册的 {@code folder("mi_guidebook")}）。 */
    public static final String MI_GUIDE_FOLDER = "mi_guidebook";

    /** 由 mod 构造函数调用一次（两端都跑；这里只是登记指南，不碰客户端类）。 */
    public static void init() {
        GuideBuilder guide = Guide.builder(ID)
                .extension(SceneElementTagCompiler.EXTENSION_POINT, new MultiblockShapeCompiler());
        TesseractGuideMETags.includeIn(guide);
        guide.build();
    }

    /**
     * 「使用手册」右键时调用（调用方保证**只在客户端**）。
     *
     * <p>默认打开 **MI 自己的指南书**并直接翻到我们的章节：玩家看到的侧栏就是 MI 那一整套
     * （序言 / 蒸汽时代 / 电气时代 / 游戏中期 / 游戏终局 / Industrialization Overdrive / **ME样板供应仓**），
     * 而不是一本只装着我们页面的小册子。
     *
     * <p>用**内容目录名**而不是硬编码指南 id 来找 MI 的指南书：MI 换 id 不影响，换了目录名则并入本身也失效了，
     * 这时退回我们自己的那本（并有冒烟自检报红）。
     */
    public static void open(Player player) {
        Guide miBook = miBook();
        if (miBook != null) {
            GuidesCommon.openGuide(player, miBook.getId(), PageAnchor.page(INDEX_PAGE));
        } else {
            LOGGER.warn("找不到内容目录为 {} 的指南书（MI 改了目录名？）——退回本 mod 自己的指南书 {}",
                    MI_GUIDE_FOLDER, ID);
            GuidesCommon.openGuide(player, ID);
        }
    }

    /** MI 的指南书；MI 缺席或内容目录改名时返回 null。 */
    @Nullable
    public static Guide miBook() {
        for (Guide guide : Guides.getAll()) {
            if (MI_GUIDE_FOLDER.equals(guide.getContentRootFolder())) {
                return guide;
            }
        }
        return null;
    }

    private MiAe2Guide() {
    }
}
