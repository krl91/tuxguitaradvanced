# F01 — Le mode « Suivre le tempo »

- **Spec Kit** : `specs/002-practice-industrialization` (Phase 9 / quickstart « Phase E »)
- **Tâches fermées** : T102 (vérification de clôture de la phase E). Et, si tout est OK, cela permet de cocher T096 (lecteur comme horloge et accompagnement), T097 (retard et calibrage du rythme), T098 (sélecteur de mode, pourcentage, retour par note). T141 est un test automatique, il n'est pas dans cette fiche.
- **Exigences** : FR-006, FR-054, US2b ; objectifs SC-004, SC-009 (connus comme **non atteints** au-delà de 2 notes par seconde, ce n'est pas ce qu'on teste ici).
- **Pourquoi c'est prioritaire** : la sortie officielle d'Arcade dépend de ta validation de ce mode (spec 003, T001).
- **Matériel** : guitare, adaptateur Rocksmith, puis le micro intégré du Mac. Casque conseillé avec le micro intégré.
- **Partition** : `tests/1/_TEST_verifie.tg` (notes et accords, tempo modéré), ou une partition multipiste à toi avec une piste guitare.
- **Durée** : environ 30 minutes.

## Préparation

1. Lance l'application, ouvre la partition.
2. Branche la guitare par le Rocksmith.
3. Ouvre la pratique : bouton **Pratique** de la barre d'outils (à droite de Lecture/Stop), ou Outils › Pratique interactive.
4. **Joue quelques notes** : le bouton de départ reste grisé tant que l'entrée n'a pas entendu la guitare (niveau pas encore vérifié). Depuis le 2026-10-29, une ligne sous la position de départ le dit.

5. **Réglages faciles (depuis le 2026-10-29)** : onglet **Mode**, **Fenêtre de temps** à **200 ms** et **Tempo** à **50 %**. Puis **Calibrage du rythme › Calibrer…** **avec le Rocksmith** (8 clics, une note sur chaque clic). Sans calibrage, toutes les notes peuvent être jugées avec un même décalage. L'étape 1 ci-dessous garde ces réglages, au lieu de 100 ms et 70 %.

> Premier essai du 2026-10-29 : étape 2 KO, « le bouton est grisé ». Cause : aucun son n'était encore arrivé sur l'entrée. Corrigé : le bouton reste grisé mais c'est expliqué (`GreyedStartExplainedTest`), et la préparation dit maintenant de jouer quelques notes. **Refais la fiche depuis l'étape 1 avec la nouvelle construction.**
>
> Deuxième essai du 2026-10-29 : étape 3 KO, la reconnaissance est trop stricte, pour la note comme pour le moment (Rocksmith, rythme **pas calibré**, 100 ms, 70 %). Suite : la fiche est refaite avec les réglages faciles ci-dessus pour valider le reste du mode, et la spec 016 traite la permissivité.

## Étapes

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| 1 | Onglet **Piste** : choisis la piste guitare. Onglet **Entrée** : choisis le Rocksmith. Onglet **Mode** : choisis **Suivre le tempo**, **Fenêtre de temps** sur la valeur par défaut (100 ms), **Tempo** à **70 %**. | Le bouton de départ devient **« Suivre le tempo de cette piste »**. |OK | |
| 2 | Appuie sur ce bouton. Réponds à la fenêtre « Avant de commencer » (*Oui, accordée*, puis gratte un accord à un niveau correct). | Un **décompte** est joué. La piste travaillée est **muette**, les autres pistes jouent. |OK | |
| 3 | Joue avec la partition : quelques notes à l'heure, une en avance exprès, une en retard exprès. | Chaque note est marquée **✓ À l'heure**, **✓ Bonne note, en avance (xx ms)** ou **✓ Bonne note, en retard (xx ms)**. |ko | trop difficile de faire la bonne note au bon moment quelque chose ne va pas, le moteur de reconnaissance de note n'est pas assez permissif que ce soit pour la note comme le moment où il faut jouer la note|
| 4 | Joue une note qui n'est pas dans la partition (entre deux notes écrites). | Elle est marquée **+ Note en trop**. | | |
| 5 | Erreur exprès : joue une mauvaise note, puis saute une note. | Marquées **✗ Mauvaise note** et **✗ Manquée**. Les notes **suivantes** sont toujours jugées à leur propre heure (l'erreur ne décale pas la suite). | | |
| 6 | Sur un accord écrit, gratte l'accord. Sur un bend écrit (s'il y en a un), ne le fais pas. | L'accord est jugé **comme un accord** (pas note par note). La technique a sa **ligne à part**. Un bend non fait ne **bloque rien**. | | |
| 7 | Pendant le jeu, débranche le Rocksmith (ou ouvre l'accordeur). | La pratique dit **En pause : …**. Les notes qui passent sont **– Non jugée**. Rebranche (ou ferme l'accordeur) : **« Prêt à reprendre… »**, puis **Reprendre** relance. | | |
| 8 | Arrête. Remets le **Tempo à 100 %** et rejoue jusqu'à la fin de la piste. | À la fin, le **Bilan de cette exécution** liste : à l'heure / en avance / en retard / mauvaise note / manquées / notes en trop / non jugées, et les **techniques à part**. | | |
| 9 | Change l'entrée pour le **micro intégré**. Onglet Mode › **Calibrage du rythme** › **Calibrer…** | Un message annonce **8 clics, environ 10 secondes**. Joue une note sur chaque clic : à la fin, **« Calibré : xx ms (à partir de n notes) »**. | | |
| 10 | Relance **Calibrer…** et appuie sur **Annuler** au 3ᵉ clic. | Les clics **s'arrêtent tout de suite**. « Calibrage annulé. Rien n'a été modifié. » | | |
| 11 | Appuie sur **Oublier** (calibrage), puis lance une exécution au micro intégré. | Le message **« Précision rythmique réduite »** s'affiche, et le mode **fonctionne quand même**. | | |
| 12 | Règle le **Retard (ms)** à la main (par exemple 80), **Appliquer**, rejoue un passage. | Les verdicts en avance / en retard se décalent dans le sens attendu. L'état dit **« Réglé à la main : 80 ms »**. | | |
| 13 | Ouvre l'accordeur, puis reviens en mode **Attendre la bonne note**. | L'accordeur et le mode Attendre **ne dépendent pas** du calibrage (rien ne change pour eux). | | |
| 14 | Pendant une exécution *Suivre le tempo*, appuie sur **Lecture** dans la barre de transport. | Le suivi s'arrête avec le message **« Le suivi du tempo s'est arrêté parce que vous avez lancé la lecture de la partition. La lecture normale continue. »** | | |
| 15 | Reviens en **Attendre la bonne note** et joue quelques notes. | Le mode Attendre se comporte **exactement comme avant**. | | |

## Ta décision finale (à me dire)

- Ce mode est-il assez bon pour que **la sortie d'Arcade ne soit plus bloquée** (003 T001) ? Oui / Non / Oui avec réserves (lesquelles).
- À 70 % puis à 100 %, les verdicts « à l'heure / en avance / en retard » te paraissent-ils justes ?
