package com.serialcraft.item;

import com.serialcraft.SerialCraft;
import com.serialcraft.block.ModBlocks;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

import java.util.function.Function;

public class ModItems {

    // Helper para registrar items (se mantiene igual)
    private static Item register(String name,
                                 Function<Item.Properties, Item> itemFactory,
                                 Item.Properties settings) {

        ResourceKey<Item> itemKey = ResourceKey.create(
                Registries.ITEM,
                Identifier.fromNamespaceAndPath(SerialCraft.MOD_ID, name)
        );

        Item item = itemFactory.apply(settings.setId(itemKey));

        return Registry.register(BuiltInRegistries.ITEM, itemKey, item);
    }

    // --- AQUÍ ESTÁ EL CAMBIO ---
    // Inicialización directa (sin bloque static {})

    public static final Item CONNECTOR_BLOCK_ITEM = register(
            "connector_block",
            props -> new BlockItem(ModBlocks.CONNECTOR_BLOCK, props),
            new Item.Properties()
    );

    public static final Item IO_BLOCK_ITEM = register(
            "io_block",
            props -> new BlockItem(ModBlocks.IO_BLOCK, props),
            new Item.Properties()
    );

    // Inicialización de grupos creativos
    public static void initialize() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.REDSTONE_BLOCKS)
                .register(entries -> {
                    entries.accept(CONNECTOR_BLOCK_ITEM);
                    entries.accept(IO_BLOCK_ITEM);
                });
    }
}