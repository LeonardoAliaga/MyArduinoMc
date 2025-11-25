package com.serialcraft;

import com.serialcraft.block.entity.ArduinoIOBlockEntity;
import com.serialcraft.screen.ConnectorScreen;
import com.serialcraft.screen.IOScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;

public class SerialCraftClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (!world.isClientSide()) {
                return InteractionResult.PASS;
            }

            BlockPos pos = hitResult.getBlockPos();

            // Bloque conector
            if (world.getBlockState(pos).is(SerialCraft.CONNECTOR_BLOCK)) {
                Minecraft.getInstance().setScreen(new ConnectorScreen());
                return InteractionResult.SUCCESS;
            }

            // Bloque IO
            if (world.getBlockState(pos).is(SerialCraft.IO_BLOCK)) {
                if (world.getBlockEntity(pos) instanceof ArduinoIOBlockEntity entity) {
                    Minecraft.getInstance().setScreen(
                            new IOScreen(pos, entity.isOutputMode, entity.targetData)
                    );
                }
                return InteractionResult.SUCCESS;
            }

            return InteractionResult.PASS;
        });
    }
}
