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
    private Component statusText;
    private boolean isConnected;

    private List<BoardInfo> boardList = new ArrayList<>();
    private final List<Button> boardButtons = new ArrayList<>();

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
    }

    @Override
    protected void init() {
        this.isConnected = (SerialCraftClient.arduinoPort != null && SerialCraftClient.arduinoPort.isOpen());
        ClientPlayNetworking.send(new ConnectorPayload(this.pos, isConnected));
        ClientPlayNetworking.send(new BoardListRequestPayload(true));

        int w = 340; int h = 220;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        String oldPort = (this.portBox != null) ? this.portBox.getValue() : "COM3";

        // INPUT PUERTO (Fondo blanco manual)
        this.portBox = new EditBox(this.font, x + 35, y + 47, 170, 16, Component.translatable("gui.serialcraft.connector.port_label"));
        this.portBox.setMaxLength(32);
        this.portBox.setValue(isConnected ? SerialCraftClient.arduinoPort.getSystemPortName() : oldPort);
        this.portBox.setTextColor(TEXT_MAIN);
        this.portBox.setBordered(false);
        this.addRenderableWidget(this.portBox);

        if (isConnected) {
            this.statusText = Component.translatable("message.serialcraft.connected", SerialCraftClient.arduinoPort.getSystemPortName());
        } else {
            this.statusText = Component.translatable("message.serialcraft.disconnected");
        }

        this.addRenderableWidget(Button.builder(Component.translatable("gui.serialcraft.connector.btn_connect"), b -> {
            Component res = SerialCraftClient.conectar(portBox.getValue().trim());
            this.statusText = res;
            this.isConnected = (SerialCraftClient.arduinoPort != null && SerialCraftClient.arduinoPort.isOpen());
            if (isConnected) ClientPlayNetworking.send(new ConnectorPayload(this.pos, true));
        }).bounds(x + 220, y + 45, 90, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("❌"), b -> {
            SerialCraftClient.desconectar();
            this.statusText = Component.translatable("message.serialcraft.disconnected");
            this.isConnected = false;
            ClientPlayNetworking.send(new ConnectorPayload(this.pos, false));
        }).bounds(x + 315, y + 45, 20, 20).build());

        refreshBoardListWidgets();
    }

    public void updateBoardList(List<BoardInfo> boards) {
        this.boardList = boards;
        this.refreshBoardListWidgets();
    }

    private void refreshBoardListWidgets() {
        for (Button btn : boardButtons) this.removeWidget(btn);
        boardButtons.clear();

        int w = 340; int h = 220;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;
        int listStartY = y + 95;

        for (int i = 0; i < boardList.size(); i++) {
            BoardInfo info = boardList.get(i);
            int rowY = listStartY + (i * 24);
            if (rowY > y + h - 25) break;

            Button toggleBtn = Button.builder(Component.translatable(info.status() ? "message.serialcraft.on_icon" : "message.serialcraft.off_icon"), b -> {
                ClientPlayNetworking.send(new RemoteTogglePayload(info.pos()));
            }).bounds(x + 300, rowY + 2, 20, 16).build();

            this.addRenderableWidget(toggleBtn);
            boardButtons.add(toggleBtn);
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(gui);
        int w = 340; int h = 220;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        gui.fill(x, y, x + w, y + h, BG_COLOR);
        drawBorder(gui, x, y, w, h, 0xFFAAAAAA);

        gui.fill(x, y, x + w, y + 30, HEADER_BG);
        gui.drawCenteredString(this.font, this.title, this.width / 2, y + 10, TEXT_MAIN);

        // Input Box Background
        drawBorder(gui, x + 30, y + 43, 180, 24, 0xFFAAAAAA);
        gui.fill(x + 31, y + 44, x + 30 + 179, y + 43 + 23, INPUT_BG);

        gui.drawString(this.font, Component.translatable("gui.serialcraft.connector.port_label"), x + 30, y + 33, TEXT_DIM, false);
        int statusColor = isConnected ? ACCENT_GREEN : ACCENT_RED;
        gui.drawString(this.font, statusText, x + 30, y + 70, statusColor, false);

        gui.fill(x + 20, y + 85, x + w - 20, y + 86, 0xFFCCCCCC);
        gui.drawString(this.font, Component.translatable("gui.serialcraft.connector.boards_header"), x + 30, y + 90, TEXT_DIM, false);

        int listStartY = y + 105;

        if (boardList.isEmpty()) {
            gui.drawCenteredString(this.font, Component.translatable("gui.serialcraft.connector.no_boards"), this.width / 2, listStartY + 20, 0xFFAAAAAA);
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
        super.render(gui, mouseX, mouseY, partialTick);
    }

    private void drawBorder(GuiGraphics gui, int x, int y, int width, int height, int color) {
        gui.fill(x, y, x + width, y + 1, color);
        gui.fill(x, y + height - 1, x + width, y + height, color);
        gui.fill(x, y, x + 1, y + height, color);
        gui.fill(x + width - 1, y, x + width, y + height, color);
    }

    @Override public boolean isPauseScreen() { return false; }
}