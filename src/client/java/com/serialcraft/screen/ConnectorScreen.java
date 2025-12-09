package com.serialcraft.screen;

import com.serialcraft.SerialCraft;
import com.serialcraft.SerialCraftClient;
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
    private String status = "§7Estado: Esperando...";

    // Usamos la nueva estructura de datos rica
    private List<SerialCraft.BoardInfo> boardList = new ArrayList<>();

    // Lista para rastrear los botones dinámicos y poder eliminarlos al actualizar
    private final List<Button> boardButtons = new ArrayList<>();

    // Estilo Moderno
    private static final int BG_COLOR = 0xE6101010;
    private static final int TITLE_COLOR = 0xFF00E5FF;
    private static final int SECTION_COLOR = 0xFF55FFFF;

    public ConnectorScreen(BlockPos pos) {
        super(Component.literal("Laptop Serial"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        boolean isConnected = (SerialCraftClient.arduinoPort != null && SerialCraftClient.arduinoPort.isOpen());

        // Enviar peticiones iniciales
        ClientPlayNetworking.send(new SerialCraft.ConnectorPayload(this.pos, isConnected));
        ClientPlayNetworking.send(new SerialCraft.BoardListRequestPayload(true));

        int w = 340; int h = 200;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        // Recuperar texto si se redimensiona la ventana
        String oldPort = (this.portBox != null) ? this.portBox.getValue() : "COM3";

        this.portBox = new EditBox(this.font, x + 20, y + 40, 200, 20, Component.literal("Puerto"));
        this.portBox.setMaxLength(32);
        this.portBox.setBordered(true);
        if (isConnected) {
            this.portBox.setValue(SerialCraftClient.arduinoPort.getSystemPortName());
            status = "§aConectado: " + SerialCraftClient.arduinoPort.getSystemPortName();
        } else {
            this.portBox.setValue(oldPort);
        }
        this.addRenderableWidget(this.portBox);

        // Botón CONECTAR
        this.addRenderableWidget(Button.builder(Component.literal("CONECTAR"), b -> {
            status = "§eConectando...";
            String res = SerialCraftClient.conectar(portBox.getValue().trim());
            status = res;
            if (res.contains("Conectado") || res.contains("Ya conectado")) {
                ClientPlayNetworking.send(new SerialCraft.ConnectorPayload(this.pos, true));
            }
        }).bounds(x + 230, y + 40, 90, 20).build());

        // Botón DESCONECTAR
        this.addRenderableWidget(Button.builder(Component.literal("DESCONECTAR"), b -> {
            SerialCraftClient.desconectar();
            status = "§cDesconectado";
            ClientPlayNetworking.send(new SerialCraft.ConnectorPayload(this.pos, false));
        }).bounds(x + 230, y + 65, 90, 20).build());

        // Si ya teníamos una lista cargada (ej. al redimensionar), restauramos los botones
        refreshBoardListWidgets();
    }

    // Llamado desde la red cuando llega el paquete BoardListResponsePayload
    public void updateBoardList(List<SerialCraft.BoardInfo> boards) {
        this.boardList = boards;
        this.refreshBoardListWidgets();
    }

    // Reconstruye solo los botones de la lista sin tocar el resto de la UI
    private void refreshBoardListWidgets() {
        // 1. Eliminar botones antiguos de la pantalla
        for (Button btn : boardButtons) {
            this.removeWidget(btn);
        }
        boardButtons.clear();

        int w = 340; int h = 200;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;
        int listY = y + 115;

        // 2. Crear nuevos botones para cada placa
        for (SerialCraft.BoardInfo info : boardList) {
            if (listY > y + h - 20) break; // Evitar desbordamiento visual

            // Botón ON/OFF
            Button toggleBtn = Button.builder(Component.literal(info.status() ? "§a[ON]" : "§c[OFF]"), b -> {
                // Al hacer clic, enviamos paquete para alternar el estado de esa placa específica
                ClientPlayNetworking.send(new SerialCraft.RemoteTogglePayload(info.pos()));
            }).bounds(x + 270, listY - 3, 50, 16).build();

            this.addRenderableWidget(toggleBtn);
            boardButtons.add(toggleBtn);

            listY += 18; // Espaciado entre filas
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        this.renderTransparentBackground(gui);

        int w = 340; int h = 200;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;

        // 1. Fondo
        gui.fill(x, y, x + w, y + h, BG_COLOR);

        // 2. Título
        gui.drawCenteredString(this.font, "§lCONTROL CENTRAL (LAPTOP)", this.width / 2, y + 10, TITLE_COLOR);

        // Estado
        gui.drawString(this.font, status, x + 20, y + 70, 0xFFFFFFFF, false);

        // Cabecera Lista
        gui.drawString(this.font, "Placas Detectadas:", x + 20, y + 95, SECTION_COLOR, false);

        // Fondo de la lista
        gui.fill(x + 20, y + 110, x + w - 20, y + h - 10, 0x88000000);

        // 3. Renderizar Textos de la Lista (Los botones se renderizan solos)
        int listY = y + 115;
        if (boardList.isEmpty()) {
            gui.drawString(this.font, "§7No se encontraron placas activas.", x + 25, listY, 0xFFAAAAAA, false);
        } else {
            for (SerialCraft.BoardInfo info : boardList) {
                if (listY > y + h - 20) break;

                // Formatear información: ID | Data | Modo
                String modeStr = switch(info.mode()) {
                    case 0 -> "§cSAL";
                    case 1 -> "§aENT";
                    case 2 -> "§9HIB";
                    default -> "?";
                };

                String text = String.format("§b%s §7| §f%s §7| %s", info.id(), info.data(), modeStr);

                gui.drawString(this.font, text, x + 25, listY, 0xFFFFFFFF, false);

                listY += 18;
            }
        }

        super.render(gui, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
}