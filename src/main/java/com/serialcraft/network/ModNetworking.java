package com.serialcraft.network;

import com.serialcraft.SerialCraft;
import com.serialcraft.block.ConnectorBlock;
import com.serialcraft.block.ModBlocks;
import com.serialcraft.block.entity.ArduinoIOBlockEntity;
import com.serialcraft.block.entity.ConnectorBlockEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.List;

public class ModNetworking {

    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(ConfigPayload.TYPE, ConfigPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ConnectorPayload.TYPE, ConnectorPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SerialInputPayload.TYPE, SerialInputPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BoardListRequestPayload.TYPE, BoardListRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RemoteTogglePayload.TYPE, RemoteTogglePayload.CODEC);

        // El payload actualizado (pos, baudRate, connected)
        PayloadTypeRegistry.playC2S().register(ConnectorConfigPayload.TYPE, ConnectorConfigPayload.CODEC);

        PayloadTypeRegistry.playS2C().register(SerialOutputPayload.TYPE, SerialOutputPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BoardListResponsePayload.TYPE, BoardListResponsePayload.CODEC);
    }

    public static void registerServerHandlers() {
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> SerialCraft.activeIOBlocks.clear());

        // 1. CONFIGURACIÓN DEL CONECTOR (Laptop)
        // Ahora maneja tanto la velocidad como el encendido visual.
        ServerPlayNetworking.registerGlobalReceiver(ConnectorConfigPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player().level().getBlockEntity(payload.pos()) instanceof ConnectorBlockEntity connector) {
                    // Actualiza la variable baudRate
                    connector.updateSettings(payload.baudRate());
                    // Actualiza el estado visual (ON/OFF) y la variable isConnected
                    connector.setConnectionState(payload.connected());
                }
            });
        });

        // 2. CONFIG INDIVIDUAL (Placas IO)
        ServerPlayNetworking.registerGlobalReceiver(ConfigPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player().level().getBlockEntity(payload.pos()) instanceof ArduinoIOBlockEntity entity) {
                    if (entity.ownerUUID == null || entity.ownerUUID.equals(context.player().getUUID())) {
                        entity.updateConfig(
                                payload.mode(),
                                payload.targetData(),
                                payload.signalType(),
                                payload.isSoftOn(),
                                payload.boardID(),
                                payload.updateFreq(),
                                payload.logicMode()
                        );
                    }
                }
            });
        });

        // Handler antiguo para encender/apagar (se mantiene por compatibilidad si se usa ConnectorPayload en otro lado)
        ServerPlayNetworking.registerGlobalReceiver(ConnectorPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                var level = context.player().level();
                var state = level.getBlockState(payload.pos());
                if (state.is(ModBlocks.CONNECTOR_BLOCK)) {
                    // Solo actualiza el bloque visualmente, sin pasar por el BlockEntity (método directo)
                    level.setBlock(payload.pos(), state.setValue(ConnectorBlock.LIT, payload.connected()), 3);
                }
            });
        });

        // Serial Input (Datos del Arduino al Juego)
        ServerPlayNetworking.registerGlobalReceiver(SerialInputPayload.TYPE, (payload, context) -> {
            ServerPlayer sender = context.player();
            context.server().execute(() -> {
                String msg = payload.message();
                synchronized (SerialCraft.activeIOBlocks) {
                    for (ArduinoIOBlockEntity io : SerialCraft.activeIOBlocks) {
                        if (io.ownerUUID != null && io.ownerUUID.equals(sender.getUUID())) {
                            io.processSerialInput(msg);
                        }
                    }
                }
            });
        });

        // Solicitud de Lista de placas
        ServerPlayNetworking.registerGlobalReceiver(BoardListRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                List<BoardInfo> boardInfos = new ArrayList<>();
                synchronized (SerialCraft.activeIOBlocks) {
                    for (ArduinoIOBlockEntity io : SerialCraft.activeIOBlocks) {
                        if (io.ownerUUID != null && io.ownerUUID.equals(player.getUUID())) {
                            boardInfos.add(new BoardInfo(io.getBlockPos(), io.boardID, io.targetData, io.ioMode, io.isSoftOn));
                        }
                    }
                }
                ServerPlayNetworking.send(player, new BoardListResponsePayload(boardInfos));
            });
        });

        // Toggle Remoto (Botón ON/OFF en la interfaz del conector)
        ServerPlayNetworking.registerGlobalReceiver(RemoteTogglePayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player().level().getBlockEntity(payload.targetPos()) instanceof ArduinoIOBlockEntity io) {
                    io.updateConfig(io.ioMode, io.targetData, io.signalType, !io.isSoftOn, io.boardID,
                            io.updateFrequency, io.logicMode);
                }
            });
        });
    }
}