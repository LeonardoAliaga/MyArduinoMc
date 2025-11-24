package com.myarduinomc;

import com.myarduinomc.block.entity.ArduinoIOBlockEntity;
import com.myarduinomc.screen.ConnectorScreen;
import com.myarduinomc.screen.IOScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;

public class MyArduinoMcClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide()) {
                // 1. Si tocamos el BLOQUE CONECTOR
                if (world.getBlockState(hitResult.getBlockPos()).is(MyArduinoMc.CONNECTOR_BLOCK)) {
                    Minecraft.getInstance().setScreen(new ConnectorScreen());
                    return InteractionResult.SUCCESS;
                }
                // 2. Si tocamos el BLOQUE IO
                if (world.getBlockState(hitResult.getBlockPos()).is(MyArduinoMc.IO_BLOCK)) {
                    if (world.getBlockEntity(hitResult.getBlockPos()) instanceof ArduinoIOBlockEntity entity) {
                        Minecraft.getInstance().setScreen(new IOScreen(hitResult.getBlockPos(), entity.isOutputMode, entity.targetData));
                    }
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        });
    }
}