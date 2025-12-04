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

    public static void initialize() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(SerialCraft.MOD_ID, "io_block");
        ResourceKey<BlockEntityType<?>> key = ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, id);

        IO_BLOCK_ENTITY = Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                key,
                FabricBlockEntityTypeBuilder.create(ArduinoIOBlockEntity::new, ModBlocks.IO_BLOCK).build()
        );
    }
}
