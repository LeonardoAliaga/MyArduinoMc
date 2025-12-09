package com.serialcraft.block.entity;

import com.serialcraft.SerialCraft;
import com.serialcraft.block.ArduinoIOBlock;
import com.serialcraft.block.IOSide;
import com.serialcraft.block.ModBlocks;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class ArduinoIOBlockEntity extends BlockEntity {

    // Configuración
    public int ioMode = 0;
    public String targetData = "";
    public UUID ownerUUID = null;
    public int inputType = 0;
    public int pulseDuration = 10;
    public int signalStrength = 15;
    public String boardID = "placa_" + (int)(Math.random() * 9999);

    // Configuración Avanzada
    public int logicMode = 0;   // 0=OR, 1=AND, 2=XOR
    public int outputType = 0;  // 0=Pulso, 1=Interruptor
    public boolean isSoftOn = true;

    // Estado Interno
    public boolean outputState = false;
    private boolean wasLogicMet = false;
    private int pulseTimer = 0;
    private int blinkTimer = 0;

    public ArduinoIOBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.IO_BLOCK_ENTITY, pos, state);
    }

    public void setOwner(UUID uuid) { this.ownerUUID = uuid; setChanged(); }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("ioMode", ioMode);
        output.putString("targetData", targetData != null ? targetData : "");
        if (this.ownerUUID != null) output.putString("OwnerUUID", this.ownerUUID.toString());
        output.putInt("inputType", inputType);
        output.putInt("pulseDuration", pulseDuration);
        output.putInt("signalStrength", signalStrength);
        output.putString("boardID", boardID);

        output.putInt("logicMode", logicMode);
        output.putInt("outputType", outputType);
        output.putBoolean("isSoftOn", isSoftOn);
        output.putBoolean("outputState", outputState);
        output.putBoolean("wasLogicMet", wasLogicMet);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.ioMode = input.getIntOr("ioMode", 0);
        this.targetData = input.getString("targetData").orElse("");
        input.getString("OwnerUUID").ifPresent(uuidStr -> {
            try { this.ownerUUID = UUID.fromString(uuidStr); } catch (Exception ignored) { this.ownerUUID = null; }
        });
        this.inputType = input.getIntOr("inputType", 0);
        this.pulseDuration = input.getIntOr("pulseDuration", 10);
        this.signalStrength = input.getIntOr("signalStrength", 15);
        this.boardID = input.getString("boardID").orElse(this.boardID);

        this.logicMode = input.getIntOr("logicMode", 0);
        this.outputType = input.getIntOr("outputType", 0);
        this.isSoftOn = input.getBooleanOr("isSoftOn", true);
        this.outputState = input.getBooleanOr("outputState", false);
        this.wasLogicMet = input.getBooleanOr("wasLogicMet", false);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("ioMode", ioMode);
        tag.putString("boardID", boardID);
        tag.putString("targetData", targetData);
        tag.putInt("logicMode", logicMode);
        tag.putInt("outputType", outputType);
        tag.putBoolean("isSoftOn", isSoftOn);
        tag.putInt("pulseDuration", pulseDuration);
        tag.putInt("signalStrength", signalStrength);
        return tag;
    }

    @Nullable @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // --- LÓGICA PRINCIPAL ---
    public void tickServer() {
        if (this.level == null || this.level.isClientSide()) return;

        BlockState state = getBlockState();
        boolean changed = false;

        // 1. Soft-OFF (Apagado por UI)
        if (!isSoftOn) {
            if (outputState) { outputState = false; changed = true; }
            if (state.getValue(ArduinoIOBlock.ENABLED)) {
                state = state.setValue(ArduinoIOBlock.ENABLED, false);
                changed = true;
            }
            if (state.getValue(ArduinoIOBlock.BLINKING)) {
                state = state.setValue(ArduinoIOBlock.BLINKING, false);
                changed = true;
            }
            if (changed) {
                level.setBlockAndUpdate(worldPosition, state);
                level.updateNeighborsAt(worldPosition, state.getBlock());
            }
            return;
        } else {
            if (!state.getValue(ArduinoIOBlock.ENABLED)) {
                state = state.setValue(ArduinoIOBlock.ENABLED, true);
                changed = true;
            }
        }

        // 2. Timer de Pulso (Solo cuenta hacia atrás, no apaga switches)
        if (pulseTimer > 0) {
            pulseTimer--;
            if (pulseTimer == 0 && outputType == 0) { // Fin del pulso
                outputState = false;
                changed = true;
            }
        }
        if (blinkTimer > 0) {
            blinkTimer--;
            if (blinkTimer == 0) {
                state = state.setValue(ArduinoIOBlock.BLINKING, false);
                changed = true;
            }
        }

        // 3. Lógica Redstone (MC -> PC)
        if (ioMode == 0 || ioMode == 2) {
            boolean conditionMet = checkInputLogic(state);

            // DETECCIÓN DE FLANCO DE SUBIDA (Rising Edge)
            // Solo actuamos cuando la condición pasa de FALSO a VERDADERO.
            if (conditionMet && !wasLogicMet) {

                // A. Enviar Dato
                sendSerialData();

                // B. Feedback Físico (Pines Rojos)
                if (outputType == 0) {
                    // PULSO: Encender y poner timer
                    outputState = true;
                    pulseTimer = pulseDuration;
                } else {
                    // INTERRUPTOR: Alternar estado (Toggle)
                    // No importa si la señal se apaga después, esto se queda así hasta el próximo trigger.
                    outputState = !outputState;
                }

                // C. Feedback Visual
                triggerBlink(state);
                changed = true;
            }

            this.wasLogicMet = conditionMet;
        }

        if (changed) {
            level.setBlockAndUpdate(worldPosition, state);
            level.updateNeighborsAt(worldPosition, state.getBlock());
        }
    }

    private boolean checkInputLogic(BlockState state) {
        int activeInputs = 0;
        int configuredInputs = 0;

        for (Direction d : Direction.values()) {
            EnumProperty<IOSide> prop = ArduinoIOBlock.getPropertyForDirection(d);
            if (state.getValue(prop) == IOSide.INPUT) {
                configuredInputs++;
                if (level.getSignal(worldPosition.relative(d), d.getOpposite()) > 0) {
                    activeInputs++;
                }
            }
        }

        if (configuredInputs == 0) return false;

        return switch (logicMode) {
            case 0 -> activeInputs > 0; // OR
            case 1 -> activeInputs == configuredInputs; // AND

            // XOR: Usamos PARIDAD (Impar = True, Par = False)
            // Si hay 1 activo -> 1 % 2 != 0 -> TRUE (Dispara)
            // Si hay 2 activos -> 2 % 2 == 0 -> FALSE (No Dispara)
            // Esto cumple tu requerimiento exacto.
            case 2 -> (activeInputs % 2) != 0;

            default -> false;
        };
    }

    private void triggerBlink(BlockState state) {
        if (!state.getValue(ArduinoIOBlock.BLINKING)) {
            level.setBlockAndUpdate(worldPosition, state.setValue(ArduinoIOBlock.BLINKING, true));
            blinkTimer = 5;
        }
    }

    // --- ACCIÓN EXTERNA (Serial PC -> MC) ---
    public void triggerAction() {
        if (level == null || level.isClientSide() || !isSoftOn) return;

        BlockState state = getBlockState();
        if (!state.is(ModBlocks.IO_BLOCK)) return;

        // Validar Condicionales en Modo Entrada
        if (ioMode == 1 || ioMode == 2) {
            boolean hasInputPins = false;
            for(Direction d : Direction.values()) if(state.getValue(ArduinoIOBlock.getPropertyForDirection(d)) == IOSide.INPUT) hasInputPins = true;

            // Si hay pines y no se cumple la lógica, ignoramos el dato del Arduino
            if (hasInputPins && !checkInputLogic(state)) return;

            // Ejecutar Salida
            if (outputType == 0) {
                this.outputState = true;
                this.pulseTimer = this.pulseDuration;
            } else {
                this.outputState = !this.outputState; // Toggle
            }

            triggerBlink(state);
            level.updateNeighborsAt(worldPosition, state.getBlock());
            setChanged();
        }
    }

    // --- CONFIGURACIÓN ---
    public void setConfig(int mode, String data, int inputType, int pulseDuration, int signalStrength, String boardID, int logicMode, int outputType, boolean isSoftOn) {
        // RESET IMPORTANTE: Evita que la placa se quede "pegada" al cambiar de modo
        resetIO();

        this.ioMode = mode;
        this.targetData = (data == null) ? "" : data;
        this.inputType = inputType;
        this.pulseDuration = pulseDuration;
        this.signalStrength = Math.max(1, Math.min(15, signalStrength));
        this.boardID = (boardID == null || boardID.isEmpty()) ? "placa_gen" : boardID;
        this.logicMode = logicMode;
        this.outputType = outputType;
        this.isSoftOn = isSoftOn;

        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, 3);
            level.updateNeighborsAt(worldPosition, state.getBlock());
        }
    }

    private void resetIO() {
        this.outputState = false;
        this.pulseTimer = 0;
        this.blinkTimer = 0;
        this.wasLogicMet = false;
    }

    private void sendSerialData() {
        if (targetData != null && !targetData.isEmpty() && ownerUUID != null) {
            Player p = level.getPlayerByUUID(ownerUUID);
            if (p instanceof ServerPlayer sp) ServerPlayNetworking.send(sp, new SerialCraft.SerialOutputPayload(targetData));
        }
    }

    public void onButtonInteract(Player player, Direction btn, boolean isShift) {
        if (level == null || level.isClientSide()) return;
        if (ownerUUID != null && !ownerUUID.equals(player.getUUID())) return;

        BlockState currentState = getBlockState();
        EnumProperty<IOSide> property = ArduinoIOBlock.getPropertyForDirection(btn);
        IOSide currentSideState = currentState.getValue(property);
        IOSide newState;

        if (isShift) {
            newState = (currentSideState == IOSide.OUTPUT) ? IOSide.NONE : IOSide.OUTPUT;
            player.displayClientMessage(Component.literal((newState==IOSide.OUTPUT ? "§6[IO] Lado SALIDA" : "§7[IO] Desconectado")), true);
        } else {
            newState = (currentSideState == IOSide.INPUT) ? IOSide.NONE : IOSide.INPUT;
            player.displayClientMessage(Component.literal((newState==IOSide.INPUT ? "§a[IO] Lado ENTRADA" : "§7[IO] Desconectado")), true);
        }

        BlockState nextState = currentState.setValue(property, newState);
        level.setBlockAndUpdate(worldPosition, nextState);
        // Recalcular lógica inmediata
        this.wasLogicMet = checkInputLogic(nextState);
    }

    public void onPlayerInteract(Player player) {
        if (level == null || level.isClientSide()) return;
        player.displayClientMessage(Component.literal("§e[IO] §fID: §b" + boardID + " §f| Power: " + (isSoftOn ? "§aON" : "§cOFF")), true);
    }
    @Override public void setLevel(Level level) { super.setLevel(level); if(level != null && !level.isClientSide()) SerialCraft.activeIOBlocks.add(this); }
    @Override public void setRemoved() { super.setRemoved(); SerialCraft.activeIOBlocks.remove(this); }
}