package com.serialcraft.network;

import com.serialcraft.SerialCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RemoteTogglePayload(BlockPos targetPos) implements CustomPacketPayload {
    public static final Type<RemoteTogglePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SerialCraft.MOD_ID, "remote_toggle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteTogglePayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, RemoteTogglePayload::targetPos,
            RemoteTogglePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}