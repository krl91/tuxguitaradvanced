# Mode Practice interactif, guide utilisateur

Le mode Practice écoute votre guitare et vérifie, note par note et accord par accord, que vous jouez ce que dit la partition. Il ne joue pas la partition à votre place et il n'enregistre jamais le son : seuls des chiffres calculés à partir du son (hauteur, niveau, notes entendues) existent en mémoire pendant son fonctionnement.

*English: [PRACTICE.md](PRACTICE.md).*

## Ce qu'il faut

* macOS (Apple Silicon), Linux ou Windows : les modules de pratique sont dans les trois paquets. L'accès au micro et le comportement des entrées audio dépendent de la prise en charge de Java Sound par le système.
* Un son de guitare sur une entrée : le micro intégré convient pour un jeu clair ; une interface audio avec le signal direct de la guitare est préférable. Un casque est conseillé si la partition joue aussi dans les haut-parleurs.
* Une partition avec une piste de guitare (accordage standard ou tout accordage écrit dans la partition).

## Démarrer

| Comment | Ce qui se passe |
|---|---|
| **Bouton Practice** <img src="images/features/practice.png" width="18" alt="Practice"> à côté du bouton Play | Pratique la piste sous le curseur de la partition, à partir du curseur, dès que l'entrée est utilisable. Un seul clic. |
| Menu *Outils > Interactive Practice* | Ouvre la fenêtre de pratique pour choisir d'abord la piste, le départ et l'entrée. |

La première fois (ou après un changement d'entrée), la fenêtre **vérifie le niveau d'entrée** : jouez une corde et regardez la jauge. Si elle indique *trop bas* ou *saturation*, rapprochez-vous ou réglez le gain (voir plus bas).

## Pendant la pratique

Une petite palette reste à l'écran et la note ou l'accord attendu est encadré sur la partition :

* **coche verte** : entendu, la pratique avance ;
* **croix rouge** : une autre note a été entendue (affichée seulement pour une note qui vient d'être frappée, pas pour la note précédente qui résonne encore) ;
* rien ne se passe sur du silence ou du bruit, et une note qui résonne n'est jamais prise pour la suivante ;
* un accord est accepté quand ses notes sont entendues ensemble ; les notes manquantes sont nommées si l'accord est incomplet ;
* **Passer** saute la note, **Reprendre** continue après une pause (lecture, entrée inutilisable), **Recommencer** reprend le passage, **Stop** termine la pratique immédiatement et ramène la fenêtre de préparation.

La pratique peut aussi se faire **par passages** : quelques mesures à la fois, répétées jusqu'à atteindre la part de notes justes que vous avez fixée.

Ce qui est vérifié, c'est la **hauteur**. La corde et la case utilisées ne peuvent pas être connues par le son, et les techniques écrites (bends, vibrato, glissés, hammer-ons, pull-offs...) sont affichées mais pas jugées : une note de hammer-on ou de pull-off est acceptée d'après sa hauteur.

## Gain d'entrée, réduction du bruit et gain suggéré

Sous la jauge de niveau de la fenêtre de pratique, et dans l'accordeur, un curseur **Gain d'entrée** renforce ou baisse le signal dans l'application (de -6 à +18 dB) ; **Remise à zéro** revient à 0 dB. Le gain n'est jamais mémorisé : il revient à 0 dB quand l'entrée change et à chaque démarrage. Si l'amplification n'amplifie surtout que le bruit de fond, ou fait saturer le signal, un avertissement le dit et conseille d'augmenter plutôt le gain de l'interface audio.

Réglages (*Réglages de reconnaissance* dans la fenêtre de pratique) : un **noise gate / soustraction du bruit de fond** optionnel et un **gain suggéré** après le contrôle de niveau ; les deux peuvent être désactivés.

## Réglages de reconnaissance

Chaque réglage a une valeur recommandée et un bouton *Restaurer*. Ceux que vous pouvez vouloir changer :

| Réglage | Effet |
|---|---|
| Écart de hauteur accepté | De combien (en cents) une note peut s'écarter de la note écrite et rester correcte. |
| Tolérance « trop haut » par case (Avancé) | Les notes sur le manche sonnent un peu trop haut, davantage vers les aigus : ce réglage ajoute ce nombre de cents par case, du côté trop haut seulement. 0 le désactive. |
| Certitude minimale | À quel point le détecteur doit être sûr avant de juger une note seule. |
| Durée de confirmation d'une note | Combien de temps une note doit sonner avant d'être acceptée. |
| Sensibilité à l'attaque | Quelle hausse soudaine du son compte comme une nouvelle attaque. |
| Réglages des accords | Avec quelle rigueur les notes d'un accord sont vérifiées et combien de temps peut durer un grattage. |

## L'accordeur

*Outils > Accordeur de guitare* ouvre l'accordeur. Il partage sa détection de hauteur avec le mode pratique.

* **Deux modes** : *Guidé (6 cordes)* vous fait passer par les cordes d'un accordage, *Chromatique* affiche la note la plus proche de ce que vous jouez.
* **Accordage** : un préréglage, *Personnalisé...* (une note par corde) ou l'accordage d'une piste de la partition ouverte (« Accordage de la piste N »).
* **L'aiguille** affiche la **médiane des mesures fiables du dernier quart de seconde** : un parasite ne la bouge pas, un trou court ne la fige pas, une nouvelle note remplace l'ancienne en quelques mesures. Le texte dessous dit quoi faire : « Trop bas : 12 cents en dessous de Mi2. Montez la corde. », « Juste », « Trop haut... ».
* **Une aiguille grise figée** (« Dernière mesure (figée) ») signifie que la corde est silencieuse ou trop faible maintenant : rejouez-la.
* **Entrée** : l'entrée choisie et son niveau (« bon / trop faible / trop fort / saturation »), un bouton **Vérifier à nouveau le niveau**, et le curseur **Gain d'entrée** (-6 à +18 dB) avec son bouton **Remise à zéro**, comme dans la fenêtre de pratique.
* **Pas pendant la pratique** : l'accordeur refuse de s'ouvrir tant que la fenêtre de pratique est ouverte (ils partagent le micro) ; fermez d'abord la fenêtre de pratique.

## Analyse de la partition (mesures invalides)

L'ouverture d'un fichier Guitar Pro ou MIDI ne vous interrompt plus avec une fenêtre sur les mesures dont la durée ne correspond pas à la signature rythmique. Pour voir **tous les problèmes d'un morceau** :

1. *Édition > Afficher les mesures invalides* (ou l'icône de la barre d'outils <img src="images/features/analyse-score.png" width="18" alt="Analyser">) ;
2. la fenêtre liste les mesures et notes liées invalides de chaque piste (piste, mesure, voix, trop longue / trop courte...), avec des filtres, l'état de la mesure courante et un bouton **Réparer** ;
3. elle ne bloque rien : laissez-la ouverte pendant que vous éditez, et rouvrez-la quand vous voulez.

## Trace de diagnostic (comprendre un échec)

Quand une note jouée correctement n'est pas reconnue (ou qu'une fausse l'est), vous pouvez demander à l'application d'écrire ce qu'elle a entendu, **en chiffres uniquement, sans audio** :

1. quittez l'application, puis lancez-la avec la propriété `tuxguitar.practice.trace` qui désigne un fichier, par exemple depuis un terminal sur macOS :
   `open -n --env JAVA_TOOL_OPTIONS="-Dtuxguitar.practice.trace=/tmp/practice-trace.csv" /chemin/vers/TuxGuitarAdvanced.app`
2. jouez le passage dans le mode Practice ;
3. ouvrez le CSV : une ligne par pas d'analyse (temps, événement, notes attendues, hauteur détectée et certitude, niveau, force de l'attaque, amplitude de chaque note attendue et de ses voisines, lignes `ACCEPTED`). Envoyez-le avec la description de ce qui aurait dû se passer.

Le fichier n'est écrit que tant que la propriété est définie.

## Vérifier la qualité vous-même

Un banc d'essai rejoue des enregistrements annotés dans la vraie chaîne de pratique et indique, par type de test, combien réussissent, échouent ou ne sont pas exécutés (et pourquoi). Il lit des enregistrements que vous gardez hors du dépôt (les licences de tels jeux de données interdisent de les redistribuer) :

```sh
cd desktop/build-scripts/tuxguitar-macosx-swt-cocoa
mvn test -pl ../../TuxGuitar-practice -am -Dgroups=dataset -DexcludedGroups= \
  -Dtest=DatasetBenchmarkTest -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtuxguitar.datasets=/chemin/vers/vos/jeux -Dtuxguitar.datasets.sample=70
```

Le rapport est écrit dans `desktop/TuxGuitar-practice/target/dataset-benchmark/report.txt` (synthèse) et `outcomes.csv` (chaque test). Les tests qui ne peuvent pas être exécutés sont comptés avec leur raison (par exemple : une octave ne se distingue pas d'un partiel de la note jouée ; la note est hors de la tessiture d'une guitare à six cordes).

Deux tests de rejeu tournent dans le build normal quand leurs enregistrements sont présents dans `tests/1`, et sont ignorés sinon.

## Qualité mesurée et limites

Sur un banc d'essai de 1 208 enregistrements annotés de vraies guitares (claires et avec 11 effets), 6 178 tests :

* **notes seules reconnues** : 99,8 % (effets) à 100 % (claires) ;
* **note à un demi-ton ou un ton de celle jouée acceptée** : aucune sur plus de 2 700 tests ;
* **intervalles et accords de trois notes avec effets reconnus** : 87 % ; **accords de guitares claires** : 95 % ;
* **accords auxquels il manque une note refusés** : 96 à 98 % ;
* **notes jouées l'une après l'autre, la précédente résonnant encore** : 97 à 100 % reconnues ;
* **délai** entre le jeu d'une note et sa reconnaissance : environ 0,1 s (médiane) sur un vrai enregistrement.

Limites, mesurées et non cachées :

* les accords dont la note la plus grave est Sol2 sont parfois acceptés un ton plus haut (les deux accords ne sont séparés que d'un pas de fréquence à cette hauteur) ;
* les accords de septième à quatre notes et certains accords ouverts (La majeur, Mi majeur) sont moins souvent reconnus ;
* des notes séparées de moins d'environ 0,15 s (triolets rapides) ne se distinguent pas par le son : Practice vous attend, il ne les saute pas ;
* une octave, ou une note qui est un harmonique d'une note plus grave de l'accord, ne peut pas être vérifiée séparément de celle-ci ;
* les réglages ont été ajustés sur les enregistrements ci-dessus : une autre guitare ou un autre micro peut demander un autre gain ou une autre sensibilité.
