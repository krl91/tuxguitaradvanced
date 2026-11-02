# F12 — La guitare entendue par un ampli et des pédales

- **Spec Kit** : `specs/022-guitar-amp-effects`
- **Tâches fermées** : T060 (porte de la spec 022) et T029 (le délai ressenti avec le câble). Les étapes vérifient aussi SC-001, SC-005, SC-006 et SC-007.
- **Exigences** : FR-001 à FR-020 ; US1 à US4.
- **Matériel** : guitare, câble Rocksmith, **casque filaire** ; le micro intégré pour les étapes 11 et 12.
- **Partitions** : `tests/demo-arcade-morceau.tg` et `tests/basic_test.tg`.
- **Durée** : environ 30 minutes, plus l'heure de pratique de l'étape 15 (à faire quand tu veux).

## Préparation

1. Lance l'application construite, branche la guitare par le câble Rocksmith et mets le casque.
2. Ouvre `tests/demo-arcade-morceau.tg`, puis la pratique, choisis la piste guitare et l'entrée Rocksmith.
3. Clique **Ampli et effets…**.

## Étapes

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| 1 | Regarde la fenêtre avant de toucher à quoi que ce soit. | **Son d'ampli** est coupé ; on n'entend rien. Le son choisi est *Clean*. Chaque contrôle a une infobulle en français. | | |
| 2 | Active **Son d'ampli** et joue. | Le son monte en une demi-seconde environ ; la guitare s'entend par *Clean*. Note la valeur de la ligne **Délai**. | | |
| 3 | **Ton avis** : le délai entre la corde et le son te gêne-t-il ? | Ça doit sonner comme un ampli (SC-001, T029). Si ça traîne, dis-le : on change la taille des blocs. | | |
| 4 | Choisis *Crunch*, *Lead* puis *Clean echo* en jouant, et tourne chaque bouton. | Chaque changement s'entend aussitôt, **sans clic**. | | |
| 5 | Ajoute une pédale (*Ajouter une pédale*), monte-la, descends-la, retire-la, en jouant. | Elle se met dans sa section ; rien ne claque. | | |
| 6 | Lance *Attendre*, *Suivre le tempo* et *Arcade*. Règle **Volume de la guitare** et **Volume de la partition**. | La guitare s'entend avec la partition ; les verdicts se comportent comme d'habitude. | | |
| 7 | Change *Lead* (le gain par exemple), choisis *Clean*, puis reviens à *Lead*. | *Lead* garde ton changement et s'affiche **(modifié)** ; **Rétablir** le remet comme livré. | | |
| 8 | Tape un nom à côté de la liste, **Enregistrer sous**, puis **Renommer** et **Supprimer**. | Ton son apparaît après les quatre sons tout faits ; les noms des sons tout faits sont refusés avec un message. | | |
| 9 | Choisis *Lead* pour cette partition. Ouvre `tests/basic_test.tg`, ouvre la pratique et choisis *Clean*. | Chaque partition garde son son. | | |
| 10 | Ferme l'application, relance-la, rouvre les deux partitions et la pratique. | Chacune retrouve **son** son avant toute exécution. | | |
| 11 | Choisis le **micro intégré** comme entrée et active le son. | Un avertissement demande un casque ; le son ne démarre qu'après **Activer quand même**. | | |
| 12 | Avec le micro intégré, **enlève le casque** et monte le volume de la guitare. | Un larsen est **coupé en moins d'une demi-seconde** avec un message ; l'interrupteur repasse sur coupé (SC-005). | | |
| 13 | Son activé, **débranche le casque ou l'interface**. | Le son s'arrête avec un message ; la pratique continue. | | |
| 14 | **Ton avis** sur les quatre sons tout faits et les plages des boutons. | Note les valeurs à changer : elles seront modifiées puis réessayées. | | |
| 15 | Quand tu veux : **une heure de pratique** avec le son activé. | Aucune coupure ni craquement (SC-006). | | |

## Pour les testeurs Linux et Windows

Ces lectures n'ont été essayées sur aucune vraie machine ; un retour dans une [issue](https://github.com/krl91/tuxguitaradvanced/issues), avec le système, sa version et l'interface audio, est précieux.

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| X1 | Ouvre **Ampli et effets…**, active le son avec ta guitare branchée et un casque. | Le son monte en une demi-seconde. **Note la valeur de la ligne Délai** et dis si le délai te gêne. | | |
| X2 | Étapes 2 à 5 ci-dessus. | Comme sur macOS : pas de clic, chaque changement s'entend aussitôt. | | |
| X3 | Avec un micro et des haut-parleurs (sans casque), active le son et monte le volume. | Un larsen est coupé en moins d'une demi-seconde avec un message. | | |
| X4 | Si possible, lance l'application depuis un terminal et copie la ligne qui commence par `TuxGuitar amp sound:`. | Elle dit si le fil audio a obtenu la priorité temps réel (Linux : SCHED_FIFO ; Windows : MMCSS). | | |
