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

    public static void registerPayloads() {
        // C2S
        PayloadTypeRegistry.playC2S().register(ConfigPayload.TYPE, ConfigPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ConnectorPayload.TYPE, ConnectorPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SerialInputPayload.TYPE, SerialInputPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(BoardListRequestPayload.TYPE, BoardListRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RemoteTogglePayload.TYPE, RemoteTogglePayload.CODEC);

        // S2C
        PayloadTypeRegistry.playS2C().register(SerialOutputPayload.TYPE, SerialOutputPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BoardListResponsePayload.TYPE, BoardListResponsePayload.CODEC);
    }

    public static void registerServerHandlers() {

        // --- Configuración Principal ---
        ServerPlayNetworking.registerGlobalReceiver(ConfigPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player().level().getBlockEntity(payload.pos()) instanceof ArduinoIOBlockEntity entity) {
                    if (entity.ownerUUID == null || entity.ownerUUID.equals(context.player().getUUID())) {
                        entity.updateConfig(
                                payload.mode(),
                                payload.targetData(), // Ahora pasamos el String
                                payload.signalType(),
                                payload.isSoftOn(),
                                payload.boardID(),
                                payload.pulseDuration()
                        );
                    }
                }
            });
        });

        // --- Connector ---
        ServerPlayNetworking.registerGlobalReceiver(ConnectorPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                var level = context.player().level();
                var state = level.getBlockState(payload.pos());
                if (state.is(ModBlocks.CONNECTOR_BLOCK)) {
                    level.setBlock(payload.pos(), state.setValue(ConnectorBlock.LIT, payload.connected()), 3);
                }
            });
        });

        // --- Input Serial (Arduino -> MC) ---
        ServerPlayNetworking.registerGlobalReceiver(SerialInputPayload.TYPE, (payload, context) -> {
            ServerPlayer sender = context.player();
            context.server().execute(() -> {
                String msg = payload.message();
                // Buscamos bloques que coincidan con el mensaje recibido
                synchronized (SerialCraft.activeIOBlocks) {
                    for (ArduinoIOBlockEntity io : SerialCraft.activeIOBlocks) {
                        if (io.ownerUUID != null && io.ownerUUID.equals(sender.getUUID())) {
                            io.processSerialInput(msg);
                        }
                    }
                }
            });
        });

        // --- Lista de Placas ---
        ServerPlayNetworking.registerGlobalReceiver(BoardListRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                List<BoardInfo> boardInfos = new ArrayList<>();
                synchronized (SerialCraft.activeIOBlocks) {
                    for (ArduinoIOBlockEntity io : SerialCraft.activeIOBlocks) {
                        if (io.ownerUUID != null && io.ownerUUID.equals(player.getUUID())) {
                            // Mostramos el ID del canal en la lista
                            boardInfos.add(new BoardInfo(io.getBlockPos(), io.boardID, io.targetData, io.ioMode, io.isSoftOn));
                        }
                    }
                }
                ServerPlayNetworking.send(player, new BoardListResponsePayload(boardInfos));
            });
        });

        // --- Toggle Remoto ---
        ServerPlayNetworking.registerGlobalReceiver(RemoteTogglePayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player().level().getBlockEntity(payload.targetPos()) instanceof ArduinoIOBlockEntity io) {
                    // Toggle simple del estado SoftOn
                    io.updateConfig(io.ioMode, io.targetData, io.signalType, !io.isSoftOn, io.boardID, io.pulseDuration);
                }
            });
        });
    }
}