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
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
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

    // VARIABLES PARA MULTITHREADING
    private static Thread serialThread;
    private static volatile boolean running = false;

    @Override
    public void onInitializeClient() {

        HudRenderCallback.EVENT.register(new SerialDebugHud());

        // Evento: Desconexión automática al salir del mundo
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            desconectar();
            SerialDebugHud.addLog("Desconectado por salida del mundo.");
        });

        // NOTA: Se ha eliminado ClientTickEvents.END_CLIENT_TICK para liberar el hilo principal.
        // La lectura ahora ocurre en 'serialLoop' dentro de un hilo separado.

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
                        player.displayClientMessage(Component.translatable("message.serialcraft.not_owner"), true);
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
                        // Configuración para lectura eficiente en hilo dedicado
                        // TIMEOUT_READ_BLOCKING permite que el hilo "duerma" esperando datos
                        arduinoPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 1000, 0);

                        // INICIAR HILO DE LECTURA
                        running = true;
                        serialThread = new Thread(SerialCraftClient::serialLoop, "SerialCraft-Reader");
                        serialThread.start();

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
        running = false; // Detener el bucle del hilo
        if (arduinoPort != null) {
            arduinoPort.closePort();
            arduinoPort = null;
        }
        // Esperar a que el hilo termine limpiamente (opcional)
        try {
            if (serialThread != null && serialThread.isAlive()) {
                serialThread.join(500);
            }
        } catch (InterruptedException e) {}
    }

    // --- HILO DEDICADO DE LECTURA (Alto Rendimiento) ---
    private static void serialLoop() {
        StringBuilder localBuffer = new StringBuilder();
        byte[] readBuffer = new byte[1024];

        while (running && arduinoPort != null && arduinoPort.isOpen()) {
            try {
                // Lee bytes disponibles. Si no hay, espera hasta 1000ms (configurado en timeout)
                // Esto evita que el CPU suba al 100% en un bucle vacío.
                int numRead = arduinoPort.readBytes(readBuffer, readBuffer.length);

                if (numRead > 0) {
                    String chunk = new String(readBuffer, 0, numRead, StandardCharsets.UTF_8);
                    localBuffer.append(chunk);

                    int newlineIndex;
                    while ((newlineIndex = localBuffer.indexOf("\n")) != -1) {
                        String fullMessage = localBuffer.substring(0, newlineIndex).trim();
                        localBuffer.delete(0, newlineIndex + 1);

                        if (!fullMessage.isEmpty()) {
                            // SOLO aquí molestamos al hilo principal del juego
                            Minecraft.getInstance().execute(() -> {
                                SerialDebugHud.addLog("RX: " + fullMessage);
                                ClientPlayNetworking.send(new SerialInputPayload(fullMessage));
                            });
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                desconectar(); // Desconexión de seguridad ante error fatal
            }
        }
    }

    public static void enviarArduinoLocal(String msg) {
        if (arduinoPort != null && arduinoPort.isOpen()) {
            try {
                // La escritura es rápida, puede hacerse en el hilo principal o delegarse
                arduinoPort.writeBytes((msg + "\n").getBytes(), msg.length() + 1);
            } catch (Exception e) { e.printStackTrace(); }
        }
    }
}