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

    private static final int BG_COLOR = 0xFF1E1E1E;
    private static final int HEADER_COLOR = 0xFF2D2D2D;
    private static final int ACCENT_COLOR = 0xFF00E5FF;
    private static final int TEXT_DIM = 0xFFAAAAAA;

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
        int w = 240; int h = 210; // Aumentado un poco el alto
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        // 1. ID Placa
        idBox = new EditBox(font, x + 20, y + 35, 140, 18, Component.literal("ID"));
        idBox.setValue(this.boardID);
        addRenderableWidget(idBox);

        // 2. Botón Power
        addRenderableWidget(Button.builder(Component.literal(isSoftOn ? "ON" : "OFF"), b -> {
            isSoftOn = !isSoftOn;
            b.setMessage(Component.literal(isSoftOn ? "ON" : "OFF"));
        }).bounds(x + 170, y + 34, 50, 20).build());

        // 3. Fila de Botones: MODO | SEÑAL | LÓGICA
        int btnY = y + 70;

        modeButton = Button.builder(getModeText(), b -> {
            ioMode = (ioMode == 0) ? 1 : 0;
            b.setMessage(getModeText());
        }).bounds(x + 20, btnY, 65, 20).build();
        addRenderableWidget(modeButton);

        signalButton = Button.builder(getSignalText(), b -> {
            signalType = (signalType == 0) ? 1 : 0;
            b.setMessage(getSignalText());
        }).bounds(x + 90, btnY, 65, 20).build();
        addRenderableWidget(signalButton);

        logicButton = Button.builder(getLogicText(), b -> {
            logicMode = (logicMode + 1) % 3;
            b.setMessage(getLogicText());
        }).bounds(x + 160, btnY, 60, 20).build();
        addRenderableWidget(logicButton);

        // 4. CAMPO COMANDO
        dataBox = new EditBox(font, x + 20, y + 120, 200, 20, Component.literal("Comando"));
        dataBox.setMaxLength(32);
        dataBox.setValue(this.targetData);
        addRenderableWidget(dataBox);

        // 5. Guardar
        addRenderableWidget(Button.builder(Component.translatable("gui.serialcraft.io.btn_save"), b -> sendPacket())
                .bounds(x + 70, y + 170, 100, 20).build());
    }

    private Component getModeText() {
        return (ioMode == 0) ? Component.translatable("gui.serialcraft.mode.out")
                : Component.translatable("gui.serialcraft.mode.in");
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
        int w = 240; int h = 210;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        gui.fill(x, y, x + w, y + h, BG_COLOR);
        gui.fill(x, y, x + w, y + 25, HEADER_COLOR);
        gui.drawCenteredString(font, this.title, this.width / 2, y + 8, ACCENT_COLOR);

        gui.drawString(font, Component.translatable("gui.serialcraft.io.label_id"), x + 20, y + 25, TEXT_DIM, false);
        gui.drawCenteredString(font, Component.translatable("gui.serialcraft.io.label_command"), this.width / 2, y + 108, ACCENT_COLOR);

        // Ayuda contextual
        String help = "";
        String cmd = dataBox.getValue().isEmpty() ? "[CMD]" : dataBox.getValue();

        if (ioMode == 0) { // Output
            help = (signalType == 0)
                    ? "Send: " + cmd + ":1 (ON) / :0 (OFF)"
                    : "Send: " + cmd + ":[0-255] (PWM)";
        } else { // Input
            help = (signalType == 0)
                    ? "Recv: " + cmd + ":[1/0] -> Max"
                    : "Recv: " + cmd + ":[0-1023] -> Var";
        }
        gui.drawCenteredString(font, help, this.width/2, y + 150, 0xFF888888);

        super.render(gui, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
}