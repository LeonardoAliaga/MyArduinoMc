package com.myarduinomc.screen;

import com.myarduinomc.MyArduinoMc;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ConnectorScreen extends Screen {
    private EditBox portBox;
    private String status = "§7Estado: Verificando...";
    private final int COLOR_FONDO = 0xFFF0F0F0;

    public ConnectorScreen() { super(Component.literal("Conector")); }

    @Override
    protected void init() {
        int x = this.width / 2;
        int y = this.height / 2;

        this.portBox = new EditBox(this.font, x - 60, y - 25, 120, 20, Component.literal("Puerto"));
        this.portBox.setValue("COM9");
        this.addRenderableWidget(this.portBox);

        this.addRenderableWidget(Button.builder(Component.literal("CONECTAR"), b -> {
            status = "§eConectando...";
            // Intentamos conectar
            String res = MyArduinoMc.conectar(portBox.getValue());
            status = res;
        }).bounds(x - 65, y + 5, 60, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("CERRAR"), b -> {
            if(MyArduinoMc.arduinoPort != null) {
                MyArduinoMc.arduinoPort.closePort();
                MyArduinoMc.arduinoPort = null;
            }
            status = "§cPuerto Cerrado";
        }).bounds(x + 5, y + 5, 60, 20).build());
    }

    @Override
    public void tick() {
        // Actualizamos el estado visual si cambia externamente
        if (MyArduinoMc.arduinoPort != null && MyArduinoMc.arduinoPort.isOpen()) {
            if (!status.contains("Conectado")) {
                status = "§2✓ Conectado a: " + MyArduinoMc.arduinoPort.getSystemPortName();
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(guiGraphics);

        int w = 200; int h = 110;
        int x = this.width / 2 - w / 2;
        int y = this.height / 2 - h / 2;

        // Ventana Blanca
        guiGraphics.fill(x, y, x + w, y + h, COLOR_FONDO);
        int border = 0xFF555555;
        guiGraphics.fill(x, y, x + w, y + 1, border);
        guiGraphics.fill(x, y + h - 1, x + w, y + h, border);
        guiGraphics.fill(x, y, x + 1, y + h, border);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, border);

        guiGraphics.drawCenteredString(this.font, "§lARDUINO MANAGER", this.width / 2, y + 10, 0xFF000000);
        guiGraphics.drawString(this.font, "Puerto COM:", x + 20, y + 30, 0xFF555555, false);
        guiGraphics.drawCenteredString(this.font, status, this.width / 2, y + h - 15, 0xFF000000);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}