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

        // Registros C2S
        PayloadTypeRegistry.playC2S().register(ConfigPayload.TYPE, ConfigPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ConnectorPayload.TYPE, ConnectorPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SerialInputPayload.TYPE, SerialInputPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BoardListRequestPayload.TYPE, BoardListRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RemoteTogglePayload.TYPE, RemoteTogglePayload.CODEC); // Nuevo

        // Registros S2C
        PayloadTypeRegistry.playS2C().register(SerialOutputPayload.TYPE, SerialOutputPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BoardListResponsePayload.TYPE, BoardListResponsePayload.CODEC);

        // --- HANDLERS ---

        // Configuración
        ServerPlayNetworking.registerGlobalReceiver(ConfigPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player().level().getBlockEntity(payload.pos) instanceof ArduinoIOBlockEntity entity) {
                    if (entity.ownerUUID != null && entity.ownerUUID.equals(context.player().getUUID())) {
                        entity.setConfig(payload.mode, payload.data, payload.inputType, payload.pulseDuration, payload.signalStrength, payload.boardID, payload.logicMode, payload.outputType, payload.isSoftOn);
                    }
                }
            });
        });

        // Remote Toggle (Botón en Laptop)
        ServerPlayNetworking.registerGlobalReceiver(RemoteTogglePayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player().level().getBlockEntity(payload.targetPos) instanceof ArduinoIOBlockEntity io) {
                    if (io.ownerUUID != null && io.ownerUUID.equals(context.player().getUUID())) {
                        // Cambiamos solo el estado isSoftOn, manteniendo el resto
                        io.setConfig(io.ioMode, io.targetData, io.inputType, io.pulseDuration, io.signalStrength, io.boardID, io.logicMode, io.outputType, !io.isSoftOn);
                    }
                }
            });
        });

        // Solicitud Lista para Laptop (Actualizado)
        ServerPlayNetworking.registerGlobalReceiver(BoardListRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                List<BoardInfo> boardInfos = new ArrayList<>();
                synchronized (activeIOBlocks) {
                    for (ArduinoIOBlockEntity io : activeIOBlocks) {
                        if (io.ownerUUID != null && io.ownerUUID.equals(player.getUUID())) {
                            // Enviamos objeto rico en datos
                            boardInfos.add(new BoardInfo(io.getBlockPos(), io.boardID, io.targetData, io.ioMode, io.isSoftOn));
                        }
                    }
                }
                ServerPlayNetworking.send(player, new BoardListResponsePayload(boardInfos));
            });
        });

        // (Otros handlers Connector y SerialInput se mantienen igual...)
        ServerPlayNetworking.registerGlobalReceiver(ConnectorPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                var level = context.player().level();
                var state = level.getBlockState(payload.pos);
                if (state.is(ModBlocks.CONNECTOR_BLOCK)) level.setBlock(payload.pos, state.setValue(ConnectorBlock.LIT, payload.connected), 3);
            });
        });
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
    }

    // --- RECORDS ---

    // Estructura auxiliar para la lista
    public record BoardInfo(BlockPos pos, String id, String data, int mode, boolean status) {}

    // Paquete para cambiar estado desde la laptop
    public record RemoteTogglePayload(BlockPos targetPos) implements CustomPacketPayload {
        public static final Type<RemoteTogglePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "remote_toggle"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RemoteTogglePayload> CODEC = StreamCodec.of(
                (b, v) -> b.writeBlockPos(v.targetPos),
                b -> new RemoteTogglePayload(b.readBlockPos())
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // Respuesta de Lista (Ahora con objetos complejos)
    public record BoardListResponsePayload(List<BoardInfo> boards) implements CustomPacketPayload {
        public static final Type<BoardListResponsePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "board_list_res"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BoardListResponsePayload> CODEC = StreamCodec.of(
                (b, v) -> {
                    b.writeInt(v.boards.size());
                    v.boards.forEach(info -> {
                        b.writeBlockPos(info.pos);
                        b.writeUtf(info.id);
                        b.writeUtf(info.data);
                        b.writeInt(info.mode);
                        b.writeBoolean(info.status);
                    });
                },
                b -> {
                    int s = b.readInt();
                    List<BoardInfo> l = new ArrayList<>();
                    for(int i=0; i<s; i++) {
                        l.add(new BoardInfo(b.readBlockPos(), b.readUtf(), b.readUtf(), b.readInt(), b.readBoolean()));
                    }
                    return new BoardListResponsePayload(l);
                }
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // (Otros records sin cambios: ConfigPayload, ConnectorPayload, etc.)
    public record ConfigPayload(BlockPos pos, int mode, String data, int inputType, int pulseDuration, int signalStrength, String boardID, int logicMode, int outputType, boolean isSoftOn) implements CustomPacketPayload {
        public static final Type<ConfigPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "config_packet"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ConfigPayload> CODEC = StreamCodec.of((b, v) -> {
            b.writeBlockPos(v.pos); b.writeInt(v.mode); b.writeUtf(v.data); b.writeInt(v.inputType); b.writeInt(v.pulseDuration); b.writeInt(v.signalStrength); b.writeUtf(v.boardID); b.writeInt(v.logicMode); b.writeInt(v.outputType); b.writeBoolean(v.isSoftOn);
        }, b -> new ConfigPayload(b.readBlockPos(), b.readInt(), b.readUtf(), b.readInt(), b.readInt(), b.readInt(), b.readUtf(), b.readInt(), b.readInt(), b.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    // ... Resto de records (Connector, SerialInput, SerialOutput, BoardListRequest) iguales ...
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
}