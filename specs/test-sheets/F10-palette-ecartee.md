# F10 — La palette s'écarte de la partition

- **Spec Kit** : `specs/017-palette-dodges-score`
- **Tâche fermée** : T021 (porte de la spec 017, objectif SC-004). Les étapes 3 et 6 vérifient aussi SC-001 et SC-003 à l'œil.
- **Exigences** : FR-001 à FR-008, US1, US2, US3.
- **Matériel** : guitare et Rocksmith (ou le micro intégré).
- **Partition** : `tests/basic_test.tg` pour *Attendre* ; pour *Suivre le tempo*, la partition de F01 (`tests/1/_TEST_verifie.tg`).
- **Durée** : environ 15 minutes.

## Préparation

1. Lance l'application construite, ouvre `tests/basic_test.tg`.
2. Ouvre la pratique (bouton **Pratique**), choisis la piste guitare et l'entrée.
3. Garde la fenêtre principale assez grande pour voir au moins deux lignes de partition.

## Étapes

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| 1 | Lance une exécution en **Attendre la note correcte**. Joue les notes jusqu'à ce que la note encadrée arrive sous la palette (en haut à droite au départ). | Avant que la note encadrée passe sous elle, la palette **saute dans un coin libre** de la partition. La note à jouer et la suivante ne sont **jamais cachées**. | | |
| 2 | Continue de jouer sur une ou deux lignes. | La palette ne fait **pas d'allers-retours** : elle ne revient pas à la place qu'elle vient de quitter tant que les notes en sont proches. | | |
| 3 | Juste après un déplacement de la palette, joue la note suivante **sans cliquer nulle part**, puis utilise un raccourci de la pratique (par exemple Passer). | La note est entendue et le raccourci marche : la palette **n'a pas pris le focus**. | | |
| 4 | Fais défiler la partition à la molette pour amener la note encadrée sous la palette, ou réduis la fenêtre principale. | En moins d'une demi-seconde, la palette s'écarte de nouveau. | | |
| 5 | Déplace la palette à la main au milieu de la partition, loin de la note à jouer. | Elle **reste où tu l'as mise** tant qu'aucune note ne passe dessous ; quand une note y arrive, elle s'écarte. | | |
| 6 | Pose la palette **à côté** de la fenêtre principale (hors de la fenêtre). Continue de jouer. | Elle **ne bouge plus**, même quand la partition défile. Ramène-la dans la fenêtre : elle recommence à s'écarter quand il le faut. | | |
| 7 | Arrête, ouvre la partition de F01, lance **Suivre le tempo** (réglages par défaut) et joue un passage. | Même comportement qu'en *Attendre* : la note jouée et la suivante restent visibles ; la palette ne cache pas le surlignage du temps joué. | | |
| 8 | Lance une partie **Arcade**. | Rien ne change en Arcade (il n'y a pas de palette qui bouge). | | |
| 9 | **Ton avis** : la palette gêne-t-elle encore la lecture ? | Réponse attendue : non (SC-004). | | |
