package com.serialcraft.block;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

public enum IOSide implements StringRepresentable {
    NONE("none"),
    INPUT("input"),   // Recibir energía (Verde/On)
    OUTPUT("output"); // Enviar energía (Rojo/Off)

    private final String name;

    IOSide(String name) {
        this.name = name;
    }

    @Override
    public @NotNull String getSerializedName() {
        return this.name;
    }
}