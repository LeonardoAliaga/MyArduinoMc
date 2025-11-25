package com.myarduinomc;

import com.fazecast.jSerialComm.SerialPort;
import com.myarduinomc.block.ConnectorBlock;
import com.myarduinomc.block.ArduinoIOBlock;
import com.myarduinomc.block.entity.ArduinoIOBlockEntity;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class MyArduinoMc implements ModInitializer {
    public static final String MOD_ID = "myarduinomc";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static SerialPort arduinoPort = null;
    public static final Set<ArduinoIOBlockEntity> activeIOBlocks = Collections.synchronizedSet(new HashSet<>());

    public static final ResourceLocation CONNECTOR_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "connector_block");
    public static final ResourceKey<Block> CONNECTOR_KEY = ResourceKey.create(Registries.BLOCK, CONNECTOR_ID);
    public static final Block CONNECTOR_BLOCK = new ConnectorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0f).setId(CONNECTOR_KEY));

    public static final ResourceLocation IO_ID = ResourceLocation.fromNamespaceAndPath(MOD_ID, "io_block");
    public static final ResourceKey<Block> IO_KEY = ResourceKey.create(Registries.BLOCK, IO_ID);
    public static final Block IO_BLOCK = new ArduinoIOBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f).setId(IO_KEY));

    public static BlockEntityType<ArduinoIOBlockEntity> IO_BLOCK_ENTITY;

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.BLOCK, CONNECTOR_ID, CONNECTOR_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, CONNECTOR_ID, new BlockItem(CONNECTOR_BLOCK, new Item.Properties().setId(ResourceKey.create(Registries.ITEM, CONNECTOR_ID))));

        Registry.register(BuiltInRegistries.BLOCK, IO_ID, IO_BLOCK);
        Registry.register(BuiltInRegistries.ITEM, IO_ID, new BlockItem(IO_BLOCK, new Item.Properties().setId(ResourceKey.create(Registries.ITEM, IO_ID))));

        IO_BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, IO_ID,
                FabricBlockEntityTypeBuilder.create(ArduinoIOBlockEntity::new, IO_BLOCK).build());

        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(c -> {
            c.accept(CONNECTOR_BLOCK);
            c.accept(IO_BLOCK);
        });

        PayloadTypeRegistry.playC2S().register(ConfigPayload.TYPE, ConfigPayload.CODEC);

        // RECIBIR DATOS DEL CLIENTE
        ServerPlayNetworking.registerGlobalReceiver(ConfigPayload.TYPE, (payload, context) -> {
            context.server().execute(() -> {
                if (context.player().level().getBlockEntity(payload.pos) instanceof ArduinoIOBlockEntity entity) {
                    LOGGER.info("GUARDANDO DATOS: Pos=" + payload.pos + " Output=" + payload.isOutput + " Data=" + payload.data);
                    entity.setConfig(payload.isOutput, payload.data);
                }
            });
        });

        // LOOP ARDUINO (INPUT)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (arduinoPort != null && arduinoPort.isOpen() && arduinoPort.bytesAvailable() > 0) {
                try {
                    byte[] buffer = new byte[arduinoPort.bytesAvailable()];
                    arduinoPort.readBytes(buffer, buffer.length);
                    String mensaje = new String(buffer, StandardCharsets.UTF_8).trim();
                    if (!mensaje.isEmpty()) {
                        LOGGER.info("[ARDUINO -> MC]: " + mensaje);
                        synchronized (activeIOBlocks) {
                            for (ArduinoIOBlockEntity entity : activeIOBlocks) {
                                if (!entity.isOutputMode && entity.targetData.equals(mensaje)) {
                                    entity.triggerRedstone();
                                }
                            }
                        }
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }
        });
    }

    // PAYLOAD (Paquete de red)
    public record ConfigPayload(BlockPos pos, boolean isOutput, String data) implements CustomPacketPayload {
        public static final Type<ConfigPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MOD_ID, "config_packet"));

        // CODEC ROBUSTO
        public static final StreamCodec<RegistryFriendlyByteBuf, ConfigPayload> CODEC = StreamCodec.of(
                (buf, val) -> {
                    buf.writeBlockPos(val.pos);
                    buf.writeBoolean(val.isOutput);
                    buf.writeUtf(val.data);
                },
                buf -> new ConfigPayload(buf.readBlockPos(), buf.readBoolean(), buf.readUtf())
        );
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // FUNCION CONECTAR MEJORADA
    public static String conectar(String puerto) {
        if (arduinoPort != null && arduinoPort.isOpen()) return "§eYa conectado";
        try {
            SerialPort[] ports = SerialPort.getCommPorts();
            if (ports.length == 0) return "§cNo hay puertos";

            for (SerialPort p : ports) {
                if (p.getSystemPortName().equalsIgnoreCase(puerto)) {
                    arduinoPort = p;
                    arduinoPort.setBaudRate(9600);
                    if (arduinoPort.openPort()) {
                        arduinoPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);
                        LOGGER.info("CONEXIÓN EXITOSA CON " + puerto);
                        return "§aConectado a " + puerto;
                    }
                }
            }
            return "§cPuerto " + puerto + " no hallado";
        } catch (Exception e) { return "§4Error: " + e.getMessage(); }
    }

    // FUNCION OUTPUT MEJORADA
    public static void enviarArduino(String msg) {
        if (arduinoPort != null && arduinoPort.isOpen()) {
            try {
                LOGGER.info("[MC -> ARDUINO]: " + msg);
                arduinoPort.writeBytes((msg + "\n").getBytes(), msg.length() + 1);
            } catch (Exception e) { e.printStackTrace(); }
        } else {
            LOGGER.warn("Intento de enviar '" + msg + "' fallido: No hay conexión.");
        }
    }
}