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

    // Arduino
    public static SerialPort arduinoPort = null;

    // Lista de IO blocks activos
    public static final Set<ArduinoIOBlockEntity> activeIOBlocks =
            Collections.synchronizedSet(new HashSet<>());

    // ------------------------------------------------------------------------
    // INIT
    // ------------------------------------------------------------------------

    @Override
    public void onInitialize() {

        // Registro Moderno (1.21.10)
        ModBlocks.initialize();
        ModItems.initialize();
        ModBlockEntities.initialize();

        LOGGER.info("[SerialCraft] Registrando payloads...");

        // Paquetes
        PayloadTypeRegistry.playC2S().register(ConfigPayload.TYPE, ConfigPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ConnectorPayload.TYPE, ConnectorPayload.CODEC);

        // Recepción de configuración del IO block
        ServerPlayNetworking.registerGlobalReceiver(ConfigPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player().level().getBlockEntity(payload.pos) instanceof ArduinoIOBlockEntity entity) {
                    LOGGER.info("CONFIG -> Pos={} Output={} Data={}", payload.pos, payload.isOutput, payload.data);
                    entity.setConfig(payload.isOutput, payload.data);
                }
            });
        });

        // Recepción de estado del Connector (Laptop)
        ServerPlayNetworking.registerGlobalReceiver(ConnectorPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                var level = context.player().level();
                var state = level.getBlockState(payload.pos);

                if (state.is(ModBlocks.CONNECTOR_BLOCK)) {
                    level.setBlock(payload.pos, state.setValue(ConnectorBlock.LIT, payload.connected), 3);
                    LOGGER.info("Connector en {} actualizado a {}", payload.pos, payload.connected);
                }
            });
        });

        // Loop Serial Arduino
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (arduinoPort != null && arduinoPort.isOpen() && arduinoPort.bytesAvailable() > 0) {
                try {
                    byte[] buffer = new byte[arduinoPort.bytesAvailable()];
                    arduinoPort.readBytes(buffer, buffer.length);

                    String mensaje = new String(buffer, StandardCharsets.UTF_8).trim();
                    if (!mensaje.isEmpty()) {
                        LOGGER.info("[ARDUINO → MC]: {}", mensaje);

                        synchronized (activeIOBlocks) {
                            for (ArduinoIOBlockEntity entity : activeIOBlocks) {
                                if (!entity.isOutputMode && entity.targetData.equals(mensaje)) {
                                    entity.triggerRedstone();
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("Error leyendo serial", e);
                }
            }
        });

        LOGGER.info("[SerialCraft] Mod cargado exitosamente.");
    }

    // ------------------------------------------------------------------------
    // PAYLOADS
    // ------------------------------------------------------------------------

    public record ConfigPayload(BlockPos pos, boolean isOutput, String data)
            implements CustomPacketPayload {

        public static final Type<ConfigPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "config_packet"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ConfigPayload> CODEC =
                StreamCodec.of(
                        (buf, val) -> {
                            buf.writeBlockPos(val.pos);
                            buf.writeBoolean(val.isOutput);
                            buf.writeUtf(val.data);
                        },
                        buf -> new ConfigPayload(
                                buf.readBlockPos(),
                                buf.readBoolean(),
                                buf.readUtf()
                        )
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ConnectorPayload(BlockPos pos, boolean connected)
            implements CustomPacketPayload {

        public static final Type<ConnectorPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "connector_packet"));

        public static final StreamCodec<RegistryFriendlyByteBuf, ConnectorPayload> CODEC =
                StreamCodec.of(
                        (buf, val) -> {
                            buf.writeBlockPos(val.pos);
                            buf.writeBoolean(val.connected);
                        },
                        buf -> new ConnectorPayload(
                                buf.readBlockPos(),
                                buf.readBoolean()
                        )
                );

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // ------------------------------------------------------------------------
    // SERIAL (Arduino)
    // ------------------------------------------------------------------------

    public static String conectar(String puerto) {
        if (arduinoPort != null && arduinoPort.isOpen())
            return "§eYa conectado";

        try {
            SerialPort[] ports = SerialPort.getCommPorts();
            if (ports.length == 0)
                return "§cNo hay puertos disponibles";

            for (SerialPort p : ports) {
                if (p.getSystemPortName().equalsIgnoreCase(puerto)) {
                    arduinoPort = p;
                    arduinoPort.setBaudRate(9600);

                    if (arduinoPort.openPort()) {
                        arduinoPort.setComPortTimeouts(
                                SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0
                        );

                        LOGGER.info("Conexión exitosa al puerto {}", puerto);
                        return "§aConectado a " + puerto;
                    }
                }
            }

            return "§cPuerto no encontrado: " + puerto;

        } catch (Exception e) {
            return "§4Error: " + e.getMessage();
        }
    }

    public static void enviarArduino(String msg) {
        if (arduinoPort != null && arduinoPort.isOpen()) {
            try {
                LOGGER.info("[MC → ARDUINO]: {}", msg);
                arduinoPort.writeBytes((msg + "\n").getBytes(), msg.length() + 1);
            } catch (Exception e) {
                LOGGER.error("Error enviando al Arduino", e);
            }
        } else {
            LOGGER.warn("No hay conexión serial: {}", msg);
        }
    }
}
