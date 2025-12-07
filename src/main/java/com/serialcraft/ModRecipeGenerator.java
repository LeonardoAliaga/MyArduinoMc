package com.serialcraft;

import com.serialcraft.block.ModBlocks;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
import net.minecraft.world.item.Items;

import java.util.concurrent.CompletableFuture;

public class ModRecipeGenerator extends FabricRecipeProvider {

    public ModRecipeGenerator(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected RecipeProvider createRecipeProvider(HolderLookup.Provider registryLookup, RecipeOutput exporter) {
        // Siguiendo tu captura de la documentación:
        return new RecipeProvider(registryLookup, exporter) {
            @Override
            public void buildRecipes() {
                // 1. Receta del Enlace Serial (Laptop)
                ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, ModBlocks.CONNECTOR_BLOCK.asItem())
                        .pattern("GGG")
                        .pattern("ICI")
                        .pattern("SRS")
                        .define('G', Items.GLASS_PANE)
                        .define('I', Items.IRON_INGOT)
                        .define('C', Items.COMPARATOR)
                        .define('S', Items.STONE_SLAB)
                        .define('R', Items.REDSTONE)
                        // Usamos conditionsFromItem si has() da problemas de scope
                        .unlockedBy("has_iron", conditionsFromItem(Items.IRON_INGOT))
                        .save(this.output);

                // 2. Receta del Nodo Serial (Arduino)
                ShapedRecipeBuilder.shaped(RecipeCategory.REDSTONE, ModBlocks.IO_BLOCK.asItem())
                        .pattern("RTR")
                        .pattern("GCG")
                        .pattern("IDI")
                        .define('R', Items.REDSTONE)
                        .define('T', Items.REDSTONE_TORCH)
                        .define('G', Items.GOLD_NUGGET)
                        .define('C', Items.COMPARATOR)
                        .define('I', Items.IRON_NUGGET)
                        .define('D', Items.GREEN_DYE)
                        .unlockedBy("has_redstone", conditionsFromItem(Items.REDSTONE))
                        .save(this.output);
            }
        };
    }

    @Override
    public String getName() {
        return "SerialCraft Recipes";
    }
}