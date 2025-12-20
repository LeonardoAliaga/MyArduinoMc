package com.serialcraft.screen;

import com.serialcraft.SerialCraftClient;
import com.serialcraft.block.entity.ConnectorBlockEntity;
import com.serialcraft.network.BoardInfo;
import com.serialcraft.network.BoardListRequestPayload;
import com.serialcraft.network.ConnectorConfigPayload;
import com.serialcraft.network.ConnectorPayload;
import com.serialcraft.network.RemoteTogglePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class ConnectorScreen extends Screen {
    private final BlockPos pos;
    private EditBox portBox;
    private Component statusText;
    private boolean isConnected;

    // Solo BaudRate aquí
    private int baudRate = 9600;

    private List<BoardInfo> boardList = new ArrayList<>();
    private final List<Renderable> uiWidgets = new ArrayList<>();
    private final List<Button> boardButtons = new ArrayList<>();

    // Colores
    private static final int BG_COLOR = 0xFFF2F2EC;
    private static final int HEADER_BG = 0xFFE6E6DF;
    private static final int CARD_BG = 0xFFFFFFFF;
    private static final int INPUT_BG = 0xFFFFFFFF;
    private static final int CARD_BORDER = 0xFFD0D0D0;
    private static final int TEXT_MAIN = 0xFF333333;
    private static final int TEXT_DIM = 0xFF888888;
    private static final int ACCENT_GREEN = 0xFF2E7D32;
    private static final int ACCENT_RED = 0xFFC62828;

    public ConnectorScreen(BlockPos pos) {
        super(Component.translatable("gui.serialcraft.connector.title"));
        this.pos = pos;
        this.statusText = Component.literal("...");

        assert net.minecraft.client.Minecraft.getInstance().level != null;
        if (net.minecraft.client.Minecraft.getInstance().level.getBlockEntity(pos) instanceof ConnectorBlockEntity be) {
            this.baudRate = be.baudRate;
        }
    }

    @Override
    protected void init() {
        super.init();
        this.uiWidgets.clear();
        this.boardButtons.clear();

        this.isConnected = (SerialCraftClient.arduinoPort != null && SerialCraftClient.arduinoPort.isOpen());

        ClientPlayNetworking.send(new ConnectorPayload(this.pos, isConnected));
        ClientPlayNetworking.send(new BoardListRequestPayload(true));

        int w = 340; int h = 230;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        String oldPort = (this.portBox != null) ? this.portBox.getValue() : "COM9";

        // Port Box
        this.portBox = new EditBox(this.font, x + 35, y + 47, 100, 16, Component.translatable("gui.serialcraft.connector.port_label"));
        this.portBox.setMaxLength(32);
        this.portBox.setValue(isConnected ? SerialCraftClient.arduinoPort.getSystemPortName() : oldPort);
        this.portBox.setTextColor(TEXT_MAIN);
        this.portBox.setBordered(false);
        this.addCustomWidget(this.portBox);

        if (isConnected) {
            this.statusText = Component.translatable("message.serialcraft.connected", SerialCraftClient.arduinoPort.getSystemPortName());
        } else {
            this.statusText = Component.translatable("message.serialcraft.disconnected");
        }

        // Conectar
        this.addCustomWidget(Button.builder(Component.translatable("gui.serialcraft.connector.btn_connect"), b -> {
            Component res = SerialCraftClient.conectar(portBox.getValue().trim(), this.baudRate);
            this.statusText = res;
            this.isConnected = (SerialCraftClient.arduinoPort != null && SerialCraftClient.arduinoPort.isOpen());
            if (isConnected) ClientPlayNetworking.send(new ConnectorPayload(this.pos, true));
        }).bounds(x + 240, y + 45, 70, 20).build());

        // Desconectar
        this.addCustomWidget(Button.builder(Component.literal("X"), b -> {
            SerialCraftClient.desconectar();
            this.statusText = Component.translatable("message.serialcraft.disconnected");
            this.isConnected = false;
            ClientPlayNetworking.send(new ConnectorPayload(this.pos, false));
        }).bounds(x + 315, y + 45, 20, 20).build());

        // CONFIG BAUD RATE (Centrado)
        int configY = y + 75;
        this.addCustomWidget(Button.builder(getBaudText(), b -> {
            this.baudRate = (this.baudRate == 9600) ? 115200 : 9600;
            b.setMessage(getBaudText());
            ClientPlayNetworking.send(new ConnectorConfigPayload(this.pos, this.baudRate));
        }).bounds(x + 100, configY, 140, 20).build());

        refreshBoardListWidgets();
    }

    // CORRECCIÓN TÉCNICA: Usar la intersección de tipos requerida por Minecraft
    private <T extends GuiEventListener & Renderable & NarratableEntry> void addCustomWidget(T widget) {
        this.addRenderableWidget(widget); // Ahora sí acepta el widget sin quejas
        this.uiWidgets.add(widget);
    }

    private Component getBaudText() {
        return (baudRate == 9600)
                ? Component.translatable("gui.serialcraft.baud.arduino")
                : Component.translatable("gui.serialcraft.baud.esp32");
    }

    public void updateBoardList(List<BoardInfo> boards) {
        this.boardList = boards;
        this.refreshBoardListWidgets();
    }

    private void refreshBoardListWidgets() {
        for (Button btn : boardButtons) {
            this.removeWidget(btn);
            this.uiWidgets.remove(btn);
        }
        boardButtons.clear();

        int w = 340; int h = 230;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;
        int listStartY = y + 115;

        for (int i = 0; i < boardList.size(); i++) {
            BoardInfo info = boardList.get(i);
            int rowY = listStartY + (i * 24);
            if (rowY > y + h - 25) break;

            Button toggleBtn = Button.builder(Component.translatable(info.status() ? "message.serialcraft.on_icon" : "message.serialcraft.off_icon"), b -> {
                ClientPlayNetworking.send(new RemoteTogglePayload(info.pos()));
            }).bounds(x + 300, rowY + 2, 20, 16).build();

            this.addCustomWidget(toggleBtn);
            boardButtons.add(toggleBtn);
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        int w = 340; int h = 230;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        gui.fill(x, y, x + w, y + h, BG_COLOR);
        drawBorder(gui, x, y, w, h, 0xFFAAAAAA);

        gui.fill(x, y, x + w, y + 30, HEADER_BG);
        // TEXTO SIN SOMBRA (false)
        gui.drawString(this.font, this.title, x + (w/2) - (this.font.width(this.title)/2), y + 10, TEXT_MAIN, false);

        drawBorder(gui, x + 30, y + 43, 110, 24, 0xFFAAAAAA);
        gui.fill(x + 31, y + 44, x + 30 + 109, y + 43 + 23, INPUT_BG);

        // Labels sin sombra
        gui.drawString(this.font, Component.translatable("gui.serialcraft.connector.port_label"), x + 30, y + 33, TEXT_DIM, false);

        int statusColor = isConnected ? ACCENT_GREEN : ACCENT_RED;
        gui.drawString(this.font, statusText, x + 150, y + 50, statusColor, false);

        gui.fill(x + 20, y + 105, x + w - 20, y + 106, 0xFFCCCCCC);
        gui.drawString(this.font, Component.translatable("gui.serialcraft.connector.boards_header"), x + 30, y + 110, TEXT_DIM, false);

        int listStartY = y + 125;

        if (boardList.isEmpty()) {
            Component noBoards = Component.translatable("gui.serialcraft.connector.no_boards");
            gui.drawString(this.font, noBoards, x + (w/2) - (this.font.width(noBoards)/2), listStartY + 20, 0xFFAAAAAA, false);
        } else {
            for (int i = 0; i < boardList.size(); i++) {
                BoardInfo info = boardList.get(i);
                int rowY = listStartY + (i * 24);

                if (rowY > y + h - 25) break;

                gui.fill(x + 25, rowY, x + w - 25, rowY + 20, CARD_BG);
                drawBorder(gui, x + 25, rowY, w - 50, 20, CARD_BORDER);

                String icon = (info.mode() == 0) ? "📤" : "📥";
                gui.drawString(this.font, icon, x + 30, rowY + 6, 0xFF000000, false);
                gui.drawString(this.font, info.id(), x + 50, rowY + 6, TEXT_MAIN, false);
                gui.drawString(this.font, "CMD: " + info.data(), x + 180, rowY + 6, TEXT_DIM, false);
            }
        }

        for (Renderable widget : this.uiWidgets) {
            widget.render(gui, mouseX, mouseY, partialTick);
        }
    }

    private void drawBorder(GuiGraphics gui, int x, int y, int width, int height, int color) {
        gui.fill(x, y, x + width, y + 1, color);
        gui.fill(x, y + height - 1, x + width, y + height, color);
        gui.fill(x, y, x + 1, y + height, color);
        gui.fill(x + width - 1, y, x + width, y + height, color);
    }

    @Override public boolean isPauseScreen() { return false; }
}