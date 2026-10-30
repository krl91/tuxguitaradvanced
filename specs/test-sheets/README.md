# Fiches de test pour le propriétaire (état au 2026-10-29)

Ces fiches regroupent **tout ce qui reste à tester par une personne** dans les specs Spec Kit (`specs/0*/tasks.md`, tâches `[HUMAN]` ou « manual » non cochées). Chaque fiche cite la spec, les tâches (`Txxx`) et les exigences (`FR-xxx`, `SC-xxx`) qu'elle ferme. Une tâche n'est cochée dans `tasks.md` qu'après ton retour (constitution XII : on n'écrit que ce qui a été vu).

## Avant toute fiche

- Application : `desktop/build-scripts/tuxguitar-macosx-swt-cocoa/target/tuxguitar-9.99-SNAPSHOT-macosx-swt-cocoa.app` (construite le 22 oct. ; elle correspond au code actuel). Double-clic pour la lancer. Une seule instance ouverte à la fois.
- Partitions : `tests/basic_test.tg` (court, notes et accords), `tests/demo-arcade-morceau.tg` (morceau de démonstration Arcade), `tests/demo-techniques.tg` (bends, hammer-on, slide…), `tests/1/_TEST_verifie.tg` (la partition de l'enregistrement de référence), `misc/tuxguitar.tg` (exemple multipiste).
- Entrées : le micro intégré du Mac, et l'adaptateur Rocksmith (entrée directe guitare).
- Pour rendre un résultat : pour chaque étape, coche **OK** ou **KO** et écris ce que tu as vu si ce n'est pas exactement l'attendu (une capture d'écran aide beaucoup). Tu peux me coller la fiche remplie ou juste « F03 : tout OK sauf étape 5, … ».

## Liste, dans l'ordre conseillé

| Fiche | Spec | Tâches fermées | Matériel | Durée | Pourquoi cet ordre |
|---|---|---|---|---|---|
| [F01](F01-suivre-le-tempo.md) | 002 Phase E, mode *Suivre le tempo* | T096, T097, T098, T102 | guitare + Rocksmith + micro intégré | 30 min | **Bloque la sortie officielle d'Arcade** (003 T001) |
| [F02](F02-version-partout.md) | 015 version et commit | T021, T022, T024 | aucun | 20 min | Rapide, sans guitare |
| [F03](F03-niveaux-arcade.md) | 014 niveaux Arcade | T040 | guitare | 20 min | Dernière fonction livrée |
| [F04](F04-calibration.md) | 013 calibration par appareil | T049 | guitare + micro intégré (+ Rocksmith) | 25 min | |
| [F05](F05-verification-avant-depart.md) | 010 vérification avant le départ (gate G) | T034 | guitare + Rocksmith | 20 min | |
| [F06](F06-flammes-arcade.md) | 009 flammes Arcade (gate G2) | T033 | guitare | 20 min | |
| [F07](F07-arcade-visuel.md) | 003 Arcade, jugements à l'écran | T106, T111, T117, T134, T136, T138, T144, T145 | guitare | 40 min | |
| [F08](F08-industrialisation-phases-a-d.md) | 002 phases A à D | T022, T043, T055, T065, T075, T086 | guitare + Rocksmith | 40 min | |
| [F09](F09-mvp-acceptation.md) | 001 MVP, recette | T049, T054, T062, T069, T072, T075, T096, T097, T082 | guitare + micro intégré + Rocksmith | 60-90 min (en plusieurs fois) | Le plus long ; une partie du comportement a évolué depuis (signalé dans la fiche) |
| [F10](F10-palette-ecartee.md) | 017 la palette s'écarte de la partition | T021 | guitare (+ Rocksmith) | 15 min | Sans focus, sans allers-retours |

## Ce qui reste ouvert mais n'est pas une fiche pour toi seul

| Tâche | Pourquoi pas maintenant |
|---|---|
| 003 T068, T069 (SC-008, SC-009) | Il faut **trois autres guitaristes** : je peux préparer une fiche courte à leur donner le jour où tu les as. |
| 003 T044 (planche des 32 captures réelles) | Attend T066 (le modèle de couloirs à coder d'abord) ; les captures actuelles sont d'avant les nouveaux graphismes. |
| 003 T072, T073, T074 (latence du retour son, dérive 30 min) | Demande une interface audio 2 entrées / 2 sorties et des câbles de bouclage. |
| 008 T030 (icônes Windows et Linux) | Demande une machine Windows et une machine Linux. |
| 001 T078, T094 ; 002 T053, T131 (enregistrements) | Ta décision du 2026-10-06 : aucun nouvel enregistrement demandé pour l'instant. |
| 001 T079 (interface multicanal) | Demande une interface à plusieurs entrées. |
| 001 T080 (30 minutes d'endurance) | Faisable si tu veux : une séance de 30 min en mode *Attendre*, en notant tout décrochage ; dis-le-moi et j'en fais une fiche. |
| 009 T029 (coût des flammes sur 10 min) | Un test automatique que je lance, mais l'écran doit rester allumé et le Mac déverrouillé pendant 10 min. |
| 011 T049 | La fiche viendra à la fin de la spec 011 (le harnais et les tests de parcours ne sont pas encore écrits). |
| 001 T084 (conformité GPL) | Reportée par toi. |
| 004 T007 (accord à une note manquante) | Travail de recherche, pas un test. |
