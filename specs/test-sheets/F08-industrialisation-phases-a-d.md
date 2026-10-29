# F08 — Industrialisation : bouton, gain, vitesse, techniques

- **Spec Kit** : `specs/002-practice-industrialization` (quickstart, phases A, B, C, D/F)
- **Tâches fermées** : T022 (phase A, US1), T043 (phase B, US8), T055 (phase C, US2), T065 (phase D1, US7), T075 (phase D2, US3), T086 (phase D3, US4)
- **Matériel** : guitare, Rocksmith, micro intégré.
- **Partitions** : `tests/basic_test.tg`, `tests/demo-techniques.tg`, les fichiers de `tests/2/` (bends : `1-bend-comme-ecrit.tg`, `2-bend-non-joue.tg`, `3-pre-bend.tg`, `4-ligne-de-technique.tg`).
- **Durée** : 40 minutes.
- **Ce qui a changé depuis l'écriture de la spec** : depuis le 2026-10-07 (ta décision), un **bend écrit doit être fait** pour que sa note soit acceptée (*Passer* la laisse passer). **Aucune technique n'est « vérifiée »** : les détecteurs ne tiennent pas leurs objectifs, donc les techniques s'affichent comme non vérifiables ou incertaines.

## Phase A — le bouton de pratique en un clic (T022)

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| A1 | Ouvre un morceau avec une piste guitare. Dans la barre d'outils, trouve le bouton **Pratique** à droite de Lecture/Stop. Clique une fois. | La fenêtre de pratique s'ouvre **pour la piste sélectionnée**. | | |
| A2 | Outils › Pratique interactive ; puis reclique sur le bouton. | **La même** fenêtre (jamais une deuxième). L'état coché du bouton **suit la fenêtre**. | | |
| A3 | Ferme la fenêtre avec sa croix. | Le bouton se **décoche**. | | |
| A4 | Fichier › Nouveau (morceau vide), clique sur le bouton. | Le bouton **explique** qu'il n'y a pas de piste à travailler, ou il est **grisé**. | | |

## Phase B — gain et bruit (T043)

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| B1 | Dans l'accordeur, puis dans la pratique : bouge le curseur de **gain d'entrée** en jouant. | Le vumètre et l'état du niveau **suivent**. **Réinitialiser** revient au neutre. | | |
| B2 | Change d'entrée, ou relance l'application. | Le gain est **revenu au neutre**. | | |
| B3 | Active, puis désactive la **réduction de bruit**, en jouant quelques notes à chaque fois. | La reconnaissance n'est **pas pire** avec la réduction de bruit. | | |
| B4 | Active la **suggestion de gain**, fais le contrôle de niveau, clique sur **suggérer**. | Une valeur est proposée et **appliquée seulement si tu confirmes**. Suggestion désactivée : le bouton **disparaît**. | | |

## Phase C — reconnaissance rapide (T055)

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| C1 | Mode Attendre, une gamme (une piste de notes simples à toi, ou les notes de `basic_test.tg`). Joue-la **de plus en plus vite**. | Note **à partir de quelle vitesse** des notes sont manquées ou acceptées à tort (fausses croix). Mesuré : environ 3 notes par seconde à 90 %. Un intervalle de 0,3 s entre deux notes picorées est la limite actuelle. | | |
| C2 | Joue **la même note 4 fois de suite** rapidement. | Note combien sont comptées (mesuré : environ 80 % à 4 répétitions par seconde). | | |

## Phase D — techniques (T065, T075, T086)

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| D1 | `tests/demo-techniques.tg` en mode Attendre : regarde la ligne de **technique** de chaque note qui en porte une. | Chaque technique écrite a sa ligne **à part**. **Aucune** n'est annoncée comme vérifiée. **Rien ne bloque** la progression à cause d'une technique (sauf le bend, voir D4). | | |
| D2 | **Hammer-on et pull-off** : joue-les liés (sans nouvelle attaque), puis en piquant chaque note. | Les notes sont acceptées par leur **hauteur**. La ligne de technique dit détecté / non détecté / **incertain** près de la limite, ou non vérifiable. Note ce que tu vois. (Mesuré : un hammer-on sans attaque n'est pas toujours vu.) | | |
| D3 | **Slide** : joue le slide écrit, puis les deux notes séparément. | Même chose que D2. | | |
| D4 | **Bend** : `tests/2/1-bend-comme-ecrit.tg` (fais le bend), puis `tests/2/2-bend-non-joue.tg` (joue la note sans bend). | Bend fait : la note **suit le chemin écrit** et elle est acceptée. Bend non fait : la note **n'est pas acceptée** ; **Passer** la laisse passer. | | |
| D5 | **Pré-bend** : `tests/2/3-pre-bend.tg`. **Vibrato** sur une note longue. | Note ce que tu vois. Le vibrato reste **non vérifié** et ne bloque rien. | | |
| D6 | `tests/2/4-ligne-de-technique.tg` | La ligne de technique est lisible et ne gêne pas la note. | | |
