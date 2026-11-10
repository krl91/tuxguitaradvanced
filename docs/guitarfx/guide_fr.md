# Guide de GuitarFX

GuitarFX transforme l'ordinateur en ampli de guitare avec pédales d'effets : noise gate, compresseur, overdrive, distorsion, l'ampli (clean, crunch ou high gain, avec graves, médiums et aigus), le baffle, chorus, écho et réverb. C'est le son de la fenêtre *Ampli et effets* de TuxGuitarAdvanced, dans un petit programme à part qui n'a besoin ni de TuxGuitarAdvanced ni de Java. Il existe en application et en plugin pour logiciel de musique (CLAP, VST3, et Audio Unit sur macOS), sur macOS, Windows et Linux.

Anglais : [guide.md](guide.md). Espagnol : [guide_es.md](guide_es.md). Toutes les valeurs internes : [parameters.md](parameters.md) (en anglais).

## Installer

Les fichiers sont sur la page de la release, à côté de ceux de TuxGuitarAdvanced.

- **macOS** (Apple Silicon, macOS 15 ou plus récent) : ouvrez `GuitarFX-<version>-macos-arm64.pkg`. GuitarFX n'est pas signé par un développeur Apple, donc macOS le refuse la première fois : ouvrez *Réglages Système > Confidentialité et sécurité* et choisissez *Ouvrir quand même*. L'installeur place `GuitarFX.app` dans *Applications* et les plugins dans `/Library/Audio/Plug-Ins` (`VST3`, `CLAP`, `Components`). La première fois que le son est activé, macOS demande d'autoriser GuitarFX à utiliser le micro : acceptez.
- **Windows** (64 bits) : lancez `GuitarFX-<version>-windows-x86_64-setup.exe`. Windows peut prévenir que l'éditeur est inconnu : choisissez *Informations complémentaires*, puis *Exécuter quand même*. GuitarFX va dans `Program Files\GuitarFX`, avec une entrée dans le menu Démarrer ; les plugins vont dans `Common Files\VST3` et `Common Files\CLAP`.
- **Linux** (x86-64) :
  - Debian, Ubuntu et leurs dérivées : `sudo apt install ./guitarfx_<version>_amd64.deb`. Le programme est `/usr/bin/guitarfx` (aussi dans le menu des applications) ; les plugins vont dans `/usr/lib/vst3` et `/usr/lib/clap`.
  - Tout autre système, pour votre utilisateur seulement : décompressez `GuitarFX-<version>-linux-x86_64.tar.gz` et lancez `GuitarFX/install.sh`. Le programme va dans `~/.local/bin/guitarfx`, les plugins dans `~/.vst3` et `~/.clap`.

Installer une version plus récente par-dessus une ancienne garde vos réglages et vos sons.

## Les périphériques

En haut de la fenêtre :

- **Entrée** : le périphérique où la guitare est branchée (une interface audio, c'est le mieux), et son **Canal** (1 pour la première entrée).
- **Sortie** : là où vous entendez le son. **Un casque est conseillé.**
- **⟳** cherche à nouveau les périphériques après en avoir branché un.

GuitarFX retient les périphériques par leur nom. S'il en manque un au démarrage suivant, il est affiché *(absent)* et le son reste coupé tant que vous ne l'avez pas branché ou choisi un autre.

**Son activé** fait passer la guitare dans l'ampli. Il est coupé à chaque démarrage, et l'activer fait monter le son en douceur. **Volume** règle le niveau de sortie ; l'indicateur **Entrée** montre le niveau de la guitare, et *sature* veut dire que l'entrée est trop forte : baissez le gain d'entrée de l'interface ou du système.

## La chaîne

La chaîne est répartie en trois onglets, dans l'ordre où le son les traverse :

1. **Avant l'ampli** : les pédales qui façonnent le signal de la guitare, comme le noise gate, le compresseur, l'overdrive ou la distorsion.
2. **Ampli** : son **Caractère** (clean, crunch ou high gain), ses boutons (gain, graves, médiums, aigus, volume) et le **Baffle**, la couleur du haut-parleur (coupé, l'ampli sonne brillant et brut, comme par une sortie ligne).
3. **Après l'ampli** : les pédales qui colorent le son de l'ampli, comme le chorus, l'écho ou la réverb.

Chaque pédale a une ligne : son interrupteur (éteinte, le son la traverse sans changement), ses boutons, et des boutons pour la déplacer plus tôt ou plus tard dans l'onglet, ou la retirer. **+ Ajouter une pédale** en ajoute une à la fin de l'onglet ; chaque pédale peut être une fois dans la chaîne. Laissez le pointeur sur un bouton pour lire ce qu'il fait, sa plage et sa valeur par défaut. Chaque changement s'entend tout de suite, sans clic.

## Les sons

La liste **Son** contient les quatre sons prêts à l'emploi (Clean, Crunch, Lead, Clean echo), puis les vôtres. *(modifié)* veut dire que la chaîne diffère du son enregistré.

- **Enregistrer…** garde la chaîne comme un de vos sons, sous le nom de votre choix.
- **⋯** donne **Renommer…**, **Supprimer** (la chaîne en cours reste telle quelle), **Rétablir** (un son prêt à l'emploi revient tel qu'il était livré), **Exporter…** et **Importer…**.

Un son peut être exporté dans un fichier `.guitarfx` et importé dans GuitarFX ou dans la fenêtre *Ampli et effets* de TuxGuitarAdvanced, dans les deux sens. Si vous avez déjà un son de ce nom, vous choisissez de le remplacer ou de garder les deux.

## Le délai

Sous les périphériques, GuitarFX affiche le délai estimé entre votre jeu et le son : les blocs des périphériques et leurs latences déclarées, additionnés. Le délai que vous entendez peut être un peu plus long. Une interface audio avec de petits blocs donne le délai le plus court ; un casque Bluetooth ajoute son propre délai, qui n'est pas compté, et qui est en général trop long pour jouer.

## Casque et larsen

Avec des haut-parleurs, et surtout avec le micro intégré de l'ordinateur, le son peut revenir dans le micro et provoquer un larsen. GuitarFX conseille un casque quand l'entrée est un micro intégré, et demande confirmation avant d'activer le son. Si un larsen démarre, il coupe le son et vous le dit : mettez un casque, ou éloignez-vous des haut-parleurs, puis réactivez le son.

Si l'ordinateur n'arrive pas à suivre, GuitarFX allège le son pas à pas (réverb coupée, chorus coupé, moins de détail dans l'ampli, puis seulement l'ampli et le baffle) et vous prévient à chaque pas. Si même l'ampli seul est trop lourd, il coupe le son : fermez d'autres programmes, puis réactivez le son.

## La fenêtre Avancé

**Avancé…** montre toutes les valeurs internes du son (103, en 19 catégories) : le plafond du limiteur, les fondus, la protection contre le larsen, le bloc des périphériques, les valeurs internes de chaque élément. Les valeurs par défaut sont celles qui ont été testées ; **Valeurs par défaut** les remet toutes, après confirmation. Un changement prend effet à la prochaine activation du son. [parameters.md](parameters.md) les liste toutes, avec leurs plages.

## Le plugin

Le plugin, c'est la même chaîne, le même étage de sortie et la même protection contre le larsen, dans votre logiciel de musique (une STAN) : ajoutez **GuitarFX** comme effet sur une piste avec votre guitare. Sa fenêtre a les mêmes trois onglets et la même liste de sons, et le projet garde la chaîne.

- Formats : **CLAP** et **VST3** sur les trois systèmes, et **Audio Unit** sur macOS.
- Il accepte une piste mono ou stéréo (une entrée stéréo est mixée en mono) et sort en stéréo.
- Il annonce son délai à l'hôte (45 échantillons), pour que l'hôte l'aligne avec les autres pistes.
- Il n'a pas de volume à lui : utilisez le fader de l'hôte.
- S'il détecte un larsen, ou si l'ordinateur ne suit pas même avec l'ampli seul, il se coupe et le dit dans sa fenêtre. Corrigez la cause, puis retirez GuitarFX et ajoutez-le à nouveau, ou rouvrez le projet.
- **Ses valeurs internes viennent des réglages de l'ordinateur** : le plugin lit les valeurs de la fenêtre *Avancé* (le fichier `settings.properties` du dossier de réglages de GuitarFX) chaque fois que l'hôte le démarre. Un projet ouvert sur un autre ordinateur utilise les valeurs de cet ordinateur ; la chaîne, elle, voyage avec le projet.

## Désinstaller

La désinstallation enlève le programme et les plugins ; **elle garde vos réglages et vos sons**, sauf si vous le demandez.

- **macOS** : dans le Terminal, `/Applications/GuitarFX.app/Contents/Resources/guitarfx-uninstall` (ajoutez `--remove-settings` pour enlever aussi `~/Library/Application Support/GuitarFX`). Il demande votre mot de passe.
- **Windows** : *Paramètres > Applications*, GuitarFX, *Désinstaller*. Le désinstalleur demande s'il faut enlever vos réglages et vos sons (`%APPDATA%\GuitarFX`).
- **Linux** : `sudo apt remove guitarfx` pour le .deb ; `GuitarFX/install.sh --uninstall` pour le tar.gz (ajoutez `--remove-settings` pour enlever aussi `~/.config/guitarfx`).
