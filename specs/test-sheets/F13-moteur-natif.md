# F13 — Le moteur de reconnaissance natif (Rust)

- **Spec Kit** : `specs/023-native-recognition-engine`
- **Tâches fermées** : T087 (porte de la spec 023) ; confirme aussi T056 (le délai au câble mesuré avec toi, les deux moteurs à la fois).
- **Exigences** : FR-001 à FR-005 (choix du moteur, Automatique, avis de repli), FR-010 (ampli et reconnaissance ensemble), SC-002 (délai), SC-003 (repli en moins de 2 s).
- **Matériel** : guitare, câble Rocksmith, **casque filaire**.
- **Partitions** : `tests/demo-arcade-morceau.tg` et `tests/basic_test.tg`.
- **Durée** : environ 30 minutes.

## Préparation

1. Lance l'application construite (relancée après la dernière construction), branche la guitare par le câble Rocksmith et mets le casque.
2. Ouvre `tests/demo-arcade-morceau.tg`, puis la pratique ; choisis la piste guitare et l'entrée Rocksmith.

## Étapes

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| 1 | Ouvre *Configuration › Reconnaissance audio › Moteur de reconnaissance* et regarde **Moteur de détection de hauteur**. | Trois choix : *Automatique (Natif)*, *Natif (Rust)*, *Java (FastYin)*, avec une infobulle. | | |
| 2 | Choisis *Java (FastYin)*, OK, ferme et relance l'application, rouvre la page. | Le choix est gardé. Refais-le avec *Natif (Rust)* puis *Automatique*. | | |
| 3 | Moteur *Automatique* : *Attendre* sur `tests/basic_test.tg`, puis la même chose sur *Java (FastYin)*. | **Ton avis** : les verdicts arrivent-ils plus vite avec le moteur natif ? | | |
| 4 | Même comparaison en *Suivre le tempo* et en *Arcade* sur `tests/demo-arcade-morceau.tg`. | Les notes et les accords sont jugés comme avec Java, mais plus tôt. Arcade reste fluide. | | |
| 5 | Ferme l'application. Dans le Finder, clic droit sur l'application › *Afficher le contenu du paquet* › `Contents/Resources/tuxguitar/lib`, renomme `libtuxguitar_monitor_native.dylib` en `libtuxguitar_monitor_native.dylib.off`. Relance, lance *Attendre*. | Une ligne d'avis dit que le moteur natif n'a pas pu être chargé ; la pratique marche avec Java et **démarre en moins de 2 s**. **Remets ensuite le nom d'origine.** | | |
| 6 | Ouvre **Ampli et effets…**, active **Son d'ampli** et lance *Attendre* puis *Arcade*. | Le son d'ampli s'entend et la reconnaissance se comporte comme sans lui. | | |
| 7 | **Ton avis final** : le moteur natif te semble-t-il plus rapide que Java ? | Ta réponse est écrite dans `final-report.md`. | | |

## Pour les testeurs Linux et Windows

Le moteur natif n'a été essayé sur aucune vraie machine Linux ou Windows ; un retour dans une [issue](https://github.com/krl91/tuxguitaradvanced/issues), avec le système, sa version et l'interface audio, est précieux. La bibliothèque est dans le dossier `lib` de l'application : `libtuxguitar_monitor_native.so` (Linux) ou `tuxguitar_monitor_native.dll` (Windows).

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| X1 | Ouvre *Configuration › Reconnaissance audio* et regarde **Moteur de détection de hauteur**. | Il affiche *Automatique (Natif)*. S'il affiche *Automatique (Java)*, dis-le : la bibliothèque n'a pas pu être chargée. | | |
| X2 | Lance *Attendre* avec ta guitare, une fois en *Natif (Rust)*, une fois en *Java (FastYin)*. | **Ton avis** : le délai entre la corde et le verdict est-il plus court en natif ? | | |
| X3 | Application fermée, renomme la bibliothèque (ajoute `.off`), relance et lance *Attendre*. | Un avis de repli s'affiche, la pratique marche avec Java et démarre en moins de 2 s. Remets le nom d'origine. | | |
| X4 | Active **Son d'ampli** (Ampli et effets…) et lance *Attendre*. | Le son et la reconnaissance marchent ensemble. | | |
| X5 | Linux seulement, si ton interface est choisie comme périphérique ALSA brut (`hw:…`, sans PipeWire ni PulseAudio) : active le son d'ampli pendant une pratique. | Un périphérique `hw:` ne s'ouvre qu'une fois : un message dit que le son d'ampli ne peut pas démarrer, la reconnaissance continue. Dis-nous si c'est ton cas. | | |
