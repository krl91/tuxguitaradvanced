# F11 — Le feu sur la partition et l'onglet Avancé

- **Spec Kit** : `specs/019-score-demo-fire`
- **Tâche fermée** : T048 (porte de la spec 019, FR-011). Les étapes vérifient aussi SC-001, SC-002, SC-003 et SC-006 à l'œil.
- **Exigences** : FR-001 à FR-010, FR-013, FR-014, FR-018 à FR-020 ; US1 à US4.
- **Matériel** : guitare et Rocksmith (ou le micro intégré).
- **Partition** : `tests/demo-arcade-morceau.tg` pour *Attendre* ; pour *Suivre le tempo*, la partition de F01 (`tests/1/_TEST_verifie.tg`).
- **Durée** : environ 25 minutes.

## Préparation

1. Lance l'application construite, ouvre `tests/demo-arcade-morceau.tg`.
2. Ouvre la pratique (bouton **Pratique**), choisis la piste guitare et l'entrée.
3. Garde la fenêtre principale assez grande (au moins 1280×800) pour voir plusieurs lignes de partition.

## Étapes

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| 1 | Onglet **Mode** : regarde le choix **Feu sur la partition**. | Il vaut **Éteint** la première fois. Son infobulle dit que c'est de la décoration, sans effet sur le jugement. | | |
| 2 | Choisis **Plein**, lance **Attendre la note correcte** et joue une dizaine de notes justes d'affilée. | Un feu en gros pixels, comme celui d'Arcade, **grandit** au bas de la partition à chaque note juste, plus haut aux coins qu'au milieu, sans dépasser environ un tiers de la zone de la partition. Chaque note juste lance une **gerbe d'étincelles** qui monte et s'éteint en une seconde environ. | | |
| 3 | Joue une **fausse note**. | Le feu **retombe** aux braises en une seconde environ (SC-002). | | |
| 4 | Fais **Pause** pendant que le feu est haut, attends quelques secondes, puis **Reprendre**. | Pendant la pause, le feu et les étincelles restent **figés** ; il repart à la reprise. | | |
| 5 | Avance jusqu'à ce que la note encadrée soit sur la **dernière ligne visible**, au bas de la fenêtre. | Le feu **s'efface** autour de la note encadrée, de la note suivante et de la palette : elles restent lisibles (SC-003). | | |
| 6 | Pendant que le feu bouge, fais défiler la partition à la molette. | Le défilement reste **fluide** ; le feu suit le bas de la zone visible (SC-006). | | |
| 7 | Arrête, choisis **Discret**, relance quelques notes justes. | Même feu, plus bas et plus transparent. | | |
| 8 | Ouvre la partition de F01, lance **Suivre le tempo** avec **Plein** et joue un passage. | Le feu grandit avec les notes justes, retombe aux notes manquées ou fausses ; les verdicts et le bilan sont les mêmes qu'avec le feu éteint (FR-008). | | |
| 9 | Onglet **Avancé** : regarde les deux groupes *Feu d'Arcade* et *Feu sur la partition*. | Chaque groupe a un **aperçu animé**, un bouton **Étincelles**, un curseur **Croissance**, **Valeurs par défaut**, puis une ligne par réglage, rangée par section. L'onglet défile si la fenêtre est petite. | | |
| 10 | Survole quelques réglages (étiquette et curseur). | Chaque **infobulle** dit ce que le réglage change, son unité et sa valeur par défaut, en français (FR-018). | | |
| 11 | Bouge **Opacité maximale**, **Hauteur aux coins** et une **couleur** (bouton *Choisir…*). Clique **Étincelles** et bouge **Croissance**. | L'aperçu suit **aussitôt** chaque changement ; *Étincelles* lance une gerbe ; *Croissance* fait monter ou descendre le feu de l'aperçu (FR-020). | | |
| 12 | Change d'onglet, puis reviens sur **Avancé**. | Hors de l'onglet, rien ne bouge ; au retour, l'aperçu repart. | | |
| 13 | Lance une exécution *Attendre* avec **Plein**. | Le feu sur la partition a les valeurs que tu viens de régler. | | |
| 14 | Clique **Valeurs par défaut** du groupe *Feu sur la partition*. | Tous ses réglages reviennent à leur valeur par défaut ; ceux du groupe *Feu d'Arcade* ne changent pas. | | |
| 15 | Règle une valeur, ferme l'application et relance-la. | Le choix **Plein / Discret** et la valeur réglée sont **gardés** (SC-007). | | |
| 16 | Réglages Système → Accessibilité → Affichage → **Réduire les animations** activé. Ouvre l'onglet **Avancé**, puis lance une exécution *Attendre* avec **Plein**. | L'aperçu et le feu **ne bougent plus** et ne lancent **pas d'étincelles** ; seule la hauteur suit le jeu. Remets ensuite le réglage comme avant. | | |
| 17 | **Ton avis** : le feu ressemble-t-il à celui d'Arcade ? Faut-il retoucher des valeurs ? | Note les valeurs à changer : elles seront modifiées puis réessayées (FR-011, SC-001). | | |

## Pour les testeurs Linux et Windows

Ces lectures n'ont été essayées sur aucune vraie machine (research R7) ; un retour dans une [issue](https://github.com/krl91/tuxguitaradvanced/issues), avec le système et sa version, est précieux.

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| L1 | **Linux GNOME** : `gsettings set org.gnome.desktop.interface enable-animations false`, rouvre la fenêtre de pratique, lance une exécution avec le feu **Plein**. | Le feu ne bouge pas et ne lance pas d'étincelles. Remets `true` ensuite. | | |
| L2 | **Linux KDE** : Configuration du système → Espace de travail → vitesse d'animation au minimum (instantané), puis même essai. | Même résultat qu'en L1. | | |
| W1 | **Windows** : Paramètres → Accessibilité → Effets visuels → **Effets d'animation** désactivés, rouvre la fenêtre de pratique, lance une exécution avec le feu **Plein**. | Même résultat qu'en L1. | | |
| X1 | Sur chacun : étapes 2, 3, 5, 9 et 11 ci-dessus. | Comme sur macOS. | | |
