package com.serialcraft;

import com.serialcraft.block.ConnectorBlock;
import com.serialcraft.block.entity.ArduinoIOBlockEntity;
import com.serialcraft.block.entity.ModBlockEntities;
import com.serialcraft.block.ModBlocks;
import com.serialcraft.item.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Collections;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SerialCraft implements ModInitializer {

    public static final String MOD_ID = "serialcraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Set<ArduinoIOBlockEntity> activeIOBlocks = Collections.synchronizedSet(new HashSet<>());

    @Override
    public void onInitialize() {
        ModBlocks.initialize();
        ModItems.initialize();
        ModBlockEntities.initialize();
        LOGGER.info("[SerialCraft] Servidor Iniciado");

        PayloadTypeRegistry.playC2S().register(ConfigPayload.TYPE, ConfigPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ConnectorPayload.TYPE, ConnectorPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SerialInputPayload.TYPE, SerialInputPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BoardListRequestPayload.TYPE, BoardListRequestPayload.CODEC);

        PayloadTypeRegistry.playS2C().register(SerialOutputPayload.TYPE, SerialOutputPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BoardListResponsePayload.TYPE, BoardListResponsePayload.CODEC);

        // Configuración
        ServerPlayNetworking.registerGlobalReceiver(ConfigPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                if (player.level().getBlockEntity(payload.pos) instanceof ArduinoIOBlockEntity entity) {
                    if (entity.ownerUUID != null && entity.ownerUUID.equals(player.getUUID())) {
                        entity.setConfig(payload.mode, payload.data, payload.inputType, payload.pulseDuration, payload.signalStrength, payload.boardID);
                    }
                }
            });
        });

        // Conector
        ServerPlayNetworking.registerGlobalReceiver(ConnectorPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                var level = context.player().level();
                var state = level.getBlockState(payload.pos);
                if (state.is(ModBlocks.CONNECTOR_BLOCK)) {
                    level.setBlock(payload.pos, state.setValue(ConnectorBlock.LIT, payload.connected), 3);
                }
            });
        });

        // Serial Input
        ServerPlayNetworking.registerGlobalReceiver(SerialInputPayload.TYPE, (payload, context) -> {
            ServerPlayer sender = context.player();
            context.server().execute(() -> {
                synchronized (activeIOBlocks) {
                    for (ArduinoIOBlockEntity io : activeIOBlocks) {
                        if (io.targetData.equals(payload.message()) && io.ownerUUID != null && io.ownerUUID.equals(sender.getUUID())) {
                            io.triggerAction();
                        }
                    }
                }
            });
        });

        // SOLICITUD LISTA
        ServerPlayNetworking.registerGlobalReceiver(BoardListRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                List<String> boardInfos = new ArrayList<>();
                synchronized (activeIOBlocks) {
                    for (ArduinoIOBlockEntity io : activeIOBlocks) {
                        if (io.ownerUUID != null && io.ownerUUID.equals(player.getUUID())) {
                            String info = String.format("§b%s §7| §f%s §7| %s",
                                    io.boardID,
                                    io.targetData,
                                    (io.ioMode == 0 ? "§cSAL" : (io.ioMode == 1 ? "§aENT" : "§9HIB"))
                            );
                            boardInfos.add(info);
                        }
                    }
                }
                ServerPlayNetworking.send(player, new BoardListResponsePayload(boardInfos));
            });
        });
    }

    // Records
    public record ConfigPayload(BlockPos pos, int mode, String data, int inputType, int pulseDuration, int signalStrength, String boardID) implements CustomPacketPayload {
        public static final Type<ConfigPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "config_packet"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ConfigPayload> CODEC = StreamCodec.of((b, v) -> {
            b.writeBlockPos(v.pos); b.writeInt(v.mode); b.writeUtf(v.data); b.writeInt(v.inputType); b.writeInt(v.pulseDuration); b.writeInt(v.signalStrength); b.writeUtf(v.boardID);
        }, b -> new ConfigPayload(b.readBlockPos(), b.readInt(), b.readUtf(), b.readInt(), b.readInt(), b.readInt(), b.readUtf()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record ConnectorPayload(BlockPos pos, boolean connected) implements CustomPacketPayload {
        public static final Type<ConnectorPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "connector_packet"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ConnectorPayload> CODEC = StreamCodec.of((b, v) -> { b.writeBlockPos(v.pos); b.writeBoolean(v.connected); }, b -> new ConnectorPayload(b.readBlockPos(), b.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record SerialInputPayload(String message) implements CustomPacketPayload {
        public static final Type<SerialInputPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "serial_in_packet"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SerialInputPayload> CODEC = StreamCodec.of((b, v) -> b.writeUtf(v.message), b -> new SerialInputPayload(b.readUtf()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record SerialOutputPayload(String message) implements CustomPacketPayload {
        public static final Type<SerialOutputPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "serial_out_packet"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SerialOutputPayload> CODEC = StreamCodec.of((b, v) -> b.writeUtf(v.message), b -> new SerialOutputPayload(b.readUtf()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record BoardListRequestPayload(boolean dummy) implements CustomPacketPayload {
        public static final Type<BoardListRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "board_list_req"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BoardListRequestPayload> CODEC = StreamCodec.of((b, v) -> b.writeBoolean(v.dummy), b -> new BoardListRequestPayload(b.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record BoardListResponsePayload(List<String> boards) implements CustomPacketPayload {
        public static final Type<BoardListResponsePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "board_list_res"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BoardListResponsePayload> CODEC = StreamCodec.of(
                (b, v) -> { b.writeInt(v.boards.size()); v.boards.forEach(b::writeUtf); },
                b -> { int s = b.readInt(); List<String> l = new ArrayList<>(); for(int i=0; i<s; i++) l.add(b.readUtf()); return new BoardListResponsePayload(l); }
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}