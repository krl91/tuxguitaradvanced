# Manuel d'utilisation de TuxGuitarAdvanced

*English: [MANUAL.md](MANUAL.md).*

TuxGuitarAdvanced est une version avancée de [TuxGuitar](https://www.tuxguitar.app/), l'éditeur et lecteur de tablatures multipistes. Il garde tout ce que fait TuxGuitar (**édition et création** de partitions, lecture, import et export de nombreux formats) et ajoute le **Practice** (la pratique interactive : l'application écoute votre guitare et vérifie ce que vous jouez), l'**accordeur stable** et des outils d'analyse de partition.

Ce manuel a trois parties :

* **[Partie A : édition et création](#partie-a--édition-et-création)** : écrire, modifier, écouter et exporter une partition ;
* **[Partie B : le Practice](#partie-b--le-practice-pratique-interactive)** : travailler une partition avec votre vraie guitare, dans trois modes (Attendre la bonne note, Suivre le tempo, Arcade), plus l'accordeur et les réglages de reconnaissance ;
* **[Partie C : un exemple complet](#partie-c--un-exemple-complet-pas-à-pas)** : d'un document vide à un morceau de trois pistes (guitare, basse, batterie), puis travaillé dans les trois modes.

*Les captures d'écran viennent de la version macOS, interface en français, thème sombre. Sous Linux et Windows, les fenêtres sont les mêmes ; les raccourcis qui utilisent ⌘ sous macOS utilisent Ctrl.*

## Sommaire

* [Installation et premier lancement](#installation-et-premier-lancement)
* **Partie A : édition et création**
  * [A1. La fenêtre principale](#a1-la-fenêtre-principale)
  * [A2. Les menus](#a2-les-menus)
  * [A3. Créer un premier morceau](#a3-créer-un-premier-morceau)
  * [A4. Saisir des notes](#a4-saisir-des-notes)
  * [A5. Les effets de jeu](#a5-les-effets-de-jeu)
  * [A6. Les accords](#a6-les-accords)
  * [A7. La structure du morceau](#a7-la-structure-du-morceau)
  * [A8. Les pistes, les instruments et les paroles](#a8-les-pistes-les-instruments-et-les-paroles)
  * [A9. L'affichage](#a9-laffichage)
  * [A10. La lecture](#a10-la-lecture)
  * [A11. Les fichiers : ouvrir, enregistrer, importer, exporter, imprimer](#a11-les-fichiers)
  * [A12. Les outils et la configuration](#a12-les-outils-et-la-configuration)
  * [A13. Raccourcis clavier](#a13-raccourcis-clavier)
* **Partie B : le Practice**
  * [B1. Principe et prérequis](#b1-principe-et-prérequis)
  * [B2. La fenêtre Pratique interactive](#b2-la-fenêtre-pratique-interactive)
  * [B3. Niveau d'entrée, gain et bruit](#b3-niveau-dentrée-gain-et-bruit)
  * [B4. Mode « Attendre la bonne note »](#b4-mode--attendre-la-bonne-note-)
  * [B5. Mode « Suivre le tempo »](#b5-mode--suivre-le-tempo-)
  * [B6. Mode Arcade (alpha)](#b6-mode-arcade-alpha)
  * [B7. Les réglages de reconnaissance audio](#b7-les-réglages-de-reconnaissance-audio)
  * [B8. L'accordeur](#b8-laccordeur)
  * [B9. Analyse de la partition (mesures invalides)](#b9-analyse-de-la-partition-mesures-invalides)
  * [B10. Qualité mesurée, limites et trace de diagnostic](#b10-qualité-mesurée-limites-et-trace-de-diagnostic)
  * [B11. Questions fréquentes et dépannage](#b11-questions-fréquentes-et-dépannage)
* **[Partie C : un exemple complet, pas à pas](#partie-c--un-exemple-complet-pas-à-pas)**

---

## Installation et premier lancement

* **Paquets :** macOS 15 Sequoia ou plus récent (Apple Silicon), Linux et Windows sont publiés dans les [Releases](https://github.com/krl91/tuxguitaradvanced/releases) du projet. Les paquets de chaque version ne sont pas forcément tous présents : la page de la release dit lesquels le sont. Le paquet Windows demande Java 17 ou plus ; les paquets macOS et Linux embarquent leur Java.
* **macOS :** le paquet est signé avec un certificat auto-créé et n'est pas notarisé. Au premier lancement : décompressez, déplacez `TuxGuitarAdvanced.app` dans *Applications* et double-cliquez dessus ; macOS indique qu'il n'a pas pu vérifier l'app : cliquez sur **Terminé** (pas sur *Placer dans la corbeille*). Ouvrez ensuite *Réglages Système > Confidentialité et sécurité*, descendez jusqu'à la section *Sécurité*, cliquez sur **Ouvrir quand même** à côté de « TuxGuitarAdvanced a été bloqué… » et confirmez avec votre mot de passe ou Touch ID. Ensuite l'app s'ouvre normalement. Si macOS dit que l'app est *endommagée* (une version antérieure à 0.6.0-beta.3), téléchargez une version plus récente, ou lancez une fois `xattr -cr /chemin/vers/TuxGuitarAdvanced.app` dans un Terminal. La première fois que la pratique écoute le micro, macOS demande l'autorisation d'accès au micro : acceptez.
* **Compiler soi-même :** voir [INSTALL.md](INSTALL.md).
* **Fichiers :** le format natif est `.tg`. L'application ouvre aussi les formats Guitar Pro (`.gp3`, `.gp4`, `.gp5`, `.gpx`, `.gp`), Power Tab (`.ptb`), TablEdit (`.tef`), MIDI, et exporte vers de nombreux formats (voir [A11](#a11-les-fichiers)).
* **Aide intégrée :** la touche **F1** ouvre l'aide de TuxGuitar (en anglais, avec des pages sur la reconnaissance audio en français et en espagnol).

---

# Partie A : édition et création

## A1. La fenêtre principale

<img src="images/manual/main-window.png" width="900" alt="La fenêtre principale de TuxGuitarAdvanced avec une petite partition">

La fenêtre principale se compose, de haut en bas :

1. **La barre de menus** (Fichier, Édition, Affichage, Composition, Piste, Mesure, Note, Marqueur, Lecteur, Outils, Aide).
2. **La barre d'outils principale** : fichiers, annuler/rétablir, affichage, zoom, lecture. Le bouton ♪ à côté de Lecture est le bouton **Practice** (une pression : pratique de la piste sous le curseur, voir [B2](#b2-la-fenêtre-pratique-interactive)).
3. **La barre d'outils latérale** (« Éditer ») : modes d'édition, structure, durées, nuances, effets, notes.
4. **La zone de partition** : la portée (notation) au-dessus et la **tablature** (une ligne par corde) dessous. Le rectangle gris est le **curseur**, c'est à lui que s'appliquent les actions. Les onglets au-dessus de la partition sont les fichiers ouverts.
5. **La liste des pistes**, avec le volume principal, et le « transporteur » (une case par mesure) pour se déplacer dans le morceau.
6. **Les commandes du manche** et le **manche** (la partie noire en bas) : il montre où poser les doigts et permet aussi de saisir en cliquant.

<img src="images/manual/toolbar.png" width="900" alt="La barre d'outils principale">

*Barre d'outils principale : nouveau, ouvrir, enregistrer, enregistrer sous, aperçu, imprimer, annuler, rétablir, affichages (barre latérale, liste des pistes, manche, instruments), zoom, mode multipiste, clavier, matrice, ajouter/supprimer une mesure, marqueur ; au centre, les commandes de lecture, le temps, le tempo, le métronome, le décompte et le mode de lecture.*

Les barres d'outils, la liste des pistes et le manche se masquent depuis le menu *Affichage*. Un **clic droit** dans la partition, dans la tablature ou sur une piste de la liste ouvre un **menu contextuel** avec les mêmes actions que les menus.

## A2. Les menus

Toutes les fonctions sont dans les menus. Les raccourcis apparaissent à droite des entrées ; la plupart sont modifiables (voir [A12](#a12-les-outils-et-la-configuration)).

### Fichier

<img src="images/manual/menu-fichier.png" width="300" alt="Le menu Fichier">

*Nouveau* (morceau vide, ou modèles « Trio blues » et « Duo de guitare classique »), *Ouvrir*, *Ouvrir une URL*, *Choisir un modèle personnalisé* (les nouveaux morceaux partiront de ce fichier) et *Rétablir le modèle par défaut*, *Fermer / Fermer les autres / Tout fermer*, *Enregistrer* (⌘S), *Enregistrer sous…* (F12), *Importer* (MIDI), *Exporter* (sous-menu ci-dessous), *Aperçu avant impression*, *Imprimer*, *Historique* (les 10 derniers fichiers), *Quitter*.

<img src="images/manual/menu-export.png" width="260" alt="Le sous-menu Exporter">

### Édition

<img src="images/manual/menu-edition.png" width="300" alt="Le menu Édition">

*Couper, Copier, Coller, Répéter* (copie la sélection et la colle juste après), *Annuler / Rétablir*, *Sélectionner tout*, *Ne rien sélectionner*, *Étendre la sélection*, puis les **modes d'édition** (sélection, édition de partition, dièses/bémols, édition libre), *Afficher les mesures invalides*, et le choix de la **voix 1 ou 2**.

### Affichage

<img src="images/manual/menu-affichage.png" width="320" alt="Le menu Affichage">

Barres d'outils, liste des pistes, manche ; fenêtres *Instruments*, *Lecteur*, *Clavier*, *Matrice* ; **mode page / linéaire / multipiste** ; afficher la portée et/ou la tablature ; mode compact ; style des accords ; zoom.

### Composition

<img src="images/manual/menu-composition.png" width="320" alt="Le menu Composition">

*Chiffrage*, *Tempo*, *Clé*, *Armure*, *Shuffle*, *Ouvrir / Fermer une reprise*, *Autres reprises* (fins alternatives), *Propriétés du morceau* (F5).

### Piste

<img src="images/manual/menu-piste.png" width="300" alt="Le menu Piste">

Navigation entre les pistes, *Ajouter une piste*, *Dupliquer la piste*, *Supprimer la piste*, *Déplacer vers le haut / le bas*, **Solo**, **Muette**, *Paroles*, *Propriétés de la piste*.

### Mesure

<img src="images/manual/menu-mesure.png" width="300" alt="Le menu Mesure">

Navigation (première, précédente, suivante, dernière mesure), *Ajouter une mesure*, *Effacer la mesure* (vide la mesure mais la garde), *Supprimer la mesure*, *Copier / Coller la mesure*, *Supprimer la voix inutilisée*, *Retour à la ligne*.

### Note

<img src="images/manual/menu-note.png" width="340" alt="Le menu Note">

*Insérer un silence*, *Supprimer la note ou le silence*, *Effacer la note*, **Durée**, **Expression** (nuances), **Effets**, **Accord**, *Insérer un texte*, orientation des hampes, **coups** (vers le haut / le bas) et **médiator** (vers le bas / le haut), *Représentation alternative* (enharmonie), demi-ton et corde au-dessus/dessous, *Déplacer vers la gauche / la droite*, *Déplacement personnalisé*.

### Marqueur, Lecteur, Outils, Aide

<img src="images/manual/menu-marqueur.png" width="300" alt="Le menu Marqueur"> <img src="images/manual/menu-lecteur.png" width="320" alt="Le menu Lecteur"> <img src="images/manual/menu-outils.png" width="280" alt="Le menu Outils"> <img src="images/manual/menu-aide.png" width="360" alt="Le menu Aide">

* **Marqueur** : ajouter, lister, naviguer entre les marqueurs.
* **Lecteur** : lecture, arrêt, métronome, décompte, mode de lecture, boucle, encadrer le temps joué, défilement continu.
* **Outils** : *Transposition*, *Liste des gammes*, *Navigateur*, *Convertisseur de fichiers par lots*, **Pratique interactive**, **Accordeur**, *Greffons*, *Raccourcis clavier*, *Configuration* (F7).
* **Aide** : l'aide intégrée et la fenêtre « À propos ».

## A3. Créer un premier morceau

**1. Un document vide.** Au lancement, un document vide est prêt. Pour en créer un autre : *Fichier > Nouveau* (⌘N) ou le premier bouton de la barre d'outils.

**2. Les informations du morceau.** *Composition > Propriétés du morceau* (F5) : nom, interprète, album, auteur-compositeur, date, droits, créateur de la tablature, transcripteur, commentaires. Le **nom** et l'**auteur** s'impriment sur la première page des exports, renseignez-les si vous sortez la partition de TuxGuitar.

<img src="images/manual/song-properties.png" width="420" alt="Les propriétés du morceau">

**3. La première piste.** Un nouveau document a déjà une piste. Une piste est un instrument : un morceau avec deux guitares et une basse a trois pistes. **Double-cliquez** sur la piste dans la liste (ou *Piste > Propriétés de la piste*) :

<img src="images/manual/track-properties.png" width="420" alt="Les propriétés de la piste">

* **Nom** et **couleur** (affichés dans la liste des pistes) ;
* **Instrument** : le son de lecture (bouton clé : réglages détaillés, voir [A8](#a8-les-pistes-les-instruments-et-les-paroles)) ;
* **Accordage** : cliquez sur la clé pour choisir l'instrument, le nombre de cordes (4 pour une basse, 7, 8...) et l'accordage, ou en définir un personnalisé ;
* **Nombre de frettes** (sauf pistes de percussions).

<img src="images/manual/track-tuning.png" width="460" alt="La fenêtre Accordage">

Donnez le **bon accordage** : sans lui, la portée et les numéros de case seraient faux. Quand vous changez l'accordage d'une piste déjà écrite, les notes sont conservées autant que possible et les numéros de case adaptés ; une note devenue injouable peut être perdue. Le **capodastre** décale la hauteur à la lecture sans changer l'affichage.

**4. Avant les notes :** réglez le **chiffrage** (4/4 par défaut), le **tempo** (120 par défaut), la **clé** et l'**armure** ([A7](#a7-la-structure-du-morceau)).

## A4. Saisir des notes

### Se déplacer

Cliquez à l'endroit voulu, ou utilisez les flèches : **← →** passent à la note suivante ou précédente de la piste, **⌘← ⌘→** (Ctrl sous Windows/Linux) à la mesure précédente ou suivante, **↑ ↓** changent de corde dans la tablature. *Configuration > Général > Jouer les notes au déplacement du curseur* fait entendre chaque note.

### Écrire dans la tablature

Placez le curseur sur une corde et **tapez le numéro de case (0 à 29)** : la note est écrite avec la **durée** et la **nuance** sélectionnées dans la barre latérale. Sur une note existante, **⇧→ / ⇧←** augmentent ou diminuent la case sur la même corde, **⇧↑ / ⇧↓** déplacent la note sur la corde du dessus ou du dessous. On peut aussi cliquer sur le **manche** en bas de la fenêtre, ou sur le **clavier** (*Affichage > Afficher le clavier*).

### Écrire sur la portée

Passez en *Mode édition de partition* (*Édition* ou barre latérale) : un clic sur une ligne ou un interligne écrit la note (des lignes supplémentaires s'ajoutent au-dessus et en dessous si besoin). Pour des dièses ou des bémols, activez aussi *Mode dièses/bémols*. Le *Mode sélection* déplace seulement le curseur sans rien modifier, utile pour naviguer sans risque.

### Durées

La somme des durées d'une mesure doit égaler son chiffrage : TuxGuitar empêche de créer une mesure irrégulière. Choisissez la durée dans la barre latérale ou *Note > Durée* (ronde à quadruple croche) ; **+ / −** (pavé numérique) la changent d'un cran. La **note pointée** allonge de 50 % (une blanche pointée = 3/4), la **double pointée** de 75 %. Le menu des **divisions** (barre latérale, flèche à droite) crée des **n-olets** (triolets...). La **liaison** (touche **L**) attache la note à la précédente : indispensable pour une note qui continue sur la mesure suivante, ou pour des durées comme 5/16 (une noire liée à une double croche). Touches : **\*** pointée, **/** triolet.

### Nuances

ppp, pp, p, mp, mf, f, ff, fff (barre latérale « Expression » ou *Note > Expression*) : une nouvelle note prend la nuance choisie ; en cliquant sur une autre nuance avec le curseur sur une note, vous changez cette note.

### Silences, suppression, décalage

* *Note > Insérer un silence* (Inser) insère un silence avant le curseur (dans la mesure, s'il y a un temps vide à la fin) ;
* *Supprimer la note ou le silence* (Suppr) : supprime la note sous le curseur ; sur un temps vide, décale vers la gauche ce qui suit (dans la mesure) ;
* *Effacer la note* (⌘Suppr) : comme ci-dessus, mais ne fait rien sur un temps déjà vide ;
* *Déplacer vers la gauche / la droite* et *Déplacement personnalisé* : décalent tous les temps qui suivent le curseur ;
* *Augmenter / Diminuer d'un demi-ton*, *d'une corde* : agissent sur toutes les notes sélectionnées si elles peuvent toutes être déplacées ;
* *Représentation alternative* (**e**) : une même note peut s'écrire do# ou ré♭ ; le choix par défaut dépend de l'armure.

### Sélection, copier, coller

Cliquez-glissez pour sélectionner des notes (un effet s'applique alors à toute la sélection). *Édition > Copier* sans sélection ouvre une fenêtre pour choisir les mesures à copier (piste courante ou toutes). *Coller* écrase le contenu à partir du curseur ; pour des mesures copiées, on choisit d'écraser ou d'insérer de nouvelles mesures. *Répéter* (⌘R) copie la sélection et la colle juste après.

### Deux voix et mode d'édition libre

Chaque piste peut avoir **deux voix** (*Édition > Sélectionner la voix 1 / 2*, ⌘1 / ⌘2) pour écrire deux lignes rythmiques indépendantes. Le **mode d'édition libre** supprime le contrôle de la somme des durées : à n'utiliser qu'avec de bonnes bases de théorie musicale, surtout à deux voix. *Afficher les mesures invalides* ouvre la fenêtre qui liste et corrige les mesures dont la durée ne correspond pas ([B9](#b9-analyse-de-la-partition-mesures-invalides)).

### Mesures

*Mesure > Ajouter une mesure* : une ou plusieurs mesures avant ou après le curseur, ou à la fin, dans toutes les pistes.

<img src="images/manual/add-measure.png" width="320" alt="La fenêtre Ajouter une mesure">

*Effacer la mesure* vide la mesure (dans la piste courante) ; *Supprimer la mesure* la retire de **toutes** les pistes ; *Copier / Coller la mesure* gère la copie d'une ou plusieurs mesures, dans la piste courante ou dans toutes, avec les marqueurs si vous le voulez, et le nombre de collages.

## A5. Les effets de jeu

Presque chaque effet a une icône dans la barre latérale « Effets » et une entrée dans *Note > Effets*. Avec des notes sélectionnées, l'effet s'applique à toute la sélection ; sinon à la note sous le curseur. Les effets s'appliquent dans la **tablature** (laissez-la affichée).

| Effet | Touche | Ce qu'il fait |
|---|---|---|
| **Vibrato** | V | Oscillation de la hauteur ; marqué de petites vagues. Sans paramètre. |
| **Bend** | B | Montée de la hauteur ; ouvre l'**éditeur de bends** (ci-dessous). |
| **Vibrato de levier** (tremolo bar) | | Variation vers le haut ou le bas jusqu'à une octave ; éditeur dédié. |
| **Slide** | S | Glissé entre la note et la suivante sur la même corde. |
| **Note étouffée** (dead note) | X | Note courte et percussive ; la hauteur reste réglable. |
| **Hammer-on / Pull-off** | H | Arc entre la note et la suivante sur la même corde, sans nouveau coup de médiator. |
| **Note fantôme** | O | Entre parenthèses ; seule l'écriture change. |
| **Accentuée / très accentuée** | | Un peu / beaucoup plus fort (marques > et ^). |
| **Let ring** | | « L.R. » : laisser sonner. |
| **Harmoniques** | | Naturelle, artificielle (A.H. 12, 9, 5, 7, 4, 3), pincée, tapée, semi. |
| **Note d'agrément** (grace note) | G | Note très courte avant ou sur le temps, avec transition (bend, slide, hammer). |
| **Trille** | | Série rapide de hammer-ons et pull-offs entre deux notes ; éditeur dédié. |
| **Trémolo de médiator** | | Répétition rapide de la même note (≡). |
| **Palm mute** | P | « P.M. » : étouffé de la paume. |
| **Staccato** | | Durée réduite de moitié (point au-dessus). |
| **Tapping** | | Marque « T » (sans effet sur le son). |
| **Slap / Pop** | | Marques « S » et « P » (sans effet sur le son). |
| **Fade in** | F | Le volume monte progressivement ; marque « < ». |

<img src="images/manual/bend-editor.png" width="420" alt="L'éditeur de bends">

**L'éditeur de bends** : la liste de droite propose les cas fréquents (Bend, Bend/relâche, Bend/relâche/bend, Pré-bend, Pré-bend/relâche), tous d'un ton (2 cases) ; vous ajustez ensuite la courbe en plaçant des points (traits rouges pleins : un ton ; pointillés rouges : un demi-ton ; gris : un quart de ton). *Effacer* retire l'effet. Dans la tablature, le bend est une flèche courbe avec la valeur du décalage.

L'éditeur de vibrato de levier fonctionne de même (de −12 à +12 cases autour de la hauteur d'origine, la ligne noire est la hauteur de départ).

## A6. Les accords

L'**éditeur d'accords** (touche **A**, ou *Note > Accord*) sert à composer, reconnaître, mémoriser et insérer un accord. C'est le seul moyen d'ajouter un **nom** ou un **diagramme** d'accord dans la tablature.

<img src="images/manual/chord-editor.png" width="900" alt="L'éditeur d'accords avec des propositions de doigtés pour do majeur">

* **À l'ouverture**, les notes du curseur sont reprises, l'accord est reconnu et on l'entend.
* **Colonne de gauche** : la fondamentale (♯ ou ♭), puis le type (M, 7, maj7, 6, m, m7, sus2, sus4, dim, aug, 5...), les extensions (9, 11, 13, avec « add »), les altérations (/5+, /5−, /9+...), et la **basse** (une basse différente de la fondamentale donne un renversement).
* **Au centre : le diagramme** : cliquez pour poser des points, au-dessus de la grille pour passer d'une corde « à vide » à « non jouée » (×). Le curseur à droite choisit la case de départ.
* **Propositions** (bas) : des doigtés générés pour la théorie choisie, pour votre accordage ; cliquer sur un diagramme le charge dans l'éditeur et donne son nom.
* **Reconnu** : les noms possibles de ce que vous avez dessiné, avec une probabilité.
* **Bibliothèque** : *Ajouter*, *Renommer*, *Supprimer* ; les accords enregistrés sont ensuite accessibles par la flèche à côté de l'icône d'accord (ou *Note > Accord*).
* **Configuration** : type (les plus courants, ouverts, fermés, renversements), nombre de propositions, accords « à vide » et plage de cases ; non mémorisé après fermeture.
* **Options** : *Insérer le diagramme et les notes* ou *le diagramme uniquement*.

Dans *Affichage > Accords*, on choisit comment un accord est montré dans la partition : le **diagramme**, le **nom**, ou les deux.

## A7. La structure du morceau

<img src="images/manual/time-signature.png" width="250" alt="Chiffrage"> <img src="images/manual/clef.png" width="240" alt="Clé"> <img src="images/manual/key-signature.png" width="330" alt="Armure">

* **Chiffrage** (*Composition > Chiffrage* ou l'icône « 4/4 ») : numérateur et dénominateur ; *Jusqu'à la fin* applique le changement au reste du morceau (sinon, une seule mesure, puis retour au chiffrage précédent). Il s'applique à toutes les pistes et peut changer en cours de morceau.
* **Tempo** (30 à 320 BPM, 120 par défaut) : on peut choisir la figure qui porte la pulsation (noire par défaut) et l'étendue : tout le morceau, jusqu'à la fin, ou jusqu'au prochain marqueur de tempo.

<img src="images/manual/tempo.png" width="520" alt="Tempo">

* **Clé** : sol (par défaut), fa, ut alto, ut ténor ; peut changer en cours de morceau.
* **Armure** : la gamme du morceau (naturel = do majeur, un bémol = fa majeur...), pour ne pas écrire chaque dièse ou bémol.
* **Shuffle / triolet de feeling** : le premier temps de chaque paire est allongé (2:1), en croches ou en doubles croches, pour un rendu swing ou blues.
* **Reprises** : *Ouvrir une reprise*, *Fermer une reprise* (on choisit le nombre de répétitions), *Autres reprises* (fins alternatives : on dit à quel passage chacune est jouée).
* **Marqueurs** (menu *Marqueur*) : nom, couleur et position, affichés au-dessus de la mesure dans toutes les pistes et dans le « transporteur » ; une liste permet d'y sauter.

## A8. Les pistes, les instruments et les paroles

### Gérer les pistes

*Piste > Ajouter une piste* (bouton « + »), *Dupliquer*, *Supprimer* (bouton « × »), *Déplacer vers le haut / le bas*. Tout cela s'annule avec ⌘Z. **Solo** n'écoute que la piste choisie ; **Muette** écoute toutes les autres. Dans la liste, cliquer le nom d'une piste l'affiche ; ⌘⇧↑ / ⌘⇧↓ passent à la piste précédente ou suivante.

### Instruments et mélangeur

*Affichage > Afficher les instruments* (ou l'icône) ouvre la fenêtre **Instruments** : pour chaque canal MIDI, le programme (instrument) et la banque, le **volume**, le **panoramique** et les effets (réverbération, chorus, tremolo, phaser). Cochez **Canal de percussions** pour une piste de batterie. **Ajouter** crée un nouveau canal ; la barre de droite règle le gain général.

<img src="images/manual/instruments.png" width="700" alt="La fenêtre Instruments">

**Canaux :** le MIDI a 16 canaux, le canal 9 étant réservé aux percussions. Chaque piste utilise un canal normal et un canal d'effet (pour jouer en même temps une note étouffée ou une harmonique et une note normale, ou pour appliquer un bend, un slide, un vibrato ou un levier à certaines notes seulement). Tant que vous avez moins de 7 instruments et une percussion, TuxGuitar attribue les canaux seul. Au-delà, faites partager un canal à deux pistes qui ne jouent jamais en même temps.

### Paroles

*Piste > Paroles* ouvre l'éditeur de paroles de la piste. Choisissez la mesure de départ ; chaque mot (séparé par un espace ou une entrée) se place sous la note suivante, les silences sont ignorés ; ajoutez des espaces pour sauter des notes.

<img src="images/manual/lyrics.png" width="420" alt="L'éditeur de paroles">

## A9. L'affichage

* **Mode page** (comme sur papier, avec retours à la ligne) ou **mode linéaire** (une seule ligne qui s'étend vers la droite). Le **mode multipiste** montre toutes les pistes ; sinon seule la piste sélectionnée est affichée. En pratique : *linéaire + multipiste* pour l'ensemble, *page sans multipiste* pour une piste ; activer le multipiste passe en linéaire, le désactiver repasse en page.
* **Afficher la portée**, **afficher la tablature** et **mode compact** (certains détails, comme ceux des bends, sont alors masqués).
* **Zoom** : *Agrandir*, *Réduire*, *Réinitialiser l'affichage*, ou ⌘ + molette.
* **Le manche** (en bas) montre les cases à jouer et accepte les clics pour saisir ; il existe pour droitiers et gauchers.
* **Le clavier** (*Afficher le clavier*) : saisie des notes à la souris.

<img src="images/manual/piano.png" width="720" alt="L'éditeur de clavier">

* **La matrice** (*Afficher la matrice*) : surtout utile pour les pistes de **percussions** ; elle découpe la mesure en cases et un clic ajoute ou retire un son. *Nombre de grilles* règle la finesse ; elle ne bloque pas la fenêtre principale (durée, nuance, mesure courante restent modifiables).

<img src="images/manual/matrix-editor.png" width="520" alt="L'éditeur de matrice">

## A10. La lecture

Les commandes de lecture sont dans la barre d'outils, le menu *Lecteur* et la fenêtre **Lecteur** (*Affichage > Afficher le lecteur*, ⌘T). **Espace** lance et met en pause. Pendant la lecture, la note jouée est colorée en rouge, la mesure en cours a un fond rouge (réglable dans *Configuration > Styles*, option *Encadrer le temps joué*).

<img src="images/manual/player.png" width="320" alt="La fenêtre Lecteur">

* **Métronome** : un clic à chaque temps. **Décompte** : une mesure d'attente avant le début (4 temps en 4/4, réglable dans la fenêtre Lecteur).
* **Mode de lecture** (F9) :
  * **Mode simple** : un pourcentage de tempo fixe (50 % = moitié moins vite), lecture en boucle, plage de mesures à jouer (début et fin de la boucle) ;
  * **Mode d'apprentissage** : un tempo de départ, un tempo d'arrivée et un incrément ; le tempo augmente de l'incrément à chaque tour (par exemple de 50 % à 100 % par pas de 5 %).

<img src="images/manual/play-mode.png" width="330" alt="Le mode de lecture">

* **Défilement :** par défaut la page défile d'un écran quand la lecture atteint le bord ; *Défilement continu* fait défiler en continu.
* **Le « transporteur »** de la liste des pistes se parcourt à la souris et défile pendant la lecture.

## A11. Les fichiers

* **Ouvrir** (⌘O) : `.tg` (format natif, recommandé), Guitar Pro (`.gtp`, `.gp3`, `.gp4`, `.gp5`, `.gpx`, `.gp`), Power Tab (`.ptb`), TablEdit (`.tef`) ; **Importer** : MIDI. *Ouvrir une URL* ouvre un fichier en ligne (à enregistrer ensuite en local). **Plusieurs fichiers** peuvent être ouverts, chacun dans un onglet.
* **Enregistrer** (⌘S) dans le dernier format natif ; **Enregistrer sous** (F12).
* **Exporter** : PDF, MIDI, ASCII, LilyPond, SVG, Guitar Pro 3/4/5, anciens formats TuxGuitar (1.0 à 1.5), fichier audio, MusicXML, et « Créer un répertoire image ». L'**export audio** utilise le synthétiseur TuxGuitar avec les réglages d'instruments du morceau.
* **Aperçu avant impression**, **Imprimer** (⌘P) : une ou toutes les pistes, plage de mesures, options de mise en page.
* **Modèles :** *Nouveau* propose un morceau vide, *Trio blues* et *Duo de guitare classique* ; *Choisir un modèle personnalisé* fait partir tout nouveau morceau d'un fichier de votre choix.
* **Convertisseur par lots :** voir [A12](#a12-les-outils-et-la-configuration).

## A12. Les outils et la configuration

### Transposition, gammes, navigateur, convertisseur

<img src="images/manual/transposition.png" width="300" alt="La transposition"> <img src="images/manual/scales.png" width="520" alt="La liste des gammes">

* **Transposition** : décale tout ou partie du morceau.
* **Liste des gammes** : les gammes sont affichées sur le manche pour aider à improviser ou composer (bouton *Gammes* sous la partition).
* **Navigateur** (⌘B) : un navigateur de collections de fichiers (dossiers locaux).

<img src="images/manual/browser.png" width="420" alt="Le navigateur">

* **Convertisseur de fichiers par lots** : dossier d'entrée (sous-dossiers compris), dossier de sortie, format de sortie ; garde les noms et change l'extension. Une fenêtre montre l'avancement et peut être annulée.

<img src="images/manual/converter.png" width="420" alt="Le convertisseur de fichiers par lots">

### Greffons

*Outils > Greffons* liste les greffons (formats de fichiers, sorties sonores, autres) : on les active ou on les désactive, **Configurer** règle ceux qui ont des options (par exemple la banque de sons), **Info** donne version, auteur et description. Un greffon est un fichier `.jar` à copier dans `share/plugins/`.

<img src="images/manual/plugins.png" width="360" alt="La fenêtre des greffons">

### Raccourcis clavier

*Outils > Raccourcis clavier* : tableau de toutes les actions et de leurs raccourcis ; double-clic pour changer, *Effacer* pour retirer, filtre de recherche en haut, *Défauts* pour tout rétablir.

<img src="images/manual/shortcuts.png" width="380" alt="L'éditeur de raccourcis clavier">

### Configuration (F7)

*Outils > Configuration* regroupe six pages :

<img src="images/manual/settings-general.png" width="760" alt="Configuration, page Général">

* **Général** : titre de la fenêtre (avec des variables comme `${appname}`, `${filename}`, `${songname}`), redimensionnement automatique de la table, écran d'accueil, jouer les notes au déplacement du curseur.
* **Styles** : polices et couleurs de la partition, de la tablature et des paroles (couleur de la mesure jouée, etc.).
* **Langues** : langue du système ou choix manuel (français, anglais, espagnol, et les autres langues de TuxGuitar).
* **Thèmes** (« skins ») : icônes et boutons, en version claire ou sombre ; un thème se choisit avec un aperçu.
* **Son** : sortie audio, c'est-à-dire le séquenceur MIDI et le port MIDI. Les ports viennent des greffons de sortie : sur macOS, le synthétiseur TuxGuitar, Java Sound (Gervill) et Audio Unit. Le synthétiseur intégré lit les banques de sons SF2 ou DLS (la « Magic Sound Font » est fournie).
* **Reconnaissance audio** : les réglages du Practice et de l'accordeur ([B7](#b7-les-réglages-de-reconnaissance-audio)).

<img src="images/manual/settings-styles.png" width="560" alt="Page Styles"> <img src="images/manual/settings-languages.png" width="560" alt="Page Langues">

<img src="images/manual/settings-themes.png" width="560" alt="Page Thèmes"> <img src="images/manual/settings-sound.png" width="560" alt="Page Son">

## A13. Raccourcis clavier

*Sous macOS, Ctrl se lit ⌘.* Tous sont modifiables.

| Catégorie | Action | Raccourci |
|---|---|---|
| **Application** | Nouveau / Ouvrir / Enregistrer | Ctrl+N / Ctrl+O / Ctrl+S |
| | Enregistrer sous | F12 |
| | Fermer le fichier | Ctrl+W |
| | Aperçu / Imprimer | Ctrl+Maj+P / Ctrl+P |
| | Quitter | Alt+F4 (⌘Q sur macOS) |
| **Édition** | Couper / Copier / Coller | Ctrl+X / Ctrl+C / Ctrl+V |
| | Répéter | Ctrl+R |
| | Annuler / Rétablir | Ctrl+Z / Ctrl+Y |
| | Sélectionner la piste | Ctrl+A |
| | Voix 1 / voix 2 | Ctrl+1 / Ctrl+2 |
| | Propriétés du morceau | F5 |
| **Pistes** | Piste suivante / précédente | Ctrl+Maj+↓ / Ctrl+Maj+↑ |
| | Ajouter / supprimer une piste | Ctrl+Maj+Inser / Ctrl+Maj+Suppr |
| **Lecture** | Lecture / pause | Espace |
| **Mesures** | Première / précédente / suivante / dernière | Ctrl+Maj+← / Ctrl+← / Ctrl+→ / Ctrl+Maj+→ |
| **Notes** | Insérer une note | chiffres 0 à 29 |
| | Insérer un silence | Inser |
| | Supprimer la note ou la sélection | Suppr |
| | Effacer le temps | Ctrl+Suppr |
| | Note liée / pointée / triolet | L / \* / / |
| | Texte | T |
| | Représentation alternative | e |
| | Note sur la corde du dessus / du dessous | Maj+↑ / Maj+↓ |
| | Hauteur + / − | Maj+→ / Maj+← |
| | Durée + / − | + / − |
| | Hampes vers le haut / le bas | Ctrl+↑ / Ctrl+↓ |
| **Effets** | Bend / fantôme / hammer-on / slide | B / O / H / S |
| | Vibrato / étouffée / palm mute | V / X / P |
| | Fade in / note d'agrément | F / G |
| **Marqueurs** | Ajouter | Maj+Inser |
| | Premier / précédent / suivant / dernier | Alt+Maj+← / Alt+← / Alt+→ / Alt+Maj+→ |
| **Fenêtres** | Éditeur d'accords | A |
| | Configuration | F7 |
| | Lecteur | Ctrl+T |
| | Navigateur | Ctrl+B |
| | Mode de lecture | F9 |
| | Aide | F1 |

---

# Partie B : le Practice (pratique interactive)

## B1. Principe et prérequis

Le Practice **écoute votre guitare** et vérifie, **note par note et accord par accord**, que vous jouez ce que la partition dit. Il ne joue pas à votre place. **Il n'enregistre jamais d'audio** : seuls des nombres dérivés du son (hauteur, niveau, notes entendues) existent en mémoire pendant la pratique.

**Ce qu'il vérifie : la hauteur.** Il ne prétend pas distinguer la corde et la case à l'oreille (ce reste une consigne de la tablature). Pour les techniques écrites (hammer-on, pull-off, slide, bend, vibrato), il suit comment la hauteur évolue ; la ligne « technique » dit « pas encore vérifiable » tant que les détecteurs ne sont pas validés sur assez d'enregistrements réels, ce qui n'est pas une erreur de votre part.

**Il vous faut :**

* macOS 15 Sequoia ou plus récent (Apple Silicon), Linux ou Windows (les modules sont dans les trois paquets) ;
* une **entrée audio** avec le son de votre guitare : le micro intégré convient pour un jeu propre ; une **interface audio** avec un signal direct est meilleure. Un **casque** est conseillé si la partition joue aussi dans les haut-parleurs, pour que le son de l'ordinateur ne soit pas pris pour votre guitare ;
* une partition avec une piste de guitare (accordage standard ou tout accordage écrit dans la partition).

Trois modes de travail, choisis dans l'onglet *Mode* :

| Mode | Principe |
|---|---|
| **Attendre la bonne note** | La partition **vous attend** : jouez chaque note quand vous êtes prêt. |
| **Suivre le tempo** | La partition **joue à son tempo** après un décompte, les autres pistes en accompagnement ; chaque note est jugée dans le temps. |
| **Arcade** (alpha) | La même chose, présentée comme un **jeu** : les notes viennent vers vous sur un manche. |

## B2. La fenêtre Pratique interactive

**Pour l'ouvrir :** *Outils > Pratique interactive*, ou le bouton **♪** à côté de Lecture : une pression travaille la piste sous le curseur de la partition, à partir du curseur, dès que l'entrée est utilisable. Le **bouton coloré** juste après (<img src="images/manual/arcade-button.png" width="40" alt="Le bouton Arcade coloré">) lance **Arcade** tout de suite à la place : voir [B6](#b6-mode-arcade-alpha). La fenêtre est organisée en **quatre onglets** ; les boutons *Travailler cette piste* (ou *Suivre le tempo de cette piste* / *Démarrer Arcade* selon le mode) et *Fermer* restent visibles quel que soit l'onglet.

### Onglet « Piste »

<img src="images/manual/practice-track.png" width="480" alt="L'onglet Piste">

Choisissez la **piste à travailler** (seules les pistes utilisables sont proposées, avec la raison pour les autres). La fenêtre rappelle l'**accordage** de la piste et d'où la pratique commence (le curseur ou le début). En **Arcade**, toute piste avec hauteur est proposée, quel que soit son nombre de cordes (basse comprise).

### Onglet « Entrée »

<img src="images/manual/practice-input.png" width="480" alt="L'onglet Entrée">

* **Entrée** : la liste des entrées audio, une par appareil mono et une par canal utilisable des appareils multicanaux. L'application ne change jamais d'entrée seule.
* **Actualiser** : relit la liste, pour une interface branchée **pendant** que l'application tourne (la liste n'est pas surveillée).
* **Mémoriser par défaut** : enregistre l'entrée affichée dans la configuration, à la place de celle mémorisée avant. Quand la liste est lue, cette entrée est sélectionnée si elle est là, et sélectionnée de nouveau quand elle revient après une absence (une interface débranchée puis rebranchée) ; un choix fait pendant qu'elle était là est conservé ; tant qu'elle est absente, l'entrée par défaut du système s'applique.
* **Niveau** : un indicateur et un message (bon, trop faible, trop fort, saturé) ; *Vérifier à nouveau le niveau* relance la mesure (jouez une corde).
* **Gain d'entrée**, *Remise à zéro*, *Suggérer un gain*, *Appliquer* : voir [B3](#b3-niveau-dentrée-gain-et-bruit).
* Un conseil rappelle de préférer un casque.

### Onglet « Mode »

<img src="images/manual/practice-mode-follow.png" width="480" alt="L'onglet Mode avec Suivre le tempo">

Le **mode d'entraînement** (liste déroulante) et, pour les modes chronométrés, le **tempo** (20 à 150 % du tempo écrit), la **fenêtre de temps** (un curseur de 40 à 200 ms), le **calibrage du rythme** et un **retard** manuel ; en Arcade, la **taille des rectangles qui arrivent** (0 à 150 %, 100 % étant la taille de la cible sur le manche) et l'**inclinaison du plan** (l'échelle à l'horizon et la hauteur de l'horizon), voir [B6](#b6-mode-arcade-alpha). En bas, la fenêtre rappelle le délai de reconnaissance mesuré (environ 0,1 s, et que des notes plus rapprochées que 0,3 s peuvent ne pas être suivies : la pratique vous attend alors).

### Onglet « Passages »

<img src="images/manual/practice-passages.png" width="480" alt="L'onglet Passages">

Pour le mode « Attendre » : **répéter les passages** (désactivé, ou toutes les 1, 2, 4 mesures) et **répéter quand les erreurs dépassent** un seuil (en pourcentage des notes et accords). Les erreurs sont les notes et accords faux ou passés ; un passage est répété deux fois au plus. Le choix est conservé jusqu'à la fermeture de l'application (il se règle aussi dans *Configuration > Reconnaissance audio > Passages*).

## B3. Niveau d'entrée, gain et bruit

* Sous l'indicateur de niveau, le curseur **Gain d'entrée** amplifie ou atténue le signal dans l'application (de −6 à +18 dB) ; **Remise à zéro** revient à 0 dB. Le gain n'est jamais mémorisé : il revient à 0 dB quand l'entrée change et à chaque démarrage. Si l'amplification ne fait qu'amplifier le bruit de fond, ou fait saturer le signal, un avertissement le dit et conseille d'augmenter le gain sur l'interface audio.
* Pendant que vous jouez, si vos notes sont trop faibles pour une reconnaissance rapide fiable (en dessous d'environ −30 dBFS mesurés), la fenêtre le dit : augmentez le gain ou rapprochez-vous ; un jeu lent peut encore être reconnu.
* **Réduire le bruit de fond** (réglage, désactivé par défaut) : réduction du bruit par seuil et soustraction du bruit de fond. **Suggérer un gain** (activé par défaut) : propose un gain après la vérification du niveau. Voir [B7](#b7-les-réglages-de-reconnaissance-audio).

**La fenêtre de démarrage (avant chaque départ).** À chaque démarrage d'un mode — bouton Démarrer, bouton Pratique de la barre de transport, bouton Arcade coloré — une petite fenêtre demande d'abord *Votre guitare est-elle accordée ?* *Non, ouvrir l'accordeur* ouvre l'accordeur et le mode ne commence pas ; quand vous fermez l'accordeur, la question revient. *Oui, accordée* demande un accord de test : grattez un accord et laissez-le sonner. La fenêtre juge le niveau des 0,3 seconde qui suivent : si plus de 0,5 % des échantillons atteignent le maximum, elle dit que le signal sature (baissez le volume de l'adaptateur ou de la guitare) ; un signal trop fort ou trop faible est aussi nommé, avec la marche à suivre ; un bon niveau lance le mode aussitôt. Un mauvais niveau bloque le départ : *Vérifier à nouveau* juge un nouvel accord, et *Continuer quand même* continue **pour ce départ seulement** (proposé aussi après 10 secondes sans accord). Relancer une partie ou *rejouer* ne redemande pas. La fenêtre ne change jamais le gain de l'interface ni du système. Le seuil de 0,5 % a été calibré sur les enregistrements du propriétaire avec un adaptateur Rocksmith et sur les 180 enregistrements microphone de GuitarSet (aucun n'est signalé) ; les conseils pour Windows et Linux reprennent le texte de macOS et n'y ont pas été essayés.

## B4. Mode « Attendre la bonne note »

C'est le mode d'origine. **Choix du mode :** onglet *Mode*, « Attendre la bonne note ». Puis *Travailler cette piste*.

<img src="images/manual/practice-mode-wait.png" width="400" alt="L'onglet Mode, mode Attendre la bonne note">

La partition **attend** : la note ou l'accord attendu est **encadré** sur la partition, le passage en cours est surligné, et la **palette** de pratique reste affichée.

<img src="images/manual/practice-wait-main.png" width="900" alt="Pratique en mode Attendre : note encadrée sur la partition et palette">

La palette (fenêtre « Pratique interactive » pendant la pratique) dit :

* ce qui a été **entendu** (« D5, 19 cents trop haut, confiance 98 % ») et ce qu'il faut **jouer** ensuite (« Jouez G4 ») ;
* l'avancement du **passage** (« Passage 1 sur 3 (mesures 1–2) · essai 1 sur 3 · erreurs 1 sur 5 ») ;
* le **niveau d'entrée** ;
* des boutons **Passer** (ignorer la note courante), **Reprendre**, **Recommencer** et **Stop**.

Une coche verte signifie « entendu », une croix rouge « une autre note a été entendue » ; **rien n'avance sur un silence ou un bruit**. **Stop** arrête la pratique immédiatement. Quand les erreurs d'un passage dépassent le seuil réglé dans l'onglet *Passages*, ce passage est répété.

Démarrer ce mode ouvre d'abord la fenêtre de démarrage (question d'accord, accord de test) : voir [B3](#b3-niveau-dentrée-gain-et-bruit).

## B5. Mode « Suivre le tempo »

**Choix du mode :** onglet *Mode*, « Suivre le tempo ». Puis *Suivre le tempo de cette piste*. La partition défile **à son tempo après un décompte** ; la **piste travaillée est muette**, les autres pistes jouent en accompagnement. Chaque note ou accord est **jugé dans le temps** :

| Verdict | Sens |
|---|---|
| À l'heure | Juste note, dans la tolérance. |
| En avance / En retard | Juste note, mais décalée (en millisecondes). |
| Fausse | Une autre note a été jouée. |
| Manquée | Rien d'attendu n'a été entendu. |
| En trop | Une note supplémentaire non écrite. |
| Non jugée | Événement qui ne peut pas être jugé (passé automatiquement, technique non vérifiable...). |

**Réglages :**

* **Tempo (% du tempo écrit)** : de 20 % à 150 %. Commencez plus lentement et montez par étapes.
* **Fenêtre de temps** : de combien une note peut s'écarter de son heure écrite et compter comme à l'heure, en **curseur de 40 à 200 ms de chaque côté, mesurés à 120 battements par minute** (défaut **100 ms**) ; la fenêtre réelle s'élargit et se resserre avec le tempo, entre un plancher et un plafond. C'est **un seul réglage enregistré** : ce que vous réglez ici est conservé et se retrouve dans *Configuration > Reconnaissance audio > Pratique, notes*. Un choix enregistré par une ancienne version est converti (stricte 50 ms, souple 125 ms ; normale, l'ancien défaut, devient le nouveau défaut de 100 ms). Les valeurs sont provisoires tant qu'elles ne sont pas validées en jeu réel, et une fenêtre large a un coût : dans un passage dense, une note peut être prise pour celle qui résonne encore (mesuré sur la partition de démonstration avec une guitare synthétique : une mauvaise note sur 30 événements à partir de 85 ms, aucune à 75 ms).
* **Calibrage du rythme** : le son met du temps à traverser votre entrée, votre sortie et le format audio ; sans calibrage, vos notes peuvent être jugées un peu en avance ou en retard et la fenêtre dit **« Précision rythmique réduite »**. Le bouton **Calibrer…** joue **8 clics** (environ 10 secondes) : jouez une note sur chaque clic, avec la guitare ; **Annuler** arrête les clics immédiatement. Le calibrage vaut pour **cette entrée, cette sortie et ce format audio** : changer l'un demande un nouveau calibrage. À défaut, saisissez un **retard (ms)** à la main (positif quand vos notes s'entendent en retard, entre −1000 et 1000 ms) avec *Appliquer*, ou *Oublier*.
* Si le délai de l'entrée est élevé ou instable (typiquement sans fil), la fenêtre l'avertit : une entrée filaire est plus stable.

Pendant la partie, la palette montre le verdict de la dernière note, le compteur (à l'heure, en avance, en retard, fausses, manquées, en trop, non jugées) et la note **suivante**. Au bout, un **bilan** détaille les nombres ; il n'est gardé que tant que la fenêtre est ouverte.

<img src="images/manual/practice-follow-palette.png" width="400" alt="Palette du mode Suivre le tempo en cours de partie"> <img src="images/manual/practice-follow-summary.png" width="400" alt="Bilan de fin de partie">

**Pause, reprise :** si vous jouez la partition depuis la fenêtre principale, le suivi s'arrête (la lecture normale continue) ; **Reprendre** repart après une pause, les notes passées entre-temps ne sont pas jugées. Le mode est impossible si la lecture est déjà en cours : arrêtez-la d'abord.

Démarrer ce mode ouvre d'abord la fenêtre de démarrage (question d'accord, accord de test) : voir [B3](#b3-niveau-dentrée-gain-et-bruit).

## B6. Mode Arcade (alpha)

**Deux façons de le lancer.**

* **Le bouton coloré** de la barre de transport (juste après le bouton ♪ Pratique, <img src="images/manual/arcade-button.png" width="40" alt="Le bouton Arcade coloré">) : un clic lance Arcade **tout de suite** sur la piste sous le curseur de la partition, avec l'entrée actuelle (celle que vous avez mémorisée par défaut, voir [B2](#b2-la-fenêtre-pratique-interactive)), sans la fenêtre de préparation. La fenêtre du jeu occupe toute la largeur de l'écran. Il démarre même si vous ne jouez pas encore.
* **La fenêtre de préparation** : onglet *Mode*, « Arcade » ; dans l'onglet *Piste*, choisissez la piste que vous jouez (toute piste avec hauteur, **basse comprise**), puis **Démarrer Arcade**. La fenêtre de pratique se masque pendant que la fenêtre du jeu tourne.

<img src="images/manual/practice-mode-arcade.png" width="460" alt="L'onglet Mode, Arcade choisi, avec la taille des rectangles et l'inclinaison du plan">

C'est le mode « Suivre le tempo » (mêmes tempo, tolérance et calibrage) présenté sous forme de jeu. La piste que vous choisissez est muette, toutes les autres jouent.

<img src="images/manual/arcade-run.png" width="760" alt="Une partie Arcade : des rectangles viennent de l'horizon en ligne droite vers leur case sur le manche">

**Comment lire l'écran :**

* **Vous voyez une mesure à l'avance.** L'image contient les notes de la mesure suivante de la piste (une seconde au moins), quel que soit le tempo, pour qu'elle ne soit jamais surchargée.
* **Une note est un rectangle arrondi de la couleur de sa corde, et elle arrive en ligne droite** depuis l'horizon jusqu'au **milieu de sa case, au pied du manche** (le bord bas), à travers le manche, qui est transparent. Le rectangle a la largeur de la case de sa frette (une case plus haute est plus étroite). Il se tient au-dessus d'une petite empreinte sur cette ligne, reliée par un fil fin ; **au moment de jouer, le rectangle est exactement sur sa cible sur le manche**. Le fil est long pour la corde en haut du manche et court pour celle du bas. Le numéro de case est écrit sous l'empreinte tant qu'elle est au-dessus du manche, et la règle des cases sous le manche met en évidence la case de la note à jouer.
* **Le manche** (en bas) montre les cordes et les cases numérotées. Les cordes sont, de la plus grave à la plus aiguë sur une guitare : **rouge, jaune, bleu clair, orange, vert, violet** ; la plus grave est en haut du manche. **La corde à jouer est allumée** (une lueur), les autres sont ternes ; la lumière s'éteint quand la note est jugée.
* **Une corde à vide** est une fine barre horizontale de la couleur de sa corde, avec un capuchon clair à gauche et les mots « Corde à vide », qui descend sur le manche.
* **Un accord** est un panneau translucide, de sa première à sa dernière case, avec un rectangle arrondi pour chaque note, son **nom** au-dessus de son coin gauche et les numéros de case sur l'accord à jouer.
* **Un bend** se voit sur la note : tant qu'elle arrive, une petite flèche en pointillés monte du bout droit de son rectangle avec la valeur du bend à côté ; quand c'est la note à jouer, un **rectangle fantôme en pointillés** apparaît sur la même corde à la case que le bend atteint (une case par demi-ton), relié à la cible par une flèche en pointillés avec la valeur au-dessus. La valeur est en tons : ¼, ½, ¾, *entier* (un ton), 1½, 2... *pré* devant signifie un pre-bend (la note commence déjà tirée) et ↓ derrière un relâché (elle redescend). Seules les notes seules l'affichent, pas les accords ; le jugement ne change pas (la courbe écrite est suivie).
* **En haut** : votre **série** (à gauche), le **tempo** en pourcentage et la **suite** (à droite).
* **Les verdicts** (à l'heure, en avance, en retard, faux, incomplet, manqué) apparaissent en **badge** sous le manche une fois la note jugée (le texte accompagne toujours la couleur).
* **Recommencer, Pause / Reprendre** et **Arrêter** sont en bas à droite. **Recommencer** repart du début du morceau (ou du passage que vous avez lancé) **sans quitter le jeu**. Pendant le décompte, les notes arrivent du fond pendant que vous entendez les clics ; après une **Pause**, **Reprendre** refait un décompte à partir de l'endroit où vous étiez. Jouer la partition depuis la fenêtre principale arrête la partie.

<img src="images/manual/arcade-chord.png" width="760" alt="Un accord : un panneau translucide avec son nom, un rectangle par note, et la cible sur le manche">

**Réglages de l'image** (onglet *Mode*, conservés jusqu'à la fermeture de l'application, lus au démarrage d'une partie) :

* **Taille des rectangles qui arrivent** : de 0 à 150 % de la taille de la cible sur le manche (100 % par défaut : le rectangle qui arrive a la taille de sa cible ; 0 % le réduit à un pixel).
* **Inclinaison du plan** : l'**échelle à l'horizon** (20 à 100 %, 35 % par défaut : plus c'est bas, plus le plan est incliné) et la **hauteur de l'horizon** (0 à 40 % de la hauteur de la fenêtre, 18 % par défaut).
* **Flammes de scène** : des flammes brûlent derrière l'image, en bas et sur les deux côtés, et **grandissent quand vous jouez mieux** : leur hauteur suit la part du score maximum atteinte dans la partie (braises à 50 % ou moins, flammes pleines à 100 %). Elles restent des braises tant que quatre événements de la partie n'ont pas été jugés, montent en une demi-seconde environ et retombent lentement en trois secondes environ, et ne sont jamais dessinées sur un texte ou une note (elles s'effacent près des numéros, des noms de corde et des pastilles de verdict). **Désactivées**, **Discrètes** (par défaut : plus petites et plus pâles, la moitié des langues de feu) ou **Pleines**, dans l'onglet *Mode* ; le choix est lu au début d'une partie, donc un changement fait pendant une partie vaut pour la suivante, et il est oublié à la fermeture de l'application. Ce n'est que de la décoration : **elles ne changent jamais le jugement ni le score**. Elles ne sont pas dessinées au niveau de qualité le plus bas et gardent leurs couleurs dans tous les aspects du jeu. **Sur macOS, quand le réglage système *Réduire les animations* est activé, les langues de feu ne bougent pas** (seule leur hauteur suit votre jeu) ; cette préférence n'est pas lue sous Windows ni Linux dans cette version.

### Les techniques : ce qu'Arcade dessine, et ce qu'il vérifie

Chaque technique écrite sur une **note seule** est dessinée **sur la note pendant qu'elle arrive, puis de nouveau sur le manche quand c'est la note à jouer**, dans le même jaune que les bends. Les accords n'en montrent aucune. Les images ci-dessous sont produites avec la même scène et le même peintre que le jeu, sur des partitions d'exemple des tests.

<img src="images/manual/arcade-techniques-1.png" width="760" alt="Un hammer-on avec son fantôme sur le manche, un pull-off et un slide qui arrivent">

<img src="images/manual/arcade-techniques-2.png" width="760" alt="Une onde de vibrato sur le manche, une pastille T, trois traits de trémolo, un bend et une barre d'harmonique qui arrivent">

| Technique | Ce que vous voyez (en vol → sur le manche) | Ce qu'Arcade **vérifie** |
|---|---|---|
| **Bend**, pre-bend, relâché | flèche et valeur → rectangle fantôme à la case atteinte et flèche (voir plus haut) | **La hauteur** : la courbe écrite est suivie. Le bend lui-même **n'est pas annoncé entendu ou non** (« ne peut pas être vérifié encore ») : le détecteur trouve 82 % des bends annotés, le seuil est 85 %. |
| **Hammer-on** (H), **pull-off** (P) | petit arc et la lettre → rectangle fantôme sur la note suivante de la corde et arc en pointillés. *H/P* quand la note n'a pas de note suivante sur sa corde (le sens est inconnu) | **La hauteur** de la note d'arrivée, acceptée sans nouvelle attaque. Le legato lui-même **n'est pas annoncé** : aucun enregistrement étiqueté ne valide son détecteur. |
| **Slide** (S) | petit rail en pointillés → rectangle fantôme sur la note suivante et rail en pointillés avec sa pointe | **La hauteur** de la note d'arrivée. Le slide lui-même **n'est pas annoncé** : détecteur à 48 % des slides annotés. |
| **Vibrato** | petite onde → onde le long de la corde après la cible | **La hauteur** comme pour une note simple. Le vibrato lui-même **n'est pas annoncé** : détecteur à 59 %. |
| **Palm mute** (P.M.), **tapping** (T) | une pastille au-dessus du rectangle, en vol et sur le manche | **Rien** de plus que la note simple : pas de détecteur. |
| **Trémolo** (picking) | trois petits traits au-dessus du rectangle | **Rien** de plus que la note simple : pas de détecteur. |
| **Harmonique** | ce n'est pas une note à jouer : la barre de timing dit *Harmonique ◇* | **Pas jugée** : elle passe toute seule. |
| Slap, pop, accents, let ring, staccato, trille, levier | non dessinés (les deux derniers montrent la barre *Automatique*) | Pas jugés. |

**En résumé : Arcade juge le timing et la hauteur. Aucune technique n'est annoncée « détectée » ou « non détectée » aujourd'hui** : la fenêtre de résultat les liste à part comme « ne peut pas être vérifié encore », ce qui n'est pas une erreur de votre part. Les dessins disent seulement ce que la partition demande.

**À la fin** (et aussi quand vous arrêtez le jeu avant la fin), la fenêtre **Résultat** donne le score, la part du maximum, la meilleure série et le décompte de chaque verdict. Vous pouvez **rejouer depuis le début** ou **depuis une mesure** choisie (*Depuis la mesure*, puis *Jouer*). Les techniques (hammer-on, slide, bend...) sont signalées à part et ne changent pas le score.

<img src="images/manual/arcade-result.png" width="330" alt="La fenêtre de résultat">

**Alpha, ce qui n'est pas vérifié :** voir [B10](#b10-qualité-mesurée-limites-et-trace-de-diagnostic). Les notes **sous sol 1 (49 Hz)** (les plus graves d'une basse standard) sont affichées mais pas jugées. L'**écoute de votre propre guitare à travers l'application n'est pas proposée** : jouez par votre ampli ou par un casque branché sur l'interface audio. La fenêtre du jeu occupe la largeur de l'écran mais n'est pas le plein écran natif de macOS.

Démarrer ce mode ouvre d'abord la fenêtre de démarrage (question d'accord, accord de test) : voir [B3](#b3-niveau-dentrée-gain-et-bruit).

## B7. Les réglages de reconnaissance audio

*Outils > Configuration > Reconnaissance audio*. Chaque réglage a une **valeur recommandée** et un bouton **Rétablir** par catégorie ; *Rétablir tous les réglages de reconnaissance audio* remet tout. Les modifications sont enregistrées avec **Valider** ; **Annuler** garde les valeurs précédentes. Les réglages de l'accordeur s'appliquent aussitôt, ceux de la pratique au **prochain Démarrer** ; les réglages de niveau et d'attaque relancent brièvement l'écoute. Survolez le nom d'un réglage pour voir son effet. Les captures montrent les valeurs de la machine de test, pas forcément les valeurs recommandées (indiquées ci-dessous).

<img src="images/manual/rec-input.png" width="700" alt="Catégorie Entrée audio">

**Catégorie « Entrée audio »** : l'**entrée** (partagée par l'accordeur et la pratique ; elle change immédiatement et n'est pas mémorisée après la fermeture de l'application, sauf si vous appuyez sur **Mémoriser par défaut** dans la fenêtre de pratique ; bouton **Actualiser** compris), le **niveau minimal utilisable** (−40 dBFS recommandé), l'**alerte de niveau élevé** (−6 dBFS), **Réduire le bruit de fond** (désactivé) et **Suggérer un gain d'entrée** (activé).

<img src="images/manual/rec-tuner.png" width="700" alt="Catégorie Accordeur">

**« Accordeur »** : l'**écart considéré comme juste** (5 cents) et **Garder la dernière note** (activé : l'aiguille grise garde la dernière lecture quand la corde se tait).

<img src="images/manual/rec-notes.png" width="700" alt="Catégorie Pratique, notes">

**« Pratique, notes »** : l'**écart de hauteur accepté** (35 cents recommandés) ; la **certitude minimale** (80 %) du détecteur avant de juger une note ; la **durée de confirmation d'une note** (107 ms) ; la **sensibilité à l'attaque** (4,5 dB) ; le traitement des **liés, tirés-poussés, glissés** (une nouvelle attaque est exigée pour chaque note, le seul choix de cette version) ; **Vérifier les techniques de jeu** (activé) ; la **tolérance de timing** pour le jeu au tempo (normale).

<img src="images/manual/rec-chords.png" width="700" alt="Catégorie Pratique, accords">

**« Pratique, accords »** : l'**exigence sur les accords** (33 %), la **fenêtre de grattage** (600 ms : le temps maximal d'un coup de médiator sur plusieurs cordes), les **cordes exigées** (toutes les cordes vérifiables) et les **cordes en trop** (pas encore vérifiées).

<img src="images/manual/rec-passages.png" width="700" alt="Catégorie Passages">

**« Passages »** : répéter les passages (toutes les 2 mesures), répéter si les erreurs dépassent un pourcentage (50 %), **nombre maximal de répétitions** (2).

<img src="images/manual/rec-engine.png" width="700" alt="Catégorie Moteur de reconnaissance">

**« Moteur de reconnaissance »** : le moteur de détection de hauteur (automatique, FastYin ; le seul choix de cette version).

<img src="images/manual/rec-advanced.png" width="700" alt="Catégorie Avancé">

**« Avancé »** : la tolérance « trop haut » par case (0,7 cent : les notes pressées sonnent un peu plus haut vers l'aigu du manche ; ce réglage ajoute ces cents du côté aigu seulement, 0 le désactive), le niveau d'échantillon saturé (99 %), la part d'échantillons saturés (10 %), la certitude minimale de l'accordeur (80 %), le seuil de détection d'attaque (−50 dBFS), les harmoniques par corde pour les accords (2) et la durée de confirmation d'un accord (43 ms).

## B8. L'accordeur

*Outils > Accordeur* ouvre l'accordeur ; il partage sa détection de hauteur avec la pratique.

<img src="images/manual/tuner.png" width="460" alt="L'accordeur guidé">

* **Deux modes** : *Guidé (6 cordes)* vous conduit corde par corde ; *Chromatique* montre la note la plus proche de ce que vous jouez.
* **Accordage** : un préréglage, *Personnalisé…* (une note par corde) ou l'**accordage d'une piste** du morceau ouvert.
* **L'aiguille** montre la **médiane des lectures sûres du dernier quart de seconde** : un parasite ne la déplace pas, une courte interruption ne la fige pas. Le texte dit quoi faire (« Trop bas : 12 cents sous mi2. Accordez plus haut », « Juste », « Trop haut... »).
* **Aiguille grise figée** (« Dernière mesure ») : la corde est silencieuse ou trop faible, rejouez-la.
* **Entrée** : l'entrée choisie et son niveau (bon / trop faible / trop fort / saturé), **Vérifier à nouveau le niveau** et le curseur de **gain** (−6 à +18 dB) avec sa **Remise à zéro** ; **Réglages** ouvre les réglages de l'accordeur.
* **Pas pendant la pratique** : l'accordeur refuse de s'ouvrir tant que la fenêtre de pratique est ouverte (ils partagent le micro) ; fermez-la d'abord, accordez, puis relancez.

## B9. Analyse de la partition (mesures invalides)

Ouvrir un fichier Guitar Pro ou MIDI ne vous interrompt plus par une boîte de dialogue sur les mesures dont la durée ne correspond pas au chiffrage. Pour voir **tous les problèmes d'un morceau** : *Édition > Afficher les mesures invalides* (ou l'icône de la barre d'outils). La fenêtre liste les mesures et notes liées invalides de chaque piste (piste, mesure, voix, trop long / trop court...), avec des filtres, l'état de la mesure courante et un bouton **Corriger**. Elle ne bloque rien : laissez-la ouverte pendant que vous éditez.

## B10. Qualité mesurée, limites et trace de diagnostic

Sur un banc d'essai de 1 208 enregistrements annotés de vraies guitares (propres et avec 11 effets), 6 178 tests :

* **notes isolées reconnues** : 99,8 % (effets) à 100 % (propre) ;
* **une note à un demi-ton ou un ton de la note jouée acceptée** : aucune sur plus de 2 700 tests ;
* **intervalles et accords de trois notes avec effets reconnus** : 87 % ; **accords de guitares propres** : 95 % ;
* **accords auxquels il manque une note refusés** : 96 à 98 % ;
* **délai** entre le jeu d'une note et sa reconnaissance : environ 0,1 s (médiane 105 ms, 90 % sous 170 ms).

Sur un **jeu de données indépendant** (GuitarSet, accompagnement rythmique à la guitare acoustique), le résultat est **bien plus bas** : 60 % des accords complets reconnus (six micros de manche additionnés), 46 % au micro ; 13 % des accords auxquels il manque une note acceptés par erreur.

**Limites, mesurées et non cachées :**

* **Notes rapprochées :** environ 0,3 s entre notes jouées au médiator sont suivies ; plus vite, seule la première d'une série est reconnue et la pratique vous attend, sans jamais compter de fausse note. La fenêtre le dit.
* Les accords dont la note grave est sol2 sont parfois acceptés un ton plus haut ; les accords de septième à quatre notes et certains accords à vide (la majeur, mi majeur) sont reconnus moins souvent.
* Une octave, ou une note qui est un harmonique d'une note grave de l'accord, ne se vérifie pas séparément.
* **Alpha (Arcade et suivi du tempo) :** validés par des tests automatiques et par le propriétaire sur un Mac, **pas** par d'autres joueurs, guitares, micros ou machines. Sur un test de charge de 10 minutes, la chaîne audio **manque encore environ une échéance d'analyse par minute** (un trou d'ordonnancement du système) ; il n'a pas été montré qu'elle change un jugement, mais l'objectif n'est pas atteint.
* Les réglages ont été mis au point sur ces enregistrements ; une autre guitare ou un autre micro peut demander un autre gain ou une autre sensibilité.

**Trace de diagnostic (pour comprendre un échec).** Quand une note jouée juste n'est pas reconnue (ou une fausse l'est), l'application peut écrire **ce qu'elle a entendu, sous forme de nombres seulement, sans audio** :

1. quittez l'application, puis lancez-la avec la propriété `tuxguitar.practice.trace` nommant un fichier, par exemple sur macOS depuis un terminal : `open -n --env JAVA_TOOL_OPTIONS="-Dtuxguitar.practice.trace=/tmp/practice-trace.csv" /chemin/vers/TuxGuitarAdvanced.app` ;
2. jouez le passage dans la pratique ;
3. ouvrez le CSV : une ligne par pas d'analyse (temps, événement, notes attendues, hauteur détectée et certitude, niveau, force de l'attaque, amplitude de chaque note attendue et de ses voisines, lignes `ACCEPTED`). Envoyez-le avec la description de ce qui aurait dû se passer.

## B11. Questions fréquentes et dépannage

**Rien n'est entendu / le niveau est « trop faible ».** Vérifiez l'**entrée** choisie (onglet *Entrée*), jouez une corde en regardant l'indicateur, augmentez le gain de l'interface (de préférence) ou le gain de l'application, rapprochez le micro. Avec une interface branchée après le lancement, appuyez sur **Actualiser**.

**macOS ne laisse pas la pratique écouter le micro.** *Réglages Système > Confidentialité et sécurité > Micro* : autorisez TuxGuitarAdvanced.

**L'accordeur ne s'ouvre pas.** La fenêtre de pratique est ouverte (ou masquée pendant une partie) : fermez-la, puis ouvrez l'accordeur.

**Les notes sont jugées « en retard » ou « en avance » systématiquement.** Calibrez le rythme (onglet *Mode*, **Calibrer…**), ou entrez un retard manuel. Une entrée filaire et un casque filaire sont plus stables que le sans fil.

**La fenêtre de pratique a disparu après une partie Arcade.** Rouvrez-la par *Outils > Pratique interactive* (elle revient depuis la version qui suit 0.5.0-alpha.1 ; dans cette version, redémarrez l'application si besoin).

**La partition d'un fichier Guitar Pro se lit mal ou la pratique refuse la piste.** La pratique ne retient que les pistes avec hauteur ; les pistes de percussions sont exclues, et la raison est indiquée dans l'onglet *Piste*. *Afficher les mesures invalides* aide à trouver des mesures dont la durée est fausse.

**Je veux plus ou moins d'exigence.** Ajustez, dans *Configuration > Reconnaissance audio*, l'écart de hauteur accepté, la certitude minimale, la durée de confirmation et, pour les accords, l'exigence sur les accords ; *Rétablir* revient aux valeurs mesurées.

**Où trouver plus de détails ?** [PRACTICE-FR.md](PRACTICE-FR.md) (guide du Practice, mesures de qualité), [README-FR.md](README-FR.md) et les notes de release.

---

# Partie C : un exemple complet, pas à pas

Ce chapitre vous mène d'un document vide à un **morceau de trois pistes** (guitare, basse, batterie), le joue, puis fait travailler la guitare dans les **trois modes** : Attendre la bonne note, Suivre le tempo et Arcade. Chaque étape a sa capture. Comptez une vingtaine de minutes. Le morceau fait quatre mesures, en 4/4 à 120 BPM, pour que tout reste simple.

*Les captures de ce chapitre montrent l'interface en anglais (les autres captures du manuel sont en français) ; les noms des menus et des boutons sont donnés ci-dessous dans les deux langues.*

**À propos de la guitare des captures.** Pour montrer les modes de pratique sans jouer d'une vraie guitare, l'ordinateur de test joue par ses haut-parleurs un son synthétique de corde pincée, que son micro intégré entend (l'entrée « Default Audio Device »). Les notes sont les bonnes : vous verrez de vrais verdicts (« Correct », « À l'heure ») ; certains sont en retard, faux ou manqués parce que le son vient d'un haut-parleur et que le rythme n'est pas celui d'un joueur. **Avec votre guitare, le résultat est le vôtre.** Sans guitare sous la main, vous pouvez faire pareil : jouez un son quelconque près du micro.

## C1. Créer le morceau

**Étape 1. Partir d'un document vide.** Au lancement, un document vide est prêt, avec une piste de guitare et une mesure (*Fichier > Nouveau*, *File > New*, en crée un autre).

<img src="images/manual-en/tut-01-blank.png" width="760" alt="Un document vide">

**Étape 2. Nommer le morceau.** *Composition > Propriétés* (*Properties*, F5) : tapez un nom et un auteur, puis **OK**.

<img src="images/manual-en/tut-02-properties.png" width="380" alt="Les propriétés du morceau remplies">

**Étape 3. Nommer la piste de guitare.** Double-cliquez la piste dans la liste du bas (ou *Piste > Propriétés*). Remplacez le nom par *Guitare* ; l'instrument (Steel String Acoustic Guitar 1) et l'accordage (E A D G B E) conviennent à une guitare standard. Fermez la fenêtre.

<img src="images/manual-en/tut-03-guitar-track.png" width="380" alt="Les propriétés de la piste de guitare">

**Étape 4. Écrire les notes de la guitare.** Cliquez la première mesure de la tablature, puis choisissez la durée **noire** dans la barre latérale. Sur la troisième corde (sol), tapez les cases **5 5 7 5** : chaque chiffre écrit une note et le curseur passe au temps suivant (→ pour avancer). Mesure 2 : **3 3 5 3**. Mesure 3 : **5 5 7 5** à nouveau. Mesure 4 : choisissez la **ronde** et écrivez un accord de do à trois notes : **5 sur la corde 3, 5 sur la corde 4 et 3 sur la corde 5** (changez de corde avec ↑ ↓ et tapez chaque chiffre sur le même temps). Si le morceau a besoin de place, *Mesure > Ajouter une mesure* ajoute des mesures (ici, quatre).

<img src="images/manual-en/tut-04-guitar-notes.png" width="760" alt="Les quatre mesures de la piste de guitare">

## C2. Ajouter la basse

**Étape 5. Ajouter une piste.** *Piste > Ajouter une piste* (bouton + vert). Une nouvelle piste apparaît, avec le même son de guitare.

<img src="images/manual-en/tut-05-added-track.png" width="760" alt="Une deuxième piste ajoutée">

**Étape 6. En faire une basse.** Double-cliquez la nouvelle piste et nommez-la *Basse*. Cliquez la clé à côté de **Instrument** (ou *Affichage > Afficher les instruments*) et choisissez **Fingered Electric Bass** comme programme de son canal.

<img src="images/manual-en/tut-06-bass-instrument.png" width="560" alt="L'instrument de la piste de basse">

**Étape 7. Lui donner quatre cordes.** Dans les propriétés de la piste, cliquez la clé à côté de **Accordage** : choisissez *Bass*, *4-String* et *E Tuning - E A D G* (cordes mi1 la1 ré2 sol2). **OK**, puis **Fermer**.

<img src="images/manual-en/tut-07-bass-tuning.png" width="400" alt="La fenêtre d'accordage de la basse">

**Étape 8. Écrire la ligne de basse.** Choisissez la durée **blanche**. Mesure 1 : **3 3** sur la troisième corde. Mesure 2 : **1 1**. Mesure 3 : **3 3**. Mesure 4 : **3 3**.

<img src="images/manual-en/tut-08-bass-notes.png" width="760" alt="La ligne de basse">

## C3. Ajouter la batterie

**Étape 9. Ajouter une troisième piste, en faire une batterie.** *Piste > Ajouter une piste*, double-clic, nommez-la *Batterie* et choisissez l'instrument **DrumKit**. Les champs d'accordage et de cases se vident : une piste de batterie n'a pas de cordes.

<img src="images/manual-en/tut-09-drums-track.png" width="400" alt="Les propriétés de la piste de batterie">

**Étape 10. Dessiner le rythme dans la matrice.** *Affichage > Afficher la matrice* ouvre l'éditeur de matrice : chaque ligne est un son de batterie. Faites défiler jusqu'à **Acoustic Bass Drum** (la grosse caisse) et **Acoustic Snare** (la caisse claire) et cliquez les cases : grosse caisse sur les temps 1 et 3, caisse claire sur les temps 2 et 4.

<img src="images/manual-en/tut-10-drums-matrix.png" width="560" alt="La matrice avec un motif grosse caisse et caisse claire">

**Étape 11. Copier la mesure.** Curseur dans la mesure 1, *Mesure > Copier la mesure*, puis allez à la mesure 2 et *Mesure > Coller la mesure* ; recommencez pour les mesures 3 et 4.

<img src="images/manual-en/tut-11-drums-done.png" width="760" alt="Les quatre mesures de batterie">

**Étape 12. Voir les trois pistes ensemble.** *Affichage > Multipiste* montre toutes les pistes à la fois (la disposition passe en linéaire) ; *Affichage > Réduire* fait tout tenir.

<img src="images/manual-en/tut-12-three-tracks.png" width="760" alt="Les trois pistes du morceau">

Enregistrez le morceau avec *Fichier > Enregistrer* (⌘S) sous le nom de votre choix.

## C4. Jouer le morceau

**Étape 13. Lecture normale.** Appuyez sur **Espace** (ou le bouton Lecture). Les trois pistes sonnent ; le temps joué est rouge et la mesure surlignée.

<img src="images/manual-en/tut-13-playing.png" width="760" alt="Le morceau en lecture : le temps joué est rouge">

Utilisez *Lecteur > Mode de lecture* pour ralentir (par exemple 60 %) ou boucler quelques mesures : c'est la façon normale d'écouter une partition ; les modes de pratique ci-dessous vous demandent de jouer, **vous**.

## C5. S'entraîner : trois modes

Assurez-vous d'abord que l'entrée audio fonctionne : ouvrez **Outils > Pratique interactive**. Dans l'onglet *Entrée*, choisissez votre entrée et vérifiez le niveau (jouez une corde).

<img src="images/manual-en/practice-input.png" width="420" alt="L'onglet Entrée avec l'indicateur de niveau">

### Mode 1 : Attendre la bonne note

**Étape 14. Démarrer.** Onglet *Piste* : choisissez la piste *Guitare* ; onglet *Mode* : **Attendre la bonne note** ; appuyez sur **Travailler cette piste**.

<img src="images/manual-en/practice-track.png" width="400" alt="L'onglet Piste, guitare choisie"> <img src="images/manual-en/practice-mode-wait.png" width="400" alt="L'onglet Mode, Attendre la bonne note">

**Étape 15. Jouer ce qui est encadré.** La note à jouer est **encadrée** sur la partition, et la palette dit « Jouez C4 » (do4). La partition vous attend.

<img src="images/manual-en/tut-16-wait-main.png" width="760" alt="La note encadrée sur la partition et la palette">

**Étape 16. Lire la réponse.** Quand la bonne note est entendue, la palette dit « Correct » avec la note ; une mauvaise note dit ce qui a été entendu et ce qu'il faut jouer.

<img src="images/manual-en/tut-14-wait-palette.png" width="380" alt="La palette qui attend C4"> <img src="images/manual-en/tut-15-wait-correct.png" width="380" alt="La palette après la bonne note">

**Étape 17. La fin.** Après la dernière note ou le dernier accord, la palette dit « Bravo, vous êtes arrivé à la fin » avec le décompte des passages réussis du premier coup. **Recommencer** relance, **Stop** ferme la pratique. L'onglet *Passages* règle ce qui se passe quand un passage a trop d'erreurs (il est répété).

<img src="images/manual-en/tut-17-wait-done.png" width="380" alt="La fin de la pratique en mode Attendre">

### Mode 2 : Suivre le tempo

**Étape 18. Choisir le mode.** De retour dans la fenêtre de pratique, onglet *Mode* : **Suivre le tempo**. Le tempo (100 % = comme écrit), la fenêtre de temps et le calibrage du rythme apparaissent. Pour un premier essai, laissez 100 %, ou baissez-le si le morceau est difficile pour vous. Appuyez ensuite sur **Suivre le tempo de cette piste**.

<img src="images/manual-en/practice-mode-follow.png" width="480" alt="L'onglet Mode avec Suivre le tempo">

**Étape 19. Jouer avec la partition.** La partition défile à son tempo, après un décompte, avec la basse et la batterie ; la guitare est muette pour que vous la jouiez. Chaque note est jugée dans le temps.

<img src="images/manual-en/tut-19-follow-main.png" width="760" alt="La partition à son tempo avec la palette">

**Étape 20. Lire les verdicts.** La palette montre le verdict de la dernière note (« Right note, late (163 ms) » : juste note, en retard de 163 ms), les compteurs (à l'heure, en avance, en retard, fausses, manquées, en trop) et la note suivante.

<img src="images/manual-en/tut-20-follow-palette.png" width="380" alt="La palette pendant la partie">

**Étape 21. Le bilan.** À la fin, un bilan liste les nombres. Si vos notes sont souvent en avance ou en retard du même écart, utilisez **Calibrer…** (ou saisissez le retard à la main) dans l'onglet *Mode* : voir [B5](#b5-mode--suivre-le-tempo-).

<img src="images/manual-en/tut-21-follow-summary.png" width="380" alt="Le bilan de la partie">

### Mode 3 : Arcade (alpha)

**Étape 22. Choisir Arcade.** Onglet *Mode* : **Arcade**. Le tempo, la fenêtre de temps et le calibrage sont les mêmes ; de nouvelles lignes règlent la **taille des rectangles qui arrivent**, l'**inclinaison du plan** et les **flammes de scène**. Appuyez sur **Démarrer Arcade** (ou, la prochaine fois, cliquez sur le bouton Arcade coloré de la barre de transport, qui démarre tout de suite sur la piste sous le curseur). Appuyez sur **Démarrer Arcade**. La fenêtre de démarrage demande alors si la guitare est accordée et un accord de test avant le début de la partie (voir B3).

<img src="images/manual-en/practice-mode-arcade.png" width="480" alt="L'onglet Mode avec Arcade">

**Étape 23. Jouer les notes quand elles atteignent le manche.** Une nouvelle fenêtre montre un manche en bas. Chaque note vient de l'horizon en ligne droite, sur la voie de sa case, et se pose sur sa cible sur le manche au moment où vous devez la jouer. Le rectangle arrondi a la couleur de sa corde ; son numéro de case est écrit sous son empreinte. En haut : votre série, le tempo et la note suivante. Les badges sous le manche disent les derniers verdicts (« À l'heure », « Faux »). **Recommencer** repart du début, **Pause** et **Arrêter** sont à côté.

<img src="images/manual-en/tut-23-arcade-run.png" width="760" alt="Arcade : des rectangles viennent vers le manche en ligne droite">

**Étape 24. Les accords.** Un accord est un panneau translucide avec son nom au-dessus de son coin gauche et un rectangle pour chaque note de l'accord.

<img src="images/manual-en/tut-24-arcade-chord.png" width="760" alt="Arcade : un accord en panneau translucide">

**Étape 25. Le résultat.** À la fin, la fenêtre de résultat donne le score, la part du maximum, la meilleure série et le décompte de chaque verdict. **From the start** (depuis le début) ou **From measure** + **Play** (depuis une mesure) rejoue.

<img src="images/manual-en/tut-25-arcade-result.png" width="300" alt="Le résultat Arcade">

**Étape 26. Retour à la fenêtre de préparation.** Fermez le résultat : la fenêtre de pratique est de retour, avec vos réglages. Rouvrez-la à tout moment par *Outils > Pratique interactive*.

<img src="images/manual-en/tut-26-back.png" width="400" alt="La fenêtre de pratique de nouveau après la partie">

### Dernier contrôle : l'accordeur

Avant une vraie séance, accordez la guitare : *Outils > Accordeur*. Sur la capture, la corde de sol sonne et l'aiguille est au centre.

<img src="images/manual-en/tut-27-tuner.png" width="380" alt="L'accordeur guidé qui lit un sol">

Vous avez écrit, joué et travaillé un morceau de trois pistes. Pour aller plus loin : [B7](#b7-les-réglages-de-reconnaissance-audio) pour les réglages de reconnaissance, [A5](#a5-les-effets-de-jeu) pour les effets et [A6](#a6-les-accords) pour les accords.
