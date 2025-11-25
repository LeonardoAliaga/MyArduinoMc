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

    // Estilo Blanco
    private final int COLOR_FONDO = 0xFFF5F5F5;
    private final int COLOR_BORDE = 0xFF404040;
    private final int COLOR_TEXTO = 0xFF000000;

    public IOScreen(BlockPos pos, boolean isOutput, String data) {
        super(Component.literal("Config IO"));
        this.pos = pos;
        this.isOutput = isOutput;
        this.currentData = data == null ? "" : data;
    }

    @Override
    protected void init() {
        int x = this.width / 2;
        int y = this.height / 2;

        // Botón Modo (Con color según estado)
        modeButton = Button.builder(Component.literal(getModeText()), b -> {
            this.isOutput = !this.isOutput;
            b.setMessage(Component.literal(getModeText()));
        }).bounds(x - 90, y - 25, 180, 20).build();
        this.addRenderableWidget(modeButton);

        // Caja Texto
        dataBox = new EditBox(this.font, x - 90, y + 15, 180, 20, Component.literal("Data"));
        dataBox.setValue(this.currentData); // <--- CARGA EL DATO GUARDADO
        dataBox.setMaxLength(50);
        this.addRenderableWidget(dataBox);

        // Botón Guardar
        this.addRenderableWidget(Button.builder(Component.literal("GUARDAR CAMBIOS"), b -> {
            ClientPlayNetworking.send(new MyArduinoMc.ConfigPayload(pos, isOutput, dataBox.getValue()));
            this.onClose();
        }).bounds(x - 60, y + 55, 120, 20).build());
    }

    private String getModeText() {
        return this.isOutput ? "§cMODO: OUTPUT (Enviar)" : "§9MODO: INPUT (Recibir)";
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(guiGraphics);

        int w = 220; int h = 150;
        int x = this.width / 2 - w / 2;
        int y = this.height / 2 - h / 2;

        // Fondo y Bordes
        guiGraphics.fill(x, y, x + w, y + h, COLOR_FONDO);
        guiGraphics.fill(x, y, x + w, y + 1, COLOR_BORDE);
        guiGraphics.fill(x, y + h - 1, x + w, y + h, COLOR_BORDE);
        guiGraphics.fill(x, y, x + 1, y + h, COLOR_BORDE);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, COLOR_BORDE);

        guiGraphics.drawCenteredString(this.font, "§lCONFIGURACIÓN NODO", this.width / 2, y + 10, COLOR_TEXTO);
        guiGraphics.drawString(this.font, "Palabra Clave (Ej: CLAP):", x + 20, y + 70, 0xFF555555, false);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}