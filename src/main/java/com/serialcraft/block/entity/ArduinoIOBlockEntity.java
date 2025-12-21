package com.serialcraft.block.entity;

import com.serialcraft.block.ArduinoIOBlock;
import com.serialcraft.block.IOSide;
import com.serialcraft.network.SerialOutputPayload;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class ArduinoIOBlockEntity extends BlockEntity {

    // Constantes
    public static final int MODE_OUTPUT = 0;
    public static final int MODE_INPUT = 1;
    public static final int SIGNAL_DIGITAL = 0;
    public static final int SIGNAL_ANALOG = 1;
    public static final int LOGIC_OR = 0;
    public static final int LOGIC_AND = 1;
    public static final int LOGIC_XOR = 2;

    // Variables de estado
    public int ioMode = MODE_OUTPUT;
    public int signalType = SIGNAL_DIGITAL;
    public String targetData = "cmd_1";
    public boolean isSoftOn = true;
    public String boardID = "placa_gen";
    public int logicMode = LOGIC_OR;

    /**
     * Frecuencia de actualización.
     * Si es 1, usamos el modo DIRECTO (sin esperas).
     * Si es > 1, usamos el modo LIMITADO (ahorro de recursos).
     */
    public int updateFrequency = 1;

    public UUID ownerUUID = null;

    // Lógica interna
    private int currentRedstoneOutput = 0;
    private int cachedRedstoneInput = -1;
    private boolean isLogicMet = true;

    // Control de tiempo (Solo para modos lentos o salida)
    private long lastUpdateTick = 0;
    private int pendingSlowInput = -1;

    public ArduinoIOBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.IO_BLOCK_ENTITY, pos, state);
    }

    public void setOwner(UUID uuid) { this.ownerUUID = uuid; setChanged(); }

    // --- CICLO DEL SERVIDOR (Tick) ---
    public void tickServer() {
        if (this.level == null || this.level.isClientSide()) return;

        // 1. Seguridad: Si está apagado lógicamente, cortar energía
        if (!isSoftOn || !isLogicMet) {
            if (currentRedstoneOutput != 0) {
                currentRedstoneOutput = 0;
                level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            }
            return;
        }

        // 2. MODO SALIDA (Minecraft -> Arduino)
        // Aquí SÍ respetamos la frecuencia siempre para no saturar el Serial.
        if (this.ioMode == MODE_OUTPUT) {
            long gameTime = level.getGameTime();
            if (gameTime - lastUpdateTick >= updateFrequency) {
                lastUpdateTick = gameTime;
                handleOutputLogic();
            }
        }

        // 3. MODO ENTRADA LENTO (Solo si updateFrequency > 1)
        // Si el usuario eligió 5Hz o 10Hz, aplicamos el valor pendiente aquí.
        else if (this.ioMode == MODE_INPUT && updateFrequency > 1) {
            long gameTime = level.getGameTime();
            if (gameTime - lastUpdateTick >= updateFrequency) {
                lastUpdateTick = gameTime;
                if (pendingSlowInput != -1) {
                    forceRedstoneUpdate(pendingSlowInput);
                    pendingSlowInput = -1;
                }
            }
        }
    }

    private void updateVisualState() {
        if (level == null) return;
        BlockState currentState = getBlockState();
        int visualMode = this.ioMode;
        boolean visualEnabled = this.isSoftOn;

        if (currentState.getValue(ArduinoIOBlock.ENABLED) != visualEnabled ||
                currentState.getValue(ArduinoIOBlock.MODE) != visualMode) {
            level.setBlock(worldPosition, currentState
                    .setValue(ArduinoIOBlock.ENABLED, visualEnabled)
                    .setValue(ArduinoIOBlock.MODE, visualMode), 3);
        }
    }

    public void updateLogicConditions() {
        if(level == null) return;
        BlockState state = getBlockState();

        if (this.ioMode == MODE_OUTPUT) {
            this.isLogicMet = true;
            return;
        }

        int inputPinsCount = 0;
        int activeInputPins = 0;

        for (Direction dir : Direction.values()) {
            if (state.getValue(ArduinoIOBlock.getPropertyForDirection(dir)) == IOSide.INPUT) {
                inputPinsCount++;
                int power = level.getSignal(worldPosition.relative(dir), dir);
                if (power > 0) activeInputPins++;
            }
        }

        if (inputPinsCount == 0) {
            this.isLogicMet = true;
            return;
        }

        switch (logicMode) {
            case LOGIC_OR:  this.isLogicMet = (activeInputPins > 0); break;
            case LOGIC_AND: this.isLogicMet = (activeInputPins == inputPinsCount); break;
            case LOGIC_XOR: this.isLogicMet = (activeInputPins % 2 != 0); break;
            default:        this.isLogicMet = true;
        }
    }

    private void handleOutputLogic() {
        assert level != null;
        int maxPower = 0;
        boolean hasInputPins = false;
        BlockState state = getBlockState();

        for (Direction dir : Direction.values()) {
            if (state.getValue(ArduinoIOBlock.getPropertyForDirection(dir)) == IOSide.INPUT) {
                hasInputPins = true;
                int p = level.getSignal(worldPosition.relative(dir), dir);
                if (p > maxPower) maxPower = p;
            }
        }

        if (!hasInputPins) maxPower = 0;

        if (this.currentRedstoneOutput != maxPower) {
            this.currentRedstoneOutput = maxPower;
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }

        if (maxPower == this.cachedRedstoneInput) return;
        this.cachedRedstoneInput = maxPower;

        StringBuilder sb = new StringBuilder();
        sb.append(this.targetData).append(':');

        if (this.signalType == SIGNAL_DIGITAL) {
            sb.append(maxPower > 0 ? '1' : '0');
        } else {
            int pwm = Math.round((maxPower * 255.0f) / 15.0f);
            sb.append(pwm);
        }

        sendSerialToClient(sb.toString());
    }

    /**
     * MODO ENTRADA (Arduino -> Minecraft)
     * Aquí ocurre la magia de la respuesta instantánea.
     */
    public void processSerialInput(String message) {
        if (!isSoftOn || !isLogicMet || this.ioMode != MODE_INPUT) return;

        try {
            if (message.startsWith(this.targetData + ":")) {
                String valueStr = message.split(":")[1];
                int value = Integer.parseInt(valueStr);

                int newRedstone = Math.max(0, Math.min(15, value));
                if (this.signalType == SIGNAL_DIGITAL) {
                    newRedstone = (value > 0) ? 15 : 0;
                }

                // --- LÓGICA DIRECTA ---
                if (updateFrequency <= 1) {
                    // Si está en modo RÁPIDO (default), aplicamos INMEDIATAMENTE.
                    // No esperamos al tick, no usamos colas. Directo al mundo.
                    forceRedstoneUpdate(newRedstone);
                } else {
                    // Si el usuario pidió modo LENTO (5Hz), guardamos para el tickServer.
                    this.pendingSlowInput = newRedstone;
                }
            }
        } catch (Exception ignored) {}
    }

    // Método auxiliar para aplicar cambios al mundo real
    private void forceRedstoneUpdate(int newValue) {
        if (this.currentRedstoneOutput != newValue) {
            this.currentRedstoneOutput = newValue;

            // Notificamos a Minecraft que este bloque cambió y debe actualizar vecinos (Redstone Wire, Lámparas)
            setChanged();
            if (level != null) {
                level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            }
        }
    }

    public int getRedstoneSignal() {
        return (isSoftOn) ? this.currentRedstoneOutput : 0;
    }

    private void sendSerialToClient(String msg) {
        if (ownerUUID != null) {
            assert level != null;
            Player p = level.getPlayerByUUID(ownerUUID);
            if (p instanceof ServerPlayer sp) {
                ServerPlayNetworking.send(sp, new SerialOutputPayload(msg));
            }
        }
    }

    // --- CONFIG UPDATE ---
    public void updateConfig(int mode, String data, int signal, boolean softOn, String bId, int frequency, int logic) {
        this.ioMode = mode;
        this.targetData = (data == null) ? "" : data;
        this.signalType = signal;
        this.isSoftOn = softOn;
        this.boardID = (bId == null || bId.isEmpty()) ? "placa_gen" : bId;

        // Frecuencia mínima 1
        this.updateFrequency = Math.max(1, frequency);

        this.logicMode = logic;
        this.cachedRedstoneInput = -1;
        this.lastUpdateTick = 0;

        updateLogicConditions();
        updateVisualState();

        setChanged();
        assert level != null;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }

    // Métodos de guardado estándar
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("ioMode", ioMode);
        output.putString("targetData", targetData);
        output.putInt("signalType", signalType);
        output.putBoolean("isSoftOn", isSoftOn);
        output.putString("boardID", boardID);
        output.putInt("updateFreq", updateFrequency);
        output.putInt("logicMode", logicMode);
        output.putInt("rsOut", currentRedstoneOutput);
        if (this.ownerUUID != null) output.putString("OwnerUUID", this.ownerUUID.toString());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.ioMode = input.getIntOr("ioMode", 0);
        this.targetData = input.getString("targetData").orElse("");
        this.signalType = input.getIntOr("signalType", 0);
        this.isSoftOn = input.getBooleanOr("isSoftOn", true);
        this.boardID = input.getString("boardID").orElse("placa_gen");
        this.updateFrequency = input.getIntOr("updateFreq", 1);
        this.logicMode = input.getIntOr("logicMode", LOGIC_OR);
        this.currentRedstoneOutput = input.getIntOr("rsOut", 0);

        input.getString("OwnerUUID").ifPresent(uuidStr -> {
            try { this.ownerUUID = UUID.fromString(uuidStr); } catch (Exception ignored) { }
        });
    }

    @Override
    public @NotNull CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("ioMode", ioMode);
        tag.putString("targetData", targetData);
        tag.putInt("signalType", signalType);
        tag.putBoolean("isSoftOn", isSoftOn);
        tag.putString("boardID", boardID);
        tag.putInt("updateFreq", updateFrequency);
        tag.putInt("logicMode", logicMode);
        return tag;
    }

    @Nullable @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public void onPlayerInteract(Player player) {
        if (level == null || level.isClientSide()) return;
        Component status = isSoftOn ? Component.translatable("message.serialcraft.on") : Component.translatable("message.serialcraft.off");
        player.displayClientMessage(Component.translatable("message.serialcraft.io_status", this.boardID, status), true);
    }
}