# SerialCraft 📦 v0.3.6

> **El Puente Entre Mundos**
>
> Conecta tu Arduino a Minecraft Java (Fabric 1.21.10) y lleva la electrónica al modo Supervivencia.

[![Fabric](https://img.shields.io/badge/Loader-Fabric-bea67e?style=for-the-badge)](https://fabricmc.net/)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.10-2d8528?style=for-the-badge)](https://www.minecraft.net/)
[![License](https://img.shields.io/badge/License-CC0_1.0-blue?style=for-the-badge)](LICENSE)

---

## 📚 Documentación

**Guía Completa y Wiki:** [https://leonardoaliaga.github.io/SerialCraft/](https://leonardoaliaga.github.io/SerialCraft/)

---

## ¿Qué es SerialCraft?

**SerialCraft** es un mod que rompe la cuarta pared, permitiendo una comunicación bidireccional en tiempo real entre el juego y dispositivos electrónicos externos (Arduino, ESP32, Raspberry Pi Pico, etc.).

A diferencia de otros mods técnicos, SerialCraft está diseñado para integrarse orgánicamente en la **experiencia Survival**. Sin comandos mágicos: construyes el hardware, configuras las conexiones y creas la lógica.

## ✨ Características Principales

* **🔌 Plug & Play:** Compatible con cualquier placa Serial/USB.
* **💻 Interfaz In-Game (UI):** Interfaz gráfica sencilla para seleccionar puertos y velocidades sin editar archivos de configuración.
* **⚡ Bidireccional:**
    * **Entrada (Físico -> Juego):** Usa sensores reales (luz, sonido, botones) para activar Redstone.
    * **Salida (Juego -> Físico):** Usa Redstone para encender LEDs reales, motores o zumbadores.
* **🧠 Compuertas Lógicas:** Los bloques incluyen lógica interna (AND, OR, XOR) para seguridad avanzada de circuitos.

## 📦 Bloques y Recetas
![Models](https://cdn.modrinth.com/data/cached_images/4aea9efd4686b3adf8ea97550df78b0240142c49.png)

### 1. Bloque Conector (Laptop)
El cerebro de la operación. Maneja la conexión USB con tu dispositivo del mundo real.

* **Uso:** Click derecho para abrir la UI. Selecciona tu **Puerto** y **Baud Rate** (debe coincidir con tu código de Arduino, ej. 9600).
* **Ajustes:** Puedes ajustar la "Velocidad de Lectura" (Rápida, Normal, Baja) para evitar lag si tu placa envía demasiados datos.

![UI Connector Block](https://cdn.modrinth.com/data/cached_images/2a06adbdf536890ecd23076067815f49d6f7f704_0.webp)

#### Crafting Recipe
![Connector Block Recipe](https://cdn.modrinth.com/data/cached_images/0796082c1617ad4cd6d1c4f3beb989fc89878192_0.webp)

---

### 2. Bloque IO (Arduino IO)
El puente físico. Este bloque se conecta a la Laptop y actúa como intermediario con la Redstone.

* **Sistema de Pines:**
    * **Click Derecho** en un conector lateral: Configura el pin como **IN (Verde)**. Lee Redstone del mundo.
    * **Shift + Click Derecho** en un conector lateral: Configura el pin como **OUT (Rojo)**. Emite Redstone al mundo.
* **Configuración:** Click derecho en el bloque para definir el `Target ID` (ej. `LED_1` o `SENSOR_A`) y el tipo de señal (Analógica o Digital).

![IOBlock UI](https://cdn.modrinth.com/data/cached_images/151ab59e0b022613135ae530b89378e60e3b8231_0.webp)
![IOBlock Model](https://cdn.modrinth.com/data/cached_images/c20ed9d2c7fe7a3a5a28704394c6a64a6cc2839b.png)

#### Crafting Recipe
![IOBlock Recipe](https://cdn.modrinth.com/data/cached_images/07d93e2ac7612058dcfb25f2161efcc182ec78a8_0.webp)

---

## 🚀 Inicio Rápido (Protocolo)

La comunicación se basa en texto con el formato `CLAVE:VALOR` terminando con un salto de línea (`\n`).

**Ejemplo en Arduino:**
```cpp
void setup() {
  Serial.begin(9600); // Coincide con la configuración de la Laptop
}

void loop() {
  // Enviar datos a Minecraft (ID de bloque "btn_1", Valor 15)
  Serial.println("btn_1:15"); 
  delay(50);
}
```

## 🤝 Comunidad y Soporte

* **Errores (Issues):** [Reportar Aquí](https://github.com/leonardoaliaga/serialcraft/issues)
* **Autor:** Leonardo Aliaga
