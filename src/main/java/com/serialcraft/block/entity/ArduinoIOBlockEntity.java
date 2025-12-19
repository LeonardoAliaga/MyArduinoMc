package com.serialcraft.block.entity;

import com.serialcraft.SerialCraft;
import com.serialcraft.network.SerialOutputPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class ArduinoIOBlockEntity extends BlockEntity {

    // --- ENUMS SIMULADOS ---
    public static final int MODE_OUTPUT = 0; // MC -> Arduino
    public static final int MODE_INPUT = 1;  // Arduino -> MC

    public static final int SIGNAL_DIGITAL = 0;
    public static final int SIGNAL_ANALOG = 1;

    // --- CONFIGURACIÓN ---
    public int ioMode = MODE_OUTPUT;
    public int signalType = SIGNAL_DIGITAL;
    public String targetData = "cmd_1"; // ID del comando/canal
    public boolean isSoftOn = true;
    public String boardID = "placa_gen";
    public int pulseDuration = 10;

    public UUID ownerUUID = null;

    // --- ESTADO INTERNO ---
    private int currentRedstoneOutput = 0;
    private int cachedRedstoneInput = -1;

    public ArduinoIOBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.IO_BLOCK_ENTITY, pos, state);
    }

    public void setOwner(UUID uuid) { this.ownerUUID = uuid; setChanged(); }

    // --- TICK DEL SERVIDOR ---
    public void tickServer() {
        if (this.level == null || this.level.isClientSide()) return;

        // Si está apagado por software, cortamos redstone y salimos
        if (!isSoftOn) {
            if (currentRedstoneOutput != 0) {
                currentRedstoneOutput = 0;
                level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            }
            return;
        }

        // Organización Lógica:
        if (this.ioMode == MODE_OUTPUT) {
            handleOutputLogic();
        }
        // El modo INPUT es manejado por eventos (processSerialInput), no por tick
    }

    // --- LÓGICA SALIDA (Minecraft -> Arduino) ---
    private void handleOutputLogic() {
        int power = level.getBestNeighborSignal(worldPosition);

        // Evitar spam si no cambia el valor
        if (power == this.cachedRedstoneInput) return;
        this.cachedRedstoneInput = power;

        String messageToSend;

        if (this.signalType == SIGNAL_DIGITAL) {
            // PROTOCOLO DIGITAL: "ID:1" o "ID:0"
            // Esto permite al usuario en Arduino hacer: if (data.startsWith("LUZ:1")) ...
            int state = (power > 0) ? 1 : 0;
            messageToSend = this.targetData + ":" + state;
        } else {
            // PROTOCOLO ANÁLOGO: "ID:ValorPWM"
            // Mapeamos 0-15 (MC) a 0-255 (PWM)
            int pwm = Math.round((power * 255.0f) / 15.0f);
            messageToSend = this.targetData + ":" + pwm;
        }

        sendSerialToClient(messageToSend);
    }

    // --- LÓGICA ENTRADA (Arduino -> Minecraft) ---
    public void processSerialInput(String message) {
        if (!isSoftOn || this.ioMode != MODE_INPUT) return;

        // Protocolo esperado: "ID:VALOR" (Ej: "SENSOR_1:1023")
        try {
            // Verificamos si el mensaje empieza con nuestro ID
            if (message.startsWith(this.targetData + ":")) {

                String valueStr = message.split(":")[1]; // Obtenemos lo que está después de ":"
                int value = Integer.parseInt(valueStr);
                int newRedstone = 0;

                if (this.signalType == SIGNAL_DIGITAL) {
                    // Digital: Cualquier valor > 0 enciende al máximo
                    newRedstone = (value > 0) ? 15 : 0;
                } else {
                    // Análogo: Mapeo de 10 bits (Arduino) a 4 bits (Minecraft)
                    // (Valor * 15) / 1023
                    newRedstone = Math.round((value * 15.0f) / 1023.0f);
                }

                if (this.currentRedstoneOutput != newRedstone) {
                    this.currentRedstoneOutput = newRedstone;
                    setChanged();
                    level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
                }
            }
        } catch (Exception e) {
            // Ignoramos mensajes mal formados o que no son para nosotros
        }
    }

    public int getRedstoneSignal() {
        return (this.ioMode == MODE_INPUT && isSoftOn) ? this.currentRedstoneOutput : 0;
    }

    private void sendSerialToClient(String msg) {
        if (ownerUUID != null) {
            Player p = level.getPlayerByUUID(ownerUUID);
            if (p instanceof ServerPlayer sp) {
                ServerPlayNetworking.send(sp, new SerialOutputPayload(msg));
            }
        }
    }

    // --- CONFIGURACIÓN Y PERSISTENCIA ---
    public void updateConfig(int mode, String data, int signal, boolean softOn, String bId, int duration) {
        this.ioMode = mode;
        this.targetData = (data == null) ? "" : data;
        this.signalType = signal;
        this.isSoftOn = softOn;
        this.boardID = (bId == null || bId.isEmpty()) ? "placa_gen" : bId;
        this.pulseDuration = duration;

        // Reset states
        this.cachedRedstoneInput = -1;
        this.currentRedstoneOutput = 0;

        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("ioMode", ioMode);
        output.putString("targetData", targetData); // Guardamos STRING
        output.putInt("signalType", signalType);
        output.putBoolean("isSoftOn", isSoftOn);
        output.putString("boardID", boardID);
        output.putInt("rsOut", currentRedstoneOutput);
        if (this.ownerUUID != null) output.putString("OwnerUUID", this.ownerUUID.toString());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.ioMode = input.getIntOr("ioMode", 0);
        this.targetData = input.getString("targetData").orElse(""); // Cargamos STRING
        this.signalType = input.getIntOr("signalType", 0);
        this.isSoftOn = input.getBooleanOr("isSoftOn", true);
        this.boardID = input.getString("boardID").orElse("placa_gen");
        this.currentRedstoneOutput = input.getIntOr("rsOut", 0);
        input.getString("OwnerUUID").ifPresent(uuidStr -> {
            try { this.ownerUUID = UUID.fromString(uuidStr); } catch (Exception ignored) { }
        });
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("ioMode", ioMode);
        tag.putString("targetData", targetData);
        tag.putInt("signalType", signalType);
        tag.putBoolean("isSoftOn", isSoftOn);
        tag.putString("boardID", boardID);
        return tag;
    }

    @Nullable @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public void onPlayerInteract(Player player) {
        if(level.isClientSide()) return;
        Component status = isSoftOn ? Component.translatable("message.serialcraft.on") : Component.translatable("message.serialcraft.off");
        player.displayClientMessage(Component.translatable("message.serialcraft.io_status", this.boardID, status), true);
    }
}