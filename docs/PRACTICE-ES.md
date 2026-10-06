# Práctica interactiva, guía del usuario

La práctica interactiva escucha tu guitarra y comprueba, nota por nota y acorde por acorde, que tocas lo que dice la partitura. No toca la partitura por ti y nunca graba audio: mientras funciona solo hay en memoria números derivados del sonido (altura, nivel, qué notas se oyeron).

*English: [PRACTICE.md](PRACTICE.md). Français : [PRACTICE-FR.md](PRACTICE-FR.md).*

## Qué necesitas

* macOS (Apple Silicon), Linux o Windows: los módulos de práctica forman parte de los tres paquetes. El acceso al micrófono y el comportamiento de las entradas de audio dependen del soporte de Java Sound del sistema.
* Un sonido de guitarra en una entrada: el micrófono integrado sirve para una interpretación limpia; una interfaz de audio con la señal directa de la guitarra es mejor. Se recomiendan auriculares si la partitura también suena por los altavoces.
* Una partitura con una pista de guitarra (afinación estándar o cualquier afinación escrita en la partitura).

## Empezar

<img src="images/features/practice-setup.png" width="460" alt="La ventana de preparación de la práctica interactiva">


| Cómo | Qué ocurre |
|---|---|
| **Botón Práctica** <img src="images/features/practice.png" width="18" alt="Práctica"> junto al botón Reproducir | Practica la pista que está bajo el cursor de la partitura, desde el cursor, en cuanto la entrada es utilizable. Un solo clic. |
| *Herramientas > Práctica interactiva* | Abre la ventana de práctica para elegir primero la pista, el inicio y la entrada. |

La primera vez (o después de cambiar la entrada) la ventana comprueba tu **nivel de entrada**: toca una cuerda y mira el medidor. Si dice *demasiado bajo* o *saturación*, acércate o ajusta la ganancia (más abajo).

## Mientras practicas

<img src="images/features/practice-window.png" width="380" alt="La ventana de práctica mientras se espera un acorde">


Una pequeña paleta se queda en pantalla y la nota o el acorde esperado aparece enmarcado en la partitura:

* **marca verde**: se ha oído, la práctica continúa;
* **cruz roja**: se oyó otra nota (solo se muestra para una nota que acaba de tocarse, no para la nota anterior que aún suena);
* no pasa nada con el silencio o el ruido, y una nota que sigue sonando nunca se toma por la siguiente;
* un acorde se acepta cuando sus notas se oyen juntas; si el acorde está incompleto, se nombran las notas que faltan;
* **Saltar** pasa la nota, **Reanudar** continúa tras una pausa (una reproducción, una entrada inutilizable), **Reiniciar** vuelve a empezar el pasaje, **Detener** termina la práctica de inmediato y vuelve a mostrar la ventana de preparación.

La práctica también puede funcionar **por pasajes**: unos pocos compases cada vez, repetidos hasta que alcances la proporción de notas correctas que hayas fijado.

Lo que se verifica es la **altura**. La cuerda y el traste que has usado no se pueden saber a partir del audio. Para cada técnica escrita (hammer-on, pull-off, slide, bend, vibrato), una línea bajo el resultado de altura dice lo que se oyó: *oído*, *no oído*, *incierto* o *todavía no verificable*. Ninguna técnica está verificada todavía (detección medida: vibrato 59 %, bend 82 %, slide 48 % de las notas anotadas, objetivo 85 %), así que por ahora la línea dice *todavía no verificable*. Nunca bloquea el ejercicio: una nota de hammer-on o de pull-off se acepta por su altura.

## Ganancia de entrada, reducción de ruido y ganancia sugerida

Bajo el medidor de nivel de la ventana de práctica, y en el afinador, un control deslizante de **Ganancia de entrada** sube o baja la señal dentro de la aplicación (de -6 a +18 dB), y **Restablecer** vuelve a 0 dB. La ganancia nunca se recuerda: vuelve a 0 dB al cambiar la entrada y en cada inicio. Si la amplificación sube sobre todo el ruido de fondo, o hace que la señal sature, un aviso lo dice y te indica que subas la ganancia de la interfaz de audio.

Mientras tocas, si tus notas son demasiado débiles para un reconocimiento rápido fiable (por debajo de unos -30 dBFS, medido), la ventana de práctica lo dice y te indica que subas la ganancia o toques más cerca; el juego lento puede seguir reconociéndose.

Ajustes (*Reconocimiento de audio* en la configuración): una **puerta de ruido / sustracción del ruido de fondo** opcional y una **ganancia sugerida** tras la comprobación del nivel; ambas se pueden desactivar.

## Ajustes de reconocimiento

<img src="images/features/practice-settings.png" width="560" alt="La página de reconocimiento de audio de la configuración">

<img src="images/features/practice-settings-notes.png" width="560" alt="Los ajustes de notas de la página de reconocimiento de audio">


Cada ajuste tiene un valor recomendado y un botón *Restablecer*. Los que quizá quieras cambiar:

| Ajuste | Efecto |
|---|---|
| Margen de altura aceptado | Cuánto puede alejarse una nota de la nota escrita (en cents) y seguir contando. |
| Tolerancia de agudo por traste (Avanzado) | Las notas pisadas suenan algo agudas, más cuanto más arriba en el mástil: esto añade esos cents por traste, solo del lado agudo. 0 lo desactiva. |
| Certeza mínima | Cuán seguro debe estar el detector de altura antes de juzgar una nota suelta. |
| Tiempo para confirmar una nota | Cuánto tiempo debe sonar una nota antes de aceptarse. |
| Sensibilidad al ataque de la púa | Cuán fuerte debe ser una subida repentina del sonido para contar como un nuevo ataque. |
| Ajustes de acordes | Con qué rigor se comprueban las notas de un acorde y cuánto puede durar un rasgueo. |

## El afinador

<img src="images/features/tuner.png" width="380" alt="El afinador guiado">


*Herramientas > Afinador* abre el afinador. Comparte su detección de altura con el modo de práctica.

* **Dos modos**: *Guiado (6 cuerdas)* te lleva por las cuerdas de una afinación, *Cromático* muestra la nota más cercana a lo que toques.
* **Afinación**: un preajuste, *Personalizada...* (una nota por cuerda) o la afinación de una pista de la partitura abierta («Afinación de la pista N»).
* **La aguja** muestra la **mediana de las medidas fiables del último cuarto de segundo**: un parásito no puede moverla, un hueco breve no la congela, una nota nueva sustituye a la anterior en pocas medidas. El texto de debajo dice qué hacer: «Demasiado grave: 12 cents por debajo de E2. Sube la afinación», «Afinada», «Demasiado aguda... Baja la afinación».
* **Una aguja gris congelada** (mostrada como «Última medida (mantenida)») significa que la cuerda está en silencio o demasiado débil ahora: tócala otra vez.
* **Entrada**: la entrada elegida y su nivel («bueno / demasiado bajo / demasiado alto / saturación»), un botón **Comprobar el nivel de nuevo** y el control de **Ganancia de entrada** (de -6 a +18 dB) con su botón **Restablecer**, como en la ventana de práctica.
* **No durante la práctica**: el afinador se niega a abrirse mientras la ventana de práctica está abierta (comparten el micrófono); cierra antes la ventana de práctica.

## Análisis de la partitura (compases no válidos)

Abrir un archivo de Guitar Pro o MIDI ya no te interrumpe con un cuadro de diálogo sobre compases cuya duración no coincide con el compás indicado. Para ver **todos los problemas de una canción**:

1. *Edición > Mostrar compases no válidos* (o el icono de la barra de herramientas <img src="images/features/analyse-score.png" width="18" alt="Analizar">);
2. la ventana lista los compases no válidos y las notas ligadas de cada pista (pista, compás, voz, demasiado largo / demasiado corto...), con filtros, el estado del compás actual y un botón **Corregir**;
3. no bloquea nada: déjala abierta mientras editas y úsala de nuevo cuando quieras.

## Traza de diagnóstico (para entender un fallo)

Cuando una nota que tocaste bien no se reconoce (o se reconoce una incorrecta), puedes pedir a la aplicación que escriba lo que oyó, **solo en forma de números, sin audio**:

1. cierra la aplicación y arráncala con la propiedad `tuxguitar.practice.trace` que nombre un archivo, por ejemplo desde un terminal en macOS:
   `open -n --env JAVA_TOOL_OPTIONS="-Dtuxguitar.practice.trace=/tmp/practice-trace.csv" /ruta/a/TuxGuitarAdvanced.app`
2. toca el pasaje en la práctica interactiva;
3. abre el CSV: una línea por paso de análisis (tiempo, evento, notas esperadas, altura detectada y certeza, nivel, fuerza del ataque, amplitud de cada nota esperada y de sus vecinas, líneas `ACCEPTED`). Envíalo junto con la descripción de lo que debería haber pasado.

El archivo solo se escribe mientras la propiedad está definida.

## Comprobar la calidad por ti mismo

Un banco de pruebas reproduce grabaciones anotadas a través de la cadena real de práctica e indica, por tipo de prueba, cuántas pasan, fallan o no se ejecutan (y por qué). Lee grabaciones que guardas fuera del repositorio (las licencias de estos conjuntos de datos prohíben redistribuirlos):

```sh
cd desktop/build-scripts/tuxguitar-macosx-swt-cocoa
mvn test -pl ../../TuxGuitar-practice -am -Dgroups=dataset -DexcludedGroups= \
  -Dtest=DatasetBenchmarkTest -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtuxguitar.datasets=/ruta/a/tus/datasets -Dtuxguitar.datasets.sample=70
```

El informe se escribe en `desktop/TuxGuitar-practice/target/dataset-benchmark/report.txt` (resumen) y `outcomes.csv` (cada prueba). Las pruebas que no se pueden ejecutar se cuentan con su motivo (por ejemplo: una octava no se distingue de un parcial de la nota tocada; la nota está fuera del rango de una guitarra de seis cuerdas).

Dos pruebas de reproducción se ejecutan en la compilación normal cuando sus grabaciones están presentes en `tests/1`, y se omiten en caso contrario.

## Calidad medida y límites

En un banco de pruebas de 1 208 grabaciones anotadas de guitarras reales (limpias y con 11 efectos), 6 178 pruebas:

* **notas sueltas reconocidas**: 99,8 % (efectos) a 100 % (limpias);
* **nota un semitono o un tono distinta de la tocada aceptada**: ninguna en más de 2 700 pruebas;
* **intervalos y tríadas con efectos reconocidos**: 87 %; **acordes de guitarras limpias**: 95 %;
* **acordes con una nota ausente rechazados**: 96 a 98 %;
* **notas tocadas una tras otra, con la anterior aún sonando**: 97 a 100 % reconocidas;
* **retraso** entre tocar una nota y su reconocimiento: unos 0,1 s (mediana 105 ms, 90 % en menos de 170 ms) en una grabación real; la ventana de preparación de la práctica lo indica.

En un **conjunto de datos independiente** (GuitarSet, 180 grabaciones de acompañamiento rítmico con guitarra acústica, 702 acordes rasgueados cuya anotación era fiable, con las cuerdas anteriores aún sonando), el resultado es mucho más bajo:

* **acordes completos reconocidos**: 60 % (seis canales de pastilla sumados), 46 % con el micrófono;
* **acordes a los que falta una nota aceptados por error**: 13 % (29 % cuando la nota que falta es el bajo, 5 % una nota interior, 3 % la nota aguda);
* ningún ajuste corrige ambas cosas a la vez: uno más estricto rechaza más acordes incompletos y también más acordes completos (detalles y tabla en las notas de investigación de la especificación, R10).

Límites, medidos y no ocultos:

* **notas muy juntas**: se siguen unos 0,3 s entre dos notas tocadas con púa (91 % de las series de tres notas a 0,35 s, 67 % a 0,25 s); más rápido, solo se reconoce la primera nota de una serie y la práctica te espera, sin marcar nunca una nota incorrecta (0 de 92 pruebas). La ventana de práctica lo indica cuando ocurre;

* los acordes cuya nota más grave es G2 a veces se aceptan un tono más alto (los dos acordes están a un solo paso de frecuencia a esa altura);
* los acordes de séptima de cuatro notas y algunos acordes abiertos (La mayor, Mi mayor) se reconocen con menos frecuencia;
* una octava, o una nota que es un armónico de una nota grave del acorde, no se puede verificar por separado de ella;
* los ajustes se afinaron con las grabaciones anteriores; otra guitarra u otro micrófono pueden pedir otra ganancia u otra sensibilidad.
