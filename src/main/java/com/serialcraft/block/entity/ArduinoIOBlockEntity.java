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
    public int ioMode = 0; // 0=Salida, 1=Entrada, 2=Híbrido
    public String targetData = "";
    public UUID ownerUUID = null;

    // Avanzado
    public int inputType = 0;
    public int pulseDuration = 10;
    public int signalStrength = 15;
    public String boardID = "placa_" + (int)(Math.random() * 9999);

    // Estado Interno
    private boolean wasPowered = false;
    private int pulseTimer = 0;
    private int blinkTimer = 0;

    // Rastreo de entradas (Bitmask) para detectar cambios individuales
    // Bit 0=Down, 1=Up, 2=North, 3=South, 4=West, 5=East
    private int lastInputMask = 0;

    public ArduinoIOBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.IO_BLOCK_ENTITY, pos, state);
    }

    public void setOwner(UUID uuid) {
        this.ownerUUID = uuid;
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("ioMode", ioMode);
        output.putString("targetData", targetData != null ? targetData : "");
        output.putBoolean("wasPowered", wasPowered);
        if (this.ownerUUID != null) output.putString("OwnerUUID", this.ownerUUID.toString());
        output.putInt("inputType", inputType);
        output.putInt("pulseDuration", pulseDuration);
        output.putInt("signalStrength", signalStrength);
        output.putString("boardID", boardID != null ? boardID : "error_id");
        output.putInt("lastInputMask", lastInputMask);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.ioMode = input.getIntOr("ioMode", 0);
        this.targetData = input.getString("targetData").orElse("");
        this.wasPowered = input.getBooleanOr("wasPowered", false);
        input.getString("OwnerUUID").ifPresent(uuidStr -> {
            try { this.ownerUUID = UUID.fromString(uuidStr); } catch (Exception ignored) { this.ownerUUID = null; }
        });
        this.inputType = input.getIntOr("inputType", 0);
        this.pulseDuration = input.getIntOr("pulseDuration", 10);
        this.signalStrength = input.getIntOr("signalStrength", 15);
        this.boardID = input.getString("boardID").orElse(this.boardID);
        this.lastInputMask = input.getIntOr("lastInputMask", 0);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("ioMode", ioMode);
        tag.putString("targetData", targetData != null ? targetData : "");
        if (ownerUUID != null) tag.putString("OwnerUUID", ownerUUID.toString());
        tag.putInt("inputType", inputType);
        tag.putInt("pulseDuration", pulseDuration);
        tag.putInt("signalStrength", signalStrength);
        tag.putString("boardID", boardID);
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

        // 1. Calcular estado actual de cada pin (Bitmask)
        int currentInputMask = 0;
        for (Direction d : Direction.values()) {
            EnumProperty<IOSide> prop = ArduinoIOBlock.getPropertyForDirection(d);
            if (state.getValue(prop) == IOSide.INPUT) {
                // Si llega energía por este lado, activamos su bit
                if (level.getSignal(worldPosition.relative(d), d.getOpposite()) > 0) {
                    currentInputMask |= (1 << d.ordinal());
                }
            }
        }

        // 2. Verificar si la placa está "ALIMENTADA" según el modo
        boolean isEnabled = checkPowerLogic(state, currentInputMask);

        if (isEnabled != state.getValue(ArduinoIOBlock.ENABLED)) {
            state = state.setValue(ArduinoIOBlock.ENABLED, isEnabled);
            changed = true;
        }

        // 3. Timers
        if (pulseTimer > 0) {
            pulseTimer--;
            if (pulseTimer == 0) {
                state = state.setValue(ArduinoIOBlock.POWERED, false);
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

        if (changed) {
            level.setBlockAndUpdate(worldPosition, state);
            level.updateNeighborsAt(worldPosition, state.getBlock());
        }

        // 4. LÓGICA DE ENVÍO (Salida o Híbrido)
        if (ioMode == 0 || ioMode == 2) {
            // Solo si la placa está viva
            if (isEnabled) {
                // Detectar si ALGÚN pin nuevo se encendió (Rising Edge per pin)
                // (current & ~last) > 0 significa que hay un bit en 1 ahora que antes era 0
                boolean newActivation = (currentInputMask & ~lastInputMask) != 0;

                // También enviar si la placa se acaba de encender por completo (Global Rising Edge)
                boolean globalPowerOn = isEnabled && !wasPowered;

                if (newActivation || globalPowerOn) {
                    if (targetData != null && !targetData.isEmpty() && ownerUUID != null) {
                        Player player = level.getPlayerByUUID(ownerUUID);
                        if (player instanceof ServerPlayer serverPlayer) {
                            ServerPlayNetworking.send(serverPlayer, new SerialCraft.SerialOutputPayload(targetData));
                        }
                    }
                }
            }
            // Guardar estado para el siguiente tick
            this.lastInputMask = currentInputMask;
            this.wasPowered = isEnabled;
        }
    }

    // --- NUEVA LÓGICA DE ENERGÍA SEGÚN MODO ---
    private boolean checkPowerLogic(BlockState state, int currentMask) {
        int activeInputsCount = Integer.bitCount(currentMask);
        int configuredInputsCount = 0;

        // Contar cuántos pines están configurados como verdes (INPUT)
        for (Direction d : Direction.values()) {
            EnumProperty<IOSide> prop = ArduinoIOBlock.getPropertyForDirection(d);
            if (state.getValue(prop) == IOSide.INPUT) {
                configuredInputsCount++;
            }
        }

        // Si no hay pines de entrada, la placa tiene energía interna (USB)
        if (configuredInputsCount == 0) return true;

        // REGLAS ESPECÍFICAS:
        if (ioMode == 1) {
            // MODO ENTRADA: Lógica AND (Estricta)
            // "Si hay dos o más, se requiere que TODOS estén alimentados"
            return activeInputsCount == configuredInputsCount;
        } else {
            // MODO SALIDA / HÍBRIDO: Lógica OR (Flexible)
            // "Encendida con solo un pin de energía"
            return activeInputsCount > 0;
        }
    }

    public void triggerAction() {
        if (level == null || level.isClientSide()) return;
        BlockState state = getBlockState();
        if (!state.is(ModBlocks.IO_BLOCK)) return;

        // Seguridad: Si la placa no está alimentada, ignora al Arduino
        if (!state.getValue(ArduinoIOBlock.ENABLED)) return;

        boolean update = false;

        if (!state.getValue(ArduinoIOBlock.BLINKING)) {
            state = state.setValue(ArduinoIOBlock.BLINKING, true);
            blinkTimer = 5;
            update = true;
        }

        if (ioMode == 1 || ioMode == 2) {
            if (this.inputType == 1) {
                if (!state.getValue(ArduinoIOBlock.POWERED)) {
                    state = state.setValue(ArduinoIOBlock.POWERED, true);
                    this.pulseTimer = this.pulseDuration;
                    update = true;
                }
            } else {
                boolean encendido = state.getValue(ArduinoIOBlock.POWERED);
                state = state.setValue(ArduinoIOBlock.POWERED, !encendido);
                update = true;
            }
        }

        if (update) {
            level.setBlockAndUpdate(worldPosition, state);
            level.updateNeighborsAt(worldPosition, state.getBlock());
        }
    }

    public void onButtonInteract(Player player, Direction btn, boolean isShift) {
        if (level == null || level.isClientSide()) return;
        if (ownerUUID != null && !ownerUUID.equals(player.getUUID())) {
            player.displayClientMessage(Component.literal("§cAcceso Denegado."), true);
            return;
        }

        BlockState currentState = getBlockState();
        EnumProperty<IOSide> property = ArduinoIOBlock.getPropertyForDirection(btn);
        IOSide currentSideState = currentState.getValue(property);
        IOSide newState;

        if (isShift) {
            newState = (currentSideState == IOSide.OUTPUT) ? IOSide.NONE : IOSide.OUTPUT;
            String msg = (newState == IOSide.OUTPUT) ? "§6[IO] Lado " + btn.getName() + ": SALIDA (Rojo)" : "§7[IO] Lado " + btn.getName() + ": Desconectado";
            player.displayClientMessage(Component.literal(msg), true);
        } else {
            newState = (currentSideState == IOSide.INPUT) ? IOSide.NONE : IOSide.INPUT;
            String msg = (newState == IOSide.INPUT) ? "§a[IO] Lado " + btn.getName() + ": ENTRADA (Verde)" : "§7[IO] Lado " + btn.getName() + ": Desconectado";
            player.displayClientMessage(Component.literal(msg), true);
        }
        BlockState nextState = currentState.setValue(property, newState);
        level.setBlockAndUpdate(worldPosition, nextState);
        level.updateNeighborsAt(worldPosition, nextState.getBlock());
    }

    public void setConfig(int mode, String data, int inputType, int pulseDuration, int signalStrength, String boardID) {
        this.ioMode = mode;
        this.targetData = (data == null) ? "" : data;
        this.inputType = inputType;
        this.pulseDuration = pulseDuration;
        this.signalStrength = Math.max(1, Math.min(15, signalStrength));
        this.boardID = (boardID == null || boardID.isEmpty()) ? "placa_gen" : boardID;
        setChanged();

        if (level != null) {
            BlockState state = getBlockState();
            if (state.getValue(ArduinoIOBlock.MODE) != mode) {
                level.setBlockAndUpdate(worldPosition, state.setValue(ArduinoIOBlock.MODE, mode));
            } else {
                level.sendBlockUpdated(worldPosition, state, state, 3);
            }
            level.updateNeighborsAt(worldPosition, state.getBlock());
        }
    }

    public void onPlayerInteract(Player player) {
        if (level == null || level.isClientSide()) return;
        player.displayClientMessage(Component.literal("§e[IO] §fID: §b" + boardID + " §f| Power: §c" + signalStrength), true);
    }

    @Override public void setLevel(Level level) { super.setLevel(level); if (level != null && !level.isClientSide()) SerialCraft.activeIOBlocks.add(this); }
    @Override public void setRemoved() { super.setRemoved(); SerialCraft.activeIOBlocks.remove(this); }
}