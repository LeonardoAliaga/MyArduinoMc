package com.serialcraft.registry;

import com.serialcraft.SerialCraft;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

import java.util.function.Function;

public class ModItems {

    private static Item register(String name,
                                 Function<Item.Properties, Item> itemFactory,
                                 Item.Properties settings) {
        ResourceKey<Item> itemKey = ResourceKey.create(
                Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(SerialCraft.MOD_ID, name)
        );

        Item item = itemFactory.apply(settings.setId(itemKey));

        return Registry.register(BuiltInRegistries.ITEM, itemKey, item);
    }

    public static Item CONNECTOR_BLOCK_ITEM;
    public static Item IO_BLOCK_ITEM;

    public static void initialize() {
        // BlockItems
        CONNECTOR_BLOCK_ITEM = register(
                "connector_block",
                props -> new BlockItem(ModBlocks.CONNECTOR_BLOCK, props),
                new Item.Properties()
        );

        IO_BLOCK_ITEM = register(
                "io_block",
                props -> new BlockItem(ModBlocks.IO_BLOCK, props),
                new Item.Properties()
        );

        // Añadir a la pestaña creativa de Redstone
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.REDSTONE_BLOCKS)
                .register(entries -> {
                    entries.accept(CONNECTOR_BLOCK_ITEM);
                    entries.accept(IO_BLOCK_ITEM);
                });
    }
}
