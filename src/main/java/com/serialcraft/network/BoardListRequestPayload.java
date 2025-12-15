package com.serialcraft.network;

import com.serialcraft.SerialCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BoardListRequestPayload(boolean dummy) implements CustomPacketPayload {
    public static final Type<BoardListRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SerialCraft.MOD_ID, "board_list_req"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BoardListRequestPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, BoardListRequestPayload::dummy,
            BoardListRequestPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}