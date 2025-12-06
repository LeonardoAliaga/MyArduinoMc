package com.serialcraft.block.entity;

import com.serialcraft.SerialCraft;
import com.serialcraft.block.ArduinoIOBlock;
import com.serialcraft.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

// Imports 1.21.10 Storage
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;

public class ArduinoIOBlockEntity extends BlockEntity {

    // 0 = SALIDA (MC -> Arduino)
    // 1 = ENTRADA PULSO (Arduino -> Redstone 1 seg)
    // 2 = ENTRADA INTERRUPTOR (Arduino -> Toggle ON/OFF)
    public int ioMode = 0;
    public String targetData = "";

    // Variable interna para detectar cambios de redstone (Flanco de subida)
    private boolean wasPowered = false;

    public ArduinoIOBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.IO_BLOCK_ENTITY, pos, state);
    }

    // =====================================================================
    // GUARDADO DISCO (ValueOutput)
    // =====================================================================
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("ioMode", ioMode);
        output.putString("targetData", targetData != null ? targetData : "");
        output.putBoolean("wasPowered", wasPowered); // Guardamos el estado redstone también
    }

    // =====================================================================
    // CARGA DISCO (ValueInput)
    // =====================================================================
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.ioMode = input.getIntOr("ioMode", 0);
        this.targetData = input.getString("targetData").orElse("");
        this.wasPowered = input.getBooleanOr("wasPowered", false);
    }

    // =====================================================================
    // RED (CompoundTag)
    // =====================================================================
    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("ioMode", ioMode);
        tag.putString("targetData", targetData != null ? targetData : "");
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // =====================================================================
    // LÓGICA PRINCIPAL (TICK SERVER) - ¡AQUÍ ESTABA EL FALLO!
    // =====================================================================

    public void tickServer() {
        if (this.level == null || this.level.isClientSide()) return;

        // Solo monitoreamos en modo SALIDA (0)
        if (this.ioMode == 0) {
            // Obtenemos la fuerza de la señal (0 a 15)
            int signal = this.level.getBestNeighborSignal(this.worldPosition);
            boolean isPowered = signal > 0;

            // LOG DE CAMBIO DE ESTADO: Solo imprime si la señal cambia (para no llenar la consola)
            if (isPowered != wasPowered) {
                SerialCraft.LOGGER.info("§e[IO DEBUG]§r Bloque en {}: Cambio de señal detectado. Potencia: {} (Encendido: {})",
                        this.worldPosition.toShortString(), signal, isPowered);
            }

            // Detectar FLANCO DE SUBIDA (Estaba apagado y ahora se encendió)
            if (isPowered && !wasPowered) {
                SerialCraft.LOGGER.info("§a[IO DEBUG]§r ¡Activación detectada! Preparando envío...");

                if (targetData != null && !targetData.isEmpty()) {
                    SerialCraft.LOGGER.info("§a[IO DEBUG]§r Enviando al Arduino: '{}'", targetData);
                    SerialCraft.enviarArduino(targetData);
                } else {
                    SerialCraft.LOGGER.warn("§c[IO DEBUG]§r Error: Se detectó señal pero 'targetData' está vacío.");
                }
            }

            // Actualizamos la memoria
            this.wasPowered = isPowered;
        }
    }

    // =====================================================================
    // RESTO DE LÓGICA
    // =====================================================================

    public void setConfig(int mode, String data) {
        this.ioMode = mode;
        this.targetData = (data == null) ? "" : data;

        // Al cambiar config, reseteamos el estado para evitar bloqueos
        this.wasPowered = false;

        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void triggerAction() {
        if (level == null || level.isClientSide()) return;

        BlockState state = getBlockState();
        if (!state.is(ModBlocks.IO_BLOCK)) return;

        if (ioMode == 1) { // PULSO
            level.setBlock(worldPosition, state.setValue(ArduinoIOBlock.POWERED, true), 3);
            level.updateNeighborsAt(worldPosition, state.getBlock());
            level.scheduleTick(worldPosition, state.getBlock(), 20);
        }
        else if (ioMode == 2) { // INTERRUPTOR
            boolean encendido = state.getValue(ArduinoIOBlock.POWERED);
            level.setBlock(worldPosition, state.setValue(ArduinoIOBlock.POWERED, !encendido), 3);
            level.updateNeighborsAt(worldPosition, state.getBlock());
        }
    }

    public void onPlayerInteract(Player player) {
        if (level == null || level.isClientSide()) return;

        String modeName = switch (ioMode) {
            case 0 -> "SALIDA (Envía)";
            case 1 -> "ENTRADA (Pulso)";
            case 2 -> "ENTRADA (Interruptor)";
            default -> "Desconocido";
        };

        // Mensaje de depuración útil
        String estadoRedstone = (ioMode == 0) ? (wasPowered ? " [ON]" : " [OFF]") : "";

        player.displayClientMessage(
                Component.literal("§e[IO] §fModo: §b" + modeName + estadoRedstone + " §f| Clave: §a" + targetData),
                true
        );
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level != null && !level.isClientSide()) SerialCraft.activeIOBlocks.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        SerialCraft.activeIOBlocks.remove(this);
    }
}