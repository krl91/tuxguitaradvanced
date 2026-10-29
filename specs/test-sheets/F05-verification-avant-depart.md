# F05 — La vérification avant le départ (gate G)

- **Spec Kit** : `specs/010-pre-run-check` (quickstart, étapes manuelles 1 à 8)
- **Tâche fermée** : T034 [HUMAN], gate G
- **Exigences** : SC-006 ; décision R4 (les redémarrages ne redemandent pas)
- **Matériel** : guitare et **adaptateur Rocksmith** (son volume d'entrée se règle dans Réglages Système › Son › Entrée).
- **Partition** : `tests/basic_test.tg`
- **Durée** : 20 minutes.

## Étapes

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| 1 | Mets le volume d'entrée du Rocksmith **au maximum (0,99)**. Ouvre la partition, lance le mode **Attendre**. Réponds **Oui, accordée**. Gratte un accord **fort**. | La fenêtre **Avant de commencer** dit **« Le signal sature : le son est trop fort et déformé. »** et dit quoi baisser. Le mode **ne démarre pas**. | | |
| 2 | Appuie sur **Continuer quand même**. | Le mode démarre, **pour ce départ seulement**. | | |
| 3 | Arrête, relance Attendre. | La fenêtre **redemande** (question d'accordage, puis le grattage). | | |
| 4 | Baisse le volume d'entrée vers **0,6**, gratte. | **« Le niveau est bon. »** et le mode démarre après le grattage. | | |
| 5 | Depuis la fenêtre de pratique, lance **Suivre le tempo**, puis **Arcade** ; puis lance Arcade depuis le **bouton pyramide** de la barre de transport. | Chaque fois, **la même** fenêtre Avant de commencer s'ouvre. | | |
| 6 | Réponds **Non, ouvrir l'accordeur**. | L'accordeur s'ouvre, le mode **ne démarre pas**. Ferme l'accordeur : la fenêtre revient **à la question**. | | |
| 7 | Réponds Oui, puis **ne gratte rien pendant 10 secondes**. | La fenêtre reste sur **« En attente de votre grattage... »** et ne dit **jamais** que le niveau est bon. | | |
| 8 | Va jusqu'au résultat d'une exécution, rejoue depuis la fenêtre de résultat, puis utilise **Recommencer**. | **Pas de nouvelle question** (décision R4). **Dis-moi si tu veux qu'elle revienne.** | | |
| 9 | Appuie deux fois très vite sur le bouton de départ. Puis, la fenêtre Avant de commencer ouverte, appuie sur le bouton Arcade de la barre de transport. | **Une seule** fenêtre de départ et **un seul** démarrage. | | |
| 10 | La fenêtre de départ ouverte, change de mode ou ferme la fenêtre de pratique. | La fenêtre de départ se ferme et **rien ne démarre**. | | |
| 11 | Ferme la fenêtre de départ avec sa croix ; rouvre-la et ferme-la avec **Échap**. | Rien ne démarre, et le bouton de départ remarche **tout de suite**. | | |
| 12 | Joue les **trois accords de la fin** de `basic_test.tg` en mode Attendre, avec un niveau bon. | Dis-moi s'ils sont reconnus **plus souvent qu'avant** la spec 010. | | |

## À me dire

- Gate G passée : oui / non.
- Les redémarrages sans nouvelle question te conviennent-ils (étape 8) ?
