package com.serialcraft;

import com.fazecast.jSerialComm.SerialPort;
import com.mojang.blaze3d.platform.InputConstants; // Necesario para Type.KEYSYM
import com.serialcraft.block.ArduinoIOBlock;
import com.serialcraft.block.ModBlocks;
import com.serialcraft.block.entity.ArduinoIOBlockEntity;
import com.serialcraft.client.SerialDebugHud;
import com.serialcraft.network.*;
import com.serialcraft.screen.ConnectorScreen;
import com.serialcraft.screen.IOScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.KeyMapping; // Mappings de Mojang (o KeyBinding en Yarn)
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;

public class SerialCraftClient implements ClientModInitializer {

    public static SerialPort arduinoPort = null;
    public static int globalSerialSpeed = 2;

    private static Thread serialThread;
    private static volatile boolean running = false;

    private static KeyMapping debugHudKey;

    // SOLUCIÓN 1.21.4+: Crear la categoría como OBJETO, no String
    private static final String CATEGORY_ID = "category.serialcraft.general";

    // Si KeyMapping.Category.register no existe en tu subversión exacta,
    // usa: new KeyMapping.Category(CATEGORY_ID)
    // Pero en 1.21.4 suele ser un método estático o constructor directo.
    // Probaremos la forma más compatible con Mojmap moderno:

    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register(new SerialDebugHud());

        debugHudKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.serialcraft.debug_hud",       // Nombre
                InputConstants.Type.KEYSYM,        // Tipo de entrada
                GLFW.GLFW_KEY_F7,                  // Tecla
                CATEGORY_ID                        // Categoría (String)
                // Si aquí te sigue dando error, cambia CATEGORY_ID por: "misc"
        ));

        // Toggle HUD
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (debugHudKey.consumeClick()) {
                SerialDebugHud.isDebugEnabled = !SerialDebugHud.isDebugEnabled;
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            desconectar();
            SerialDebugHud.addLog("Desconectado por salida del mundo.");
        });

        // HANDLERS...
        ClientPlayNetworking.registerGlobalReceiver(SerialOutputPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                String msg = payload.message();
                SerialDebugHud.addLog("TX: " + msg);
                enviarArduinoLocal(msg);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(BoardListResponsePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (Minecraft.getInstance().screen instanceof ConnectorScreen screen) {
                    screen.updateBoardList(payload.boards());
                }
            });
        });

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

    // ... (Resto de métodos conectar, desconectar, serialLoop, dispatchMessage, enviarArduinoLocal IGUALES)
    public static Component conectar(String puerto, int baudRate) {
        if (arduinoPort != null && arduinoPort.isOpen()) return Component.translatable("message.serialcraft.already_connected");
        try {
            SerialPort[] ports = SerialPort.getCommPorts();
            if (ports.length == 0) return Component.translatable("message.serialcraft.no_ports");
            for (SerialPort p : ports) {
                if (p.getSystemPortName().equalsIgnoreCase(puerto)) {
                    arduinoPort = p;
                    arduinoPort.setBaudRate(baudRate);
                    if (arduinoPort.openPort()) {
                        arduinoPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 50, 0);
                        running = true;
                        serialThread = new Thread(SerialCraftClient::serialLoop, "SerialCraft-Reader");
                        serialThread.start();
                        return Component.translatable("message.serialcraft.connected", puerto);
                    }
                }
            }
            return Component.translatable("message.serialcraft.port_not_found", puerto);
        } catch (Exception e) { return Component.translatable("message.serialcraft.error", e.getMessage()); }
    }

    public static void desconectar() {
        running = false;
        if (arduinoPort != null) { arduinoPort.closePort(); arduinoPort = null; }
        try { if (serialThread != null && serialThread.isAlive()) serialThread.join(500); } catch (InterruptedException e) {}
    }

    private static void serialLoop() {
        StringBuilder localBuffer = new StringBuilder();
        byte[] readBuffer = new byte[1024];
        long lastDispatchTime = 0;
        String pendingMessage = null;
        while (running && arduinoPort != null && arduinoPort.isOpen()) {
            try {
                int numRead = arduinoPort.readBytes(readBuffer, readBuffer.length);
                if (numRead > 0) {
                    String chunk = new String(readBuffer, 0, numRead, StandardCharsets.UTF_8);
                    localBuffer.append(chunk);
                    int newlineIndex;
                    while ((newlineIndex = localBuffer.indexOf("\n")) != -1) {
                        String fullMessage = localBuffer.substring(0, newlineIndex).trim();
                        localBuffer.delete(0, newlineIndex + 1);
                        if (!fullMessage.isEmpty()) {
                            if (globalSerialSpeed == 2) dispatchMessage(fullMessage);
                            else pendingMessage = fullMessage;
                        }
                    }
                }
                if (globalSerialSpeed != 2 && pendingMessage != null) {
                    long now = System.currentTimeMillis();
                    int delay = (globalSerialSpeed == 0) ? 200 : 50;
                    if (now - lastDispatchTime >= delay) {
                        dispatchMessage(pendingMessage);
                        pendingMessage = null;
                        lastDispatchTime = now;
                    }
                }
                try { Thread.sleep(2); } catch (InterruptedException e) { break; }
            } catch (Exception e) { e.printStackTrace(); desconectar(); }
        }
    }

    private static void dispatchMessage(String msg) {
        SerialDebugHud.addLog("RX: " + msg);
        Minecraft.getInstance().execute(() -> ClientPlayNetworking.send(new SerialInputPayload(msg)));
    }

    public static void enviarArduinoLocal(String msg) {
        if (arduinoPort != null && arduinoPort.isOpen()) {
            try { arduinoPort.writeBytes((msg + "\n").getBytes(), msg.length() + 1); } catch (Exception e) { e.printStackTrace(); }
        }
    }
}