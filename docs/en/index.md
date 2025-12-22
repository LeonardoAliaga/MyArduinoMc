---
layout: home

hero:
  name: "SerialCraft"
  text: "The Bridge Between Worlds"
  tagline: Connect your Arduino to Minecraft Java and bring electronics into Survival mode.
  actions:
    - theme: brand
      text: Start Guide
      link: /en/guide
    - theme: alt
      text: View on GitHub
      link: https://github.com/leonardoaliaga/serialcraft

features:
  - title: Real Hardware in Survival
    details: No magic commands. Craft the Laptop, build your circuits, and connect them using in-game resources.
  - title: Plug & Play
    details: Compatible with Arduino, ESP32, and any Serial board. Simple GUI to connect and configure.
  - title: Code as Redstone
    details: Control Redstone with real sensors or trigger physical LEDs with in-game events.
  - title: Native Multi-Language
    details: The mod automatically detects your region. Available in English and Spanish (Spain, Argentina, Mexico).
---

# What is SerialCraft?

**SerialCraft** is a mod for **Minecraft 1.21.10 (Fabric)** that breaks the fourth wall, allowing real-time bidirectional communication between the game and external electronic devices.

### Project Philosophy
This is a **learning project**. It was born from the idea of demonstrating that programming logic and physical circuit logic are two sides of the same coin.

Unlike other technical mods, SerialCraft aims to integrate organically into the survival experience:
* **Accessible:** You don't need to be an engineer to turn on an LED from Minecraft.
* **Scalable:** Useful for teaching basic electronics or creating complex smart home systems controlled from your base.

### How does it work?
The mod uses the `jSerialComm` library to open a direct channel between Java and your USB port.
1.  **Input:** Arduino sends data (e.g., light sensor) -> Minecraft converts it to Redstone signal.
2.  **Output:** Minecraft sends block states -> Arduino turns on physical actuators.

[Start your first circuit now!](/en/guide)

---

## 🌍 Community & License

SerialCraft is an **Open Source** project.
You are free to study the code, modify it, or use it in your modpacks.

* **Supported Languages:** English (US), Spanish (Spain, Argentina, Mexico).
* **Found a bug?** [Report it on GitHub](https://github.com/leonardoaliaga/serialcraft/issues).