package com.serialcraft.screen;

import com.serialcraft.SerialCraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ConnectorScreen extends Screen {
    private EditBox portBox;
    private String status = "§7Estado: Verificando...";
    private static final int COLOR_FONDO = 0xFFF5F5F5;

    public ConnectorScreen() {
        super(Component.literal("Conector"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.portBox = new EditBox(
                this.font,
                centerX - 60,
                centerY - 25,
                120,
                20,
                Component.literal("Puerto")
        );
        this.portBox.setMaxLength(32);
        this.portBox.setValue(
                SerialCraft.arduinoPort != null && SerialCraft.arduinoPort.isOpen()
                        ? SerialCraft.arduinoPort.getSystemPortName()
                        : "COM9"
        );
        this.addRenderableWidget(this.portBox);

        // Botón CONECTAR
        this.addRenderableWidget(Button.builder(Component.literal("CONECTAR"), b -> {
            status = "§eConectando...";
            String res = SerialCraft.conectar(portBox.getValue().trim());
            status = res;
        }).bounds(centerX - 65, centerY + 5, 60, 20).build());

        // Botón CERRAR
        this.addRenderableWidget(Button.builder(Component.literal("CERRAR"), b -> {
            if (SerialCraft.arduinoPort != null) {
                SerialCraft.arduinoPort.closePort();
                SerialCraft.arduinoPort = null;
            }
            status = "§cPuerto cerrado";
        }).bounds(centerX + 5, centerY + 5, 60, 20).build());
    }

    @Override
    public void tick() {
        // Refrescar estado si está conectado
        if (SerialCraft.arduinoPort != null && SerialCraft.arduinoPort.isOpen()) {
            String connected = "§2✓ Conectado a: " + SerialCraft.arduinoPort.getSystemPortName();
            if (!status.equals(connected)) {
                status = connected;
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(guiGraphics);

        int w = 200;
        int h = 110;
        int x = this.width / 2 - w / 2;
        int y = this.height / 2 - h / 2;

        // Ventana blanca con borde
        guiGraphics.fill(x, y, x + w, y + h, COLOR_FONDO);
        int border = 0xFF555555;
        guiGraphics.fill(x, y, x + w, y + 1, border);
        guiGraphics.fill(x, y + h - 1, x + w, y + h, border);
        guiGraphics.fill(x, y, x + 1, y + h, border);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, border);

        guiGraphics.drawCenteredString(this.font,
                "§lARDUINO MANAGER",
                this.width / 2,
                y + 10,
                0xFF000000
        );
        guiGraphics.drawString(this.font,
                "Puerto COM:",
                x + 20,
                y + 30,
                0xFF555555,
                false
        );
        guiGraphics.drawCenteredString(this.font,
                status,
                this.width / 2,
                y + h - 15,
                0xFF000000
        );

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
