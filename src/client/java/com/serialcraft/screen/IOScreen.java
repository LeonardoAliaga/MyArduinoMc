package com.serialcraft.screen;

import com.serialcraft.block.entity.ArduinoIOBlockEntity;
import com.serialcraft.network.ConfigPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class IOScreen extends Screen {
    private final BlockPos pos;

    private int ioMode;
    private int signalType;
    private String targetData;
    private boolean isSoftOn;
    private String boardID;
    private int logicMode;

    private EditBox idBox;
    private EditBox dataBox;
    private Button modeButton, signalButton, logicButton;

    // --- PALETA DE COLORES ---
    private static final int BG_COLOR = 0xFFF2F2EC;
    private static final int CARD_COLOR = 0xFFE6E6DF;
    private static final int TEXT_MAIN = 0xFF333333;
    private static final int TEXT_DIM = 0xFF777777;
    private static final int ACCENT_COLOR = 0xFF00838F;
    private static final int BORDER_COLOR = 0xFFAAAAAA;
    private static final int INPUT_BG = 0xFFFFFFFF; // Fondo blanco para inputs

    public IOScreen(BlockPos pos, int mode, String data) {
        super(Component.translatable("gui.serialcraft.io.title"));
        this.pos = pos;

        if (net.minecraft.client.Minecraft.getInstance().level.getBlockEntity(pos) instanceof ArduinoIOBlockEntity io) {
            this.ioMode = io.ioMode;
            this.signalType = io.signalType;
            this.targetData = io.targetData;
            this.isSoftOn = io.isSoftOn;
            this.boardID = io.boardID;
            this.logicMode = io.logicMode;
        } else {
            this.ioMode = 0;
            this.signalType = 0;
            this.targetData = (data == null) ? "cmd_1" : data;
            this.isSoftOn = true;
            this.boardID = "Arduino_1";
            this.logicMode = 0;
        }
    }

    @Override
    protected void init() {
        int w = 260; int h = 210;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        // 1. INPUT ID (Sin borde default, fondo custom en render)
        idBox = new EditBox(font, x + 30, y + 46, 140, 16, Component.translatable("gui.serialcraft.io.label_id"));
        idBox.setValue(this.boardID);
        idBox.setTextColor(TEXT_MAIN);
        idBox.setBordered(false); // Quitamos el borde negro default
        addRenderableWidget(idBox);

        // Botón Power
        addRenderableWidget(Button.builder(Component.translatable(isSoftOn ? "message.serialcraft.on_icon" : "message.serialcraft.off_icon"), b -> {
            isSoftOn = !isSoftOn;
            b.setMessage(Component.translatable(isSoftOn ? "message.serialcraft.on_icon" : "message.serialcraft.off_icon"));
        }).bounds(x + 185, y + 44, 50, 20).build());

        // 2. Botones Config
        int btnY = y + 90;
        int btnW = 70;
        int gap = 5;

        modeButton = Button.builder(getModeText(), b -> {
            ioMode = (ioMode == 0) ? 1 : 0;
            b.setMessage(getModeText());
            logicButton.visible = (ioMode == 1);
        }).bounds(x + 20, btnY, btnW, 20).build();
        addRenderableWidget(modeButton);

        signalButton = Button.builder(getSignalText(), b -> {
            signalType = (signalType == 0) ? 1 : 0;
            b.setMessage(getSignalText());
        }).bounds(x + 20 + btnW + gap, btnY, btnW, 20).build();
        addRenderableWidget(signalButton);

        logicButton = Button.builder(getLogicText(), b -> {
            logicMode = (logicMode + 1) % 3;
            b.setMessage(getLogicText());
        }).bounds(x + 20 + (btnW + gap) * 2, btnY, btnW, 20).build();
        logicButton.visible = (ioMode == 1);
        addRenderableWidget(logicButton);

        // 3. INPUT DATA
        dataBox = new EditBox(font, x + 30, y + 141, 200, 16, Component.literal("Data"));
        dataBox.setMaxLength(32);
        dataBox.setValue(this.targetData);
        dataBox.setTextColor(TEXT_MAIN);
        dataBox.setBordered(false);
        addRenderableWidget(dataBox);

        // 4. Guardar
        addRenderableWidget(Button.builder(Component.translatable("gui.serialcraft.io.btn_save"), b -> sendPacket())
                .bounds(x + 80, y + 180, 100, 20).build());
    }

    private Component getModeText() {
        return (ioMode == 0) ? Component.translatable("gui.serialcraft.mode.out") : Component.translatable("gui.serialcraft.mode.in");
    }
    private Component getSignalText() {
        return (signalType == 0) ? Component.translatable("gui.serialcraft.signal.digital") : Component.translatable("gui.serialcraft.signal.analog");
    }
    private Component getLogicText() {
        switch(logicMode) {
            case 0: return Component.translatable("gui.serialcraft.logic.or");
            case 1: return Component.translatable("gui.serialcraft.logic.and");
            case 2: return Component.translatable("gui.serialcraft.logic.xor");
            default: return Component.literal("OR");
        }
    }

    private void sendPacket() {
        ClientPlayNetworking.send(new ConfigPayload(
                pos, ioMode, dataBox.getValue(), signalType, isSoftOn, idBox.getValue(), 10, logicMode
        ));
        this.onClose();
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(gui);
        int w = 260; int h = 210;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        gui.fill(x, y, x + w, y + h, BG_COLOR);
        drawBorder(gui, x, y, w, h, BORDER_COLOR);

        // Cabecera
        gui.fill(x, y, x + w, y + 30, CARD_COLOR);
        gui.drawCenteredString(font, this.title, this.width / 2, y + 10, TEXT_MAIN);

        // DIBUJAR CAJAS BLANCAS DETRÁS DE LOS INPUTS
        // Caja ID
        drawBorder(gui, x + 25, y + 42, 150, 24, BORDER_COLOR);
        gui.fill(x + 26, y + 43, x + 25 + 149, y + 42 + 23, INPUT_BG);
        gui.drawString(font, Component.translatable("gui.serialcraft.io.label_id"), x + 25, y + 32, TEXT_DIM, false);

        // Separador Modo
        gui.drawCenteredString(font, Component.translatable("gui.serialcraft.io.section_mode"), this.width / 2, y + 75, ACCENT_COLOR);

        // Caja Data
        drawBorder(gui, x + 25, y + 137, 210, 24, BORDER_COLOR);
        gui.fill(x + 26, y + 138, x + 25 + 209, y + 137 + 23, INPUT_BG);
        gui.drawString(font, Component.translatable("gui.serialcraft.io.label_command"), x + 25, y + 125, TEXT_DIM, false);

        // Ayuda
        String cmd = dataBox.getValue().isEmpty() ? "[CMD]" : dataBox.getValue();
        String helpKey = (ioMode == 0)
                ? ((signalType == 0) ? "gui.serialcraft.help.out_dig" : "gui.serialcraft.help.out_pwm")
                : ((signalType == 0) ? "gui.serialcraft.help.in_dig" : "gui.serialcraft.help.in_pwm");

        gui.drawCenteredString(font, Component.translatable(helpKey, cmd), this.width/2, y + 165, 0xFF888888);

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