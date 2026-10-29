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

**La ventana de inicio (antes de cada inicio).** Cada vez que empieza un modo — el botón Empezar, el botón Práctica de la barra de transporte, el botón Arcade de color — una ventana pequeña pregunta primero *¿Está afinada tu guitarra?* *No, abrir el afinador* abre el afinador y el modo no empieza; al cerrar el afinador vuelve la pregunta. *Sí, afinada* pide un acorde de prueba: rasguea un acorde y déjalo sonar. La ventana juzga el nivel de los 0,3 segundos siguientes: si más del 0,5 % de las muestras llega al máximo, dice que la señal satura (baja el volumen del adaptador o de la guitarra); una señal demasiado fuerte o demasiado débil también se indica, con lo que hay que hacer; un buen nivel inicia el modo al instante. Un mal nivel bloquea el inicio: *Comprobar de nuevo* juzga un nuevo acorde y *Continuar de todos modos* sigue **solo para este inicio** (también se ofrece tras 10 segundos sin acorde). En **Seguir el tempo** y en **Arcade**, cuando el ritmo de tu entrada y tu salida nunca se calibró, la ventana lo dice después, con **Calibrar ahora** (los 8 clics de la pestaña Modo, unos 10 segundos, dentro de la ventana; *Cancelar* los detiene y vuelven los dos botones; el retardo encontrado se guarda como desde la pestaña Modo) y **Seguir sin calibrar** (no se vuelve a preguntar para esta entrada y esta salida hasta que TuxGuitar arranque de nuevo). Otra entrada, salida o formato no queda calibrado por el primero. Reiniciar una partida o *volver a jugar* no vuelve a preguntar. La ventana nunca cambia la ganancia de la interfaz ni del sistema. El umbral del 0,5 % se calibró con las grabaciones del propietario con un adaptador Rocksmith y con las 180 grabaciones de micrófono de GuitarSet (ninguna se señala); los consejos para Windows y Linux usan el texto de macOS y no se probaron allí.

## Practicar con la banda: Seguir el tempo y Arcade (alfa)

En la pestaña **Modo** de la ventana de práctica eliges cómo se trabaja la partitura:

| Modo | Qué pasa |
|---|---|
| **Esperar la nota correcta** | La partitura te espera (el modo descrito arriba). |
| **Seguir el tempo** | La partitura suena a su tempo, tras una cuenta atrás. La pista que practicas queda muda y las demás suenan de acompañamiento. Cada nota o acorde se juzga en el tiempo: a tiempo, pronto, tarde, errónea, fallada. |
| **Arcade** | La misma partida, presentada como un juego: las notas vienen hacia ti sobre un mástil y se juzgan al pasar. El resultado se muestra al final. |

Los dos modos cronometrados comparten el **tempo** (20 % a 150 % del tempo escrito) y la **calibración del ritmo** de tu cadena de audio (calibra con clics, o escribe el retraso a mano: sin ella las notas pueden juzgarse algo pronto o tarde, y la ventana indica *Precisión rítmica reducida*). **Arcade** juzga el momento con la **ventana de tiempo** (un control deslizante de 40 a 200 ms a cada lado a 120 pulsos por minuto, **100 ms** por defecto; crece y se reduce con el tempo, es un único ajuste guardado que también aparece en la ventana de Configuración, y una elección antigua se convierte: estricta 50 ms, flexible 125 ms, normal el nuevo valor por defecto; una ventana ancha puede hacer confundir una nota con la que aún suena en un pasaje denso); **Seguir el tempo** lo juzga con el **tiempo para tocar la nota** (abajo).

### Seguir el tempo: el tiempo para tocar la nota y la permisividad

**Tiempo para tocar la nota** (pestaña Modo, solo Seguir el tempo; de 100 a 1200 ms, **600 ms** por defecto desde la primera prueba del propietario; un único ajuste guardado, que también aparece en la ventana de Configuración, sección *Pasajes*): desde que una nota se pone roja, cuánto tiempo puedes tocarla todavía y que se acepte. Hasta la mitad de ese tiempo cuenta *a tiempo*, después *tarde*; también puede llegar hasta 50 ms antes de su rojo (*pronto*). Cuando las notas están juntas, el tiempo se detiene 20 ms antes de que la nota siguiente se ponga roja y el adelanto de la nota siguiente se reduce a lo que queda, para que una nota nunca cuente por dos: con notas a 200 ms una de otra, una nota puede tocarse hasta 180 ms después de su rojo, y la siguiente desde 20 ms antes del suyo. Un cambio hecho durante una partida vale para la partida siguiente.

**Permisividad** (pestaña Modo, solo Seguir el tempo): *Estricta* (el reconocimiento de notas de los otros modos de práctica), *Permisiva* (por defecto) o *Muy permisiva*: una nota un poco desafinada o no del todo clara se acepta más fácilmente; una nota equivocada se sigue rechazando, y el silencio nunca se acepta. Solo relaja, nunca endurece, lo que tú o un perfil de calibración ya ajustasteis; se guarda aparte de la permisividad de Arcade; el resumen y el registro de una partida nombran la posición y el tiempo para tocar. **Medido hasta ahora:** en la grabación de referencia las tres posiciones dan los mismos resultados (35 de 38 notas correctas aceptadas, ninguna de las 98 pruebas de nota equivocada), y ampliar la aceptación en cents hace perder una nota correcta, así que no se amplía. Los valores se eligieron después lo más permisivos posible sin perder ninguna nota correcta (spec 016). En la primera partida grabada del propietario, dos rechazos de cada tres eran un ataque nunca oído, algo que ninguna permisividad corrige: toca lo bastante fuerte para que el indicador de nivel se mueva.

### Seguir el tempo: por qué se rechazó una nota, y el registro de una partida

En **Seguir el tempo**, la paleta dice bajo cada nota rechazada por qué, de una lista fija: *no se oyó ningún ataque*, *se oyó un sonido, pero no con claridad suficiente para reconocer la nota* (con su certeza; nunca cuenta como nota falsa), *se oyó X en lugar de Y*, *nota correcta, N ms demasiado pronto* o *demasiado tarde*, *no juzgada*. El resumen final cuenta cada motivo.

Dos casillas de la pestaña **Modo**, visibles solo en Seguir y desmarcadas cada vez que TuxGuitar arranca, guardan un registro de las partidas siguientes: **Registrar esta partida** escribe, al final de una partida (completa, detenida, o terminada al reproducir la partitura), un archivo CSV `follow-<fecha>-<hora>.csv` en la carpeta `tuxguitar-follow-takes` de tu carpeta personal: por nota, lo esperado, lo oído, cuándo, con qué certeza, el veredicto y su motivo, y los ajustes de la partida. **Guardar también una copia del sonido**, ofrecida solo con el registro, añade un WAV de lo que oyó el reconocimiento (10 minutos como máximo) con el mismo nombre. No se escribe nada para una partida detenida durante su cuenta atrás, nada sin la casilla, y nada sale de tu ordenador: eres tú quien envía el archivo si quieres que se examine. Detalles: `specs/016-follow-mode-leniency/`.

### Arcade

Elige **Arcade** en la pestaña Modo, la pista que tocas en la pestaña Pista (cualquier pista con altura, bajo incluido) y pulsa *Iniciar Arcade*. La ventana de práctica se oculta mientras corre la ventana del juego.

<img src="images/features/practice-modes.png" width="480" alt="Elección del modo">

<img src="images/features/practice-mode-arcade.png" width="420" alt="Pestaña Modo con Arcade elegido, con el tamaño de los rectángulos y la inclinación del plano">

<img src="images/features/arcade-notes.png" width="640" alt="Una partida Arcade: rectángulos que vienen del horizonte en línea recta, uno se posa en su objetivo en el mástil">

* **Se ve un compás por adelantado** (un segundo como mínimo), sea cual sea el tempo. Una nota es un **rectángulo redondeado** del color de su cuerda; llega **en línea recta** desde el horizonte hasta el centro de su traste al pie del mástil, a través del mástil transparente, y tiene el ancho de la casilla de su traste. En el momento de tocar está exactamente sobre su objetivo en el mástil.
* **Los acordes** son un panel translúcido desde el primer hasta el último traste del acorde, con un rectángulo por nota y el nombre sobre la esquina izquierda.
* **Los veredictos** (a tiempo, pronto, tarde, erróneo, incompleto, fallado) aparecen como insignia una vez juzgada la nota. La parte superior muestra tu racha, el tempo y lo que viene.
* **Reiniciar, pausa, reanudar y parar** están en la ventana. *Reiniciar* vuelve a empezar desde el principio de la pieza (o del pasaje) sin salir del juego. Durante la cuenta atrás las notas llegan desde el fondo mientras oyes los clics; tras una pausa, *Reanudar* hace otra cuenta atrás desde donde estabas. Reproducir la partitura desde la ventana principal detiene la partida.
* **Ajustes de la imagen** (pestaña Modo): el **tamaño de los rectángulos que llegan** (0 % a 150 % del tamaño del objetivo) y la **inclinación del plano** (escala en el horizonte 20 % a 100 %, 35 % por defecto; altura del horizonte 0 % a 40 %, 18 % por defecto).
* **Llamas de escenario** (pestaña Modo): llamas detrás de la imagen que **crecen cuando tocas mejor** (su altura sigue tu parte de la puntuación máxima; brasas hasta que se juzgan cuatro eventos). **Desactivadas**, **Discretas** (por defecto) o **Plenas**; se leen al empezar una partida. Solo decoración: nunca cambian el juicio ni la puntuación, nunca cubren un texto, y se quedan quietas cuando macOS está en *Reducir movimiento* (solo macOS en esta versión). Detalles en el [manual](MANUAL.md#b6-arcade-mode-alpha).
* **Las técnicas escritas en una nota sola se dibujan** sobre la nota mientras llega y de nuevo en el mástil, en amarillo: bends (flecha, valor, rectángulo fantasma en el traste alcanzado), hammer-on y pull-off (arco, H o P, fantasma en la nota siguiente de la cuerda), slide (raíl de puntos, S), vibrato (onda), palm mute y tapping (píldoras P.M. y T), trémolo (tres trazos); un armónico es una barra que dice *Armónico ◇*. **Lo que se comprueba: el tempo y la altura** (la curva escrita de un bend, la nota de llegada de un hammer-on, pull-off o slide). **Ninguna técnica se anuncia como detectada o no detectada**: los detectores aún no están validados (bend 82 %, vibrato 59 %, slide 48 % de las notas anotadas, 85 % requerido; ninguno para hammer-on y pull-off; ninguno para palm mute, tapping y trémolo). Detalles e imágenes en el [manual](MANUAL.md#b6-arcade-mode-alpha).
* Al final (y también cuando detienes el juego antes del final), el **resultado** da la puntuación, la parte del máximo, la mejor racha y el recuento de cada juicio; puedes **repetir desde el principio o desde un compás**.
* **Entradas:** el botón *Actualizar* de la pestaña Entrada vuelve a listar las entradas de audio; *Recordar como predeterminada* guarda la entrada elegida en la configuración (se selecciona cuando está y de nuevo cuando vuelve tras una ausencia).
* **Arranque directo:** el **botón Arcade de color** de la barra de transporte (justo después del botón ♪ Práctica) lanza Arcade de inmediato en la pista bajo el cursor, con la entrada actual, sin la ventana de preparación.

<img src="images/features/arcade-chord.png" width="640" alt="Un acorde: su diagrama con nombre, las notas que llegan y los objetivos en el mástil">

<img src="images/features/arcade-result.png" width="300" alt="Ventana de resultado al final de la partida">

<img src="images/features/practice-input-refresh.png" width="420" alt="La pestaña Entrada con el botón Actualizar">

*Capturas de la aplicación, mostradas con su interfaz en francés.*

### Niveles de Arcade y permisividad

**Cinco niveles** hacen tocable una pieza difícil. La ventana de inicio de Arcade pregunta el nivel cada vez (el nivel 1 la primera vez, luego el último elegido): el **nivel 1** conserva cerca del 30 % de las notas y ningún acorde; el **nivel 2** el 30 % de las notas y el 30 % de los acordes; el **nivel 3** el 50 % y el 50 %; el **nivel 4** el 75 % y el 75 %; el **nivel 5** toda la pieza, como antes. El primer sonido de cada clase siempre se conserva y los demás se reparten con regularidad después, los mismos en cada partida. Los sonidos que un nivel deja fuera **no se muestran, no se esperan y no se juzgan**, y la puntuación se cuenta sobre los sonidos conservados; **el acompañamiento los toca por usted**, para que la música siga completa (se envían con el instrumento de su pista, sin bends ni deslizamientos). La ventana de resultado indica el nivel y la permisividad.

**Permisividad** (pestaña Modo, Arcade): *Como la práctica* (el reconocimiento y el ritmo de los modos de práctica, nada se relaja), *Divertido* (el valor por defecto) o *Muy permisivo*. Solo se aplica a Arcade (Seguimiento, Espera y el afinador no cambian) y solo relaja, nunca endurece, lo que usted o un perfil de calibración ya fijó: ensancha la ventana de ritmo (un cuarto o la mitad, nunca más de 200 ms) y relaja la confianza, el tiempo de estabilidad y los umbrales de acordes. **Medido:** en la grabación de referencia y en las notas del micrófono integrado **no cambia nada** de lo reconocido (los ajustes por defecto ya alcanzan el techo) y **no** hace el veredicto más rápido: ese retardo lo fija el detector de ataques. Su efecto en entradas más débiles no está medido.

### Alfa: lo que aún no está verificado

* Es una **alfa**: el tiempo de la vista Arcade y del seguimiento de tempo está validado con pruebas automáticas, y el aspecto de la vista por el propietario en un Mac; **no** se ha validado con otros jugadores, guitarras, micrófonos o máquinas.
* En una prueba de carga de 10 minutos, la cadena de audio **aún pierde un plazo de análisis por minuto** (un hueco de planificación del sistema); no se ha visto que cambie un juicio, pero el objetivo de menos no se cumple todavía.
* **Bajo:** las notas por debajo de sol 1 (49 Hz) se muestran pero no se juzgan.
* **Oír tu propia guitarra a través de la aplicación** no se ofrece: toca por tu amplificador, o por unos auriculares conectados a tu interfaz de audio.
* Las notas más juntas de unos 0,3 s pueden no seguirse; las técnicas (hammer-on, slide, bend...) se indican aparte y no cambian la puntuación.

## Mientras practicas

<img src="images/features/practice-window.png" width="380" alt="La ventana de práctica mientras se espera un acorde">


Una pequeña paleta se queda en pantalla y la nota o el acorde esperado aparece enmarcado en la partitura:

* **marca verde**: se ha oído, la práctica continúa;
* **cruz roja**: se oyó otra nota (solo se muestra para una nota que acaba de tocarse, no para la nota anterior que aún suena);
* no pasa nada con el silencio o el ruido, y una nota que sigue sonando nunca se toma por la siguiente;
* un acorde se acepta cuando sus notas se oyen juntas; si el acorde está incompleto, se nombran las notas que faltan;
* una **nota con bend** se acepta cuando el bend alcanza el objetivo escrito: la nota de partida (un pre-bend, su altura ya subida) inicia el bend y la práctica te dice que subas la nota; mientras haces el bend, la liberación o el pre-bend como está escrito, ninguna altura intermedia es una nota incorrecta, la altura que alcanza el bend no se toma por la nota siguiente (que necesita su propia púa), y una liberación escrita también debe hacerse (la práctica te dice que liberes hasta su final); **Saltar** permite pasar un bend o una liberación que no quieres tocar. Si el bend fue exactamente justo es un resultado de técnica, que por ahora dice *aún no se puede comprobar*;
* **Saltar** pasa la nota, **Reanudar** continúa tras una pausa (una reproducción, una entrada inutilizable), **Reiniciar** vuelve a empezar el pasaje, **Detener** termina la práctica de inmediato y vuelve a mostrar la ventana de preparación.

La práctica también puede funcionar **por pasajes**: unos pocos compases cada vez, repetidos hasta que alcances la proporción de notas correctas que hayas fijado.

Lo que se verifica es la **altura**. La cuerda y el traste que has usado no se pueden saber a partir del audio. Para cada técnica escrita (hammer-on, pull-off, slide, bend, vibrato), una línea bajo el resultado de altura dice lo que se oyó: *oído*, *no oído*, *incierto* o *todavía no verificable*. Ninguna técnica está verificada todavía (detección medida: vibrato 59 %, bend 82 %, slide 48 % de las notas anotadas, objetivo 85 %), así que por ahora la línea dice *todavía no verificable*. Nunca bloquea el ejercicio: una nota de hammer-on o de pull-off se acepta por su altura.

## Ganancia de entrada, reducción de ruido y ganancia sugerida

Bajo el medidor de nivel de la ventana de práctica, y en el afinador, un control deslizante de **Ganancia de entrada** sube o baja la señal dentro de la aplicación (de -6 a +18 dB), y **Restablecer** vuelve a 0 dB. La ganancia nunca se recuerda: vuelve a 0 dB al cambiar la entrada y en cada inicio. Si la amplificación sube sobre todo el ruido de fondo, o hace que la señal sature, un aviso lo dice y te indica que subas la ganancia de la interfaz de audio.

Mientras tocas, si tus notas son demasiado débiles para un reconocimiento rápido fiable (por debajo de unos -30 dBFS, medido), la ventana de práctica lo dice y te indica que subas la ganancia o toques más cerca; el juego lento puede seguir reconociéndose.

Ajustes (*Reconocimiento de audio* en la configuración): una **puerta de ruido / sustracción del ruido de fondo** opcional y una **ganancia sugerida** tras la comprobación del nivel; ambas se pueden desactivar.

**La ventana de inicio (antes de cada inicio).** Cada vez que empieza un modo — el botón Empezar, el botón Práctica de la barra de transporte, el botón Arcade de color — una ventana pequeña pregunta primero *¿Está afinada tu guitarra?* *No, abrir el afinador* abre el afinador y el modo no empieza; al cerrar el afinador vuelve la pregunta. *Sí, afinada* pide un acorde de prueba: rasguea un acorde y déjalo sonar. La ventana juzga el nivel de los 0,3 segundos siguientes: si más del 0,5 % de las muestras llega al máximo, dice que la señal satura (baja el volumen del adaptador o de la guitarra); una señal demasiado fuerte o demasiado débil también se indica, con lo que hay que hacer; un buen nivel inicia el modo al instante. Un mal nivel bloquea el inicio: *Comprobar de nuevo* juzga un nuevo acorde y *Continuar de todos modos* sigue **solo para este inicio** (también se ofrece tras 10 segundos sin acorde). En **Seguir el tempo** y en **Arcade**, cuando el ritmo de tu entrada y tu salida nunca se calibró, la ventana lo dice después, con **Calibrar ahora** (los 8 clics de la pestaña Modo, unos 10 segundos, dentro de la ventana; *Cancelar* los detiene y vuelven los dos botones; el retardo encontrado se guarda como desde la pestaña Modo) y **Seguir sin calibrar** (no se vuelve a preguntar para esta entrada y esta salida hasta que TuxGuitar arranque de nuevo). Otra entrada, salida o formato no queda calibrado por el primero. Reiniciar una partida o *volver a jugar* no vuelve a preguntar. La ventana nunca cambia la ganancia de la interfaz ni del sistema. El umbral del 0,5 % se calibró con las grabaciones del propietario con un adaptador Rocksmith y con las 180 grabaciones de micrófono de GuitarSet (ninguna se señala); los consejos para Windows y Linux usan el texto de macOS y no se probaron allí.

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

## Preguntas frecuentes

**El botón de inicio está en gris: ¿por qué?** La entrada todavía no ha oído tu guitarra. Antes de empezar, hay que comprobar el nivel de la entrada elegida: toca unas notas (o rasguea un acorde) y el botón se activa. Si sigue en gris, la entrada no se puede usar: la línea *Nivel* de la pestaña *Entrada* dice por qué (demasiado bajo, saturación, desconectada, acceso al micrófono denegado). Desde el 2026-10-29 una línea bajo la posición de inicio lo dice también.
