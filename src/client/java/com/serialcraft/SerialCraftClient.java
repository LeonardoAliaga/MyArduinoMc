package com.serialcraft;

import com.fazecast.jSerialComm.SerialPort;
import com.serialcraft.block.ArduinoIOBlock;
import com.serialcraft.block.ModBlocks;
import com.serialcraft.block.entity.ArduinoIOBlockEntity;
import com.serialcraft.client.SerialDebugHud;
import com.serialcraft.network.*;
import com.serialcraft.screen.ConnectorScreen;
import com.serialcraft.screen.IOScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;

public class SerialCraftClient implements ClientModInitializer {

    public static SerialPort arduinoPort = null;

    // BUFFER PARA RECONSTRUIR MENSAJES PARTIDOS
    private static final StringBuilder serialBuffer = new StringBuilder();

    @Override
    public void onInitializeClient() {

        HudRenderCallback.EVENT.register(new SerialDebugHud());

        // --- CORRECCIÓN CRÍTICA DE LECTURA SERIAL ---
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (arduinoPort != null && arduinoPort.isOpen()) {
                try {
                    int bytesAvailable = arduinoPort.bytesAvailable();
                    if (bytesAvailable > 0) {
                        byte[] buffer = new byte[bytesAvailable];
                        int bytesRead = arduinoPort.readBytes(buffer, buffer.length);

                        if (bytesRead > 0) {
                            // 1. Añadir lo nuevo al acumulador
                            String chunk = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                            serialBuffer.append(chunk);

                            // 2. Buscar saltos de línea (\n) para procesar mensajes completos
                            int newlineIndex;
                            while ((newlineIndex = serialBuffer.indexOf("\n")) != -1) {
                                // Extraer la línea completa (sin el \n)
                                String fullMessage = serialBuffer.substring(0, newlineIndex).trim();

                                // Eliminar esa línea del buffer para no procesarla de nuevo
                                serialBuffer.delete(0, newlineIndex + 1);

                                // 3. Procesar mensaje limpio
                                if (!fullMessage.isEmpty()) {
                                    SerialDebugHud.addLog("RX: " + fullMessage);
                                    ClientPlayNetworking.send(new SerialInputPayload(fullMessage));
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        // HANDLER: Salida Serial (MC -> Arduino)
        ClientPlayNetworking.registerGlobalReceiver(SerialOutputPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                String msg = payload.message();
                SerialDebugHud.addLog("TX: " + msg);
                enviarArduinoLocal(msg);
            });
        });

        // HANDLER: Lista de Placas
        ClientPlayNetworking.registerGlobalReceiver(BoardListResponsePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (Minecraft.getInstance().screen instanceof ConnectorScreen screen) {
                    screen.updateBoardList(payload.boards());
                }
            });
        });

        // EVENTO INTERACCIÓN
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!level.isClientSide() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

            BlockPos pos = hit.getBlockPos();
            var state = level.getBlockState(pos);
            Minecraft mc = Minecraft.getInstance();

            if (state.is(ModBlocks.CONNECTOR_BLOCK)) {
                mc.setScreen(new ConnectorScreen(pos));
                return InteractionResult.SUCCESS;
            }

            if (state.is(ModBlocks.IO_BLOCK)) {
                if (state.getBlock() instanceof ArduinoIOBlock ioBlock) {
                    Vec3 hitPos = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
                    if (ioBlock.getHitButton(hitPos) != null) return InteractionResult.PASS;
                }

                int mode = 0;
                String data = "";
                var be = level.getBlockEntity(pos);

                if (be instanceof ArduinoIOBlockEntity io) {
                    if (io.ownerUUID != null && !io.ownerUUID.equals(player.getUUID())) {
                        player.displayClientMessage(Component.literal("§cEsta placa pertenece a otro jugador."), true);
                        return InteractionResult.FAIL;
                    }
                    mode = io.ioMode;
                    data = io.targetData;
                }

                mc.setScreen(new IOScreen(pos, mode, data));
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
    }

    public static Component conectar(String puerto) {
        if (arduinoPort != null && arduinoPort.isOpen())
            return Component.translatable("message.serialcraft.already_connected");

        try {
            SerialPort[] ports = SerialPort.getCommPorts();
            if (ports.length == 0)
                return Component.translatable("message.serialcraft.no_ports");

            for (SerialPort p : ports) {
                if (p.getSystemPortName().equalsIgnoreCase(puerto)) {
                    arduinoPort = p;
                    arduinoPort.setBaudRate(9600);
                    if (arduinoPort.openPort()) {
                        arduinoPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);
                        return Component.translatable("message.serialcraft.connected", puerto);
                    }
                }
            }
            return Component.translatable("message.serialcraft.port_not_found", puerto);
        } catch (Exception e) {
            return Component.translatable("message.serialcraft.error", e.getMessage());
        }
    }

    public static void desconectar() {
        if (arduinoPort != null) {
            arduinoPort.closePort();
            arduinoPort = null;
        }
        serialBuffer.setLength(0); // Limpiar buffer al desconectar
    }

    public static void enviarArduinoLocal(String msg) {
        if (arduinoPort != null && arduinoPort.isOpen()) {
            try {
                arduinoPort.writeBytes((msg + "\n").getBytes(), msg.length() + 1);
            } catch (Exception e) { e.printStackTrace(); }
        }
    }
}