package com.serialcraft;

import com.serialcraft.block.ModBlocks;
import com.serialcraft.block.entity.ArduinoIOBlockEntity;
import com.serialcraft.screen.ConnectorScreen;
import com.serialcraft.screen.IOScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

public class SerialCraftClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {

        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {

            if (!level.isClientSide() || hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }

            BlockPos pos = hit.getBlockPos();
            var state = level.getBlockState(pos);
            Minecraft mc = Minecraft.getInstance();

            if (state.is(ModBlocks.CONNECTOR_BLOCK)) {
                mc.setScreen(new ConnectorScreen(pos));
                return InteractionResult.SUCCESS;
            }

            if (state.is(ModBlocks.IO_BLOCK)) {
                int mode = 0;
                String data = "";

                var be = level.getBlockEntity(pos);
                if (be instanceof ArduinoIOBlockEntity io) {
                    mode = io.ioMode; // Leemos modo (int)
                    data = io.targetData;
                }

                mc.setScreen(new IOScreen(pos, mode, data));
                return InteractionResult.SUCCESS;
            }

            return InteractionResult.PASS;
        });

        System.out.println("[SerialCraft] Client initialized!");
    }
}