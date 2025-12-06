package com.serialcraft;

import com.fazecast.jSerialComm.SerialPort;
import com.serialcraft.block.ConnectorBlock;
import com.serialcraft.block.entity.ArduinoIOBlockEntity;
import com.serialcraft.block.entity.ModBlockEntities;
import com.serialcraft.block.ModBlocks;
import com.serialcraft.item.ModItems;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SerialCraft implements ModInitializer {

    public static final String MOD_ID = "serialcraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static SerialPort arduinoPort = null;
    public static final Set<ArduinoIOBlockEntity> activeIOBlocks =
            Collections.synchronizedSet(new HashSet<>());

    @Override
    public void onInitialize() {
        ModBlocks.initialize();
        ModItems.initialize();
        ModBlockEntities.initialize();

        LOGGER.info("[SerialCraft] Inicializando...");

        PayloadTypeRegistry.playC2S().register(ConfigPayload.TYPE, ConfigPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ConnectorPayload.TYPE, ConnectorPayload.CODEC);

        // Recepción de Configuración (Ahora recibe 'mode' como int)
        ServerPlayNetworking.registerGlobalReceiver(ConfigPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player().level().getBlockEntity(payload.pos) instanceof ArduinoIOBlockEntity entity) {
                    entity.setConfig(payload.mode, payload.data);
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ConnectorPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                var level = context.player().level();
                var state = level.getBlockState(payload.pos);
                if (state.is(ModBlocks.CONNECTOR_BLOCK)) {
                    level.setBlock(payload.pos, state.setValue(ConnectorBlock.LIT, payload.connected), 3);
                }
            });
        });

        // Loop Serial
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (arduinoPort != null && arduinoPort.isOpen() && arduinoPort.bytesAvailable() > 0) {
                try {
                    byte[] buffer = new byte[arduinoPort.bytesAvailable()];
                    arduinoPort.readBytes(buffer, buffer.length);
                    String mensaje = new String(buffer, StandardCharsets.UTF_8).trim();

                    if (!mensaje.isEmpty()) {
                        LOGGER.info("[ARDUINO -> MC]: {}", mensaje);
                        synchronized (activeIOBlocks) {
                            for (ArduinoIOBlockEntity entity : activeIOBlocks) {
                                // Si el mensaje coincide con la "clave" del bloque, disparamos la acción
                                if (entity.targetData.equals(mensaje)) {
                                    entity.triggerAction();
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Error leyendo serial", e);
                }
            }
        });
    }

    // --- PAYLOADS ACTUALIZADOS ---

    // Cambiado 'boolean isOutput' por 'int mode'
    public record ConfigPayload(BlockPos pos, int mode, String data) implements CustomPacketPayload {
        public static final Type<ConfigPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "config_packet"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ConfigPayload> CODEC = StreamCodec.of(
                (buf, val) -> {
                    buf.writeBlockPos(val.pos);
                    buf.writeInt(val.mode); // Int
                    buf.writeUtf(val.data);
                },
                buf -> new ConfigPayload(buf.readBlockPos(), buf.readInt(), buf.readUtf())
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ConnectorPayload(BlockPos pos, boolean connected) implements CustomPacketPayload {
        public static final Type<ConnectorPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "connector_packet"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ConnectorPayload> CODEC = StreamCodec.of(
                (buf, val) -> { buf.writeBlockPos(val.pos); buf.writeBoolean(val.connected); },
                buf -> new ConnectorPayload(buf.readBlockPos(), buf.readBoolean())
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // --- ARDUINO ---
    public static String conectar(String puerto) {
        if (arduinoPort != null && arduinoPort.isOpen()) return "§eYa conectado";
        try {
            SerialPort[] ports = SerialPort.getCommPorts();
            if (ports.length == 0) return "§cNo hay puertos";
            for (SerialPort p : ports) {
                if (p.getSystemPortName().equalsIgnoreCase(puerto)) {
                    arduinoPort = p;
                    arduinoPort.setBaudRate(9600);
                    if (arduinoPort.openPort()) {
                        arduinoPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);
                        return "§aConectado a " + puerto;
                    }
                }
            }
            return "§cPuerto no encontrado: " + puerto;
        } catch (Exception e) { return "§4Error: " + e.getMessage(); }
    }

    public static void enviarArduino(String msg) {
        if (arduinoPort != null && arduinoPort.isOpen()) {
            try {
                LOGGER.info("[MC -> ARDUINO]: {}", msg);
                arduinoPort.writeBytes((msg + "\n").getBytes(), msg.length() + 1);
            } catch (Exception e) { LOGGER.error("Error enviando", e); }
        }
    }
}