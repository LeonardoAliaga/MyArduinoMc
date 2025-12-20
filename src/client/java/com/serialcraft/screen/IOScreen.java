package com.serialcraft.screen;

import com.serialcraft.block.entity.ArduinoIOBlockEntity;
import com.serialcraft.client.ui.SolidButton;
import com.serialcraft.network.ConfigPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class IOScreen extends Screen {
    private final BlockPos pos;

    private int ioMode;
    private int signalType;
    private final String targetData;
    private boolean isSoftOn;
    private final String boardID;
    private int logicMode;

    // Solo Frecuencia individual aquí
    private int updateFreq;

    private EditBox idBox;
    private EditBox dataBox;
    private SolidButton logicButton;

    private final List<Renderable> uiWidgets = new ArrayList<>();

    private static final int BG_COLOR = 0xFFF2F2EC;
    private static final int CARD_COLOR = 0xFFE6E6DF;
    private static final int TEXT_MAIN = 0xFF333333;
    private static final int TEXT_DIM = 0xFF777777;
    private static final int ACCENT_COLOR = 0xFF00838F;
    private static final int BORDER_COLOR = 0xFFAAAAAA;
    private static final int INPUT_BG = 0xFFFFFFFF;

    public IOScreen(BlockPos pos, int mode, String data) {
        super(Component.translatable("gui.serialcraft.io.title"));
        this.pos = pos;

        assert net.minecraft.client.Minecraft.getInstance().level != null;
        if (net.minecraft.client.Minecraft.getInstance().level.getBlockEntity(pos) instanceof ArduinoIOBlockEntity io) {
            this.ioMode = io.ioMode;
            this.signalType = io.signalType;
            this.targetData = io.targetData;
            this.isSoftOn = io.isSoftOn;
            this.boardID = io.boardID;
            this.logicMode = io.logicMode;
            this.updateFreq = io.updateFrequency;
        } else {
            this.ioMode = 0;
            this.signalType = 0;
            this.targetData = (data == null) ? "cmd_1" : data;
            this.isSoftOn = true;
            this.boardID = "Arduino_1";
            this.logicMode = 0;
            this.updateFreq = 1; // 20Hz por defecto
        }
    }

    @Override
    protected void init() {
        super.init();
        this.uiWidgets.clear();

        int w = 260; int h = 230;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        // ID Box
        idBox = new EditBox(font, x + 30, y + 48, 160, 18, Component.translatable("gui.serialcraft.io.label_id"));
        idBox.setValue(this.boardID);
        idBox.setTextColor(TEXT_MAIN);
        idBox.setBordered(false);
        addCustomWidget(idBox);

        // Power
        SolidButton powerBtn = SolidButton.of(
                x + 200, y + 45, 35, 20,
                Component.translatable(isSoftOn ? "message.serialcraft.on_icon" : "message.serialcraft.off_icon"),
                b -> {
                    isSoftOn = !isSoftOn;
                    b.setMessage(Component.translatable(isSoftOn ? "message.serialcraft.on_icon" : "message.serialcraft.off_icon"));
                    if (b instanceof SolidButton sb) {
                        sb.setVariant(isSoftOn ? SolidButton.Variant.SUCCESS : SolidButton.Variant.DANGER);
                    }
                }, isSoftOn ? SolidButton.Variant.SUCCESS : SolidButton.Variant.DANGER);
        addCustomWidget(powerBtn);

        // Fila 1
        int btnY = y + 90;
        int btnW = 70;
        int gap = 5;

        SolidButton modeButton = SolidButton.primary(x + 20, btnY, btnW, 20, getModeText(), b -> {
            ioMode = (ioMode == 0) ? 1 : 0;
            b.setMessage(getModeText());
            logicButton.visible = (ioMode == 1);
        });
        addCustomWidget(modeButton);

        SolidButton signalButton = SolidButton.primary(x + 20 + btnW + gap, btnY, btnW, 20, getSignalText(), b -> {
            signalType = (signalType == 0) ? 1 : 0;
            b.setMessage(getSignalText());
        });
        addCustomWidget(signalButton);

        logicButton = SolidButton.primary(x + 20 + (btnW + gap) * 2, btnY, btnW, 20, getLogicText(), b -> {
            logicMode = (logicMode + 1) % 3;
            b.setMessage(getLogicText());
        });
        logicButton.visible = (ioMode == 1);
        addCustomWidget(logicButton);

        // Fila 2: Solo Frecuencia (Centrado)
        int btnY2 = y + 115;
        SolidButton freqButton = SolidButton.soft(x + 80, btnY2, 100, 20, getFreqText(), b -> {
            if (updateFreq == 1) updateFreq = 2;
            else if (updateFreq == 2) updateFreq = 4;
            else updateFreq = 1;
            b.setMessage(getFreqText());
        });
        addCustomWidget(freqButton);

        // Data Box
        dataBox = new EditBox(font, x + 30, y + 157, 200, 18, Component.literal("Data"));
        dataBox.setMaxLength(32);
        dataBox.setValue(this.targetData);
        dataBox.setTextColor(TEXT_MAIN);
        dataBox.setBordered(false);
        addCustomWidget(dataBox);

        // Guardar
        addCustomWidget(SolidButton.success(x + 80, y + 195, 100, 20,
                Component.translatable("gui.serialcraft.io.btn_save"), b -> sendPacket()));
    }

    // CORRECCIÓN TÉCNICA
    private <T extends GuiEventListener & Renderable & NarratableEntry> void addCustomWidget(T widget) {
        this.addRenderableWidget(widget);
        this.uiWidgets.add(widget);
    }

    private Component getModeText() { return (ioMode == 0) ? Component.translatable("gui.serialcraft.mode.out") : Component.translatable("gui.serialcraft.mode.in"); }
    private Component getSignalText() { return (signalType == 0) ? Component.translatable("gui.serialcraft.signal.digital") : Component.translatable("gui.serialcraft.signal.analog"); }

    private Component getLogicText() {
        return switch(logicMode) {
            case 0 -> Component.translatable("gui.serialcraft.logic.or");
            case 1 -> Component.translatable("gui.serialcraft.logic.and");
            default -> Component.translatable("gui.serialcraft.logic.xor");
        };
    }

    private Component getFreqText() {
        return switch (updateFreq) {
            case 1 -> Component.translatable("gui.serialcraft.freq.fast");
            case 2 -> Component.translatable("gui.serialcraft.freq.normal");
            default -> Component.translatable("gui.serialcraft.freq.slow");
        };
    }

    private void sendPacket() {
        ClientPlayNetworking.send(new ConfigPayload(
                pos, ioMode, dataBox.getValue(), signalType, isSoftOn, idBox.getValue(),
                updateFreq, logicMode
        ));
        this.onClose();
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        int w = 260; int h = 230;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        gui.fill(x, y, x + w, y + h, BG_COLOR);
        drawBorder(gui, x, y, w, h, BORDER_COLOR);

        gui.fill(x, y, x + w, y + 30, CARD_COLOR);
        // TEXTO SIN SOMBRA (false)
        gui.drawString(font, this.title, x + (w/2) - (font.width(this.title)/2), y + 10, TEXT_MAIN, false);

        // Inputs más altos para legibilidad
        drawBorder(gui, x + 25, y + 42, 170, 26, BORDER_COLOR);
        gui.fill(x + 26, y + 43, x + 25 + 169, y + 42 + 25, INPUT_BG);
        // Label sin sombra
        gui.drawString(font, Component.translatable("gui.serialcraft.io.label_id"), x + 25, y + 32, TEXT_DIM, false);

        gui.drawCenteredString(font, Component.translatable("gui.serialcraft.io.section_mode"), this.width / 2, y + 75, ACCENT_COLOR);

        drawBorder(gui, x + 25, y + 151, 210, 26, BORDER_COLOR);
        gui.fill(x + 26, y + 152, x + 25 + 209, y + 151 + 25, INPUT_BG);
        // Label sin sombra
        gui.drawString(font, Component.translatable("gui.serialcraft.io.label_command"), x + 25, y + 140, TEXT_DIM, false);

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