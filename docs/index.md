---
# Configuración de la portada estilo VitePress
layout: home

hero:
  name: "SerialCraft"
  text: "El Puente entre Mundos"
  tagline: Conecta tu Arduino a Minecraft Java y lleva la electrónica al modo Survival.
  actions:
    - theme: brand
      text: Empezar Guía
      link: /guide
    - theme: alt
      text: Ver en GitHub
      link: https://github.com/leonardoaliaga/serialcraft

features:
  - title: Hardware Real en Survival
    details: Sin comandos mágicos. Craftea la Laptop, construye tus circuitos y conéctalos usando recursos del juego.
  - title: Plug & Play
    details: Compatible con Arduino, ESP32 y cualquier placa Serial. Interfaz gráfica simple para conectar y configurar.
  - title: Código como Redstone
    details: Controla la Redstone con sensores reales o activa LEDs físicos con eventos del juego.
  - title: Multi-Idioma Nativo
    details: El mod detecta automáticamente tu región. Disponible en Inglés y Español (con localizaciones para España, Argentina y México).
---

# ¿Qué es SerialCraft?

**SerialCraft** es un mod para **Minecraft 1.21.10 (Fabric)** que rompe la cuarta pared, permitiendo una comunicación bidireccional en tiempo real entre el juego y dispositivos electrónicos externos.

### Filosofía del Proyecto
Este es un **proyecto de aprendizaje** y experimentación. Nació de la idea de demostrar que la lógica de programación y la lógica de circuitos físicos son dos caras de la misma moneda.

A diferencia de otros mods técnicos, SerialCraft busca integrarse orgánicamente en la experiencia de supervivencia:
* **Accesible:** No necesitas ser ingeniero para encender un LED desde Minecraft.
* **Escalable:** Útil tanto para enseñar conceptos básicos de electrónica como para crear sistemas domóticos complejos controlados desde tu base.

### ¿Cómo funciona?
El mod utiliza la librería `jSerialComm` para abrir un canal directo entre Java y tu puerto USB.
1.  **Entrada:** Arduino envía datos (ej. sensor de luz) -> Minecraft los convierte en señal de Redstone.
2.  **Salida:** Minecraft envía estados de bloque -> Arduino enciende actuadores físicos.

[¡Empieza tu primer circuito ahora!](/guide)

---

## 🌍 Comunidad y Licencia

SerialCraft es un proyecto de **Código Abierto** (Open Source).
Eres libre de estudiar el código, modificarlo o usarlo en tus modpacks.

* **Idiomas Soportados:** English (US), Español (España, Argentina, México).
* **¿Encontraste un error?** [Repórtalo en GitHub](https://github.com/leonardoaliaga/serialcraft/issues).