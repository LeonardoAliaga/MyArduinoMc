package com.myarduinomc;

import com.fazecast.jSerialComm.SerialPort;
import com.myarduinomc.block.SignalBlock;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents; // Necesario para el loop
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyArduinoMc implements ModInitializer {
    public static final String MOD_ID = "myarduinomc";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static SerialPort arduinoPort = null;

    // VARIABLES PARA SABER DÓNDE DISPARAR EL RAYO
    // Se actualizarán cuando le des clic derecho al bloque
    public static ServerLevel mundoObjetivo = null;
    public static BlockPos posicionObjetivo = null;

    public static final ResourceLocation SIGNAL_BLOCK_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "signal_block");
    public static final ResourceKey<Block> SIGNAL_BLOCK_KEY = ResourceKey.create(Registries.BLOCK, SIGNAL_BLOCK_ID);
    public static final ResourceKey<Item> SIGNAL_ITEM_KEY = ResourceKey.create(Registries.ITEM, SIGNAL_BLOCK_ID);

    public static final Block SIGNAL_BLOCK = new SignalBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .strength(2.0f)
            .setId(SIGNAL_BLOCK_KEY));

    @Override
    public void onInitialize() {
        LOGGER.info("Inicializando MyArduinoMc...");

        Registry.register(BuiltInRegistries.BLOCK, SIGNAL_BLOCK_ID, SIGNAL_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, SIGNAL_BLOCK_ID,
                new BlockItem(SIGNAL_BLOCK, new Item.Properties().setId(SIGNAL_ITEM_KEY)));

        conectarArduino();

        // --- AQUÍ ESTÁ LA MAGIA: ESCUCHAR AL ARDUINO EN CADA TIC ---
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (arduinoPort != null && arduinoPort.isOpen()) {
                // Si hay datos esperando ser leídos...
                if (arduinoPort.bytesAvailable() > 0) {
                    byte[] buffer = new byte[1];
                    arduinoPort.readBytes(buffer, 1); // Leemos 1 byte

                    // Si el byte es el número 2 (Botón presionado)
                    if (buffer[0] == 2) {
                        dispararRayo();
                    }
                }
            }
        });
    }

    private void dispararRayo() {
        if (mundoObjetivo != null && posicionObjetivo != null) {
            LOGGER.info("¡BOTÓN DETECTADO! Invocando rayo...");

            // CORRECCIÓN 1: Ahora 'create' pide obligatoriamente una 'EntitySpawnReason'
            // Usamos .COMMAND porque es como si fueramos un comando
            LightningBolt rayo = EntityType.LIGHTNING_BOLT.create(mundoObjetivo, net.minecraft.world.entity.EntitySpawnReason.COMMAND);

            if (rayo != null) {
                rayo.setPos(posicionObjetivo.getX() + 0.5, posicionObjetivo.getY() + 1, posicionObjetivo.getZ() + 0.5);

                // Añadirlo al mundo
                mundoObjetivo.addFreshEntity(rayo);
            }
        } else {
            LOGGER.warn("Botón presionado, pero no hay objetivo. ¡Haz clic derecho en un bloque primero!");
        }
    }

    public static void conectarArduino() {
        if (arduinoPort != null && arduinoPort.isOpen()) return;

        SerialPort[] ports = SerialPort.getCommPorts();
        if (ports.length > 0) {
            arduinoPort = ports[0];
            arduinoPort.setBaudRate(9600);
            if (arduinoPort.openPort()) {
                LOGGER.info("[Arduino] Conectado a: " + arduinoPort.getSystemPortName());
                try { Thread.sleep(2000); } catch (InterruptedException e) {}
            }
        }
    }
}