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

    // --- GUARDADO UNIVERSAL (Atrapamos todo) ---

    // Método real de guardado
    private void writeMyData(CompoundTag tag) {
        tag.putBoolean("isOutput", isOutputMode);
        tag.putString("targetData", targetData == null ? "" : targetData);
        // MyArduinoMc.LOGGER.info("Guardando datos en " + worldPosition); // Log opcional
    }

    // Variante 1: Con Provider (La moderna)
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        writeMyData(tag);
        // No llamamos a super para evitar crash
    }

    // Variante 2: Sin Provider (La antigua/fallback)
    protected void saveAdditional(CompoundTag tag) {
        writeMyData(tag);
    }

    // --- CARGA UNIVERSAL ---

    // Método real de carga
    private void readMyData(CompoundTag tag) {
        try {
            if (tag.contains("isOutput")) {
                // Intentamos leer como boolean directo, si falla (Optional), usamos el catch
                this.isOutputMode = tag.getBoolean("isOutput");
            }
            if (tag.contains("targetData")) {
                this.targetData = tag.getString("targetData");
            }
        } catch (Exception e) {
            // Si falla por ser Optional, probamos la lógica de 1.21.10
            try {
                // Truco sucio: Usamos reflexión o lógica manual si el método directo falla
                // Pero para simplificar, asumimos que si falla el directo, reseteamos o intentamos otro
                this.isOutputMode = false;
                this.targetData = "";
            } catch (Exception ignored) {}
        }
    }

    // Variante 1: Carga Moderna
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        readMyData(tag);
    }

    // Variante 2: Carga Antigua
    public void load(CompoundTag tag) {
        // En algunas versiones 'load' es el padre de todo.
        // No llamamos a super.load(tag) si da error.
        readMyData(tag);
    }

    // --- RED Y SYNC ---
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeMyData(tag);
        return tag;
    }

    // --- LÓGICA ---
    public void tick() {
        if (!registered && level != null && !level.isClientSide()) {
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
                MyArduinoMc.LOGGER.info("-> ACTIVANDO REDSTONE en " + worldPosition);
                this.level.setBlock(this.worldPosition, this.getBlockState().setValue(ArduinoIOBlock.LIT, true), 3);
                this.level.scheduleTick(this.worldPosition, this.getBlockState().getBlock(), 20);
            });
        }
    }
}