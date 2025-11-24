package com.myarduinomc.screen;

import com.myarduinomc.MyArduinoMc;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class IOScreen extends Screen {
    private final BlockPos pos;
    private boolean isOutput;
    private String currentData;

    private Button modeButton;
    private EditBox dataBox;

    public IOScreen(BlockPos pos, boolean isOutput, String data) {
        super(Component.literal("Configurar Bloque IO"));
        this.pos = pos;
        this.isOutput = isOutput;
        this.currentData = data;
    }

    @Override
    protected void init() {
        int x = this.width / 2;
        int y = this.height / 2;

        // Botón Modo
        modeButton = Button.builder(Component.literal(isOutput ? "MODO: OUTPUT (Envía)" : "MODO: INPUT (Recibe)"), b -> {
            isOutput = !isOutput;
            b.setMessage(Component.literal(isOutput ? "MODO: OUTPUT (Envía)" : "MODO: INPUT (Recibe)"));
        }).bounds(x - 100, y - 40, 200, 20).build();
        this.addRenderableWidget(modeButton);

        // Caja de Texto
        dataBox = new EditBox(this.font, x - 100, y, 200, 20, Component.literal("Datos"));
        dataBox.setValue(currentData);
        dataBox.setMaxLength(32);
        this.addRenderableWidget(dataBox);

        // Botón Guardar
        this.addRenderableWidget(Button.builder(Component.literal("GUARDAR"), b -> {
            // Enviar paquete al servidor
            ClientPlayNetworking.send(new MyArduinoMc.ConfigPayload(pos, isOutput, dataBox.getValue()));
            this.onClose();
        }).bounds(x - 50, y + 40, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, "Palabra Clave (ej: CLAP)", this.width / 2, this.height / 2 - 15, 0xAAAAAA);
    }
}