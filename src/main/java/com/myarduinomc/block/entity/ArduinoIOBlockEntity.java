package com.myarduinomc.block.entity;

import com.myarduinomc.MyArduinoMc;
import com.myarduinomc.block.ArduinoIOBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ArduinoIOBlockEntity extends BlockEntity {
    public boolean isOutputMode = false;
    public String targetData = "";
    private boolean registered = false;

    public ArduinoIOBlockEntity(BlockPos pos, BlockState blockState) {
        super(MyArduinoMc.IO_BLOCK_ENTITY, pos, blockState);
    }

    // --- GUARDADO (Save) ---
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("isOutput", isOutputMode);
        tag.putString("targetData", targetData == null ? "" : targetData);
    }

    // --- CARGA (Load) - AQUÍ ESTÁ EL ARREGLO ---
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // En 1.21.10, getBoolean devuelve Optional, así que usamos orElse
        // Si te sigue dando error, prueba con tag.getBoolean("isOutput").orElse(false)
        // Si tu IDE dice que es boolean primitivo, quita el .orElse
        try {
            if (tag.contains("isOutput")) {
                this.isOutputMode = tag.getBoolean("isOutput");
            }
            if (tag.contains("targetData")) {
                this.targetData = tag.getString("targetData");
            }
        } catch (Exception e) {
            // Fallback por si la API cambia
            this.isOutputMode = false;
        }
    }

    // --- Resto del código igual ---
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    public void tick() {
        if (!registered && !level.isClientSide()) {
            MyArduinoMc.activeIOBlocks.add(this);
            registered = true;
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        MyArduinoMc.activeIOBlocks.remove(this);
    }

    public void setConfig(boolean isOutput, String data) {
        this.isOutputMode = isOutput;
        this.targetData = data;
        this.setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public void triggerRedstone() {
        if (this.level != null && !this.level.isClientSide()) {
            this.level.getServer().execute(() -> {
                this.level.setBlock(this.worldPosition, this.getBlockState().setValue(ArduinoIOBlock.LIT, true), 3);
                this.level.scheduleTick(this.worldPosition, this.getBlockState().getBlock(), 20);
            });
        }
    }
}