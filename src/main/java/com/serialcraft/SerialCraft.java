package com.serialcraft;

import com.serialcraft.block.ModBlocks;
import com.serialcraft.block.entity.ArduinoIOBlockEntity;
import com.serialcraft.block.entity.ModBlockEntities;
import com.serialcraft.item.ModItems;
import com.serialcraft.network.ModNetworking; // <--- Importamos nuestra nueva clase
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SerialCraft implements ModInitializer {

    public static final String MOD_ID = "serialcraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Mantenemos esto aquí porque es un estado global del mod, no solo de red
    public static final Set<ArduinoIOBlockEntity> activeIOBlocks = Collections.synchronizedSet(new HashSet<>());

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing SerialCraft Core...");

        // 1. Registros de Juego
        ModBlocks.initialize();
        ModItems.initialize();
        ModBlockEntities.initialize();

        // 2. Registros de Red (Codecs y Handlers)
        ModNetworking.registerPayloads();
        ModNetworking.registerServerHandlers();

        LOGGER.info("SerialCraft Initialized successfully!");
    }
}