package com.serialcraft.block.entity;

import com.serialcraft.SerialCraft;
import com.serialcraft.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ArduinoIOBlockEntity extends BlockEntity {

    // Configuración del bloque IO
    public boolean isOutputMode = false;
    public String targetData = "";

    public ArduinoIOBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.IO_BLOCK_ENTITY, pos, state);
    }

    // ---------------------------------------------------------------------
    // INTERACCIÓN DEL JUGADOR (click derecho en el bloque)
    // ---------------------------------------------------------------------
    public void onPlayerInteract(Player player) {
        if (level == null || level.isClientSide()) return;

        player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("Configurar IO Block"), false
        );

        // Aquí puedes abrir una pantalla o alternar valores
        // Ejemplo:
        this.isOutputMode = !this.isOutputMode;

        player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("Modo: " + (isOutputMode ? "Output" : "Input")),
                false
        );
    }

    // ---------------------------------------------------------------------
    // TICK DEL SERVIDOR
    // ---------------------------------------------------------------------
    public void tickServer() {
        if (level == null || level.isClientSide()) return;

        if (isOutputMode) {
            // Si el bloque es de salida, envía siempre datos al Arduino
            SerialCraft.enviarArduino(targetData);
        }
    }

    // ---------------------------------------------------------------------
    // Cambiar configuración desde un paquete
    // ---------------------------------------------------------------------
    public void setConfig(boolean isOutput, String data) {
        this.isOutputMode = isOutput;
        this.targetData = data;

        setChanged();
    }
}
