package com.serialcraft.network;

import com.serialcraft.SerialCraft;
import com.serialcraft.block.ConnectorBlock;
import com.serialcraft.block.ModBlocks;
import com.serialcraft.block.entity.ArduinoIOBlockEntity;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public class ModNetworking {

    // 1. Registrar los TIPOS de paquetes (Común para Cliente y Servidor)
    public static void registerPayloads() {
        // C2S: Cliente a Servidor
        PayloadTypeRegistry.playC2S().register(ConfigPayload.TYPE, ConfigPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ConnectorPayload.TYPE, ConnectorPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SerialInputPayload.TYPE, SerialInputPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BoardListRequestPayload.TYPE, BoardListRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RemoteTogglePayload.TYPE, RemoteTogglePayload.CODEC);

        // S2C: Servidor a Cliente
        PayloadTypeRegistry.playS2C().register(SerialOutputPayload.TYPE, SerialOutputPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BoardListResponsePayload.TYPE, BoardListResponsePayload.CODEC);
    }

    // 2. Registrar la LÓGICA del Servidor (Qué pasa cuando recibes el paquete)
    public static void registerServerHandlers() {

        // --- Configuración de la Placa ---
        ServerPlayNetworking.registerGlobalReceiver(ConfigPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player().level().getBlockEntity(payload.pos()) instanceof ArduinoIOBlockEntity entity) {
                    if (entity.ownerUUID != null && entity.ownerUUID.equals(context.player().getUUID())) {
                        entity.setConfig(
                                payload.mode(), payload.data(), payload.inputType(),
                                payload.pulseDuration(), payload.signalStrength(),
                                payload.boardID(), payload.logicMode(),
                                payload.outputType(), payload.isSoftOn()
                        );
                    }
                }
            });
        });

        // --- Toggle Remoto (Laptop) ---
        ServerPlayNetworking.registerGlobalReceiver(RemoteTogglePayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player().level().getBlockEntity(payload.targetPos()) instanceof ArduinoIOBlockEntity io) {
                    if (io.ownerUUID != null && io.ownerUUID.equals(context.player().getUUID())) {
                        io.setConfig(io.ioMode, io.targetData, io.inputType, io.pulseDuration,
                                io.signalStrength, io.boardID, io.logicMode,
                                io.outputType, !io.isSoftOn);
                    }
                }
            });
        });

        // --- Solicitud de Lista de Placas ---
        ServerPlayNetworking.registerGlobalReceiver(BoardListRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                List<BoardInfo> boardInfos = new ArrayList<>();
                synchronized (SerialCraft.activeIOBlocks) { // Accedemos a la lista estática de la clase principal
                    for (ArduinoIOBlockEntity io : SerialCraft.activeIOBlocks) {
                        if (io.ownerUUID != null && io.ownerUUID.equals(player.getUUID())) {
                            boardInfos.add(new BoardInfo(
                                    io.getBlockPos(), io.boardID, io.targetData,
                                    io.ioMode, io.isSoftOn
                            ));
                        }
                    }
                }
                ServerPlayNetworking.send(player, new BoardListResponsePayload(boardInfos));
            });
        });

        // --- Actualización Visual Conector ---
        ServerPlayNetworking.registerGlobalReceiver(ConnectorPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                var level = context.player().level();
                var state = level.getBlockState(payload.pos());
                if (state.is(ModBlocks.CONNECTOR_BLOCK)) {
                    level.setBlock(payload.pos(), state.setValue(ConnectorBlock.LIT, payload.connected()), 3);
                }
            });
        });

        // --- Input Serial ---
        ServerPlayNetworking.registerGlobalReceiver(SerialInputPayload.TYPE, (payload, context) -> {
            ServerPlayer sender = context.player();
            context.server().execute(() -> {
                synchronized (SerialCraft.activeIOBlocks) {
                    for (ArduinoIOBlockEntity io : SerialCraft.activeIOBlocks) {
                        if (io.targetData.equals(payload.message()) && io.ownerUUID != null && io.ownerUUID.equals(sender.getUUID())) {
                            io.triggerAction();
                        }
                    }
                }
            });
        });
    }
}