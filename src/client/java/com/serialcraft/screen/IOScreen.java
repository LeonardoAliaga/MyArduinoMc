package com.serialcraft.screen;

import com.serialcraft.SerialCraft;
import com.serialcraft.block.entity.ArduinoIOBlockEntity;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class IOScreen extends Screen {
    private final BlockPos pos;

    // Datos temporales
    private int mode;
    private int inputType;
    private int pulseDuration;
    private int signalStrength;
    private String boardID;
    private String currentData;

    // Widgets
    private EditBox dataBox;
    private EditBox idBox;
    private EditBox durationBox;
    private EditBox strengthBox;
    private Button btnMode, btnInputType, btnSave;

    // --- ESTILO VISUAL (Sin Borde) ---
    private static final int BG_COLOR = 0xEE0A0A0A;     // Negro profundo (93% opacidad)
    private static final int TITLE_COLOR = 0xFF00E5FF; // Cyan Neón (Solo texto)
    private static final int SECTION_COLOR = 0xFF55FFFF;
    private static final int TEXT_COLOR = 0xFFAAAAAA;

    public IOScreen(BlockPos pos, int mode, String data) {
        super(Component.literal("Config IO"));
        this.pos = pos;

        if (net.minecraft.client.Minecraft.getInstance().level.getBlockEntity(pos) instanceof ArduinoIOBlockEntity io) {
            this.inputType = io.inputType;
            this.pulseDuration = io.pulseDuration;
            this.signalStrength = io.signalStrength;
            this.boardID = io.boardID;
        } else {
            this.inputType = 0;
            this.pulseDuration = 10;
            this.signalStrength = 15;
            this.boardID = "placa_" + (int)(Math.random()*1000);
        }
        this.mode = mode;
        this.currentData = (data == null) ? "" : data;
    }

    @Override
    protected void init() {
        int w = 320;
        int h = 210;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        // ID Box
        idBox = new EditBox(font, x + 20, y + 40, 280, 18, Component.literal("ID"));
        idBox.setValue(this.boardID);
        idBox.setMaxLength(20);
        addRenderableWidget(idBox);

        // --- COMPORTAMIENTO ---

        // Botón Modo
        btnMode = Button.builder(Component.literal(getModeText()), b -> {
            this.mode = (this.mode + 1) % 3;
            b.setMessage(Component.literal(getModeText()));
            refreshWidgets();
        }).bounds(x + 20, y + 90, 135, 20).build();
        addRenderableWidget(btnMode);

        // Botón Tipo
        btnInputType = Button.builder(Component.literal(getInputTypeText()), b -> {
            this.inputType = (this.inputType == 0) ? 1 : 0;
            b.setMessage(Component.literal(getInputTypeText()));
            refreshWidgets();
        }).bounds(x + 165, y + 90, 135, 20).build();
        addRenderableWidget(btnInputType);

        // Caja Duración
        durationBox = new EditBox(font, x + 165, y + 115, 60, 18, Component.literal("Ticks"));
        durationBox.setValue(String.valueOf(this.pulseDuration));
        durationBox.setFilter(s -> s.matches("\\d*"));
        durationBox.setMaxLength(5);
        addRenderableWidget(durationBox);

        // Caja Intensidad
        strengthBox = new EditBox(font, x + 240, y + 115, 60, 18, Component.literal("Power"));
        strengthBox.setValue(String.valueOf(this.signalStrength));
        strengthBox.setFilter(s -> s.matches("\\d*"));
        strengthBox.setMaxLength(2);
        addRenderableWidget(strengthBox);

        // Data Serial
        dataBox = new EditBox(font, x + 20, y + 155, 280, 18, Component.literal("Data"));
        dataBox.setValue(this.currentData);
        dataBox.setMaxLength(50);
        addRenderableWidget(dataBox);

        // Guardar
        btnSave = Button.builder(Component.literal("§l[ GUARDAR CAMBIOS ]"), b -> sendPacket())
                .bounds(x + 100, y + 185, 120, 20).build();
        addRenderableWidget(btnSave);

        refreshWidgets();
    }

    private void refreshWidgets() {
        boolean isInput = (mode != 0); // 1 o 2

        btnInputType.visible = isInput;
        durationBox.visible = isInput && (inputType == 1);
        strengthBox.visible = true;
    }

    private void sendPacket() {
        int finalDuration = 10;
        int finalStrength = 15;

        try {
            String txt = durationBox.getValue().trim();
            if(!txt.isEmpty()) finalDuration = Integer.parseInt(txt);
            if(finalDuration < 1) finalDuration = 1;
            if(finalDuration > 72000) finalDuration = 72000;

            String strTxt = strengthBox.getValue().trim();
            if(!strTxt.isEmpty()) finalStrength = Integer.parseInt(strTxt);
            if(finalStrength < 1) finalStrength = 1;
            if(finalStrength > 15) finalStrength = 15;

        } catch(Exception ignored){}

        ClientPlayNetworking.send(new SerialCraft.ConfigPayload(
                pos, mode, dataBox.getValue(), inputType, finalDuration, finalStrength, idBox.getValue()
        ));
        this.onClose();
    }

    private String getModeText() {
        return switch (mode) {
            case 0 -> "§cMODO: SALIDA (PC->MC)";
            case 1 -> "§aMODO: ENTRADA (MC->PC)";
            case 2 -> "§9MODO: HÍBRIDO";
            default -> "Error";
        };
    }

    private String getInputTypeText() {
        return (inputType == 0) ? "Tipo: Interruptor" : "Tipo: Pulso";
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(gui);

        int w = 320;
        int h = 210;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        // 1. Fondo Limpio (Sin borde)
        gui.fill(x, y, x + w, y + h, BG_COLOR);

        // 2. Título
        gui.drawCenteredString(font, "§lCONFIGURACIÓN PLACA IO", this.width / 2, y + 10, TITLE_COLOR);

        // 3. Textos
        gui.drawString(font, "Identificación", x + 20, y + 28, SECTION_COLOR, false);
        gui.drawString(font, "Comportamiento Lógico", x + 20, y + 78, SECTION_COLOR, false);
        gui.drawString(font, "Comunicación Serial (Arduino)", x + 20, y + 143, SECTION_COLOR, false);

        if(durationBox.visible) {
            gui.drawString(font, "Duración (Ticks):", x + 165, y + 115 + 4, TEXT_COLOR, false);
        }

        gui.drawString(font, "Señal (1-15):", x + 240, y + 115 - 10, TEXT_COLOR, false);

        super.render(gui, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
}