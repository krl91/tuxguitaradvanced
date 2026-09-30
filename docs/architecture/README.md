# Architecture de TuxGuitarAdvanced

Diagrammes de l'architecture, du mode Practice et de l'accordeur. Les sources modifiables sont à côté des images :
`.drawio` (ouvrir avec [draw.io](https://www.drawio.com/)) et `.puml` ([PlantUML](https://plantuml.com/)).

Légende des schémas CESAM : **bleu** = existant (MVP), **orange pointillé** = prévu (spec 002, `specs/002-practice-industrialization/`), **gris** = acteur ou élément externe.

## Les trois visions de la méthode CESAM (`cesam/`)

[CESAM](https://www.cesames.net/en/cesam-framework/) (CESAMES Systems Architecting Method) décrit un système par trois visions complémentaires, chacune avec les mêmes cinq aspects : propriétés attendues, états, éléments statiques, scénarios, interfaces.

| Vision | Question | Fichier `.drawio` | Pages |
|---|---|---|---|
| Opérationnelle | pourquoi / quoi : le système vu de son environnement | `01-vue-operationnelle.drawio` | 1 contexte et services (éléments statiques) · 2 scénario nominal · 3 états d'usage, propriétés attendues, interfaces |
| Fonctionnelle | quoi / comment : fonctions, flux, comportement | `02-vue-fonctionnelle.drawio` | 1 fonctions et flux de données · 2 états de la session · 3 scénario d'acceptation d'une note · 4 interfaces et propriétés |
| Organique (constructionnelle) | comment : composants qui réalisent les fonctions | `03-vue-organique.drawio` | 1 composants · 2 fils d'exécution et interfaces · 3 déploiement et distribution |

Chaque page existe aussi en PNG (`0X-vue-…-N-….png`). Le contenu est tiré du code inspecté ; les éléments marqués « prévu » ne sont pas encore implémentés.

## Diagrammes de séquence (`sequences/`)

| Fichier | Contenu |
|---|---|
| `practice-mode.puml` / `.png` | Mode Practice : initialisation, chaîne signal → trame, puis trois cas d'usage : détection d'une **note**, d'un **accord**, d'un **effet / technique** (existant : technique affichée et non vérifiée ; prévu : détecteurs de technique). |
| `tuner.puml` / `.png` | Accordeur : ouverture, chaîne signal → trame, affichage (guidé, chromatique, lecture conservée), changements en cours d'usage, fermeture, lien avec Practice (pause pendant que l'accordeur est ouvert). |

Régénérer les PNG : `java -jar plantuml.jar -tpng sequences/*.puml` (PlantUML et Graphviz installés).
