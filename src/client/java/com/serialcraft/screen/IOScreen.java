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
    // ... Variables ...
    private int ioMode;
    private int signalType;
    private final String targetData;
    private boolean isSoftOn;
    private final String boardID;
    private int logicMode;

    private EditBox idBox;
    private EditBox dataBox;
    private SolidButton logicButton;

    private final List<Renderable> uiWidgets = new ArrayList<>();

    // Colores
    private static final int BG_COLOR = 0xFFF2F2EC;
    private static final int CARD_COLOR = 0xFFE6E6DF;
    private static final int TEXT_MAIN = 0xFF333333;
    private static final int TEXT_DIM = 0xFF777777;
    private static final int ACCENT_COLOR = 0xFF00838F;
    private static final int BORDER_COLOR = 0xFFAAAAAA;

    // Dark Inputs
    private static final int INPUT_BG = 0xFF1A1A1A;
    private static final int TEXT_INPUT = 0xFFE0E0E0;

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
        super.init();
        this.uiWidgets.clear();

        int w = 260; int h = 230;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        // ID Box
        idBox = new EditBox(font, x + 30, y + 48, 160, 18, Component.translatable("gui.serialcraft.io.label_id"));
        idBox.setValue(this.boardID);
        idBox.setTextColor(TEXT_INPUT);
        idBox.setBordered(false);
        addCustomWidget(idBox);

        // Power Button
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

        // Botones Config
        int btnY = y + 95;
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

        // Data Box
        dataBox = new EditBox(font, x + 30, y + 157, 200, 18, Component.literal("Data"));
        dataBox.setMaxLength(32);
        dataBox.setValue(this.targetData);
        dataBox.setTextColor(TEXT_INPUT);
        dataBox.setBordered(false);
        addCustomWidget(dataBox);

        // Guardar
        addCustomWidget(SolidButton.success(x + 80, y + 195, 100, 20,
                Component.translatable("gui.serialcraft.io.btn_save"), b -> sendPacket()));
    }

    private <T extends GuiEventListener & Renderable & NarratableEntry> void addCustomWidget(T widget) {
        this.addRenderableWidget(widget);
        this.uiWidgets.add(widget);
    }

    // ... Getters de texto igual ...
    private Component getModeText() { return (ioMode == 0) ? Component.translatable("gui.serialcraft.mode.out") : Component.translatable("gui.serialcraft.mode.in"); }
    private Component getSignalText() { return (signalType == 0) ? Component.translatable("gui.serialcraft.signal.digital") : Component.translatable("gui.serialcraft.signal.analog"); }
    private Component getLogicText() {
        return switch(logicMode) {
            case 0 -> Component.translatable("gui.serialcraft.logic.or");
            case 1 -> Component.translatable("gui.serialcraft.logic.and");
            default -> Component.translatable("gui.serialcraft.logic.xor");
        };
    }

    private void sendPacket() {
        ClientPlayNetworking.send(new ConfigPayload(
                pos, ioMode, dataBox.getValue(), signalType, isSoftOn, idBox.getValue(), logicMode
        ));
        this.onClose();
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        int w = 260; int h = 230;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        gui.fill(x, y, x + w, y + h, BG_COLOR);
        // Bordes y Headers...
        gui.fill(x, y, x + width, y + 1, BORDER_COLOR);
        gui.fill(x, y, x + 1, y + height, BORDER_COLOR);
        gui.fill(x + width - 1, y, x + width, y + height, BORDER_COLOR);
        gui.fill(x, y + height - 1, x + width, y + height, BORDER_COLOR);

        gui.fill(x, y, x + w, y + 30, CARD_COLOR);
        gui.drawString(font, this.title, x + (w/2) - (font.width(this.title)/2), y + 10, TEXT_MAIN, false);

        // Input ID Dark
        gui.fill(x + 25, y + 42, x + 25 + 170, y + 42 + 26, BORDER_COLOR); // Borde
        gui.fill(x + 26, y + 43, x + 25 + 169, y + 42 + 25, INPUT_BG); // Relleno
        gui.drawString(font, Component.translatable("gui.serialcraft.io.label_id"), x + 25, y + 32, TEXT_DIM, false);

        gui.drawCenteredString(font, Component.translatable("gui.serialcraft.io.section_mode"), this.width / 2, y + 75, ACCENT_COLOR);

        // Input Data Dark
        gui.fill(x + 25, y + 151, x + 25 + 210, y + 151 + 26, BORDER_COLOR);
        gui.fill(x + 26, y + 152, x + 25 + 209, y + 151 + 25, INPUT_BG);
        gui.drawString(font, Component.translatable("gui.serialcraft.io.label_command"), x + 25, y + 140, TEXT_DIM, false);

        for (Renderable widget : this.uiWidgets) {
            widget.render(gui, mouseX, mouseY, partialTick);
        }
    }

    @Override public boolean isPauseScreen() { return false; }
}