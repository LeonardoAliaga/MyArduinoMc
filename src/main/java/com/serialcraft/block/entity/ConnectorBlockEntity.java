package com.serialcraft.block.entity;

import com.serialcraft.block.ConnectorBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level; // Importante
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ConnectorBlockEntity extends BlockEntity implements MenuProvider {

    public int baudRate = 9600;
    public boolean isConnected = false;

    public ConnectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONNECTOR_BLOCK_ENTITY, pos, state);
    }

    public void updateSettings(int newBaud) {
        this.baudRate = newBaud;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void setConnectionState(boolean connected) {
        // Actualizamos variable interna y forzamos actualización de bloque si cambia
        if (this.isConnected != connected) {
            this.isConnected = connected;
            if (level != null) {
                BlockState state = getBlockState();
                if (state.hasProperty(ConnectorBlock.LIT)) {
                    level.setBlock(worldPosition, state.setValue(ConnectorBlock.LIT, connected), 3);
                }
            }
        }
    }

    // --- PERSISTENCIA (ValueInput / ValueOutput) ---
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("baudRate", baudRate);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.baudRate = input.getIntOr("baudRate", 9600);
    }

    // --- RED ---
    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("baudRate", baudRate);
        tag.putBoolean("isConnected", isConnected);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // --- MENÚ ---
    @Override
    public Component getDisplayName() {
        return Component.translatable("block.serialcraft.connector_block");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int i, Inventory inventory, Player player) {
        return null; // O tu MenuHandler aquí
    }

    // --- MÉTODO AGREGADO PARA EL TICKER ---
    public static void tick(Level level, BlockPos pos, BlockState state, ConnectorBlockEntity entity) {
        if (level.isClientSide()) return;

        // Sincronización visual simple: si la propiedad LIT no coincide con isConnected, actualizamos
        boolean visualLit = state.getValue(ConnectorBlock.LIT);
        if (visualLit != entity.isConnected) {
            level.setBlock(pos, state.setValue(ConnectorBlock.LIT, entity.isConnected), 3);
        }
    }
}