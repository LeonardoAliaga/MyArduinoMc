package com.serialcraft;

import com.serialcraft.block.ModBlocks;
import com.serialcraft.block.entity.ArduinoIOBlockEntity;
import com.serialcraft.block.entity.ModBlockEntities;
import com.serialcraft.config.SerialConfig; // <--- Import nuevo
import com.serialcraft.item.ModItems;
import com.serialcraft.network.ModNetworking;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SerialCraft implements ModInitializer {

    public static final String MOD_ID = "serialcraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Set<ArduinoIOBlockEntity> activeIOBlocks = Collections.synchronizedSet(new HashSet<>());

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing SerialCraft Core...");

        // 0. Cargar Configuración (CRUCIAL HACERLO PRIMERO)
        SerialConfig.load();

        // 1. Registros de Juego
        ModBlocks.initialize();
        ModItems.initialize();
        ModBlockEntities.initialize();

        // 2. Registros de Red
        ModNetworking.registerPayloads();
        ModNetworking.registerServerHandlers();

        LOGGER.info("SerialCraft Initialized successfully!");
    }
}