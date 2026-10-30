# F06 — Les flammes de scène d'Arcade (gate G2 de la spec 009, porte T023 de la spec 018)

- **Spec Kit** : `specs/009-arcade-flames` (quickstart, étapes manuelles 2 à 7)
- **Tâche fermée** : T033 [HUMAN], gate G2. (T034, la mise à jour de STATUS, suit ton retour.)
- **Exigences** : FR-013 (réduire les animations), SC-006 pour le coût (mesuré à part, T029)
- **Matériel** : guitare.
- **Partition** : `tests/demo-arcade-morceau.tg`
- **Durée** : 20 minutes.

> **Spec 018 (2026-10-30) : les langues sont remplacées par le feu des démos Amiga.** Refais la fiche avec la nouvelle construction. Les étapes 1 à 9 restent valables (là où elles disent « langues », lis « feu »). Les étapes 10 à 14 regardent le nouveau look ; c'est la porte T023 (SC-001, SC-007).

## Étapes

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| 1 | Fenêtre de pratique, onglet **Mode** : **Flammes de scène** sur **Pleines**. Lance Arcade (niveau 5) et joue **bien** (presque tout juste). | Les flammes montent jusqu'à **pleines** vers 100 % de réussite. | | |
| 2 | Puis rate exprès plusieurs notes de suite. | À **50 % ou moins**, il ne reste que des **braises**. La descente prend **environ 3 secondes** (pas d'un coup). | | |
| 3 | Flammes pleines : lis les noms des cordes, les numéros de case, le badge de verdict et les textes du haut. Redimensionne la fenêtre (large, haute, petite). | Tout reste **facile à lire** dans toutes les formes de fenêtre. | | |
| 4 | Essaie **Désactivées**, puis **Discrètes**, puis **Pleines**. | Désactivées : **rien** n'est dessiné. Discrètes : **plus basses et plus pâles** que Pleines. | | |
| 5 | Change le réglage **pendant** une partie. | Le changement s'applique à **la partie suivante**. | | |
| 6 | Quitte et relance l'application. | Le choix est-il gardé ? Attendu : il est gardé **jusqu'à la fermeture** de l'application. Note ce que tu vois après la relance. | | |
| 7 | **Pause** pendant que les flammes sont hautes, attends, puis **Reprendre**. | En pause, les flammes gardent **leur niveau et leur mouvement**. Après la reprise, pendant le nouveau décompte, le niveau **reste tenu** et les langues **bougent encore**. Ensuite il repart du même niveau. | | |
| 8 | Active **Réglages Système › Accessibilité › Affichage › Réduire les animations**, lance une partie. | Les langues **ne bougent plus**, seule leur **hauteur** suit le jeu. (C'est la toute première fois que ce réglage est essayé : note bien ce que tu vois.) Pense à désactiver le réglage ensuite. | | |
| 9 | Change le **look** du jeu (s'il y en a plusieurs : clair, sombre, monochrome). | Les flammes gardent **leurs couleurs** dans chaque look. | | |
| 10 | **Pleines**, joue bien jusqu'à ce que le feu soit haut. Regarde-le de près. | Un feu en **gros pixels carrés**, du rouge sombre au blanc vers la base, qui **monte et scintille** comme le feu des démos Amiga (« variante A »). Plus haut dans les coins, faible au milieu. | | |
| 11 | Regarde le manche, les notes et les cordes **à travers** le feu. | Le jeu **se voit à travers** le feu partout (jamais plus de 85 % d'opacité) : **pas de bande noire**, rien de caché. | | |
| 12 | Enchaîne plusieurs notes justes, puis une série longue. | À chaque note juste, une **grosse gerbe d'étincelles** (20 à 50) part du haut du feu vers un coin et s'éteint en **moins d'une seconde** ; plus nombreuses avec une série plus longue. Une fausse note ou une note manquée n'en lance aucune. | | |
| 13 | **Discrètes**, puis **Désactivées**. | Discrètes : feu **plus bas et plus pâle**, moins d'étincelles. Désactivées : **rien**. | | |
| 14 | Joue une minute en **Pleines** en regardant la fluidité. | L'image reste **fluide** (pas de saccade due au feu). Si elle saccade, note-le : la qualité peut baisser d'elle-même (gros pixels doublés, sans étincelles). | | |

## À me dire

- Gate G2 passée : oui / non.
- Discrètes par défaut : ça te va ?
- Spec 018 : le look (étapes 10 à 12) est-il celui de la démo ? Taille des pixels, couleurs, transparence à changer ?
