# F14 — Les fenêtres de pratique et d'ampli tiennent dans l'écran et disent quoi faire

- **Spec Kit** : `specs/025-practice-amp-ergonomics`
- **Tâche fermée** : T031 (porte de la spec 025).
- **Exigences** : FR-001 à FR-004 (la vérification du niveau repart, la ligne sous le bouton, *Vérifier à nouveau*), FR-005 et FR-006 (la fenêtre de pratique dans 768 px), FR-007 à FR-012 (la fenêtre de l'ampli en onglets, les nouveaux noms, les sons gardés) ; SC-001, SC-003, SC-005.
- **Matériel** : guitare, câble Rocksmith (ou ton interface), casque.
- **Partition** : `tests/demo-arcade-morceau.tg`.
- **Durée** : environ 20 minutes.

## Préparation

1. Lance l'application construite (relancée après la dernière construction), branche la guitare.
2. Ouvre `tests/demo-arcade-morceau.tg`.

## Étapes

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| 1 | Ouvre *Outils › Interactive Practice*, choisis l'entrée, **attends 15 s sans jouer**, puis joue quelques notes. | Pendant l'attente, une ligne sous **Travailler cette piste** dit quoi faire (par exemple « pas de signal : joue quelques notes »), sur **chaque** onglet. Quand tu joues, le bouton devient utilisable **en moins de 5 s**, **sans** avoir à rechoisir l'entrée. | | |
| 2 | Débranche le câble. | La ligne dit que l'entrée est perdue, avec **Vérifier à nouveau**. Rebranche, appuie sur **Vérifier à nouveau**, joue : le bouton redevient utilisable. | | |
| 3 | Rends la fenêtre de pratique basse (tire son bord du bas vers le haut) et passe sur chaque onglet (Piste, Entrée, Mode, Passages, Avancé). | Chaque onglet trop haut **défile** ; **Travailler cette piste** et **Fermer** restent toujours visibles. À l'ouverture, la fenêtre n'est jamais plus haute que l'écran. | | |
| 4 | Ouvre **Ampli et effets…**. | La fenêtre tient dans l'écran avec tous ses boutons. En haut : la case **Entendre ma guitare dans l'ampli**, la liste des sons avec **Enregistrer…** et **⋯**, le délai. Dessous, quatre onglets : *Pédales avant l'ampli*, *Ampli*, *Effets après l'ampli*, *Sortie*. | | |
| 5 | Coche **Entendre ma guitare dans l'ampli** (avec le casque) et joue. Dans l'onglet *Ampli*, trouve **Simulation du haut-parleur** et passe la souris dessus. | Le son d'ampli s'entend. L'infobulle dit ce que fait la simulation du haut-parleur. | | |
| 6 | Avec **Ajouter une pédale**, ajoute un *Chorus*, puis une *Distorsion*. | Chaque pédale apparaît dans son onglet, qui s'affiche tout seul ; la fenêtre garde sa taille, l'onglet défile s'il le faut. | | |
| 7 | **Enregistrer…** : tape `Lead`, puis un nom de plus de 40 caractères, puis `Mon son`, OK. | `Lead` et le nom trop long sont refusés avec la raison, OK reste grisé ; `Mon son` est enregistré et choisi. | | |
| 8 | **⋯ › Renommer…** : `Mon son 2`, OK ; puis **⋯ › Supprimer**. | Le son est renommé, puis supprimé ; *Clean* revient. | | |
| 9 | Ferme et rouvre **Ampli et effets…**. | Elle revient à la même place, à la même taille, jamais plus haute que l'écran ; tes réglages (case, sortie, volumes, son) sont gardés. | | |
| 10 | Passe le Mac en apparence claire puis sombre (Réglages Système › Apparence) et regarde les deux fenêtres. | La ligne sous le bouton et les onglets restent lisibles dans les deux apparences. | | |
| 11 | **Ton avis** : les deux fenêtres sont-elles maintenant claires et à la bonne taille ? | Ta réponse est écrite dans `final-report.md`. | | |

## Pour les testeurs Linux et Windows

Les deux fenêtres utilisent le même code sur les trois systèmes ; elles n'ont été regardées que sur macOS. Un retour dans une [issue](https://github.com/krl91/tuxguitaradvanced/issues), avec le système, sa version et la taille de l'écran, est précieux.

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| X1 | Étapes 1, 3 et 4 ci-dessus. | Les mêmes résultats ; dis la hauteur de ton écran. | | |
