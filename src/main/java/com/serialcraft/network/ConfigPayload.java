package com.serialcraft.network;

import com.serialcraft.SerialCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record ConfigPayload(BlockPos pos, int mode, String data, int inputType, int pulseDuration, int signalStrength, String boardID, int logicMode, int outputType, boolean isSoftOn) implements CustomPacketPayload {
    public static final Type<ConfigPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SerialCraft.MOD_ID, "config_packet"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigPayload> CODEC = StreamCodec.of(
            (b, v) -> {
                BlockPos.STREAM_CODEC.encode(b, v.pos);
                b.writeInt(v.mode);
                ByteBufCodecs.STRING_UTF8.encode(b, v.data);
                b.writeInt(v.inputType);
                b.writeInt(v.pulseDuration);
                b.writeInt(v.signalStrength);
                ByteBufCodecs.STRING_UTF8.encode(b, v.boardID);
                b.writeInt(v.logicMode);
                b.writeInt(v.outputType);
                b.writeBoolean(v.isSoftOn);
            },
            b -> new ConfigPayload(
                    BlockPos.STREAM_CODEC.decode(b),
                    b.readInt(),
                    ByteBufCodecs.STRING_UTF8.decode(b),
                    b.readInt(),
                    b.readInt(),
                    b.readInt(),
                    ByteBufCodecs.STRING_UTF8.decode(b),
                    b.readInt(),
                    b.readInt(),
                    b.readBoolean()
            )
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}