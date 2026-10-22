# TuxGuitarAdvanced

<p align="center"><img src="../specs/008-brand-identity/assets/tuxguitaradvanced-logo-primary.png" width="260" alt="TuxGuitarAdvanced"></p>

<p align="center"><sub>El logotipo es un rediseño asistido por IA, elegido por el propietario del producto. Los avisos y la licencia de TuxGuitar original no cambian, y no se reclama la propiedad de marcas de terceros.</sub></p>

*[English](../README.md) · [Français](README-FR.md) · Español*

### 🎸 Tu guitarra es el mando. Tu partitura es el juego.

**Abre una tablatura. Toca las notas que vuelan hacia tu mástil. Te juzgan por la altura *y* el ritmo, en tiempo real, con tu guitarra de verdad.**

<p align="center"><img src="images/arcade-flames.png" width="760" alt="Modo Arcade con las llamas de una racha de 12 notas a tiempo, un hammer-on/pull-off y un bend"></p>

<p align="center"><em>Imagen dibujada por el motor de render de Arcade de la aplicación a partir de una escena de referencia (no es una captura de una ventana): una racha de 12 notas a tiempo, las llamas a ambos lados, un hammer-on/pull-off y un bend.</em></p>

TuxGuitarAdvanced es un editor de tablaturas libre y de código abierto que **escucha tu guitarra** (basta un micrófono o una interfaz de audio). Abre cualquier partitura (Guitar Pro, MIDI, TuxGuitar...), elige una pista y toca con el resto de la banda. Sin suscripción, sin catálogo, sin cuenta: **tus canciones, tu guitarra, tu máquina**.

> 🙏 **Construido sobre TuxGuitar, que ya es excelente.** TuxGuitarAdvanced es un **fork de [TuxGuitar](https://www.tuxguitar.app/)**, el gran editor y reproductor de tablaturas multipista libre. Todo lo que hace TuxGuitar sigue ahí, intacto: edición, reproducción, importación y exportación Guitar Pro / MIDI / MusicXML / PDF, muchos idiomas. **Solo añadimos cosas nuevas encima**: los modos de práctica (Esperar, Seguir el tempo, Arcade) y un afinador más estable. El mérito del editor es de los autores y colaboradores de TuxGuitar ([helge17](https://github.com/helge17/tuxguitar) y muchos más); si solo quieres un editor magnífico, [usa TuxGuitar](https://www.tuxguitar.app/), se lo merece. (Algunas capturas están en inglés o francés: la interfaz existe en español, francés e inglés.)

> ⚠️ **Lee esto antes de fiarte de ella.** TuxGuitarAdvanced es joven. Las pruebas automáticas son numerosas y las mediciones sobre grabaciones son buenas, **pero la validación en condiciones reales es pequeña**: se ha tocado sobre todo en el Mac y la guitarra del autor, **los paquetes de Windows y Linux nunca los ha ejecutado nadie**, y se han probado muy pocos micrófonos e interfaces de audio. Los modos de práctica siguen marcados como alfa más abajo. Funciona para nosotros y todavía está algo áspera en algunos puntos: por eso necesitamos jugadores como tú, en **tu** máquina y con **tu** interfaz. [Lo que está validado y lo que no](#dónde-está-el-proyecto) · [cómo ayudar](#únete-a-la-banda-ayúdanos-a-subir-de-nivel).

## Elige tu modo de juego

| | Modo | La idea |
|---|---|---|
| 🎯 | **Esperar la nota correcta** | La partitura **te espera**. La siguiente nota aparece enmarcada; tócala y se enciende una marca verde. Sin presión, ideal para aprender una pieza nueva. |
| 🥁 | **Seguir el tempo** | La banda arranca (cuenta atrás, bajo, batería, todo menos tu pista) y tocas **a tiempo**. Cada nota es *a tiempo, pronto, tarde, errónea o fallada*. Sube el tempo poco a poco del 20 % al 150 %. |
| 🕹️ | **Arcade** (alfa) | La misma banda, pero como **juego**: rectángulos redondeados vuelan desde el horizonte en línea recta, un compás antes, hasta su traste en el mástil, del color de su cuerda; los acordes llegan como un panel y todo aterriza al pie del mástil a la vez, tras una cuenta atrás durante la cual las notas vienen del fondo. Un clic en el botón de color de la barra de transporte lo lanza de inmediato; la ventana de resultado también aparece si paras antes del final. Persigue rachas, lee las insignias de veredicto, supera tu puntuación. |

<p align="center"><img src="images/manual/arcade-chord.png" width="700" alt="Modo Arcade (interfaz en francés): llega un acorde como un panel translúcido con su nombre y un rectángulo por nota"></p>

## También es un editor de verdad (y un afinador de verdad)

Bajo el juego está el editor **TuxGuitar** completo: escribe pistas de guitarra, bajo y batería, tablatura y pentagrama, acordes y efectos, escúchalos, importa y exporta Guitar Pro, MIDI, MusicXML, PDF... El **[ejemplo completo](MANUAL.md#part-c-a-complete-example-step-by-step)** (en inglés y francés) te lleva de una página vacía a una canción de tres pistas y a los tres modos.

<p align="center"><img src="images/manual/main-window.png" width="760" alt="El editor TuxGuitarAdvanced (interfaz en francés) con una parte de guitarra en pentagrama y tablatura"></p>

## Empezar en dos minutos

1. **Descarga** la última versión en [Releases](https://github.com/krl91/tuxguitaradvanced/releases) (macOS Apple Silicon hoy).
2. Abre una partitura y **Herramientas > Práctica interactiva** (o el botón ♪ junto a Reproducir).
3. Elige tu pista, tu entrada (comprueba el nivel punteando una cuerda) y un modo. **Empieza.**

## Únete a la banda: ayúdanos a subir de nivel

El proyecto es joven y toda ayuda cuenta. No hace falta ser programador:

* 🎸 **Toca y cuéntanos.** Tu guitarra, tu micrófono, tu interfaz, tu máquina: **más guitarras es la mejor prueba**. Abre una [issue](https://github.com/krl91/tuxguitaradvanced/issues).
* 🎚️ **Vence a los jefes.** Temporización más fiable bajo carga, notas de bajo por debajo de sol1, validar hammer-on / slide / vibrato con toque real, paquetes de Linux y Windows.
* 🌍 **Traduce.** Interfaz en inglés, francés y español; más idiomas están a un fichero de texto.
* 🎨 **Embellece.** Arcade usa formas simples: colores, temas, efectos y sonidos están abiertos.
* 🧪 **Escribe una prueba o corrige un error.** Java, Maven, más de 700 pruebas automáticas ([INSTALL.md](../INSTALL.md)). Cada error recibe primero una prueba que lo reproduce.
* 📣 **Comparte.** ¿Conoces a un guitarrista al que le gustaría tocar sus tablaturas como un juego?

## Dónde está el proyecto

Disponible hoy (versión 0.6.0): la práctica nota a nota y acorde a acorde con tu guitarra real (se comprueba la altura), los bends, las liberaciones y los pre-bends seguidos a lo largo de su recorrido escrito, la llegada de un hammer-on, un pull-off o un slide aceptada sin una nueva púa, un afinador estable, la ganancia de entrada y la reducción de ruido. **Nuevo, en alfa:** el **modo de seguimiento del tempo** (la partitura suena a su tempo con las demás pistas de acompañamiento, y cada nota se juzga en el tiempo: pronto, tarde, fallada) y **Arcade**, la misma partida como juego en el que las notas vienen hacia ti sobre un mástil, un compás antes. **Aún no disponible:** un veredicto *oído / no oído* para cada técnica (los detectores aún no están validados con suficientes grabaciones reales, así que la línea de técnica sigue diciendo «aún no se puede comprobar»). La guía de uso enumera lo que está medido y lo que no: [English](PRACTICE.md) · [Français](PRACTICE-FR.md) · [Español](PRACTICE-ES.md).


**Lo que está validado y lo que no.** Las pruebas automáticas son numerosas (más de 1.500) y el reconocimiento se midió con muchas grabaciones: eso dice cómo se comporta **con sonidos grabados**, no en tu instalación. En directo la validación es **pequeña**: tocada sobre todo en el Mac y la guitarra del autor (las mediciones de la calibración y de Arcade se hicieron con el micrófono integrado de ese Mac); **los paquetes de Windows y Linux nunca se han ejecutado** (están construidos y la versión que llevan está comprobada); muy pocas interfaces de audio y micrófonos probados; todavía no se ha medido ninguna ganancia de la calibración. Detalle en inglés: [README](../README.md#what-is-validated-and-what-is-not). Pruébala en tu máquina y cuéntanos qué pasó, para bien o para mal.

**Manual de usuario** (edición y creación de partituras, y todo el Practice, con capturas de pantalla; en inglés y francés): [English](MANUAL.md) · [Français](MANUAL-FR.md).

El proyecto es una versión avanzada de [TuxGuitar](https://www.tuxguitar.app/), el editor libre de tablaturas multipista. Consulta el [README principal](../README.md) (en inglés) para las funciones, las mediciones con grabaciones reales y la instalación.
