package com.serialcraft.client;

import com.serialcraft.SerialCraftClient;
import com.serialcraft.block.entity.ArduinoIOBlockEntity;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;

public class SerialDebugHud implements HudRenderCallback {

    // Configuración
    public static boolean isDebugEnabled = true;
    private static final List<String> eventLog = new ArrayList<>();
    private static final int MAX_LOGS = 8;

    // Método estático para añadir logs
    public static void addLog(String message) {
        synchronized (eventLog) {
            eventLog.add(0, message);
            if (eventLog.size() > MAX_LOGS) {
                eventLog.remove(eventLog.size() - 1);
            }
        }
    }

    @Override
    public void onHudRender(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        if (!isDebugEnabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.level == null) return;

        Font font = mc.font;
        int x = 5;
        int y = 5;

        // --- 1. LOG DE RED (Esquina Superior Izquierda) ---
        boolean connected = (SerialCraftClient.arduinoPort != null && SerialCraftClient.arduinoPort.isOpen());
        String portInfo = connected ? " (" + SerialCraftClient.arduinoPort.getSystemPortName() + ")" : " (No Conectado)";
        int portColor = connected ? 0xFF55FF55 : 0xFFFF5555;

        guiGraphics.drawString(font, "§6[SerialCraft DEBUG]§r" + portInfo, x, y, portColor, true);
        y += 12;

        synchronized (eventLog) {
            for (String log : eventLog) {
                int logColor = log.startsWith("RX") ? 0xFFFFAA00 : 0xFFAAAAFF;
                guiGraphics.drawString(font, "> " + log, x, y, logColor, true);
                y += 10;
            }
        }

        // --- 2. INFO DEL BLOQUE (Raycast) ---
        HitResult hit = mc.hitResult;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hit;
            BlockPos pos = blockHit.getBlockPos();

            if (mc.level.getBlockEntity(pos) instanceof ArduinoIOBlockEntity io) {

                int screenWidth = mc.getWindow().getGuiScaledWidth();
                int screenHeight = mc.getWindow().getGuiScaledHeight();
                int cx = (screenWidth / 2) + 15;
                int cy = (screenHeight / 2) - 15;

                guiGraphics.fill(cx - 5, cy - 5, cx + 130, cy + 90, 0x90000000);

                int textY = cy;
                int labelCol = 0xFFAAAAAA;
                int valCol = 0xFFFFFFFF;

                // ID
                guiGraphics.drawString(font, "ID: ", cx, textY, labelCol, true);
                guiGraphics.drawString(font, io.boardID, cx + 20, textY, valCol, true);
                textY += 10;

                // MODO
                String modeStr = (io.ioMode == ArduinoIOBlockEntity.MODE_OUTPUT) ? "OUTPUT (MC->Ard)" : "INPUT (Ard->MC)";
                int modeColor = (io.ioMode == ArduinoIOBlockEntity.MODE_OUTPUT) ? 0xFF55FFFF : 0xFFFFAA00;
                guiGraphics.drawString(font, modeStr, cx, textY, modeColor, true);
                textY += 10;

                // SEÑAL
                boolean isAnalog = (io.signalType == ArduinoIOBlockEntity.SIGNAL_ANALOG);
                String signalStr = isAnalog ? "ANALOG (PWM)" : "DIGITAL (I/O)";
                guiGraphics.drawString(font, signalStr, cx, textY, isAnalog ? 0xFFFF55FF : 0xFF55FF55, true);
                textY += 10;

                // DATA
                guiGraphics.drawString(font, "CMD: ", cx, textY, labelCol, true);
                guiGraphics.drawString(font, io.targetData, cx + 25, textY, 0xFFFFFF55, true);
                textY += 10;

                // ESTADO
                guiGraphics.drawString(font, "Soft Power: ", cx, textY, labelCol, true);
                guiGraphics.drawString(font, io.isSoftOn ? "ON" : "OFF", cx + 60, textY, io.isSoftOn ? 0xFF55FF55 : 0xFFFF5555, true);
                textY += 10;

                // REDSTONE
                guiGraphics.drawString(font, "Redstone: ", cx, textY, labelCol, true);
                int rsVal = io.getRedstoneSignal();
                guiGraphics.drawString(font, String.valueOf(rsVal), cx + 55, textY, (rsVal > 0) ? 0xFFFF5555 : 0xFFAAAAAA, true);
            }
        }
    }
}