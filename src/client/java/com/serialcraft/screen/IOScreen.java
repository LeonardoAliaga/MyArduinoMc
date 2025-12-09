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

    // Datos
    private int ioMode;
    private int logicMode;   // Nuevo: AND/OR/XOR
    private int outputType;  // Nuevo: Pulse/Switch
    private boolean isSoftOn; // Nuevo: Power

    private int pulseDuration;
    private int signalStrength;
    private int inputType; // Legacy
    private String boardID;
    private String currentData;

    // Widgets
    private EditBox dataBox, idBox, durationBox, strengthBox;
    private Button btnIoMode, btnLogic, btnOutput, btnPower, btnSave;

    // Estilos
    private static final int BG_COLOR = 0xEE0A0A0A;
    private static final int TITLE_COLOR = 0xFF00E5FF;
    private static final int SECTION_COLOR = 0xFF55FFFF;
    private static final int TEXT_COLOR = 0xFFAAAAAA;

    public IOScreen(BlockPos pos, int mode, String data) {
        super(Component.literal("Config IO"));
        this.pos = pos;

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
            // Valores por defecto
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

        // ID y Power (Parte superior)
        idBox = new EditBox(font, x + 20, y + 40, 200, 18, Component.literal("ID"));
        idBox.setValue(this.boardID);
        addRenderableWidget(idBox);

        btnPower = Button.builder(Component.literal(isSoftOn ? "§a[ON]" : "§c[OFF]"), b -> {
            this.isSoftOn = !this.isSoftOn;
            b.setMessage(Component.literal(isSoftOn ? "§a[ON]" : "§c[OFF]"));
        }).bounds(x + 230, y + 39, 70, 20).build();
        addRenderableWidget(btnPower);

        // Fila 1: Modo IO y Lógica de Entrada
        btnIoMode = Button.builder(Component.literal(getIoModeText()), b -> {
            this.ioMode = (this.ioMode + 1) % 3;
            b.setMessage(Component.literal(getIoModeText()));
            refreshWidgets();
        }).bounds(x + 20, y + 85, 135, 20).build();
        addRenderableWidget(btnIoMode);

        btnLogic = Button.builder(Component.literal(getLogicText()), b -> {
            this.logicMode = (this.logicMode + 1) % 3;
            b.setMessage(Component.literal(getLogicText()));
        }).bounds(x + 165, y + 85, 135, 20).build();
        addRenderableWidget(btnLogic);

        // Fila 2: Tipo de Salida y Valores Numéricos
        btnOutput = Button.builder(Component.literal(getOutputText()), b -> {
            this.outputType = (this.outputType + 1) % 2;
            b.setMessage(Component.literal(getOutputText()));
            refreshWidgets();
        }).bounds(x + 20, y + 120, 135, 20).build();
        addRenderableWidget(btnOutput);

        durationBox = new EditBox(font, x + 165, y + 120, 60, 18, Component.literal("Ticks"));
        durationBox.setValue(String.valueOf(this.pulseDuration));
        addRenderableWidget(durationBox);

        strengthBox = new EditBox(font, x + 240, y + 120, 60, 18, Component.literal("Power"));
        strengthBox.setValue(String.valueOf(this.signalStrength));
        addRenderableWidget(strengthBox);

        // Data Serial (Parte inferior)
        dataBox = new EditBox(font, x + 20, y + 160, 280, 18, Component.literal("Data"));
        dataBox.setValue(this.currentData);
        addRenderableWidget(dataBox);

        // Guardar
        btnSave = Button.builder(Component.literal("§l[ GUARDAR CONFIG ]"), b -> sendPacket())
                .bounds(x + 100, y + 185, 120, 20).build();
        addRenderableWidget(btnSave);

        refreshWidgets();
    }

    private void refreshWidgets() {
        // La duración (ticks) solo es relevante si estamos en modo PULSO (outputType 0)
        durationBox.visible = (outputType == 0);
        // El botón de lógica solo es útil si hay pines de entrada (Modo 0, 2),
        // pero lo dejamos visible para configurar previamente.
    }

    private String getIoModeText() {
        return switch (ioMode) {
            case 0 -> "§cDIR: SALIDA (PC->MC)";
            case 1 -> "§aDIR: ENTRADA (MC->PC)";
            case 2 -> "§9DIR: HÍBRIDO";
            default -> "Error";
        };
    }

    private String getLogicText() {
        return switch (logicMode) {
            case 0 -> "Lógica: OR (Alguno)";
            case 1 -> "Lógica: AND (Todos)";
            case 2 -> "Lógica: XOR (Impar)";
            default -> "Error";
        };
    }

    private String getOutputText() {
        return (outputType == 0) ? "Salida: PULSO" : "Salida: INTERRUPTOR";
    }

    private void sendPacket() {
        int d = 10; try { d = Integer.parseInt(durationBox.getValue()); } catch(Exception ignored){}
        int s = 15; try { s = Integer.parseInt(strengthBox.getValue()); } catch(Exception ignored){}

        // Enviar paquete actualizado con TODOS los campos
        ClientPlayNetworking.send(new SerialCraft.ConfigPayload(
                pos,
                ioMode,
                dataBox.getValue(),
                inputType, // Mantenemos inputType por si acaso, aunque no lo editemos aquí
                d,
                s,
                idBox.getValue(),
                logicMode,   // Nuevo
                outputType,  // Nuevo
                isSoftOn     // Nuevo
        ));
        this.onClose();
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(gui);

        int w = 320; int h = 210;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        // Fondo
        gui.fill(x, y, x + w, y + h, BG_COLOR);
        gui.drawCenteredString(font, "§lPANEL DE CONTROL SERIAL", this.width / 2, y + 10, TITLE_COLOR);

        // Etiquetas
        gui.drawString(font, "Identificación & Energía", x + 20, y + 28, SECTION_COLOR, false);
        gui.drawString(font, "Lógica de Pines (Entrada -> Salida)", x + 20, y + 72, SECTION_COLOR, false);
        gui.drawString(font, "Comunicación Serial", x + 20, y + 148, SECTION_COLOR, false);

        if(durationBox.visible) {
            gui.drawString(font, "Ticks", x + 165, y + 110, TEXT_COLOR, false);
        }
        gui.drawString(font, "Fuerza", x + 240, y + 110, TEXT_COLOR, false);

        super.render(gui, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
}