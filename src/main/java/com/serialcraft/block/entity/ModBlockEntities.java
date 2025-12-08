package com.serialcraft.block.entity;

import com.serialcraft.SerialCraft;
import com.serialcraft.block.ModBlocks;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class ModBlockEntities {

    public static BlockEntityType<ArduinoIOBlockEntity> IO_BLOCK_ENTITY;
    public static BlockEntityType<ConnectorBlockEntity> CONNECTOR_BLOCK_ENTITY; // Nuevo

    public static void initialize() {
        // IO Block Entity
        ResourceLocation idIO = ResourceLocation.fromNamespaceAndPath(SerialCraft.MOD_ID, "io_block");
        ResourceKey<BlockEntityType<?>> keyIO = ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, idIO);
        IO_BLOCK_ENTITY = Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                keyIO,
                FabricBlockEntityTypeBuilder.create(ArduinoIOBlockEntity::new, ModBlocks.IO_BLOCK).build()
        );

        // Connector Block Entity (Nuevo)
        ResourceLocation idConn = ResourceLocation.fromNamespaceAndPath(SerialCraft.MOD_ID, "connector_block");
        ResourceKey<BlockEntityType<?>> keyConn = ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, idConn);
        CONNECTOR_BLOCK_ENTITY = Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                keyConn,
                FabricBlockEntityTypeBuilder.create(ConnectorBlockEntity::new, ModBlocks.CONNECTOR_BLOCK).build()
        );
    }
}