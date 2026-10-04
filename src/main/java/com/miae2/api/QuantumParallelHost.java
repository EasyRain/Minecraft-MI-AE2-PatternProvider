package com.miae2.api;

/**
 * 标记接口：<b>装上量子超频模块之后，并行上限真的会被抬高的机器</b>。
 *
 * <p>目前只有两类，都由本 mod 的并行 mixin 直接打在目标类上：
 * <ul>
 *   <li>EI 处理阵列控制器 {@code ProcessingArrayBlockEntity}（见 {@code ProcessingArrayParallelMixin}）；</li>
 *   <li>IO 多处理阵列 {@code MultiProcessingArrayBlockEntity}（见 {@code MultiProcessingArrayParallelMixin}，
 *       只有装了 Industrialization Overdrive 才存在）。</li>
 * </ul>
 *
 * <p><b>为什么用接口而不是「按类名/按 mod 判断」</b>：超频槽那条「量子模块只许阵列」的规则
 * （见 {@code OverclockModules#blockedIn}）想问的其实是「装了到底有没有用」，
 * 而「有没有用」的权威答案就是<b>并行 mixin 有没有真的贴上去</b>。接口由 mixin 顺手贴上，
 * 所以两者永远一致：哪天并行注入点失效（MI/EI 改签名、被别的 mod 抢先），这个接口也不会被贴上，
 * 超频槽就会自动拒收量子模块 —— 宁可拒收（玩家看得见、不会白装），也不要让玩家装上去却毫无效果。
 *
 * <p>MI 自己的机器（单方块电动机器、多方块控制器）与 EI 大型电炉、IO 热解炉都不是宿主：
 * 它们的倍率来自别的地方（线圈档位、构造参数），量子模块在那里等价于高级超频模块。
 */
public interface QuantumParallelHost {
}
