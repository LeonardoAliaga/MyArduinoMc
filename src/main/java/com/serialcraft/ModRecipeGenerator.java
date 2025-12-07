package com.serialcraft;

import com.serialcraft.block.ModBlocks;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class ModRecipeGenerator extends FabricRecipeProvider {

    public ModRecipeGenerator(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected @NotNull RecipeProvider createRecipeProvider(HolderLookup.Provider registryLookup, RecipeOutput exporter) {
        return new RecipeProvider(registryLookup, exporter) {
            @Override
            public void buildRecipes() {
                // Obtenemos el lookup necesario para la versión 1.21
                HolderLookup.RegistryLookup<Item> items = registryLookup.lookupOrThrow(Registries.ITEM);

                // 1. Receta del Enlace Serial (Laptop)
                ShapedRecipeBuilder.shaped(items, RecipeCategory.REDSTONE, ModBlocks.CONNECTOR_BLOCK.asItem())
                        .pattern("GGG")
                        .pattern("ICI")
                        .pattern("SRS")
                        .define('G', Items.GLASS_PANE)
                        .define('I', Items.IRON_INGOT)
                        .define('C', Items.COMPARATOR)
                        .define('S', Items.STONE_SLAB)
                        .define('R', Items.REDSTONE)
                        .unlockedBy("has_iron", has(Items.IRON_INGOT))
                        .save(this.output);

                // 2. Receta del Nodo Serial (Arduino) - Opción 2: Alta Tecnología
                // Sin tinte, usando componentes lógicos.
                ShapedRecipeBuilder.shaped(items, RecipeCategory.REDSTONE, ModBlocks.IO_BLOCK.asItem())
                        .pattern("TRT") // Torch, Repeater, Torch
                        .pattern("ICI") // Iron, Comparator, Iron
                        .pattern("SDS") // Stone, Dust (Redstone), Stone

                        // Definición de materiales
                        .define('T', Items.REDSTONE_TORCH) // Energía
                        .define('R', Items.REPEATER)       // Procesamiento de señal
                        .define('I', Items.IRON_INGOT)     // Estructura
                        .define('C', Items.COMPARATOR)     // Lógica IO
                        .define('S', Items.SMOOTH_STONE)   // Base aislante limpia
                        .define('D', Items.REDSTONE)       // Conexión

                        // Desbloqueo: Se aprende al obtener un Repetidor
                        .unlockedBy("has_repeater", has(Items.REPEATER))
                        .save(this.output);
            }
        };
    }

    @Override
    public @NotNull String getName() {
        return "SerialCraft Recipes";
    }
}