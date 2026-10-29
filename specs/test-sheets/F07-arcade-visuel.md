# F07 — Arcade : ce qu'on voit à l'écran

- **Spec Kit** : `specs/003-arcade-mode` (quickstart, sections « Manual check… »)
- **Tâches fermées** : T106 (palette, corde allumée, corde à vide), T111 (rectangles arrondis, fil, empreinte, bande), T117 (panneau d'accord), T134 (bends), T136 (marques de techniques), T138 (atterrissage des accords et des barres à vide), T144 (décompte, reprise, textes du haut, résultat sur un arrêt). La partie G sert au point de contrôle T059. Et une tentative de reproduire **T145** (blocage après plusieurs lancements).
- **Exigences** : FR-067 à FR-072, FR-074, FR-077, FR-079 à FR-082, SC-025, SC-027, SC-028 ; questions Q37 à Q42.
- **Pas dans cette fiche** : T044 (planche des 32 captures réelles), qui attend d'abord le travail de code T066.
- **Matériel** : guitare.
- **Partitions** : `tests/demo-arcade-morceau.tg` (notes, accords, cordes à vide), `tests/demo-techniques.tg` (bends, hammer-on, slide…), `misc/tuxguitar.tg` (multipiste).
- **Durée** : 40 minutes.

## A — Palette et corde allumée (T106 ; FR-067, FR-068, SC-025)

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| A1 | Lance Arcade sur une piste à 6 cordes. Regarde les cordes, du haut au bas du manche. | **rouge, jaune, bleu clair, orange, vert, violet**. Le violet est la corde **la plus aiguë**. | | |
| A2 | Quand aucun événement n'est actif, puis quand une note arrive. | Sans événement : cordes **atténuées**. Note active : **sa corde brille** (avec un halo), les autres restent atténuées. Accord : **toutes ses cordes** brillent ensemble. La lumière s'éteint au verdict. | | |
| A3 | Passe au look **monochrome** et en qualité **PERFORMANCE** (si tu as accès au réglage). | La corde visée reste **visiblement plus claire ou plus épaisse**. | | |

## B — Corde à vide (T106 ; FR-069)

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| B1 | Une note à vide (case 0) arrive. | Une **fine barre horizontale** de la couleur de sa corde, aussi large que les couloirs, avec le mot **open** ; **pas** de disque, **pas** de numéro de case. | | |
| B2 | Elle atterrit ; puis un accord contenant une corde à vide. | Sa corde s'allume. Dans un accord, la corde à vide est une **ligne en travers du panneau**, allumée avec l'accord. | | |

## C — Rectangles arrondis, fil, empreinte, bande (T111 ; FR-070 à FR-072, SC-027)

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| C1 | Regarde une note frettée arriver. | Un petit **rectangle arrondi plein**, couleur de sa corde, **au-dessus** de son couloir, relié par un **fil fin** à une **empreinte** sur le couloir, avec son **numéro de case** dessous. | | |
| C2 | Sur le manche. | La cible est un rectangle arrondi qui **remplit la case**, sur la corde. Le numéro de cette case est **mis en évidence** sous le manche. | | |
| C3 | La zone d'approche. | Une **bande bleue translucide** éclaire la zone d'approche. Le couloir de la case active est **un peu plus clair**. Les deux suivent les couloirs **jusqu'au pied du manche**, où la prochaine note ou le prochain accord atterrit. | | |
| C4 | Ton avis général sur les nouveaux graphismes. | Beaux et lisibles ? | | |

## D — Panneau d'accord (T117 ; FR-074, SC-028)

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| D1 | Un accord arrive. | Un **panneau translucide** en travers du couloir, avec les rectangles de ses notes à leurs cases et cordes, et **son nom** au-dessus du coin gauche. | | |
| D2 | Plusieurs accords à la suite. | Des panneaux **en perspective**. L'actif atterrit sur le manche, ses cordes s'allument, ses rectangles portent les **numéros de case**. | | |
| D3 | | **Aucune** ancienne barre ni cartouche de diagramme ne reste. | | |

## E — Bends et techniques (T134, T136 ; FR-077)

Utilise `tests/demo-techniques.tg`.

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| E1 | Un bend arrive, puis atterrit. | Une **flèche en pointillés** avec la **valeur** du bend, et un **rectangle fantôme** à la case du sommet, en vol et sur le manche. Lisible ? | | |
| E2 | Hammer-on, pull-off, slide. | Un fantôme et un **arc** (hammer/pull) ou un **rail** (slide) entre les deux notes. | | |
| E3 | Vibrato, palm mute, tapping, trémolo, harmonique. | **Vague** pour le vibrato, **pastilles** pour le palm mute et le tapping, **traits** pour le trémolo, marque pour l'harmonique. Chacune est-elle compréhensible sans légende ? | | |

## F — Atterrissage, décompte, fenêtre (T138, T144 ; Q37 à Q42, FR-079 à FR-082)

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| F1 | Regarde des accords, des barres à vide et des notes atterrir. | Tous atterrissent **au pied du manche au même instant**. Les rectangles d'un accord tombent sur leurs cibles à l'attaque (Q37). | | |
| F2 | Lance Arcade depuis le **curseur**, puis depuis le **début**, plusieurs fois de suite. | Pendant les clics du décompte, les notes et le premier accord **viennent du fond**, jamais déjà posés sur le manche. Le premier événement **ne saute pas** à la fin du décompte (Q38). | | |
| F3 | **Pause** pendant le décompte, puis pendant la musique, puis **Reprendre**. | L'image est figée en pause. Le métronome refait un décompte, les notes reviennent du fond. Les boutons marchent **autant de fois** qu'on appuie (Q38, Q41). | | |
| F4 | Redimensionne la fenêtre dans plusieurs formes. | La **série**, la **vitesse** et **Suivant** restent **entièrement visibles** (Q39). | | |
| F5 | Laisse une partie aller au bout **sans jouer** ; arrête-en une autre **avant la fin**. | La fenêtre de **résultat** s'affiche les deux fois. **Depuis le début** rejoue depuis la première mesure ; **Depuis la mesure** respecte la mesure choisie (Q40). | | |
| F6 | Pendant le jeu, regarde la barre de transport. | Le bouton note **n'est pas encadré** et **aucune** fenêtre de préparation ne passe par-dessus le jeu (Q41). | | |

## G — Accompagnement et résultat (quickstart 003, étapes 1, 2 et 6 ; partie du point de contrôle T059)

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| G1 | `misc/tuxguitar.tg` (multipiste) : choisis la piste guitare, Arcade à **70 %**. | Décompte, les autres pistes jouent, la piste choisie est muette. Les erreurs n'arrêtent pas le morceau. | | |
| G2 | Quitte Arcade, regarde les boutons muet/solo des pistes. | **Exactement comme avant** Arcade. | | |
| G3 | Le résultat à la fin. | Score, comptes, meilleure série. Rien pendant le jeu. Rien de gardé après la fermeture. | | |

## H — Le blocage après plusieurs lancements (T145)

Tu avais signalé que le jeu semblait se bloquer après 3 ou 4 lancements de suite. Je n'ai pas réussi à le reproduire (14 lancements essayés). **Note ta suite exacte de clics**, par exemple : « bouton pyramide → Oui → grattage → jeu 20 s → Stop → Depuis le début → … ».

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| H1 | Lance et arrête Arcade **6 fois de suite**, comme tu le fais d'habitude. | Pas de blocage. Si ça bloque : **ne ferme rien**, dis-le-moi tout de suite (je lirai l'état des threads sur l'application encore ouverte), avec ta suite de clics. | | |
