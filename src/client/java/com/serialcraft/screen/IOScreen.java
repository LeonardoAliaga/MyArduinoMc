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

    // Config
    private int ioMode;
    private int signalType;
    private String targetData;  // El texto/ID
    private boolean isSoftOn;
    private String boardID;

    private EditBox idBox;     // Para nombre de placa
    private EditBox dataBox;   // Para el comando/ID (LUZ, SENSOR...)
    private Button modeButton, signalButton;

    // Colores tema oscuro
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
        } else {
            this.ioMode = 0;
            this.signalType = 0;
            this.targetData = (data == null) ? "cmd_1" : data;
            this.isSoftOn = true;
            this.boardID = "Arduino_1";
        }
    }

    @Override
    protected void init() {
        int w = 240; int h = 190;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        // 1. Campo ID de Placa (Decorativo / Logs)
        idBox = new EditBox(font, x + 20, y + 35, 140, 18, Component.literal("ID Placa"));
        idBox.setValue(this.boardID);
        addRenderableWidget(idBox);

        // 2. Botón Power
        addRenderableWidget(Button.builder(Component.literal(isSoftOn ? "ON" : "OFF"), b -> {
            isSoftOn = !isSoftOn;
            b.setMessage(Component.literal(isSoftOn ? "ON" : "OFF"));
        }).bounds(x + 170, y + 34, 50, 20).build());

        // 3. Botones de Configuración
        modeButton = Button.builder(getModeText(), b -> {
            ioMode = (ioMode == 0) ? 1 : 0;
            b.setMessage(getModeText());
        }).bounds(x + 20, y + 70, 95, 20).build();
        addRenderableWidget(modeButton);

        signalButton = Button.builder(getSignalText(), b -> {
            signalType = (signalType == 0) ? 1 : 0;
            b.setMessage(getSignalText());
        }).bounds(x + 125, y + 70, 95, 20).build();
        addRenderableWidget(signalButton);

        // 4. CAMPO DE DATOS (EL CORE DE LA COMUNICACIÓN)
        // Reemplaza al selector de pines. El usuario escribe "LUZ_COCINA", "SENSOR_1", etc.
        dataBox = new EditBox(font, x + 20, y + 115, 200, 20, Component.literal("Comando/ID"));
        dataBox.setMaxLength(32);
        dataBox.setValue(this.targetData);
        addRenderableWidget(dataBox);

        // 5. Guardar
        addRenderableWidget(Button.builder(Component.translatable("gui.serialcraft.io.btn_save"), b -> sendPacket())
                .bounds(x + 70, y + 155, 100, 20).build());
    }

    private Component getModeText() {
        return (ioMode == 0) ? Component.translatable("gui.serialcraft.mode.out")
                : Component.translatable("gui.serialcraft.mode.in");
    }

    private Component getSignalText() {
        return (signalType == 0) ? Component.literal("DIGITAL") : Component.literal("ANALOG");
    }

    private void sendPacket() {
        ClientPlayNetworking.send(new ConfigPayload(
                pos, ioMode, dataBox.getValue(), signalType, isSoftOn, idBox.getValue(), 10
        ));
        this.onClose();
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(gui);
        int w = 240; int h = 190;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        // Fondo
        gui.fill(x, y, x + w, y + h, BG_COLOR);
        gui.fill(x, y, x + w, y + 25, HEADER_COLOR);
        gui.drawCenteredString(font, this.title, this.width / 2, y + 8, ACCENT_COLOR);

        // Etiquetas
        gui.drawString(font, "ID Dispositivo:", x + 20, y + 25, TEXT_DIM, false); // Encima de idBox

        gui.drawCenteredString(font, "CANAL / COMANDO SERIAL", this.width / 2, y + 102, ACCENT_COLOR);

        // Texto de Ayuda Dinámico (Explicando el protocolo)
        String help = "";
        String cmd = dataBox.getValue().isEmpty() ? "[CMD]" : dataBox.getValue();

        if (ioMode == 0) { // Output
            help = (signalType == 0)
                    ? "Envía: " + cmd + ":1 (ON) / " + cmd + ":0 (OFF)"
                    : "Envía: " + cmd + ":[0-255] (PWM)";
        } else { // Input
            help = (signalType == 0)
                    ? "Recibe: " + cmd + ":[1/0] -> Redstone MAX"
                    : "Recibe: " + cmd + ":[0-1023] -> Redstone Variable";
        }
        gui.drawCenteredString(font, help, this.width/2, y + 140, 0xFF888888);

        super.render(gui, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
}