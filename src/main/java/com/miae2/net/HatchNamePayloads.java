package com.miae2.net;

import com.miae2.MiAe2PatternProvider;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 自定义名的两条网络包：服务端 → 客户端「打开重命名界面」，客户端 → 服务端「提交名字」。
 *
 * <p>为什么不用菜单（Menu）：重命名既不需要槽位也不需要同步容器，用菜单要额外注册 MenuType +
 * Screen 工厂，成本高很多。这里直接用一个轻量 payload 打开**纯客户端 Screen**，提交时再回一个 payload。
 */
public final class HatchNamePayloads {

    private HatchNamePayloads() {
    }

    /** 服务端 → 客户端：打开这个坐标的区块重命名界面。 */
    public record OpenEditor(BlockPos pos) implements CustomPacketPayload {
        public static final Type<OpenEditor> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(MiAe2PatternProvider.MOD_ID, "open_hatch_name_editor"));
        public static final StreamCodec<ByteBuf, OpenEditor> CODEC =
                StreamCodec.composite(BlockPos.STREAM_CODEC, OpenEditor::pos, OpenEditor::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** 客户端 → 服务端：提交名字（空串 = 清除自定义名）。 */
    public record SetName(BlockPos pos, String name) implements CustomPacketPayload {
        public static final Type<SetName> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(MiAe2PatternProvider.MOD_ID, "set_hatch_name"));
        public static final StreamCodec<ByteBuf, SetName> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SetName::pos,
                ByteBufCodecs.STRING_UTF8, SetName::name,
                SetName::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }


    /**
     * 服务端 → 客户端：把生效后的名字同步给客户端方块实体。
     *
     * <p>Jade 我们走的是服务端数据，但 **AE 界面标题是客户端读方块实体**拼的；
     * 不同步的话「留空清除名字」后客户端还留着旧名（2026-11-10 实机反馈）。
     */
    public record NameSync(BlockPos pos, String name) implements CustomPacketPayload {
        public static final Type<NameSync> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(MiAe2PatternProvider.MOD_ID, "sync_hatch_name"));
        public static final StreamCodec<ByteBuf, NameSync> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, NameSync::pos,
                ByteBufCodecs.STRING_UTF8, NameSync::name,
                NameSync::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
