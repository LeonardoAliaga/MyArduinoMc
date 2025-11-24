package com.myarduinomc.screen;

import com.myarduinomc.MyArduinoMc;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class ConnectorScreen extends Screen {
    private EditBox portBox;
    private String status = "§7Desconectado";

    public ConnectorScreen() { super(Component.literal("Conector Arduino")); }

    @Override
    protected void init() {
        int x = this.width / 2;
        int y = this.height / 2;

        this.portBox = new EditBox(this.font, x - 50, y - 20, 100, 20, Component.literal("Puerto"));
        this.portBox.setValue("COM9");
        this.addRenderableWidget(this.portBox);

        this.addRenderableWidget(Button.builder(Component.literal("CONECTAR"), b -> {
            status = MyArduinoMc.conectar(portBox.getValue());
        }).bounds(x - 55, y + 10, 50, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("OFF"), b -> {
            if(MyArduinoMc.arduinoPort != null) MyArduinoMc.arduinoPort.closePort();
            MyArduinoMc.arduinoPort = null;
            status = "§cCerrado";
        }).bounds(x + 5, y + 10, 50, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.drawCenteredString(this.font, status, this.width / 2, this.height / 2 + 40, 0xFFFFFF);
    }
}