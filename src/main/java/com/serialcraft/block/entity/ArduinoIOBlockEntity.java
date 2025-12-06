package com.serialcraft.block.entity;

import com.serialcraft.SerialCraft;
import com.serialcraft.block.ArduinoIOBlock;
import com.serialcraft.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public class ArduinoIOBlockEntity extends BlockEntity {

    public int ioMode = 0;
    public String targetData = "";

    public EnumSet<Direction> activeDirections = EnumSet.noneOf(Direction.class);
    public EnumSet<Direction> passThroughDirections = EnumSet.noneOf(Direction.class);

    private boolean wasPowered = false;

    public ArduinoIOBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.IO_BLOCK_ENTITY, pos, state);
    }

    // --- GUARDADO/CARGA ---
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("ioMode", ioMode);
        output.putString("targetData", targetData != null ? targetData : "");
        output.putBoolean("wasPowered", wasPowered);
        output.putInt("activeDirs", encodeDirections(activeDirections));
        output.putInt("passDirs", encodeDirections(passThroughDirections));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.ioMode = input.getIntOr("ioMode", 0);
        this.targetData = input.getString("targetData").orElse("");
        this.wasPowered = input.getBooleanOr("wasPowered", false);
        this.activeDirections = decodeDirections(input.getIntOr("activeDirs", 0));
        this.passThroughDirections = decodeDirections(input.getIntOr("passDirs", 0));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("ioMode", ioMode);
        tag.putString("targetData", targetData != null ? targetData : "");
        tag.putInt("activeDirs", encodeDirections(activeDirections));
        tag.putInt("passDirs", encodeDirections(passThroughDirections));
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private int encodeDirections(EnumSet<Direction> dirs) {
        int mask = 0;
        for (Direction d : dirs) mask |= (1 << d.ordinal());
        return mask;
    }
    private EnumSet<Direction> decodeDirections(int mask) {
        EnumSet<Direction> dirs = EnumSet.noneOf(Direction.class);
        for (Direction d : Direction.values()) {
            if ((mask & (1 << d.ordinal())) != 0) dirs.add(d);
        }
        return dirs;
    }

    // --- INTERACCIÓN BOTONES (CORREGIDO: MUTUALMENTE EXCLUYENTE) ---
    public void onButtonInteract(Player player, Direction btn, boolean isShift) {
        if (level == null || level.isClientSide()) return;

        BlockState currentState = getBlockState();
        BooleanProperty property = getPropertyForDirection(btn);

        if (isShift) {
            // SHIFT CLICK: Configurar PUENTE
            // 1. Eliminar de la lista "Normal" si existe (Exclusividad)
            if (activeDirections.contains(btn)) {
                activeDirections.remove(btn);
            }

            // 2. Toggle Puente
            if (passThroughDirections.contains(btn)) {
                passThroughDirections.remove(btn);
                // Apagamos visualmente (ninguno está activo)
                level.setBlockAndUpdate(worldPosition, currentState.setValue(property, false));
                player.displayClientMessage(Component.literal("§6[IO] Puente " + btn.getName() + ": OFF"), true);
            } else {
                passThroughDirections.add(btn);
                // Encendemos visualmente
                level.setBlockAndUpdate(worldPosition, currentState.setValue(property, true));
                player.displayClientMessage(Component.literal("§6[IO] Puente " + btn.getName() + ": ON"), true);
            }
        } else {
            // CLICK NORMAL: Configurar PRINCIPAL
            // 1. Eliminar de la lista "Puente" si existe (Exclusividad)
            if (passThroughDirections.contains(btn)) {
                passThroughDirections.remove(btn);
            }

            // 2. Toggle Principal
            if (activeDirections.contains(btn)) {
                activeDirections.remove(btn);
                // Apagamos visualmente
                level.setBlockAndUpdate(worldPosition, currentState.setValue(property, false));
                player.displayClientMessage(Component.literal("§b[IO] Puerto " + btn.getName() + ": OFF"), true);
            } else {
                activeDirections.add(btn);
                // Encendemos visualmente
                level.setBlockAndUpdate(worldPosition, currentState.setValue(property, true));
                player.displayClientMessage(Component.literal("§b[IO] Puerto " + btn.getName() + ": ON"), true);
            }
        }
        setChanged();
    }

    private BooleanProperty getPropertyForDirection(Direction dir) {
        return switch (dir) {
            case NORTH -> ArduinoIOBlock.NORTH;
            case SOUTH -> ArduinoIOBlock.SOUTH;
            case EAST  -> ArduinoIOBlock.EAST;
            case WEST  -> ArduinoIOBlock.WEST;
            case UP    -> ArduinoIOBlock.UP;
            case DOWN  -> ArduinoIOBlock.DOWN;
        };
    }

    // --- LÓGICA PRINCIPAL ---

    public void tickServer() {
        if (this.level == null || this.level.isClientSide() || this.ioMode != 0) return;

        boolean receivingValidSignal;
        if (activeDirections.isEmpty()) {
            receivingValidSignal = level.getBestNeighborSignal(worldPosition) > 0;
        } else {
            receivingValidSignal = true;
            for (Direction d : activeDirections) {
                if (level.getSignal(worldPosition.relative(d), d) == 0) {
                    receivingValidSignal = false;
                    break;
                }
            }
        }

        BlockState state = getBlockState();
        boolean isPowered = state.getValue(ArduinoIOBlock.POWERED);

        if (receivingValidSignal != isPowered) {
            level.setBlockAndUpdate(worldPosition, state.setValue(ArduinoIOBlock.POWERED, receivingValidSignal));
            level.updateNeighborsAt(worldPosition, state.getBlock());
        }

        if (receivingValidSignal && !wasPowered) {
            if (targetData != null && !targetData.isEmpty()) {
                SerialCraft.enviarArduino(targetData);
            }
        }
        this.wasPowered = receivingValidSignal;
    }

    public void triggerAction() {
        if (level == null || level.isClientSide()) return;
        BlockState state = getBlockState();
        if (!state.is(ModBlocks.IO_BLOCK)) return;

        if (ioMode == 1) {
            level.setBlock(worldPosition, state.setValue(ArduinoIOBlock.POWERED, true), 3);
            level.updateNeighborsAt(worldPosition, state.getBlock());
            level.scheduleTick(worldPosition, state.getBlock(), 20);
        } else if (ioMode == 2) {
            boolean encendido = state.getValue(ArduinoIOBlock.POWERED);
            level.setBlock(worldPosition, state.setValue(ArduinoIOBlock.POWERED, !encendido), 3);
            level.updateNeighborsAt(worldPosition, state.getBlock());
        }
    }

    public void setConfig(int mode, String data) {
        this.ioMode = mode;
        this.targetData = (data == null) ? "" : data;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public void onPlayerInteract(Player player) {
        if (level == null || level.isClientSide()) return;
        String modeName = switch (ioMode) {
            case 0 -> "SALIDA (Envía)";
            case 1 -> "ENTRADA (Pulso)";
            case 2 -> "ENTRADA (Interruptor)";
            default -> "DESC";
        };
        player.displayClientMessage(Component.literal("§e[IO] §fModo: §b" + modeName + " §f| Clave: §a" + targetData), true);
    }

    @Override public void setLevel(Level level) { super.setLevel(level); if (level != null && !level.isClientSide()) SerialCraft.activeIOBlocks.add(this); }
    @Override public void setRemoved() { super.setRemoved(); SerialCraft.activeIOBlocks.remove(this); }
}