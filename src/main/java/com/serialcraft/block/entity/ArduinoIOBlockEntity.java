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

    public int ioMode = 0;
    public String targetData = "";
    private boolean wasPowered = false;

    public UUID ownerUUID = null;

    private int pulseTimer = 0;
    private int blinkTimer = 0;

    public ArduinoIOBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.IO_BLOCK_ENTITY, pos, state);
    }

    public void setOwner(UUID uuid) {
        this.ownerUUID = uuid;
        setChanged();
    }

    // --- GUARDADO/CARGA ---
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("ioMode", ioMode);
        output.putString("targetData", targetData != null ? targetData : "");
        output.putBoolean("wasPowered", wasPowered);
        if (this.ownerUUID != null) {
            output.putString("OwnerUUID", this.ownerUUID.toString());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.ioMode = input.getIntOr("ioMode", 0);
        this.targetData = input.getString("targetData").orElse("");
        this.wasPowered = input.getBooleanOr("wasPowered", false);

        // Bloque corregido:
        input.getString("OwnerUUID").ifPresent(uuidStr -> {
            try {
                this.ownerUUID = UUID.fromString(uuidStr);
            } catch (Exception ignored) {
                this.ownerUUID = null;
            }
        });
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("ioMode", ioMode);
        tag.putString("targetData", targetData != null ? targetData : "");
        if (ownerUUID != null) {
            tag.putString("OwnerUUID", ownerUUID.toString());
        }
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public void onButtonInteract(Player player, Direction btn, boolean isShift) {
        if (level == null || level.isClientSide()) return;

        if (ownerUUID != null && !ownerUUID.equals(player.getUUID())) {
            player.displayClientMessage(Component.literal("§cNo tienes permiso para modificar esta placa."), true);
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
        level.setBlockAndUpdate(worldPosition, currentState.setValue(property, newState));
    }

    private boolean areInputsSatisfied() {
        if (level == null) return false;
        BlockState state = getBlockState();
        int inputCount = 0;
        boolean allInputsPowered = true;

        for (Direction d : Direction.values()) {
            EnumProperty<IOSide> prop = ArduinoIOBlock.getPropertyForDirection(d);
            IOSide sideState = state.getValue(prop);

            if (sideState == IOSide.INPUT) {
                inputCount++;
                if (level.getSignal(worldPosition.relative(d), d) == 0) {
                    allInputsPowered = false;
                    break;
                }
            }
        }
        if (inputCount == 0) return false;
        return allInputsPowered;
    }

    public void tickServer() {
        if (this.level == null || this.level.isClientSide()) return;

        BlockState state = getBlockState();
        boolean changed = false;

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

        boolean isEnabled = areInputsSatisfied();
        if (isEnabled != state.getValue(ArduinoIOBlock.ENABLED)) {
            state = state.setValue(ArduinoIOBlock.ENABLED, isEnabled);
            changed = true;
        }

        if (changed) {
            level.setBlockAndUpdate(worldPosition, state);
            level.updateNeighborsAt(worldPosition, state.getBlock());
        }

        if (ioMode == 0) {
            if (isEnabled && !wasPowered) {
                if (targetData != null && !targetData.isEmpty() && ownerUUID != null) {
                    Player player = level.getPlayerByUUID(ownerUUID);
                    if (player instanceof ServerPlayer serverPlayer) {
                        ServerPlayNetworking.send(serverPlayer, new SerialCraft.SerialOutputPayload(targetData));
                    }
                }
            }
            this.wasPowered = isEnabled;
        }
    }

    public void triggerAction() {
        if (level == null || level.isClientSide()) return;
        BlockState state = getBlockState();
        if (!state.is(ModBlocks.IO_BLOCK)) return;

        if (!state.getValue(ArduinoIOBlock.ENABLED)) return;

        boolean update = false;

        if (!state.getValue(ArduinoIOBlock.BLINKING)) {
            state = state.setValue(ArduinoIOBlock.BLINKING, true);
            blinkTimer = 5;
            update = true;
        }

        if (ioMode == 1) {
            if (!state.getValue(ArduinoIOBlock.POWERED)) {
                state = state.setValue(ArduinoIOBlock.POWERED, true);
                pulseTimer = 20;
                update = true;
            }
        } else if (ioMode == 2) {
            boolean encendido = state.getValue(ArduinoIOBlock.POWERED);
            state = state.setValue(ArduinoIOBlock.POWERED, !encendido);
            update = true;
        }

        if (update) {
            level.setBlockAndUpdate(worldPosition, state);
            level.updateNeighborsAt(worldPosition, state.getBlock());
        }
    }

    public void setConfig(int mode, String data) {
        this.ioMode = mode;
        this.targetData = (data == null) ? "" : data;
        setChanged();

        if (level != null) {
            BlockState state = getBlockState();
            if (state.getValue(ArduinoIOBlock.MODE) != mode) {
                level.setBlockAndUpdate(worldPosition, state.setValue(ArduinoIOBlock.MODE, mode));
            } else {
                level.sendBlockUpdated(worldPosition, state, state, 3);
            }
        }
    }

    public void onPlayerInteract(Player player) {
        if (level == null || level.isClientSide()) return;
        player.displayClientMessage(Component.literal("§e[IO] §fClave: §a" + targetData), true);
    }

    @Override public void setLevel(Level level) { super.setLevel(level); if (level != null && !level.isClientSide()) SerialCraft.activeIOBlocks.add(this); }
    @Override public void setRemoved() { super.setRemoved(); SerialCraft.activeIOBlocks.remove(this); }
}