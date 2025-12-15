package com.serialcraft.network;

import com.serialcraft.SerialCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.ArrayList;
import java.util.List;

public record BoardListResponsePayload(List<BoardInfo> boards) implements CustomPacketPayload {
    public static final Type<BoardListResponsePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SerialCraft.MOD_ID, "board_list_res"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BoardListResponsePayload> CODEC = StreamCodec.composite(
            BoardInfo.CODEC.apply(ByteBufCodecs.list()), BoardListResponsePayload::boards,
            BoardListResponsePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}