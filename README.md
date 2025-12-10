# 🔌 SerialCraft: Puente entre Mundos

### Lleva tu hardware del mundo real a tu mundo Survival.

**SerialCraft** es un mod experimental y educativo que rompe la cuarta pared, permitiendo una comunicación bidireccional en tiempo real entre un **Arduino** (o cualquier dispositivo Serial) y **Minecraft** utilizando Java.

Este proyecto no es solo una demostración técnica; está diseñado para encajar naturalmente en tu experiencia **Survival**, convirtiendo la electrónica externa en una parte funcional de tu progresión en el juego.

---

## 🛠️ Características Principales

### 💻 Nuevo Bloque: La Laptop
El cerebro de la operación. En SerialCraft, la conexión no aparece por arte de magia; tienes que construirla.
* **Integración en Survival:** La Laptop es un bloque crafteable. Debes reunir los recursos necesarios para construirla, asegurando que se ajuste al equilibrio de una partida de supervivencia.
* **La Interfaz:** Al hacer clic derecho en la Laptop, se abre una GUI que te permite seleccionar y conectar el Puerto COM (Puerto Serial) de tu dispositivo.

### ⚡ Físico a Digital (Entrada)
Controla tu mundo usando componentes reales.
* Conecta **botones, sensores o interruptores** físicos a tu Arduino.
* El mod lee estas señales Seriales y las traduce en acciones dentro del juego o señales de Redstone.
* *Ejemplo:* Acciona un interruptor real en tu escritorio para abrir la puerta de hierro de tu base.

### 🔄 Digital a Físico (Salida) *[En Desarrollo]*
* Envía datos del juego (como estado de salud, niveles de luz o estados de bloques) hacia tu Arduino para encender LEDs, zumbadores o motores en el mundo real.

---

## 🧠 El Concepto: Lógica y Circuitos

Este proyecto nació de un viaje personal: unir el juego que definió mi infancia con mi pasión por la electrónica.

Al desarrollar **SerialCraft**, el objetivo fue demostrar que la lógica utilizada en la programación es sorprendentemente similar al diseño de circuitos eléctricos físicos:

* **Código como Cableado:** La **lógica condicional** (`if/else`) escrita en Java actúa exactamente igual que los interruptores físicos o las compuertas lógicas en una protoboard.
* **Datos como Corriente:** El flujo de información a través del puerto Serie imita el flujo de la corriente eléctrica; si la lógica no está "cerrada", la señal no llega a su destino.

Este mod es un tributo a esa conexión: usar código para cerrar el circuito entre el mundo virtual de bloques y el mundo físico.

---

## ⚙️ Instalación y Uso

### Requisitos
* **Minecraft:** 1.21.10
* **Loader:** Fabric
* **Dependencia:** [Fabric API](https://modrinth.com/mod/fabric-api)
* **Hardware:** Una placa Arduino (Uno, Nano, Mega) o cualquier microcontrolador capaz de comunicación Serial.

### Primeros Pasos
1.  Descarga e instala el mod y la Fabric API.
2.  Conecta tu Arduino a tu PC vía USB.
3.  Inicia Minecraft y entra a tu mundo Survival.
4.  **Craftea la Laptop** (Receta visible vía REI/JEI).
5.  Coloca la Laptop, haz clic derecho y selecciona el Puerto de tu Arduino.
6.  ¡Empieza a enviar señales!

---

## 📜 Licencia
Este proyecto es de código abierto. ¡Siéntete libre de explorar el código para aprender más sobre cómo Java maneja la comunicación Serial!