package com.serialcraft.screen;

import com.serialcraft.client.ui.NavBar; // <--- Importamos tu nueva clase
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class PanelUI extends Screen {

    private final NavBar navBar = new NavBar();

    public PanelUI() {
        super(Component.literal("PanelUI"));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {

        navBar.render(guiGraphics, this.width, this.height);

        super.render(guiGraphics, mouseX, mouseY, delta);
    }
}