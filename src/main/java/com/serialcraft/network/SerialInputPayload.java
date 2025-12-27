package com.serialcraft.network;

import com.serialcraft.SerialCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record SerialInputPayload(String message) implements CustomPacketPayload {
    public static final Type<SerialInputPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SerialCraft.MOD_ID, "serial_in_packet"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SerialInputPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SerialInputPayload::message,
            SerialInputPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}