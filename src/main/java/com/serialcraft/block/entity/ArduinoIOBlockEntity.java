package com.serialcraft.block.entity;

import com.serialcraft.SerialCraft;
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

    // --- CONSTANTES ---
    public static final int MODE_OUTPUT = 0; // MC -> Ard
    public static final int MODE_INPUT = 1;  // Ard -> MC

    public static final int SIGNAL_DIGITAL = 0;
    public static final int SIGNAL_ANALOG = 1;

    public static final int LOGIC_OR = 0;
    public static final int LOGIC_AND = 1;
    public static final int LOGIC_XOR = 2;

    // --- CONFIGURACIÓN ---
    public int ioMode = MODE_OUTPUT;
    public int signalType = SIGNAL_DIGITAL;
    public String targetData = "cmd_1";
    public boolean isSoftOn = true;
    public String boardID = "placa_gen";
    public int pulseDuration = 10;
    public int logicMode = LOGIC_OR;

    public UUID ownerUUID = null;

    // --- ESTADO INTERNO ---
    private int currentRedstoneOutput = 0;
    private int cachedRedstoneInput = -1;
    private boolean isLogicMet = true;

    public ArduinoIOBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.IO_BLOCK_ENTITY, pos, state);
    }

    public void setOwner(UUID uuid) { this.ownerUUID = uuid; setChanged(); }

    // --- TICK DEL SERVIDOR ---
    public void tickServer() {
        if (this.level == null || this.level.isClientSide()) return;

        // 1. Validar funcionamiento (Soft On + Lógica de Pines)
        if (!isSoftOn || !isLogicMet) {
            if (currentRedstoneOutput != 0) {
                currentRedstoneOutput = 0;
                level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            }
            return;
        }

        // 2. Ejecutar lógica según modo
        if (this.ioMode == MODE_OUTPUT) {
            handleOutputLogic();
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

    // --- LÓGICA DE CONDICIONALES ---
    // Verifica si los cables IN conectados reciben señal para habilitar la placa
    public void updateLogicConditions() {
        if(level == null) return;
        BlockState state = getBlockState();
        int inputPinsCount = 0;
        int activeInputPins = 0;

        for (Direction dir : Direction.values()) {
            if (state.getValue(ArduinoIOBlock.getPropertyForDirection(dir)) == IOSide.INPUT) {
                inputPinsCount++;
                int power = level.getSignal(worldPosition.relative(dir), dir);
                if (power > 0) activeInputPins++;
            }
        }

        // CONDICIÓN IMPORTANTE: Si no hay pines IN, funciona por defecto
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
        int power = level.getBestNeighborSignal(worldPosition);

        if (power == this.cachedRedstoneInput) return;
        this.cachedRedstoneInput = power;

        String messageToSend;

        if (this.signalType == SIGNAL_DIGITAL) {
            int state = (power > 0) ? 1 : 0;
            messageToSend = this.targetData + ":" + state;
        } else {
            // Salida Analógica (MC->Arduino): Enviamos 0-255 (PWM)
            int pwm = Math.round((power * 255.0f) / 15.0f);
            messageToSend = this.targetData + ":" + pwm;
        }

        sendSerialToClient(messageToSend);
    }

    // --- LÓGICA ENTRADA (Arduino -> Minecraft) ---
    public void processSerialInput(String message) {
        if (!isSoftOn || !isLogicMet || this.ioMode != MODE_INPUT) return;

        try {
            if (message.startsWith(this.targetData + ":")) {
                String valueStr = message.split(":")[1];
                int value = Integer.parseInt(valueStr);
                int newRedstone = 0;

                if (this.signalType == SIGNAL_DIGITAL) {
                    newRedstone = (value > 0) ? 15 : 0;
                } else {
                    // CAMBIO APLICADO: Ya no calculamos proporción.
                    // Esperamos que Arduino envíe 0-15 directamente.
                    // Usamos Math.max/min para seguridad (Clamp)
                    newRedstone = Math.max(0, Math.min(15, value));
                }

                if (this.currentRedstoneOutput != newRedstone) {
                    this.currentRedstoneOutput = newRedstone;
                    setChanged();
                    assert level != null;
                    level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
                }
            }
        } catch (Exception ignored) {}
    }

    public int getRedstoneSignal() {
        return (this.ioMode == MODE_INPUT && isSoftOn && isLogicMet) ? this.currentRedstoneOutput : 0;
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

    public void updateConfig(int mode, String data, int signal, boolean softOn, String bId, int duration, int logic) {
        this.ioMode = mode;
        this.targetData = (data == null) ? "" : data;
        this.signalType = signal;
        this.isSoftOn = softOn;
        this.boardID = (bId == null || bId.isEmpty()) ? "placa_gen" : bId;
        this.pulseDuration = duration;
        this.logicMode = logic;

        this.cachedRedstoneInput = -1;
        this.currentRedstoneOutput = 0;

        updateLogicConditions();
        updateVisualState();

        setChanged();
        assert level != null;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("ioMode", ioMode);
        output.putString("targetData", targetData);
        output.putInt("signalType", signalType);
        output.putBoolean("isSoftOn", isSoftOn);
        output.putString("boardID", boardID);
        output.putInt("rsOut", currentRedstoneOutput);
        output.putInt("logicMode", logicMode);
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
        this.currentRedstoneOutput = input.getIntOr("rsOut", 0);
        this.logicMode = input.getIntOr("logicMode", LOGIC_OR);
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
        tag.putInt("logicMode", logicMode);
        return tag;
    }

    @Nullable @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public void onPlayerInteract(Player player) {
        assert level != null;
        if(level.isClientSide()) return;
        Component status = isSoftOn ? Component.translatable("message.serialcraft.on") : Component.translatable("message.serialcraft.off");
        player.displayClientMessage(Component.translatable("message.serialcraft.io_status", this.boardID, status), true);
    }
}