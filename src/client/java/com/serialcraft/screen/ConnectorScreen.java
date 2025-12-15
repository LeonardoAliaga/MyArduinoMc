package com.serialcraft.screen;

import com.serialcraft.SerialCraftClient;
import com.serialcraft.network.BoardInfo;
import com.serialcraft.network.BoardListRequestPayload;
import com.serialcraft.network.ConnectorPayload;
import com.serialcraft.network.RemoteTogglePayload;
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
    private Component statusText; // Ahora usamos Component para el estado

    private List<BoardInfo> boardList = new ArrayList<>();
    private final List<Button> boardButtons = new ArrayList<>();

    private static final int BG_COLOR = 0xE6101010;
    private static final int TITLE_COLOR = 0xFF00E5FF;
    private static final int SECTION_COLOR = 0xFF55FFFF;

    public ConnectorScreen(BlockPos pos) {
        // Título traducible
        super(Component.translatable("gui.serialcraft.connector.title"));
        this.pos = pos;
        this.statusText = Component.translatable("gui.serialcraft.connector.waiting");
    }

    @Override
    protected void init() {
        boolean isConnected = (SerialCraftClient.arduinoPort != null && SerialCraftClient.arduinoPort.isOpen());

        ClientPlayNetworking.send(new ConnectorPayload(this.pos, isConnected));
        ClientPlayNetworking.send(new BoardListRequestPayload(true));

        int w = 340; int h = 200;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        String oldPort = (this.portBox != null) ? this.portBox.getValue() : "COM3";

        // Etiqueta del puerto traducida
        this.portBox = new EditBox(this.font, x + 20, y + 40, 200, 20, Component.translatable("gui.serialcraft.connector.port_label"));
        this.portBox.setMaxLength(32);
        this.portBox.setBordered(true);
        if (isConnected) {
            this.portBox.setValue(SerialCraftClient.arduinoPort.getSystemPortName());
            this.statusText = Component.translatable("message.serialcraft.connected", SerialCraftClient.arduinoPort.getSystemPortName());
        } else {
            this.portBox.setValue(oldPort);
        }
        this.addRenderableWidget(this.portBox);

        // Botón CONECTAR
        this.addRenderableWidget(Button.builder(Component.translatable("gui.serialcraft.connector.btn_connect"), b -> {
            this.statusText = Component.translatable("message.serialcraft.connecting");

            // Llamamos a conectar y recibimos un Component (ver SerialCraftClient modificado abajo)
            Component res = SerialCraftClient.conectar(portBox.getValue().trim());
            this.statusText = res;

            // Verificamos el contenido del mensaje (esto es un poco hacky para componentes, pero funcional para tu lógica)
            // Idealmente conectar() devolvería un booleano o enum
            if (SerialCraftClient.arduinoPort != null && SerialCraftClient.arduinoPort.isOpen()) {
                ClientPlayNetworking.send(new ConnectorPayload(this.pos, true));
            }
        }).bounds(x + 230, y + 40, 90, 20).build());

        // Botón DESCONECTAR
        this.addRenderableWidget(Button.builder(Component.translatable("gui.serialcraft.connector.btn_disconnect"), b -> {
            SerialCraftClient.desconectar();
            this.statusText = Component.translatable("message.serialcraft.disconnected");
            ClientPlayNetworking.send(new ConnectorPayload(this.pos, false));
        }).bounds(x + 230, y + 65, 90, 20).build());

        refreshBoardListWidgets();
    }

    public void updateBoardList(List<BoardInfo> boards) {
        this.boardList = boards;
        this.refreshBoardListWidgets();
    }

    private void refreshBoardListWidgets() {
        for (Button btn : boardButtons) this.removeWidget(btn);
        boardButtons.clear();

        int w = 340; int h = 200;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;
        int listY = y + 115;

        for (BoardInfo info : boardList) {
            if (listY > y + h - 20) break;
            // Botones ON/OFF usan las claves globales
            Button toggleBtn = Button.builder(Component.translatable(info.status() ? "message.serialcraft.on" : "message.serialcraft.off"), b -> {
                ClientPlayNetworking.send(new RemoteTogglePayload(info.pos()));
            }).bounds(x + 270, listY - 3, 50, 16).build();

            this.addRenderableWidget(toggleBtn);
            boardButtons.add(toggleBtn);
            listY += 18;
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(gui);
        int w = 340; int h = 200;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        gui.fill(x, y, x + w, y + h, BG_COLOR);
        gui.drawCenteredString(this.font, this.title, this.width / 2, y + 10, TITLE_COLOR);

        // Renderizamos el Componente de estado
        gui.drawString(this.font, Component.translatable("gui.serialcraft.connector.status", statusText), x + 20, y + 70, 0xFFFFFFFF, false);

        gui.drawString(this.font, Component.translatable("gui.serialcraft.connector.boards_header"), x + 20, y + 95, SECTION_COLOR, false);
        gui.fill(x + 20, y + 110, x + w - 20, y + h - 10, 0x88000000);

        int listY = y + 115;
        if (boardList.isEmpty()) {
            gui.drawString(this.font, Component.translatable("gui.serialcraft.connector.no_boards"), x + 25, listY, 0xFFAAAAAA, false);
        } else {
            for (BoardInfo info : boardList) {
                if (listY > y + h - 20) break;
                // Modos traducidos cortos
                String modeKey = switch(info.mode()) {
                    case 0 -> "gui.serialcraft.mode.out"; // Deberías agregar versiones cortas si prefieres (SAL/ENT)
                    case 1 -> "gui.serialcraft.mode.in";
                    default -> "gui.serialcraft.mode.hyb";
                };

                // Construcción manual de la línea para mezclar texto plano y traducido
                // "ID | Data | MODO"
                String display = "§b" + info.id() + " §7| §f" + info.data() + " §7| ";
                gui.drawString(this.font, display, x + 25, listY, 0xFFFFFFFF, false);
                // Dibujamos el modo traducido justo después (calculando el ancho)
                gui.drawString(this.font, Component.translatable(modeKey), x + 25 + this.font.width(display), listY, 0xFFFFFFFF, false);

                listY += 18;
            }
        }
        super.render(gui, mouseX, mouseY, partialTick);
    }
    @Override public boolean isPauseScreen() { return false; }
}