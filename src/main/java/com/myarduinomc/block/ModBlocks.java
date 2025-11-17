package com.myarduinomc.block;

import com.myarduinomc.MyArduinoMc;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;

/**
 * Central place to register every block that belongs to the mod.
 */
public final class ModBlocks {
        public static final Block ARDUINO_INTERFACE_BLOCK = registerBlock(
                        "arduino_interface_block",
                        FabricBlockSettings.copyOf(Blocks.IRON_BLOCK).sounds(SoundType.COPPER));

        private ModBlocks() {
                // Utility class
        }

        private static Block registerBlock(String name, FabricBlockSettings settings) {
                Block block = new Block(settings);
                registerBlockItem(name, block);
                return Registry.register(BuiltInRegistries.BLOCK, new ResourceLocation(MyArduinoMc.MOD_ID, name), block);
        }

        private static void registerBlockItem(String name, Block block) {
                BlockItem blockItem = new BlockItem(block, new Item.Properties());
                Registry.register(BuiltInRegistries.ITEM, new ResourceLocation(MyArduinoMc.MOD_ID, name), blockItem);
                ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.REDSTONE_BLOCKS)
                                .register(entries -> entries.accept(blockItem));
        }

        public static void register() {
                MyArduinoMc.LOGGER.info("Registering blocks for {}", MyArduinoMc.MOD_ID);
        }
}
