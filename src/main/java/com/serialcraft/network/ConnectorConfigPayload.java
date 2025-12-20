package com.serialcraft.network;

import com.serialcraft.SerialCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record ConnectorConfigPayload(BlockPos pos, int baudRate) implements CustomPacketPayload {

    public static final Type<ConnectorConfigPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SerialCraft.MOD_ID, "connector_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConnectorConfigPayload> CODEC = StreamCodec.of(
            (b, v) -> {
                BlockPos.STREAM_CODEC.encode(b, v.pos);
                b.writeInt(v.baudRate);
            },
            b -> new ConnectorConfigPayload(
                    BlockPos.STREAM_CODEC.decode(b),
                    b.readInt()
            )
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}