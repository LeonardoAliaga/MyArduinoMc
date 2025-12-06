package com.serialcraft.screen;

import com.serialcraft.SerialCraft;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class IOScreen extends Screen {
    private final BlockPos pos;
    private int currentMode; // 0, 1, 2
    private String currentData;

    private Button modeButton;
    private EditBox dataBox;

    private static final int COLOR_FONDO = 0xFFF5F5F5;
    private static final int COLOR_BORDE = 0xFF404040;
    private static final int COLOR_TEXTO = 0xFF000000;

    // Constructor recibe int mode
    public IOScreen(BlockPos pos, int mode, String data) {
        super(Component.literal("Config IO"));
        this.pos = pos;
        this.currentMode = mode;
        this.currentData = data == null ? "" : data;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        // Botón que rota los modos
        modeButton = Button.builder(Component.literal(getModeText()), b -> {
            this.currentMode = (this.currentMode + 1) % 3; // 0 -> 1 -> 2 -> 0...
            b.setMessage(Component.literal(getModeText()));
        }).bounds(cx - 100, cy - 25, 200, 20).build();
        this.addRenderableWidget(modeButton);

        dataBox = new EditBox(this.font, cx - 90, cy + 15, 180, 20, Component.literal("Data"));
        dataBox.setValue(this.currentData);
        dataBox.setMaxLength(50);
        this.addRenderableWidget(dataBox);

        // Guardar cambios
        this.addRenderableWidget(Button.builder(Component.literal("GUARDAR"), b -> {
            ClientPlayNetworking.send(
                    new SerialCraft.ConfigPayload(pos, currentMode, dataBox.getValue())
            );
            this.onClose();
        }).bounds(cx - 60, cy + 55, 120, 20).build());
    }

    private String getModeText() {
        return switch (this.currentMode) {
            case 0 -> "§cMODO: SALIDA (MC -> Arduino)";
            case 1 -> "§aMODO: ENTRADA (Pulso)";
            case 2 -> "§9MODO: ENTRADA (Interruptor)";
            default -> "Error";
        };
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(guiGraphics);

        int w = 260;
        int h = 150;
        int x = this.width / 2 - w / 2;
        int y = this.height / 2 - h / 2;

        guiGraphics.fill(x, y, x + w, y + h, COLOR_FONDO);
        guiGraphics.fill(x, y, x + w, y + 1, COLOR_BORDE);
        guiGraphics.fill(x, y + h - 1, x + w, y + h, COLOR_BORDE);
        guiGraphics.fill(x, y, x + 1, y + h, COLOR_BORDE);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, COLOR_BORDE);

        guiGraphics.drawCenteredString(this.font, "§lCONFIGURACIÓN NODO", this.width / 2, y + 10, COLOR_TEXTO);
        guiGraphics.drawString(this.font, "Palabra Clave (Arduino):", x + 20, y + 70, 0xFF555555, false);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}