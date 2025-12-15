package com.serialcraft.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// Clase auxiliar limpia para usar dentro de las listas
public record BoardInfo(BlockPos pos, String id, String data, int mode, boolean status) {
    public static final StreamCodec<RegistryFriendlyByteBuf, BoardInfo> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, BoardInfo::pos,
            ByteBufCodecs.STRING_UTF8, BoardInfo::id,
            ByteBufCodecs.STRING_UTF8, BoardInfo::data,
            ByteBufCodecs.INT, BoardInfo::mode,
            ByteBufCodecs.BOOL, BoardInfo::status,
            BoardInfo::new
    );
}