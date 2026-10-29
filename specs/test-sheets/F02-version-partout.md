# F02 — La même version et le même commit partout

- **Spec Kit** : `specs/015-version-and-commit-everywhere`
- **Tâches fermées** : T021 (la partie jamais vue à l'écran : fenêtre À propos, titre, bouton Copier), T022 (en-tête d'un `.tg` enregistré, SC-005), T024 (les trois cas d'une version de développement, US4 scénarios 1 à 3). T025 (commit) suit seulement si un cas échoue.
- **Exigences** : US1, US4, SC-005.
- **Matériel** : aucun (pas de guitare).
- **Durée** : 20 minutes (plus deux constructions de 5 à 10 minutes pour la partie C).

## Partie A — l'application actuelle (T021)

L'application du 22 oct. a été construite avec des modifications pas encore commitées : elle doit donc dire **modified**.

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| A1 | Lance l'application **sans** ouvrir de fichier. Regarde le titre de la fenêtre principale. | `TuxGuitarAdvanced dev - <nom du fichier>` (pour un morceau neuf, le nom par défaut du fichier). Jamais `9.99-SNAPSHOT`. | | |
| A2 | Ouvre `tests/basic_test.tg`. | Le titre devient `TuxGuitarAdvanced dev - basic_test.tg`. | | |
| A3 | Menu **Aide › À propos**. | Le titre de la fenêtre dit `TuxGuitarAdvanced dev`, avec en dessous le commit court `16b4b29` et la mention **modified**. | | |
| A4 | Dans À propos, ouvre l'onglet de description. | Le commit **complet** (40 caractères, commence par `16b4b297d95c`) y figure. | | |
| A5 | La nouvelle ligne et le bouton **Copier la version** sont-ils bien placés (rien de coupé, rien qui déborde) ? | Mise en page propre. | | |
| A6 | Clique sur **Copier la version**, puis colle dans TextEdit ou Notes. | Le texte collé est `TuxGuitarAdvanced dev (16b4b297d95c21029d315dcf6cd30b68e84cf39f, modified)`. | | |
| A7 | Dans un Terminal : `desktop/build-scripts/tuxguitar-macosx-swt-cocoa/target/tuxguitar-9.99-SNAPSHOT-macosx-swt-cocoa.app/Contents/Resources/tuxguitar/tuxguitar.sh --version` (lance-le depuis la racine du dépôt) | Affiche `TuxGuitarAdvanced dev (16b4b29)`. | | |

## Partie B — l'en-tête d'un fichier `.tg` (T022, SC-005)

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| B1 | Avec `basic_test.tg` ouvert, **Fichier › Enregistrer sous…** dans `~/Desktop/test-entete.tg`. Ferme l'application. | Le fichier est créé. | | |
| B2 | Dis-moi quand c'est fait : je compare son en-tête avec celui d'un `.tg` enregistré avant la spec 015. (Ou, si tu veux le faire toi-même : `head -c 40 ~/Desktop/test-entete.tg \| xxd`.) | Même en-tête qu'avant : la version du format TuxGuitar reste `2.1.1`, sans rien de « TuxGuitarAdvanced ». | | |

## Partie C — les trois cas d'une version de développement (T024, US4)

Ces cas demandent de reconstruire l'application. Je peux faire les constructions moi-même et te laisser seulement regarder À propos. Dis-moi ce que tu préfères.

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| C1 | **Arbre propre** : sans aucune modification de fichier suivi (`git status` ne liste que `tests/` en non suivi), construis **sans les tests** (un test de benchmark réécrit un fichier suivi et rendrait l'arbre « modifié ») : `mvn -e clean verify -P native-modules -DskipTests`. Ouvre À propos. | `dev`, le commit court de `HEAD`, et **pas** de « modified ». | | |
| C2 | **Arbre modifié** : modifie un fichier suivi (par exemple ajoute une ligne vide à la fin de `README.md`), reconstruis de la même façon, ouvre À propos. Puis annule la modification. | `dev`, le commit, et **modified**. | | |
| C3 | **Sans le fichier de version** : fais une copie du `.app`, supprime dans la copie `Contents/Resources/tuxguitar/share/build-info.properties`, lance la copie, ouvre À propos. | `dev`, commit **inconnu**, et **aucune erreur**. | | |
