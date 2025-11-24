package com.myarduinomc.block;

import com.myarduinomc.MyArduinoMc;
import java.io.IOException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import com.fazecast.jSerialComm.SerialPort;

public class SignalBlock extends Block {

    public SignalBlock(Properties properties) {
        super(properties);
    }

    // CORRECCIÓN AQUÍ: Usamos 'useWithoutItem' y quitamos 'InteractionHand'
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {

            // 1. ACTUALIZAMOS EL OBJETIVO DEL RAYO
            if (level instanceof net.minecraft.server.level.ServerLevel) {
                MyArduinoMc.mundoObjetivo = (net.minecraft.server.level.ServerLevel) level;
                MyArduinoMc.posicionObjetivo = pos;
                player.displayClientMessage(Component.literal("§e[Arduino] §fObjetivo fijado. ¡Presiona el botón físico!"), true);
            }

            // 2. ENVIAMOS LA SEÑAL AL LED
            if (MyArduinoMc.arduinoPort != null && MyArduinoMc.arduinoPort.isOpen()) {
                // --- AQUÍ ESTÁ EL ARREGLO (TRY-CATCH) ---
                try {
                    MyArduinoMc.arduinoPort.getOutputStream().write(1);
                    MyArduinoMc.arduinoPort.getOutputStream().flush();
                } catch (IOException e) {
                    // Si falla, imprimimos el error en la consola
                    MyArduinoMc.LOGGER.error("Error al enviar señal al Arduino", e);
                }
                // ------------------------------------------
            } else {
                player.displayClientMessage(Component.literal("§c[Arduino] §fDesconectado. Reconectando..."), true);
                MyArduinoMc.conectarArduino();
            }

            return InteractionResult.SUCCESS;
        }
        return InteractionResult.SUCCESS;
    }

    private void sendSignalToArduino() {
        try {
            SerialPort[] ports = SerialPort.getCommPorts();

            // AVISO 1: ¿Cuántos puertos ve?
            MyArduinoMc.LOGGER.info("[Arduino] Escaneando... Se encontraron {} puertos.", ports.length);

            if (ports.length > 0) {
                SerialPort arduinoPort = ports[0];
                // --- AÑADE ESTA LÍNEA AQUÍ ---
                arduinoPort.setBaudRate(9600);
                // -----------------------------
                MyArduinoMc.LOGGER.info("[Arduino] Intentando abrir puerto: {}", arduinoPort.getSystemPortName());

                if (arduinoPort.openPort()) {
                    Thread.sleep(2000); // Opcional: Esperar 2s a que el Arduino se reinicie al abrir puerto
                    arduinoPort.getOutputStream().write(1);
                    arduinoPort.getOutputStream().flush(); // Asegurar envío
                    arduinoPort.closePort();
                    MyArduinoMc.LOGGER.info("[Arduino] ¡ÉXITO! Señal enviada al puerto: {}", arduinoPort.getSystemPortName());
                } else {
                    // AVISO 2: Error al abrir
                    MyArduinoMc.LOGGER.error("[Arduino] ERROR: No se pudo abrir el puerto {}. ¿Está abierto el Monitor Serie de Arduino IDE?", arduinoPort.getSystemPortName());
                }
            } else {
                MyArduinoMc.LOGGER.warn("[Arduino] No se encontraron Arduinos conectados.");
            }
        } catch (Exception e) {
            MyArduinoMc.LOGGER.error("[Arduino] Excepción crítica:", e);
        }
    }
}