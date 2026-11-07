# F16 — Les réglages experts se règlent sans reconstruire, et la fenêtre tient sur l'écran

- **Spec Kit** : `specs/026-engine-external-parameters`
- **Tâche fermée** : T043 (porte de la spec 026).
- **Exigences** : SC-001 (une valeur changée sans reconstruire se voit dans une séance, avec chaque moteur) ; SC-007 (chaque catégorie de la fenêtre tient sur un écran de 768 pixels de haut, chaque réglage est atteignable) ; FR-004 (*Rétablir* par catégorie).
- **Matériel** : guitare, **câble Rocksmith** (l'entrée de toute la fiche).
- **Partition** : `tests/basic_test.tg`.
- **Durée** : environ 20 minutes.

Ce qu'on appelle ici **une note rejouée** : la même note jouée deux fois de suite, à la même case, chaque fois avec un nouveau coup de médiator, sans étouffer entre les deux.

## Préparation

1. Lance l'application construite (relancée après la dernière construction), branche la guitare par le câble Rocksmith.
2. Ouvre `tests/basic_test.tg`, puis *Outils › Interactive Practice* ; choisis la piste guitare et l'entrée Rocksmith.
3. Dans *Configuration › Reconnaissance audio*, catégorie **Moteur de reconnaissance**, note ce que dit **Moteur de détection de hauteur** (par exemple *Automatique (Natif)*).

   Moteur au départ : ______________________

## Étapes

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| 1 | *Configuration › Reconnaissance audio* : fais défiler la liste des catégories jusqu'aux catégories **Avancé : …** (après *Avancé*). Ouvre-les une à une, de **Avancé : Attaques** à **Avancé : Entrée faible**. | Il y a 35 catégories *Avancé : …*. Dans chacune, toutes les valeurs (sept au plus) et le bouton **Rétablir les valeurs recommandées de cette catégorie** se voient sans que rien ne dépasse de l'écran ; chaque curseur se manipule. | | |
| 2 | Survole le nom de quelques réglages avancés. | L'info-bulle dit ce que fait la valeur et dans quel sens elle rend la reconnaissance plus tolérante ou plus sensible, et finit par « Réglage expert : la valeur recommandée a été mesurée ; ne la changez que pour essayer. » | | |
| 3 | Valeur de référence. Mode **Attendre la bonne note**, **Démarrer**. Sur une note de la partition, joue **une note rejouée** plusieurs fois (à un rythme modéré). | Avec la valeur recommandée, chaque nouveau coup de médiator est entendu comme une nouvelle attaque (la note est acceptée, puis la suivante attend de nouveau un coup). | | |
| 4 | Arrête la séance. Catégorie **Avancé : Attaques**, mets **Attaque : facteur de flux** à sa valeur **la plus haute**, OK. **Sans relancer l'application**, redémarre en *Attendre* et rejoue les mêmes notes rejouées. | **Moins d'attaques** : plusieurs coups de médiator ne sont plus entendus comme une nouvelle note (il faut attaquer plus fort, ou la note n'est pas reprise). Ni reconstruction ni relance de l'application n'ont été nécessaires. | | |
| 5 | Dans **Avancé : Attaques**, clique **Rétablir les valeurs recommandées de cette catégorie**, OK, puis rejoue l'étape 3. | **Attaque : facteur de flux** est revenu à sa valeur recommandée (1,5) ; les notes rejouées sont de nouveau toutes entendues. | | |
| 6 | Catégorie **Moteur de reconnaissance** : choisis **Natif (Rust)** (ou **Java (FastYin)** si le départ était déjà natif), OK. Refais les étapes 3, 4 et 5 avec ce moteur. | Le même effet avec l'autre moteur : moins d'attaques avec la valeur la plus haute, de nouveau toutes après *Rétablir*. | | |
| 7 | Remets le **Moteur de détection de hauteur** noté en préparation, OK. | La pratique fonctionne comme avant la fiche. | | |
| 8 | **Ton avis** : les catégories avancées sont-elles lisibles ? Une info-bulle t'a-t-elle paru fausse ou obscure ? | Ta réponse est écrite dans `final-report.md`. | | |

## Pour les testeurs Linux et Windows

Les réglages experts utilisent le même code sur les trois systèmes, avec les deux moteurs ; la fiche n'a été faite que sur macOS. Un retour dans une [issue](https://github.com/krl91/tuxguitaradvanced/issues), avec le système, sa version, la hauteur de l'écran et l'entrée audio, est précieux.
