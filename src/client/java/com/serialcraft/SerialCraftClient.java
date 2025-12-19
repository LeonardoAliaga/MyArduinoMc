package com.serialcraft;

import com.fazecast.jSerialComm.SerialPort;
import com.serialcraft.block.ArduinoIOBlock;
import com.serialcraft.block.ModBlocks;
import com.serialcraft.block.entity.ArduinoIOBlockEntity;
import com.serialcraft.client.SerialDebugHud; // Importante: Tu nuevo HUD
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

    @Override
    public void onInitializeClient() {

        // 1. REGISTRAR EL DEBUG HUD
        // Esto hace que se dibuje el texto en la esquina y la info flotante
        HudRenderCallback.EVENT.register(new SerialDebugHud());

        // 2. EVENTO TICK: Lectura desde Arduino Físico -> Enviar al Servidor
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (arduinoPort != null && arduinoPort.isOpen() && arduinoPort.bytesAvailable() > 0) {
                try {
                    byte[] buffer = new byte[arduinoPort.bytesAvailable()];
                    arduinoPort.readBytes(buffer, buffer.length);
                    String mensaje = new String(buffer, StandardCharsets.UTF_8).trim();

                    if (!mensaje.isEmpty()) {
                        // AQUI: Registramos el evento en el HUD (RX = Recepción)
                        SerialDebugHud.addLog("RX: " + mensaje);

                        // Enviamos al servidor para que procese la lógica
                        ClientPlayNetworking.send(new SerialInputPayload(mensaje));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        // 3. HANDLER: Datos desde Servidor -> Enviar a Arduino Físico
        ClientPlayNetworking.registerGlobalReceiver(SerialOutputPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                String msg = payload.message();

                // AQUI: Registramos el evento en el HUD (TX = Transmisión)
                SerialDebugHud.addLog("TX: " + msg);

                // Escribimos en el puerto serial real
                enviarArduinoLocal(msg);
            });
        });

        // 4. HANDLER: Respuesta de Lista de Placas (Para la UI del Conector)
        ClientPlayNetworking.registerGlobalReceiver(BoardListResponsePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                // Si tenemos la pantalla abierta, actualizamos la lista
                if (Minecraft.getInstance().screen instanceof ConnectorScreen screen) {
                    screen.updateBoardList(payload.boards());
                }
            });
        });

        // 5. EVENTO DE INTERACCIÓN: Abrir GUIs
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!level.isClientSide() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

            BlockPos pos = hit.getBlockPos();
            var state = level.getBlockState(pos);
            Minecraft mc = Minecraft.getInstance();

            // CASO A: Bloque Conector (Laptop)
            if (state.is(ModBlocks.CONNECTOR_BLOCK)) {
                mc.setScreen(new ConnectorScreen(pos));
                return InteractionResult.SUCCESS;
            }

            // CASO B: Bloque IO (Arduino)
            if (state.is(ModBlocks.IO_BLOCK)) {
                // Verificación de Click en Botones Físicos (Cables)
                // Si el jugador tocó un cable, dejamos que el bloque maneje la lógica (PASS)
                if (state.getBlock() instanceof ArduinoIOBlock ioBlock) {
                    Vec3 hitPos = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
                    if (ioBlock.getHitButton(hitPos) != null) return InteractionResult.PASS;
                }

                // Si tocó la placa base, abrimos la GUI de Configuración
                int mode = 0;
                String data = ""; // Usamos String ahora (targetData)
                var be = level.getBlockEntity(pos);

                if (be instanceof ArduinoIOBlockEntity io) {
                    // Seguridad básica de propiedad
                    if (io.ownerUUID != null && !io.ownerUUID.equals(player.getUUID())) {
                        player.displayClientMessage(Component.literal("§cEsta placa pertenece a otro jugador."), true);
                        return InteractionResult.FAIL;
                    }
                    mode = io.ioMode;
                    data = io.targetData;
                }

                // Abrir la nueva pantalla
                mc.setScreen(new IOScreen(pos, mode, data));
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
    }

    // --- MÉTODOS DE GESTIÓN SERIAL ---

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
                        // Timeout importante para no congelar el juego si no hay datos
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
    }

    public static void enviarArduinoLocal(String msg) {
        if (arduinoPort != null && arduinoPort.isOpen()) {
            try {
                // Enviamos con salto de línea estándar
                arduinoPort.writeBytes((msg + "\n").getBytes(), msg.length() + 1);
            } catch (Exception e) { e.printStackTrace(); }
        }
    }
}