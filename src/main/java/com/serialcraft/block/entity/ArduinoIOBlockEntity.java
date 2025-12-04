package com.serialcraft.block.entity;

import com.serialcraft.SerialCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ArduinoIOBlockEntity extends BlockEntity {

    // true = salida (MC → Arduino) / false = entrada (Arduino → Redstone)
    public boolean isOutputMode = false;
    public String targetData = "";

    public ArduinoIOBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.IO_BLOCK_ENTITY, pos, state);
    }

    // ---------------------------------------------------------------------
    // REGISTRO DEL BLOCK ENTITY EN EL MUNDO (1.21+)
    // ---------------------------------------------------------------------
    @Override
    public void setLevel(Level level) {
        super.setLevel(level);

        if (level != null && !level.isClientSide()) {
            SerialCraft.activeIOBlocks.add(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        SerialCraft.activeIOBlocks.remove(this);
    }

    // ---------------------------------------------------------------------
    // INTERACCIÓN DEL JUGADOR (llamado desde ArduinoIOBlock.useWithoutItem)
    // ---------------------------------------------------------------------
    public void onPlayerInteract(Player player) {
        if (level == null || level.isClientSide()) {
            return;
        }

        // Por ahora alternamos el modo
        isOutputMode = !isOutputMode;

        player.displayClientMessage(
                Component.literal("Modo IO: " + (isOutputMode
                        ? "Salida (MC → Arduino)"
                        : "Entrada (Arduino → Redstone)")),
                false
        );

        setChanged();
    }

    // ---------------------------------------------------------------------
    // TICK DEL SERVIDOR (usado en SerialCraft.activeIOBlocks loop)
    // ---------------------------------------------------------------------
    public void tickServer() {
        if (this.level == null || this.level.isClientSide()) return;

        if (isOutputMode && targetData != null && !targetData.isEmpty()) {
            SerialCraft.enviarArduino(targetData);
        }
    }

    // ---------------------------------------------------------------------
    // DISPARAR SEÑAL DE REDSTONE (llamado desde SerialCraft al recibir mensaje)
    // ---------------------------------------------------------------------
    public void triggerRedstone() {
        if (level == null) return;

        BlockPos pos = getBlockPos();
        BlockState state = getBlockState();

        // Notifica a los vecinos que algo cambió
        level.updateNeighborsAt(pos, state.getBlock());

        // Pulso corto de redstone
        level.scheduleTick(pos, state.getBlock(), 1);
    }

    // ---------------------------------------------------------------------
    // Configuración desde la pantalla IO (ConfigPayload)
    // ---------------------------------------------------------------------
    public void setConfig(boolean output, String data) {
        this.isOutputMode = output;
        this.targetData = (data == null) ? "" : data;
        setChanged();
    }
}
