package com.serialcraft.network;

import com.serialcraft.SerialCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ConnectorPayload(BlockPos pos, boolean connected) implements CustomPacketPayload {
    public static final Type<ConnectorPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SerialCraft.MOD_ID, "connector_packet"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConnectorPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConnectorPayload::pos,
            ByteBufCodecs.BOOL, ConnectorPayload::connected,
            ConnectorPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}