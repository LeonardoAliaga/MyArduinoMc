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
    private int logicMode;
    private int outputType;
    private boolean isSoftOn;

    private final int pulseDuration;
    private final int signalStrength;
    private final int inputType;
    private final String boardID;
    private final String currentData;

    private EditBox dataBox, idBox, durationBox, strengthBox;

    private static final int BG_COLOR = 0xEE0A0A0A;
    private static final int TITLE_COLOR = 0xFF00E5FF;
    private static final int SECTION_COLOR = 0xFF55FFFF;
    private static final int TEXT_COLOR = 0xFFAAAAAA;

    public IOScreen(BlockPos pos, int mode, String data) {
        super(Component.translatable("gui.serialcraft.io.title"));
        this.pos = pos;

        assert net.minecraft.client.Minecraft.getInstance().level != null;
        if (net.minecraft.client.Minecraft.getInstance().level.getBlockEntity(pos) instanceof ArduinoIOBlockEntity io) {
            this.ioMode = io.ioMode;
            this.logicMode = io.logicMode;
            this.outputType = io.outputType;
            this.isSoftOn = io.isSoftOn;
            this.inputType = io.inputType;
            this.pulseDuration = io.pulseDuration;
            this.signalStrength = io.signalStrength;
            this.boardID = io.boardID;
            this.currentData = io.targetData;
        } else {
            this.ioMode = mode;
            this.logicMode = 0;
            this.outputType = 0;
            this.isSoftOn = true;
            this.inputType = 0;
            this.pulseDuration = 10;
            this.signalStrength = 15;
            this.boardID = "placa_" + (int)(Math.random()*1000);
            this.currentData = (data == null) ? "" : data;
        }
    }

    @Override
    protected void init() {
        int w = 320; int h = 210;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        idBox = new EditBox(font, x + 20, y + 40, 200, 18, Component.translatable("gui.serialcraft.io.label_id"));
        idBox.setValue(this.boardID);
        addRenderableWidget(idBox);

        Button btnPower = Button.builder(Component.translatable(isSoftOn ? "message.serialcraft.on" : "message.serialcraft.off"), b -> {
            this.isSoftOn = !this.isSoftOn;
            b.setMessage(Component.translatable(isSoftOn ? "message.serialcraft.on" : "message.serialcraft.off"));
        }).bounds(x + 230, y + 39, 70, 20).build();
        addRenderableWidget(btnPower);

        Button btnIoMode = Button.builder(getIoModeText(), b -> {
            this.ioMode = (this.ioMode + 1) % 3;
            b.setMessage(getIoModeText());
            refreshWidgets();
        }).bounds(x + 20, y + 85, 135, 20).build();
        addRenderableWidget(btnIoMode);

        Button btnLogic = Button.builder(getLogicText(), b -> {
            this.logicMode = (this.logicMode + 1) % 3;
            b.setMessage(getLogicText());
        }).bounds(x + 165, y + 85, 135, 20).build();
        addRenderableWidget(btnLogic);

        Button btnOutput = Button.builder(getOutputText(), b -> {
            this.outputType = (this.outputType + 1) % 2;
            b.setMessage(getOutputText());
            refreshWidgets();
        }).bounds(x + 20, y + 120, 135, 20).build();
        addRenderableWidget(btnOutput);

        durationBox = new EditBox(font, x + 165, y + 120, 60, 18, Component.translatable("gui.serialcraft.io.label_ticks"));
        durationBox.setValue(String.valueOf(this.pulseDuration));
        addRenderableWidget(durationBox);

        strengthBox = new EditBox(font, x + 240, y + 120, 60, 18, Component.translatable("gui.serialcraft.io.label_power"));
        strengthBox.setValue(String.valueOf(this.signalStrength));
        addRenderableWidget(strengthBox);

        dataBox = new EditBox(font, x + 20, y + 160, 280, 18, Component.translatable("gui.serialcraft.io.label_data"));
        dataBox.setValue(this.currentData);
        addRenderableWidget(dataBox);

        Button btnSave = Button.builder(Component.translatable("gui.serialcraft.io.btn_save"), b -> sendPacket())
                .bounds(x + 100, y + 185, 120, 20).build();
        addRenderableWidget(btnSave);

        refreshWidgets();
    }

    private void refreshWidgets() {
        durationBox.visible = (outputType == 0);
    }

    private Component getIoModeText() {
        return switch (ioMode) {
            case 0 -> Component.translatable("gui.serialcraft.mode.out");
            case 1 -> Component.translatable("gui.serialcraft.mode.in");
            case 2 -> Component.translatable("gui.serialcraft.mode.hyb");
            default -> Component.literal("Error");
        };
    }

    private Component getLogicText() {
        return switch (logicMode) {
            case 0 -> Component.translatable("gui.serialcraft.logic.or");
            case 1 -> Component.translatable("gui.serialcraft.logic.and");
            case 2 -> Component.translatable("gui.serialcraft.logic.xor");
            default -> Component.literal("Error");
        };
    }

    private Component getOutputText() {
        return (outputType == 0) ? Component.translatable("gui.serialcraft.output.pulse") : Component.translatable("gui.serialcraft.output.switch");
    }

    private void sendPacket() {
        int d = 10; try { d = Integer.parseInt(durationBox.getValue()); } catch(Exception ignored){}
        int s = 15; try { s = Integer.parseInt(strengthBox.getValue()); } catch(Exception ignored){}
        ClientPlayNetworking.send(new ConfigPayload(pos, ioMode, dataBox.getValue(), inputType, d, s, idBox.getValue(), logicMode, outputType, isSoftOn));
        this.onClose();
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(gui);
        int w = 320; int h = 210;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        gui.fill(x, y, x + w, y + h, BG_COLOR);
        gui.drawCenteredString(font, this.title, this.width / 2, y + 10, TITLE_COLOR);

        gui.drawString(font, Component.translatable("gui.serialcraft.io.sec_id"), x + 20, y + 28, SECTION_COLOR, false);
        gui.drawString(font, Component.translatable("gui.serialcraft.io.sec_logic"), x + 20, y + 72, SECTION_COLOR, false);
        gui.drawString(font, Component.translatable("gui.serialcraft.io.sec_serial"), x + 20, y + 148, SECTION_COLOR, false);

        if(durationBox.visible) {
            gui.drawString(font, Component.translatable("gui.serialcraft.io.label_ticks"), x + 165, y + 110, TEXT_COLOR, false);
        }
        gui.drawString(font, Component.translatable("gui.serialcraft.io.label_power"), x + 240, y + 110, TEXT_COLOR, false);

        super.render(gui, mouseX, mouseY, partialTick);
    }
    @Override public boolean isPauseScreen() { return false; }
}