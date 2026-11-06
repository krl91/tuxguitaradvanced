# F15 — Les accords bien joués sont acceptés, les accords nettement faux refusés

- **Spec Kit** : `specs/024-lenient-chords`
- **Tâche fermée** : T045 (porte de la spec 024).
- **Exigences** : SC-005 (ton jugement sur les accords en *Attendre*, *Suivre* et *Arcade* avec le niveau par défaut) ; FR-005, FR-007 et FR-012 (la *Vérification des accords* dans l'onglet *Mode*, ses trois niveaux, *Personnalisé*) ; FR-009 (le même jugement dans les trois modes) ; FR-013 (un réglage changé sans reconstruire ni relancer).
- **Matériel** : guitare, **câble Rocksmith** (c'est l'entrée de toute la fiche).
- **Partitions** : `tests/basic_test.tg` (notes et accords) et `tests/demo-arcade-morceau.tg`.
- **Durée** : environ 25 minutes.

Ce qu'on appelle ici :
- **un accord nettement faux** : il manque au moins deux des noms de notes de l'accord attendu (l'octave ne compte pas). Exemples : la partition attend *Am* (la do mi), tu joues *D* (ré fa♯ la) ; elle attend *C* (do mi sol), tu joues *D*. Il doit **toujours** être refusé ;
- **un autre doigté du même accord** : les mêmes noms de notes sur d'autres cordes ou à une autre octave (par exemple *Am* ouvert et *Am* en barré case 5). Il est accepté : le son ne permet pas de les distinguer.

## Préparation

1. Lance l'application construite (relancée après la dernière construction), branche la guitare par le câble Rocksmith.
2. Ouvre `tests/basic_test.tg`, puis *Outils › Interactive Practice* ; choisis la piste guitare et l'entrée Rocksmith.
3. Dans l'onglet *Mode*, regarde **Calibrage du rythme** et note ce qu'il dit pour le câble : *Non calibré*, *Calibré : … ms* ou *Réglé à la main : … ms*. Ne change rien : la fiche se fait avec l'état actuel.

   Calibrage du câble pendant la fiche : ______________________

## Étapes

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| 1 | Onglet *Mode* : regarde **Vérification des accords**, juste sous le choix du mode. Ouvre la liste, sans rien choisir. | La liste propose *Strict*, *Normal*, *Tolérant* ; *Normal* est choisi (c'est le niveau par défaut). Une ligne sous la liste dit ce que le niveau choisi accepte et ce qu'il risque. | | |
| 2 | Niveau *Normal*, mode **Attendre la bonne note**, **Travailler cette piste**. Joue les accords de la partition **comme écrits**. | Chaque accord bien joué est accepté. | | |
| 3 | Toujours en *Attendre*, sur un accord : joue d'abord un **accord nettement faux** (deux notes différentes, voir plus haut), puis **un autre doigté** du bon accord. | L'accord nettement faux est **refusé** (la partition attend toujours). L'autre doigté du bon accord est **accepté**. | | |
| 4 | Mode **Suivre le tempo**, niveau *Normal* : joue les accords de la partition, dont un nettement faux et un autre doigté. | Les accords bien joués sont acceptés, l'accord nettement faux refusé, l'autre doigté accepté, comme en *Attendre*. | | |
| 5 | Ouvre `tests/demo-arcade-morceau.tg`, mode **Arcade**, niveau *Normal*, **Démarrer Arcade** : joue les accords, dont un nettement faux. | Les accords bien joués sont comptés justes, l'accord nettement faux compté faux, comme dans les deux autres modes. | | |
| 6 | Retour sur `tests/basic_test.tg`, *Attendre*. Choisis *Strict*, joue un accord bien joué ; puis *Tolérant*, le même accord ; puis *Normal*. Pour chaque niveau, lis la ligne sous la liste. | Avec les trois niveaux, l'accord bien joué est accepté (en *Strict*, un accord bien joué peut parfois être refusé : sa ligne le dit). Un accord nettement faux est refusé avec les trois niveaux. | | |
| 7 | Choisis *Strict*. Ouvre *Configuration › Reconnaissance audio*, catégorie **Pratique — accords** : regarde **Règle des accords**, **Correspondance de l'accord**, **Note la plus faible d'un accord**, **Accepter un accord sur son attaque** (et la catégorie **Détails des accords**). Change **Correspondance de l'accord**, OK, puis reviens à l'onglet *Mode* de la pratique. | Les réglages montrent les valeurs de *Strict* (règle *Noms des notes sur le grattage*, *Accepter un accord sur son attaque* décoché). Après ton changement, **Vérification des accords** affiche *Personnalisé*. Rechoisis *Normal* : *Personnalisé* disparaît de la liste. | | |
| 8 | Réglage pris au prochain départ (FR-013) : dans *Configuration › Reconnaissance audio › Pratique — accords*, mets **Correspondance de l'accord** à sa valeur **la plus haute**, OK. **Sans relancer l'application**, lance *Attendre* et joue un accord bien joué. Puis remets la valeur (rechoisis *Normal* dans l'onglet *Mode*, ou **Rétablir** dans la catégorie) et rejoue-le. | Avec la valeur la plus haute, l'accord bien joué est refusé ou accepté beaucoup plus difficilement **dès ce départ** ; avec *Normal* revenu, il est de nouveau accepté. Ni reconstruction ni relance n'ont été nécessaires. | | |
| 9 | Accord juste après une note tenue. Dans une partition (par exemple une copie de `tests/basic_test.tg`, sans l'enregistrer), écris une note **liée** (ou en *let ring*) suivie d'un accord, par exemple *Am*. En *Attendre*, niveau *Normal* : joue la note, **laisse-la sonner** et joue l'accord aussitôt. | L'accord est accepté, même si la note tenue sonne encore. | | |
| 10 | Même passage : joue la note, **attends qu'elle s'éteigne**, puis joue l'accord. Refais les étapes 9 et 10 en *Suivre le tempo*. | L'accord est accepté dans les deux cas, dans les deux modes. | | |
| 11 | **Ton avis** : avec *Normal*, les bons accords sont-ils acceptés et les faux refusés ? Un accord incomplet (une note en moins) peut être accepté, et les accords graves (vers *sol* 2, par exemple *Gm* en bas du manche) sont plus difficiles : est-ce gênant pour toi ? | Ta réponse est écrite dans `final-report.md`. | | |

## Pour les testeurs Linux et Windows

La vérification des accords utilise le même code sur les trois systèmes, avec les deux moteurs ; elle n'a été essayée que sur macOS. Un retour dans une [issue](https://github.com/krl91/tuxguitaradvanced/issues), avec le système, sa version et l'entrée audio, est précieux.

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| X1 | Étapes 1 à 3 et 7 ci-dessus. | Les mêmes résultats ; dis ton entrée audio. | | |
