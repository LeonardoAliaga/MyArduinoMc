package com.serialcraft.network;

import com.serialcraft.SerialCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record ConfigPayload(
        BlockPos pos,
        int mode,
        String targetData,
        int signalType,
        boolean isSoftOn,
        String boardID,
        int logicMode // Sin updateFreq
) implements CustomPacketPayload {

    public static final Type<ConfigPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SerialCraft.MOD_ID, "config_packet"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigPayload> CODEC = StreamCodec.of(
            (b, v) -> {
                BlockPos.STREAM_CODEC.encode(b, v.pos);
                b.writeInt(v.mode);
                ByteBufCodecs.STRING_UTF8.encode(b, v.targetData);
                b.writeInt(v.signalType);
                b.writeBoolean(v.isSoftOn);
                ByteBufCodecs.STRING_UTF8.encode(b, v.boardID);
                b.writeInt(v.logicMode);
            },
            b -> new ConfigPayload(
                    BlockPos.STREAM_CODEC.decode(b),
                    b.readInt(),
                    ByteBufCodecs.STRING_UTF8.decode(b),
                    b.readInt(),
                    b.readBoolean(),
                    ByteBufCodecs.STRING_UTF8.decode(b),
                    b.readInt()
            )
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}