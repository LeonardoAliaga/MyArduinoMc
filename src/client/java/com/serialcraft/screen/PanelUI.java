package com.serialcraft.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class PanelUI extends Screen {

    // Textura
    private static final ResourceLocation GATO_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    "serialcraft",
                    "textures/gui/gato.png"
            );

    // Tamaño real de la imagen
    private static final int TEX_WIDTH = 434;
    private static final int TEX_HEIGHT = 434;

    public PanelUI() {
        super(Component.literal("PanelUI"));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {

        // Centramos la imagen
        int x = (this.width - TEX_WIDTH) / 2;
        int y = (this.height - TEX_HEIGHT) / 2;

        // Render de la textura (API nueva)
        guiGraphics.blit(
                RenderPipelines.GUI_TEXTURED,
                GATO_TEXTURE,
                x, y,
                0, 0,
                TEX_WIDTH, TEX_HEIGHT,
                TEX_WIDTH, TEX_HEIGHT
        );

        super.render(guiGraphics, mouseX, mouseY, delta);
    }
}
