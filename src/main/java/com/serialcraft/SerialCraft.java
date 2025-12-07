package com.serialcraft;

import com.serialcraft.block.ConnectorBlock;
import com.serialcraft.block.entity.ArduinoIOBlockEntity;
import com.serialcraft.block.entity.ModBlockEntities;
import com.serialcraft.block.ModBlocks;
import com.serialcraft.item.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SerialCraft implements ModInitializer {

    public static final String MOD_ID = "serialcraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Set<ArduinoIOBlockEntity> activeIOBlocks = Collections.synchronizedSet(new HashSet<>());

    @Override
    public void onInitialize() {
        ModBlocks.initialize();
        ModItems.initialize();
        ModBlockEntities.initialize();
        LOGGER.info("[SerialCraft] Servidor Iniciado");

        PayloadTypeRegistry.playC2S().register(ConfigPayload.TYPE, ConfigPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ConnectorPayload.TYPE, ConnectorPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SerialInputPayload.TYPE, SerialInputPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SerialOutputPayload.TYPE, SerialOutputPayload.CODEC);

        // 1. CONFIGURACIÓN (Seguridad Añadida)
        ServerPlayNetworking.registerGlobalReceiver(ConfigPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> {
                if (player.level().getBlockEntity(payload.pos) instanceof ArduinoIOBlockEntity entity) {
                    // SEGURIDAD: Verificar dueño
                    if (entity.ownerUUID != null && entity.ownerUUID.equals(player.getUUID())) {
                        entity.setConfig(payload.mode, payload.data);
                    } else {
                        player.displayClientMessage(
                                net.minecraft.network.chat.Component.literal("§cAcceso Denegado: No eres el dueño."), true
                        );
                    }
                }
            });
        });

        // 2. Conector
        ServerPlayNetworking.registerGlobalReceiver(ConnectorPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                var level = context.player().level();
                var state = level.getBlockState(payload.pos);
                if (state.is(ModBlocks.CONNECTOR_BLOCK)) {
                    level.setBlock(payload.pos, state.setValue(ConnectorBlock.LIT, payload.connected), 3);
                }
            });
        });

        // 3. Señal de Arduino (Cliente -> Servidor)
        ServerPlayNetworking.registerGlobalReceiver(SerialInputPayload.TYPE, (payload, context) -> {
            ServerPlayer sender = context.player();
            String msg = payload.message;
            context.server().execute(() -> {
                synchronized (activeIOBlocks) {
                    for (ArduinoIOBlockEntity io : activeIOBlocks) {
                        // Seguridad: Solo activar mis propios bloques
                        if (io.targetData.equals(msg) &&
                                io.ownerUUID != null &&
                                io.ownerUUID.equals(sender.getUUID())) {
                            io.triggerAction();
                        }
                    }
                }
            });
        });
    }

    // --- PACKETS ---
    public record ConfigPayload(BlockPos pos, int mode, String data) implements CustomPacketPayload {
        public static final Type<ConfigPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "config_packet"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ConfigPayload> CODEC = StreamCodec.of(
                (buf, val) -> { buf.writeBlockPos(val.pos); buf.writeInt(val.mode); buf.writeUtf(val.data); },
                buf -> new ConfigPayload(buf.readBlockPos(), buf.readInt(), buf.readUtf())
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ConnectorPayload(BlockPos pos, boolean connected) implements CustomPacketPayload {
        public static final Type<ConnectorPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "connector_packet"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ConnectorPayload> CODEC = StreamCodec.of(
                (buf, val) -> { buf.writeBlockPos(val.pos); buf.writeBoolean(val.connected); },
                buf -> new ConnectorPayload(buf.readBlockPos(), buf.readBoolean())
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SerialInputPayload(String message) implements CustomPacketPayload {
        public static final Type<SerialInputPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "serial_in_packet"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SerialInputPayload> CODEC = StreamCodec.of((buf, val) -> buf.writeUtf(val.message), buf -> new SerialInputPayload(buf.readUtf()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SerialOutputPayload(String message) implements CustomPacketPayload {
        public static final Type<SerialOutputPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "serial_out_packet"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SerialOutputPayload> CODEC = StreamCodec.of((buf, val) -> buf.writeUtf(val.message), buf -> new SerialOutputPayload(buf.readUtf()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}