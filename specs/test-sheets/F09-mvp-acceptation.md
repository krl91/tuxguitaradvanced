# F09 — Recette du MVP (pratique note par note, accordeur, entrée)

- **Spec Kit** : `specs/001-interactive-practice-mvp` ; les résultats seront reportés dans `checklists/acceptance.md`
- **Tâches fermées** : T049 (US1), T054 (US2), T062 (US3), T069 (US4), T072 (US5), T075 (US6), T096 (US7), T097 (bug de la fenêtre qui débordait, contrôle P1), T082 (la recette complète, SC-009)
- **Matériel** : guitare, Rocksmith, micro intégré, casque.
- **Partitions** : `tests/basic_test.tg`, `misc/tuxguitar.tg`, `tests/demo-techniques.tg`, une partition avec une **reprise** (barres de reprise), pour US6.
- **Durée** : 60 à 90 minutes. Tu peux la faire **en plusieurs fois**, partie par partie.
- **Comportements qui ont changé depuis la spec** (l'attendu ci-dessous est déjà corrigé) :
  - les **accords** sont maintenant jugés (US7), ils ne sont plus « non gérés » (US5.1) ;
  - un **bend** écrit doit être fait (ta décision du 2026-10-07), il n'est plus passé automatiquement (US5.3) ;
  - chaque départ passe par la fenêtre **Avant de commencer** (spec 010) : réponds *Oui, accordée* et gratte un accord.

## US1 — Travailler une mélodie note par note (T049)

Déjà OK le 2026-09-27 : ouverture, piste, accordage, entrée, contrôle de niveau, vue de départ, Stop. Il reste ce qui demande la guitare :

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| 1.1 | Mode Attendre sur `basic_test.tg` (Rocksmith). Joue la note demandée. | Elle passe en **vert** et le cadre rouge passe à l'événement suivant. | | |
| 1.2 | Joue une mauvaise note, la bonne note **à la mauvaise octave**, puis rien. | Aucune n'avance. États : *incorrect*, *incorrect* (octave), *en attente*. | | |
| 1.3 | Deux notes identiques à la suite : **tiens** la première sans rejouer ; puis **repince**. | Tenir ne valide **pas** la deuxième. Repincer **la valide** (un repincement très faible peut demander de rejouer). | | |
| 1.4 | Pendant la pratique, déplace le curseur de la partition. | Le **cadre de pratique** est distinct du curseur, qui reste déplaçable. La pratique continue sur le même événement. | | |
| 1.5 | Pendant la pratique, appuie sur **Lecture** (MIDI). | La pratique se **met en pause**. Après l'arrêt de la lecture, **Reprendre**. | | |
| 1.6 | **F2** ouvre ou ramène la fenêtre de pratique. Avec la pratique au premier plan, essaie **⌘⇧S**, **⌘⇧K**, **⌘⇧R**, **⌘⇧X**. | ⌘⇧S **démarre**, ⌘⇧K **passe** l'événement, ⌘⇧R **reprend** après une pause, ⌘⇧X **arrête**. | | |
| 1.7 | Va jusqu'au bout de la piste. | « Session terminée », avec **Recommencer** ou **Quitter**. Recommencer repart de la **position de départ d'origine**. | | |
| 1.8 | Événement suivant hors de l'écran. | La partition **défile seule** pour le montrer. | | |

## US2 — Comprendre ce qui ne va pas (T054)

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| 2.1 | La partition attend **E4** (mi corde 1 à vide) : joue **F4** (case 1) un peu bas. | Affiché : attendu E4, **entendu F4** avec **son propre** écart en cents (bas), état *incorrect*. **Pas** d'écart mesuré par rapport à E4. | | |
| 2.2 | La partition attend **E3** : joue **E4**. | *incorrect*, avec une explication du type « bon nom de note, mauvaise octave ». | | |
| 2.3 | Joue la bonne note **légèrement désaccordée**. | Peut être acceptée, et l'affichage dit **si c'était trop haut ou trop bas**. | | |
| 2.4 | Joue le **demi-ton voisin**, même tiré vers la bonne note. | **Pas** acceptée. | | |
| 2.5 | Joue très faiblement, ou plusieurs cordes à la fois sur une note simple. | État **incertain** (« ? Impossible de savoir ce qui a été joué… »), **pas** présenté comme une erreur, pas d'avance. | | |
| 2.6 | Regarde la corde et la case affichées. | Présentées comme **l'instruction de la tablature**. Rien ne dit que la corde ou la case jouée a été vérifiée. | | |
| 2.7 | Joue une note avec vibrato ou palm mute écrit. | Acceptée par sa hauteur. Rien ne dit que la technique a été vérifiée. | | |

## US3 — Régler et surveiller l'entrée (T062)

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| 3.1 | Onglet **Entrée** : la liste des entrées. | Toutes les entrées sont proposées (micro intégré, Rocksmith…). Une interface n'est pas obligatoire. | | |
| 3.2 | Joue en regardant le vumètre. | Il dit trop faible / bon / trop fort / saturation. | | |
| 3.3 | Choisis le **micro intégré**. Puis le **Rocksmith**. | Micro : une recommandation **non bloquante** de mettre un casque. Rocksmith (entrée directe) : **pas** de recommandation. | | |
| 3.4 | Change d'entrée et joue. | Un contrôle de niveau **rapide** se fait tout seul. S'il est bon, tu peux continuer sans calibration séparée. | | |
| 3.5 | **Balayage du gain au micro intégré** : volume d'entrée macOS bas, moyen, haut. | Bas : averti (« niveau trop faible », indication d'augmenter le gain dans Réglages Système › Son › Entrée). Moyen : bon. Saturé : bloqué avec l'explication. | | |
| 3.6 | Pendant la pratique, **débranche** le Rocksmith. | Pause **automatique** sur l'événement en cours, explication (« L'entrée n'est plus disponible… »). Rebranche : elle reste en pause, dit qu'elle est prête, et ne reprend **que** sur **Reprendre**. | | |
| 3.7 | Choisis une entrée **muette** (ou un canal sans guitare). | Pas de départ, explication de ce qui manque. | | |
| 3.8 | Changer l'entrée dans l'accordeur, puis revenir à la pratique. | L'entrée est **la même** dans les deux, et un nouveau contrôle de niveau se fait. | | |

## US4 — Accordeur (T069)

Déjà OK le 2026-09-27 : menu, libellés français, entrée par défaut, Drop D, réouverture sans erreur, choix gardé, rendu. Il reste :

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| 4.1 | Accordeur en mode **chromatique**, joue des notes trop basses, justes, trop hautes. | L'aiguille va **à gauche** si trop bas, **en haut** si juste, **à droite** si trop haut, avec note, octave, cents, texte et **Hz**. | | |
| 4.2 | Réglages de l'accordeur. | Limités à : entrée, état du niveau, revérifier le niveau (FR-059). Plus d'options FFT. | | |
| 4.3 | Accordage **personnalisé** : change chaque corde. | Chaque corde à vide se choisit. Le choix tient jusqu'à la fermeture de l'application, pas au-delà. | | |
| 4.4 | Depuis la pratique : **Accorder la guitare**. | Le **même** accordeur s'ouvre, avec l'accordage **de la piste** annoncé comme venant de la piste. | | |
| 4.5 | Mets l'accordeur sur un autre accordage, reviens à la pratique. | « Remarque : l'accordeur est réglé sur un autre accordage… ». La partition et la piste sont **inchangées**. | | |
| 4.6 | Pendant une pratique, ouvre l'accordeur. | Pratique **en pause** (« l'accordeur est ouvert »). Accordeur fermé : même événement, reprise **seulement** sur Reprendre. | | |
| 4.7 | Joue trop faible, puis à saturation. | L'accordeur signale le **problème d'entrée** et ne donne **pas** d'indication d'accordage sûre. | | |

## US5 — Événements que le MVP ne valide pas (T072)

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| 5.1 | Une **note morte** (x) dans la partition. | Passée **automatiquement**, brièvement signalée, **sans** demander Passer. | | |
| 5.2 | Une **harmonique**, un **trille** ou un effet de **vibrato de barre**. | Note ce que tu vois. Attendu de la spec : passée automatiquement, signalée comme non gérée, **jamais** affichée comme juste. | | |
| 5.3 | Sur n'importe quel événement : **Passer**. | On avance. L'événement est marqué **passé**, jamais *juste*. | | |

## US6 — Partir d'une position choisie (T075)

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| 6.1 | Mets le curseur **sur une note** de la piste, ouvre la pratique. | La préparation dit « Commence au curseur de la partition, mesure n… » et la pratique part **de cette note**. | | |
| 6.2 | Curseur sur un **silence** de la piste. | Départ au **premier événement à ou après** le curseur. | | |
| 6.3 | Pas de curseur utilisable sur la piste (curseur sur une autre piste). | Départ au **début de la piste**. | | |
| 6.4 | Curseur **au milieu d'une reprise**. | Départ **exactement là**, sans revenir au début de la reprise. Ensuite, l'ordre suit celui de la lecture (la reprise est rejouée). | | |
| 6.5 | Arrête la pratique. | Édition et lecture **normales**. Le morceau est **inchangé**. | | |

## US7 — Pratiquer depuis la partition, accords compris (T096)

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| 7.1 | Regarde la partition pendant la pratique. | **Exactement** la note attendue est encadrée en rouge sur la portée **et** sur la tablature. | | |
| 7.2 | Joue-la juste ; puis joue faux. | Juste : flash **vert**, le cadre avance. Faux : le cadre **ne bouge pas**. | | |
| 7.3 | Un accord arrive. Gratte-le en entier. | Tout l'accord est encadré. Accepté : flash vert et avance. | | |
| 7.4 | Mi majeur **sans** la corde de sol (G#3). | L'accord reste encadré et la palette dit **« ✗ Non entendu : G#3… »**. (Connu : ce refus n'est pas fiable, 2 accords incomplets sur 12 passent encore.) | | |
| 7.5 | La palette pendant la pratique. | Une **palette compacte** déplaçable (note ou état de l'accord, cents, statut, boutons). La fenêtre de préparation est cachée. | | |
| 7.6 | Arrête. | Les cadres disparaissent. Rendu normal. | | |
| 7.7 | Accords E, A, D, G, C, Am, Em grattés juste, puis Am joué à la place de E. | Justes : ✓. Am sur E : ✗. Silence : rien. Note les **attentes à tort** et les **acceptations à tort**. | | |

## Contrôles en attente du 2026-09-28 (P1 à P21 de `checklists/acceptance.md`)

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| P1 | Choisis le micro, puis une autre entrée, puis revient (**T097**). | Démarrer / Accorder la guitare / Fermer restent **visibles sans redimensionner**. Les lignes longues passent à la ligne. La fenêtre reste dans la fenêtre principale. | | |
| P2 | Onglet **Passages** : « Répéter les passages » et « Répéter quand les erreurs dépassent ». | Off / chaque mesure / toutes les 2 / toutes les 4 (2 par défaut) ; 25 / 50 / 75 % (50 par défaut). Le seuil se grise sur Off. Tout tient sans redimensionner. | | |
| P3 | Change les deux, ferme et rouvre la pratique ; puis relance TuxGuitar. | Choix gardé tant que TuxGuitar tourne. Après relance : **2 mesures / 50 %**. | | |
| P4 | Démarre avec les passages actifs. | La palette dit « Passage n sur N (mesures a–b) · essai 1 sur 3 · erreurs 0 sur j ». Tous les boutons visibles. | | |
| P5 | Démarre avec les passages sur Off. | Pas de ligne de passage. | | |
| P6 | Accordeur : laisse une corde s'éteindre ; puis change de mode. | Aiguille **grise et immobile**, « ⏸ Dernière lecture (tenue) ». Effacé au changement de mode. | | |
| P7 | Notes simples : pince chaque note une fois ; tiens une note pendant que la suivante est attendue ; silence ; tape sur le corps de la guitare. | Chaque pincement net : ✓ vert. Note tenue, silence et tapes : **n'avancent jamais**. Note les attentes à tort. | | |
| P9 | Passages 2 mesures / 50 % : rate plus de la moitié d'un passage. | À sa dernière note, le cadre rouge et la vue **reviennent** à sa première note. « ↻ Encore … (plus de 50 %) ». La dernière note qui sonne n'accepte pas la première. | | |
| P10 | Même passage avec **exactement** la moitié ratée ; puis rate-le **trois fois**. | La moitié : on continue (« Dernier passage réussi »). Au 3ᵉ échec : « » On continue après 3 essais ». | | |
| P11 | Seuils **25 %** et **75 %** une fois chacun ; finis le morceau. | Les répétitions suivent le seuil. Fin : bilan (du premier coup / après répétition / non réussis avec les mesures / répétitions). Recommencer l'efface. | | |
| P12 | Une section entre barres de reprise : rate **seulement** son deuxième passage. | Seul le **deuxième** passage est répété. | | |
| P13 | **Réglages › Reconnaissance audio** : parcours chaque catégorie. | Page en français. Chaque catégorie tient sans redimensionner. Chaque réglage apparaît **une seule fois**. Chaque curseur met à jour **sa propre** valeur pendant qu'il bouge. | | |
| P14 | Au **clavier seul** : Tab, flèches sur un curseur, une liste, une case. | Tout est atteignable et modifiable au clavier. | | |
| P15 | Change des valeurs, **Annuler**, rouvre. | Anciennes valeurs (rien d'enregistré). | | |
| P16 | Change des valeurs, **OK**, relance TuxGuitar, rouvre. | Valeurs **gardées**. | | |
| P17 | « Restaurer les valeurs recommandées de cette section », puis « … toutes », OK. | Section, puis tout, revenus aux valeurs recommandées. Le bouton **Défauts** du dialogue le fait aussi. | | |
| P18 | Ouvre et ferme le dialogue des réglages **10 fois** en changeant de section. | Pas d'erreur. La fenêtre ne grandit pas au-delà de l'écran. | | |
| P19 | Accordeur ouvert : change « Plage juste » et « Garder la dernière note », OK. | L'accordeur suit **tout de suite**. | | |
| P20 | Monte « Niveau minimal utilisable » à **−25 dBFS** pendant que l'accordeur écoute ; puis restaure. | Bref redémarrage, puis un jeu doux affiche « trop faible ». Après restauration : normal. | | |
| P21 | Change les valeurs de passage dans les Réglages, la pratique ouverte. Puis Aide › Reconnaissance audio. | Les listes de passages de la pratique montrent les nouvelles valeurs. La page d'aide s'ouvre. | | |

(P8 est couvert par la ligne 7.7.)
