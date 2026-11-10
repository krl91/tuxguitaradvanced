# Guía de GuitarFX

GuitarFX convierte el ordenador en un amplificador de guitarra con pedales de efectos: noise gate, compresor, overdrive, distorsión, el amplificador (clean, crunch o high gain, con graves, medios y agudos), la pantalla, chorus, eco y reverb. Es el sonido de la ventana *Amplificador y efectos* de TuxGuitarAdvanced, en un pequeño programa propio que no necesita ni TuxGuitarAdvanced ni Java. Existe como aplicación y como plugin para programas de música (CLAP, VST3, y Audio Unit en macOS), en macOS, Windows y Linux.

Inglés: [guide.md](guide.md). Francés: [guide_fr.md](guide_fr.md). Todos los valores internos: [parameters.md](parameters.md) (en inglés).

## Instalar

Los archivos están en la página de la release, junto a los de TuxGuitarAdvanced.

- **macOS** (Apple Silicon, macOS 15 o posterior): abra `GuitarFX-<version>-macos-arm64.pkg`. GuitarFX no está firmado por un desarrollador de Apple, así que macOS lo rechaza la primera vez: abra *Ajustes del Sistema > Privacidad y seguridad* y elija *Abrir igualmente*. El instalador coloca `GuitarFX.app` en *Aplicaciones* y los plugins en `/Library/Audio/Plug-Ins` (`VST3`, `CLAP`, `Components`). La primera vez que se activa el sonido, macOS pide permiso para que GuitarFX use el micrófono: acéptelo.
- **Windows** (64 bits): ejecute `GuitarFX-<version>-windows-x86_64-setup.exe`. Windows puede avisar de que el editor es desconocido: elija *Más información* y luego *Ejecutar de todas formas*. GuitarFX va a `Program Files\GuitarFX`, con una entrada en el menú Inicio; los plugins van a `Common Files\VST3` y `Common Files\CLAP`.
- **Linux** (x86-64):
  - Debian, Ubuntu y derivadas: `sudo apt install ./guitarfx_<version>_amd64.deb`. El programa es `/usr/bin/guitarfx` (también en el menú de aplicaciones); los plugins van a `/usr/lib/vst3` y `/usr/lib/clap`.
  - Cualquier otro sistema, solo para su usuario: descomprima `GuitarFX-<version>-linux-x86_64.tar.gz` y ejecute `GuitarFX/install.sh`. El programa va a `~/.local/bin/guitarfx`, los plugins a `~/.vst3` y `~/.clap`.

Instalar una versión más reciente sobre una anterior conserva sus ajustes y sus sonidos.

## Los dispositivos

Arriba en la ventana:

- **Entrada**: el dispositivo donde está conectada la guitarra (lo mejor es una interfaz de audio), y su **Canal** (1 para la primera entrada).
- **Salida**: donde oye el sonido. **Se aconsejan auriculares.**
- **⟳** vuelve a buscar los dispositivos después de conectar uno.

GuitarFX recuerda los dispositivos por su nombre. Si falta uno en el siguiente inicio, aparece como *(ausente)* y el sonido sigue apagado hasta que lo conecte o elija otro.

**Sonido activado** hace pasar la guitarra por el amplificador. Está apagado en cada inicio, y al activarlo el sonido sube suavemente. **Volumen** ajusta el nivel de salida; el indicador **Entrada** muestra el nivel de la guitarra, y *satura* significa que la entrada está demasiado fuerte: baje la ganancia de entrada de la interfaz o del sistema.

## La cadena

La cadena está en tres pestañas, en el orden en que el sonido las atraviesa:

1. **Antes del amplificador**: los pedales que dan forma a la señal de la guitarra, como el noise gate, el compresor, el overdrive o la distorsión.
2. **Amplificador**: su **Carácter** (clean, crunch o high gain), sus mandos (ganancia, graves, medios, agudos, volumen) y la **Pantalla**, el color del altavoz (apagada, el amplificador suena brillante y crudo, como por una salida de línea).
3. **Después del amplificador**: los pedales que colorean el sonido del amplificador, como el chorus, el eco o la reverb.

Cada pedal tiene una fila: su interruptor (apagado, el sonido lo atraviesa sin cambios), sus mandos, y botones para moverlo antes o después dentro de la pestaña, o quitarlo. **+ Añadir un pedal** añade uno al final de la pestaña; cada pedal puede estar una vez en la cadena. Deje el puntero sobre un mando para leer lo que hace, su rango y su valor por defecto. Cada cambio se oye enseguida, sin clic.

## Los sonidos

La lista **Sonido** contiene los cuatro sonidos listos para usar (Clean, Crunch, Lead, Clean echo) y luego los suyos. *(modificado)* significa que la cadena difiere del sonido guardado.

- **Guardar…** conserva la cadena como uno de sus sonidos, con el nombre que elija.
- **⋯** ofrece **Renombrar…**, **Borrar** (la cadena en uso queda como está), **Restaurar** (un sonido listo para usar vuelve tal como se entregó), **Exportar…** e **Importar…**.

Un sonido puede exportarse a un archivo `.guitarfx` e importarse en GuitarFX o en la ventana *Amplificador y efectos* de TuxGuitarAdvanced, en ambos sentidos. Si ya tiene un sonido con ese nombre, elige reemplazarlo o conservar ambos.

## El retardo

Bajo los dispositivos, GuitarFX muestra el retardo estimado entre lo que toca y el sonido: los bloques de los dispositivos y sus latencias declaradas, sumados. El retardo que oye puede ser algo mayor. Una interfaz de audio con bloques pequeños da el retardo más corto; los auriculares Bluetooth añaden su propio retardo, que no se cuenta y que suele ser demasiado largo para tocar.

## Auriculares y acoples

Con altavoces, y sobre todo con el micrófono integrado del ordenador, el sonido puede volver al micrófono y acoplarse. GuitarFX aconseja auriculares cuando la entrada es un micrófono integrado, y pregunta antes de activar el sonido. Si empieza un acople, apaga el sonido y se lo dice: use auriculares, o aléjese de los altavoces, y vuelva a activar el sonido.

Si el ordenador no da abasto, GuitarFX aligera el sonido paso a paso (reverb apagada, chorus apagado, menos detalle en el amplificador, y luego solo el amplificador y la pantalla) y le avisa en cada paso. Si incluso el amplificador solo es demasiado, apaga el sonido: cierre otros programas y vuelva a activar el sonido.

## La ventana Avanzado

**Avanzado…** muestra todos los valores internos del sonido (103, en 19 categorías): el techo del limitador, los fundidos, la protección contra acoples, el bloque de los dispositivos, los valores internos de cada elemento. Los valores por defecto son los probados; **Valores por defecto** los restablece todos, tras confirmar. Un cambio surte efecto la próxima vez que se activa el sonido. [parameters.md](parameters.md) los lista todos, con sus rangos.

## El plugin

El plugin es la misma cadena, la misma etapa de salida y la misma protección contra acoples, dentro de su programa de música (un DAW): añada **GuitarFX** como efecto en una pista con su guitarra. Su ventana tiene las mismas tres pestañas y la misma lista de sonidos, y el proyecto guarda la cadena.

- Formatos: **CLAP** y **VST3** en los tres sistemas, y **Audio Unit** en macOS.
- Acepta una pista mono o estéreo (una entrada estéreo se mezcla a mono) y sale en estéreo.
- Comunica su retardo al anfitrión (45 muestras), para que lo alinee con las demás pistas.
- No tiene volumen propio: use el fader del anfitrión.
- Si detecta un acople, o si el ordenador no da abasto ni con el amplificador solo, se silencia y lo dice en su ventana. Corrija la causa y luego quite GuitarFX y vuelva a añadirlo, o reabra el proyecto.
- **Sus valores internos vienen de los ajustes del ordenador**: el plugin lee los valores de la ventana *Avanzado* (el archivo `settings.properties` de la carpeta de ajustes de GuitarFX) cada vez que el anfitrión lo inicia. Un proyecto abierto en otro ordenador usa los valores de ese ordenador; la cadena viaja con el proyecto.

## Desinstalar

La desinstalación quita el programa y los plugins; **conserva sus ajustes y sus sonidos**, salvo que lo pida.

- **macOS**: en Terminal, `/Applications/GuitarFX.app/Contents/Resources/guitarfx-uninstall` (añada `--remove-settings` para quitar también `~/Library/Application Support/GuitarFX`). Pide su contraseña.
- **Windows**: *Configuración > Aplicaciones*, GuitarFX, *Desinstalar*. El desinstalador pregunta si quitar sus ajustes y sonidos (`%APPDATA%\GuitarFX`).
- **Linux**: `sudo apt remove guitarfx` para el .deb; `GuitarFX/install.sh --uninstall` para el tar.gz (añada `--remove-settings` para quitar también `~/.config/guitarfx`).
