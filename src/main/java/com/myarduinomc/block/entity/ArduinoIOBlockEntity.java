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

    // --- MÉTODOS DE LECTURA/ESCRITURA ---

    private void writeMyData(CompoundTag tag) {
        tag.putBoolean("isOutput", isOutputMode);
        tag.putString("targetData", targetData == null ? "" : targetData);
    }

    private void readMyData(CompoundTag tag) {
        // CORRECCIÓN FINAL: Usamos .orElse() explícitamente
        if (tag.contains("isOutput")) {
            this.isOutputMode = tag.getBoolean("isOutput").orElse(false);
        }
        if (tag.contains("targetData")) {
            this.targetData = tag.getString("targetData").orElse("");
        }
    }

    // --- GUARDADO (Soporte Híbrido) ---

    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        writeMyData(tag);
    }

    protected void saveAdditional(CompoundTag tag) {
        writeMyData(tag);
    }

    // --- CARGA (Soporte Híbrido) ---

    public void load(CompoundTag tag) {
        // No llamamos a super.load(tag) para evitar conflictos
        readMyData(tag);
    }

    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        readMyData(tag);
    }

    // --- RED ---
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