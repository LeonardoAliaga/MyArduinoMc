package com.serialcraft;

import com.serialcraft.screen.ConnectorScreen;
import com.serialcraft.screen.IOScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

public class SerialCraftClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {

        // ============================================================
        //  CLIENT PAYLOAD: Abrir pantalla de conector (Laptop)
        // ============================================================
        ClientPlayNetworking.registerGlobalReceiver(SerialCraft.OPEN_CONNECTOR_SCREEN,
                (client, handler, buf, responseSender) -> {

                    BlockPos pos = buf.readBlockPos(); // posición enviada por el servidor

                    client.execute(() -> {
                        Minecraft.getInstance().setScreen(new ConnectorScreen(pos));
                    });
                });

        // ============================================================
        //  CLIENT PAYLOAD: Abrir pantalla IO
        // ============================================================
        ClientPlayNetworking.registerGlobalReceiver(SerialCraft.OPEN_IO_SCREEN,
                (client, handler, buf, responseSender) -> {

                    BlockPos pos = buf.readBlockPos();
                    boolean isOutput = buf.readBoolean();
                    int targetData = buf.readInt();

                    client.execute(() -> {
                        Minecraft.getInstance().setScreen(
                                new IOScreen(pos, isOutput, targetData)
                        );
                    });
                });

        System.out.println("[SerialCraft] Client initialized!");
    }
}
