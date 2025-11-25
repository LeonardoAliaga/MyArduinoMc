package com.serialcraft.block.entity;

import com.serialcraft.SerialCraft;
import com.serialcraft.block.ArduinoIOBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class ArduinoIOBlockEntity extends BlockEntity {

    // ----- DATOS QUE QUEREMOS GUARDAR -----
    public boolean isOutputMode = false;
    public String targetData = "";
    private boolean registered = false;
    private boolean lastPowered = false;

    public ArduinoIOBlockEntity(BlockPos pos, BlockState state) {
        super(SerialCraft.IO_BLOCK_ENTITY, pos, state);
    }

    // ---------- GUARDAR A NBT (1.21.10) ----------
    @Override
    protected void saveAdditional(ValueOutput out) {
        out.putBoolean("isOutput", this.isOutputMode);
        out.putString("targetData", this.targetData == null ? "" : this.targetData);

        // SIEMPRE llamar al super al final
        super.saveAdditional(out);
    }

    // ---------- CARGAR DESDE NBT (1.21.10) ----------
    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);

        // getBooleanOr / getStringOr -> valor por defecto si no existe
        this.isOutputMode = in.getBooleanOr("isOutput", false);
        this.targetData   = in.getStringOr("targetData", "");
    }

    // ---------- SYNC CON EL CLIENTE ----------
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registryLookup) {
        // helper nuevo en 1.21.10
        return this.saveWithoutMetadata(registryLookup);
    }

    // ---------- LÓGICA POR TICK ----------
    public void tick() {
        if (this.level == null || this.level.isClientSide()) return;

        if (!registered) {
            SerialCraft.activeIOBlocks.add(this);
            registered = true;
        }

        // Mirar redstone alrededor del bloque
        boolean powered = this.level.hasNeighborSignal(this.worldPosition);

        if (isOutputMode) {
            // flanco de subida: apagado -> encendido
            if (powered && !lastPowered) {
                SerialCraft.LOGGER.info("REDSTONE DETECTADA -> Enviando: {}", targetData);
                SerialCraft.enviarArduino(targetData);
                this.level.setBlock(this.worldPosition,
                        this.getBlockState().setValue(ArduinoIOBlock.LIT, true),
                        3);
            }
            // flanco de bajada: encendido -> apagado
            else if (!powered && lastPowered) {
                this.level.setBlock(this.worldPosition,
                        this.getBlockState().setValue(ArduinoIOBlock.LIT, false),
                        3);
            }
        }

        lastPowered = powered;
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        SerialCraft.activeIOBlocks.remove(this);
    }

    // llamado cuando guardas la config desde la pantalla
    public void setConfig(boolean isOutput, String data) {
        this.isOutputMode = isOutput;
        this.targetData = data;
        this.setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // usado cuando el ARDUINO quiere encender este bloque en modo INPUT
    public void triggerRedstone() {
        if (this.level != null && !this.level.isClientSide()) {
            this.level.getServer().execute(() -> {
                SerialCraft.LOGGER.info("-> ACTIVANDO REDSTONE en {}", worldPosition);
                this.level.setBlock(this.worldPosition,
                        this.getBlockState().setValue(ArduinoIOBlock.LIT, true),
                        3);
            });
        }
    }
}
