package com.serialcraft.screen;

import com.serialcraft.SerialCraft;
import com.serialcraft.SerialCraftClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class ConnectorScreen extends Screen {
    private final BlockPos pos;
    private EditBox portBox;
    private String status = "§7Estado: Esperando...";
    private List<String> boardList = new ArrayList<>();

    // Estilo Moderno (Sin Borde)
    private static final int BG_COLOR = 0xE6101010;
    private static final int TITLE_COLOR = 0xFF00E5FF;
    private static final int SECTION_COLOR = 0xFF55FFFF;

    public ConnectorScreen(BlockPos pos) {
        super(Component.literal("Laptop Serial"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        boolean isConnected = (SerialCraftClient.arduinoPort != null && SerialCraftClient.arduinoPort.isOpen());
        ClientPlayNetworking.send(new SerialCraft.ConnectorPayload(this.pos, isConnected));
        ClientPlayNetworking.send(new SerialCraft.BoardListRequestPayload(true));

        int w = 340; int h = 200;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        this.portBox = new EditBox(this.font, x + 20, y + 40, 200, 20, Component.literal("Puerto"));
        this.portBox.setMaxLength(32);
        this.portBox.setBordered(true);
        if (isConnected) {
            this.portBox.setValue(SerialCraftClient.arduinoPort.getSystemPortName());
            status = "§aConectado: " + SerialCraftClient.arduinoPort.getSystemPortName();
        } else {
            this.portBox.setValue("COM3");
        }
        this.addRenderableWidget(this.portBox);

        this.addRenderableWidget(Button.builder(Component.literal("CONECTAR"), b -> {
            status = "§eConectando...";
            String res = SerialCraftClient.conectar(portBox.getValue().trim());
            status = res;
            if (res.contains("Conectado") || res.contains("Ya conectado")) {
                ClientPlayNetworking.send(new SerialCraft.ConnectorPayload(this.pos, true));
            }
        }).bounds(x + 230, y + 40, 90, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("DESCONECTAR"), b -> {
            SerialCraftClient.desconectar();
            status = "§cDesconectado";
            ClientPlayNetworking.send(new SerialCraft.ConnectorPayload(this.pos, false));
        }).bounds(x + 230, y + 65, 90, 20).build());
    }

    public void updateBoardList(List<String> boards) {
        this.boardList = boards;
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(gui);

        int w = 340; int h = 200;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        // 1. Fondo Limpio
        gui.fill(x, y, x + w, y + h, BG_COLOR);

        // 2. Título
        gui.drawCenteredString(this.font, "§lCONTROL CENTRAL (LAPTOP)", this.width / 2, y + 10, TITLE_COLOR);

        // Estado
        gui.drawString(this.font, status, x + 20, y + 70, 0xFFFFFFFF, false);

        // Lista
        gui.drawString(this.font, "Placas Detectadas:", x + 20, y + 95, SECTION_COLOR, false);

        // Fondo interno para la lista (opcional, para separar visualmente)
        gui.fill(x + 20, y + 110, x + w - 20, y + h - 10, 0x88000000);

        int listY = y + 115;
        if (boardList.isEmpty()) {
            gui.drawString(this.font, "§7No se encontraron placas activas.", x + 25, listY, 0xFFFFFFFF, false);
        } else {
            for (String info : boardList) {
                if (listY > y + h - 20) break;
                gui.drawString(this.font, info, x + 25, listY, 0xFFFFFFFF, false);
                listY += 12;
            }
        }
        super.render(gui, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
}