# F17 — GuitarFX : l'ampli et les pédales sans TuxGuitarAdvanced, en application et en plugin

- **Spec Kit** : `specs/027-standalone-amp`
- **Tâche fermée** : T066 (porte de la spec 027).
- **Exigences** : SC-001 (installer, trouver les plugins, jouer sans Java, désinstaller) ; SC-004 (chaque changement entendu tout de suite, sans clic) ; SC-005 (larsen coupé en moins de 0,5 s) ; SC-007 (son coupé au démarrage, réglages gardés) ; SC-011 (un son échangé avec TuxGuitarAdvanced dans les deux sens) ; SC-012 (le plugin dans SoundCraft et dans GarageBand ou Logic) ; FR-012 (l'avertissement du micro intégré) ; FR-016 et SC-008 (la fenêtre *Avancé*) ; FR-026 (la désinstallation garde tes réglages).
- **Matériel** : guitare, **câble Rocksmith**, **casque filaire** ; pour l'étape du larsen, le micro intégré et les haut-parleurs du Mac (son bas).
- **Logiciels** : TuxGuitarAdvanced construit (pour l'échange de sons), **SoundCraft**, **GarageBand** ou **Logic**.
- **Durée** : environ 45 minutes.

⚠️ **Étape 9 : un larsen volontaire.** Mets le volume du Mac bas avant, enlève le casque. GuitarFX doit le couper tout seul ; s'il ne le fait pas, coupe le son toi-même (**Son activé**, ou le volume du Mac).

## Préparation

1. L'installeur : je le prépare avec la porte finale (`GuitarFX-0.1.0.pkg`, construit depuis `guitarfx/target/` par `guitarfx/packaging/macos/build-pkg.sh`), ou prends celui de la release quand elle existe. Note lequel :

   Installeur utilisé : ______________________

2. Ferme TuxGuitarAdvanced et toute STAN.

## Étapes

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| 1 | Ouvre le `.pkg`. Si macOS le refuse : *Réglages Système › Confidentialité et sécurité › Ouvrir quand même*. Installe. | `GuitarFX` est dans *Applications* ; dans le Finder, `/Library/Audio/Plug-Ins/` contient `VST3/GuitarFX.vst3`, `CLAP/GuitarFX.clap` et `Components/GuitarFX.component`. Rien à copier à la main. | | |
| 2 | Lance GuitarFX. Branche le câble, mets le casque. Choisis le câble en **Entrée** (canal 1) et le casque en **Sortie**, puis **Son activé**. Autorise le micro si macOS le demande. Joue. | Le son est coupé au lancement ; il monte en douceur quand tu l'actives. Tu entends ta guitare par l'ampli *Clean*. Le délai estimé s'affiche ; l'indicateur **Entrée** bouge. | | |
| 3 | Choisis tour à tour les quatre sons prêts à l'emploi (*Clean*, *Crunch*, *Lead*, *Clean echo*) en jouant. | Chaque son s'entend tout de suite, sans clic ni trou. | | |
| 4 | Onglet *Avant l'ampli* : **+ Ajouter une pédale** (overdrive), monte-la, descends-la, éteins-la, retire-la. Onglet *Ampli* : change le **Caractère**, coupe et remets le **Baffle**. Tourne chaque bouton d'un bout à l'autre en jouant. Laisse le pointeur sur un bouton. | Chaque changement s'entend tout de suite, sans clic, sans saut de volume (SC-004). L'infobulle dit ce que fait le bouton, sa plage et sa valeur par défaut. Au maximum partout, le son reste supportable au casque (SC-007). | | |
| 5 | **Enregistrer…** la chaîne sous le nom *Essai F17*. Change un bouton : *(modifié)* apparaît. Puis **⋯ › Renommer…**, **Supprimer** un son à toi, **Rétablir** un son prêt à l'emploi que tu as modifié. | Ton son est dans la liste après les sons prêts à l'emploi ; chaque action fait ce qu'elle dit ; *Rétablir* rend le son livré. | | |
| 6 | Quitte GuitarFX et relance-le. | Mêmes périphériques, même son, même chaîne ; le son est **coupé** (SC-007). | | |
| 7 | **⋯ › Exporter…** *Essai F17* dans un fichier `.guitarfx`. Dans TuxGuitarAdvanced, fenêtre *Ampli et effets* : **⋯ › Importer…** ce fichier et joue. Puis fais l'inverse : exporte un son depuis TuxGuitarAdvanced et importe-le dans GuitarFX. | Le son importé est listé des deux côtés et sonne pareil qu'à l'origine (SC-011). Si un son du même nom existe, on te demande *Le remplacer* ou *Garder les deux*. | | |
| 8 | **Avancé…** : parcours quelques catégories, change une valeur (par exemple *Sortie et sécurité › Montée* à 2 s), ferme, coupe le son et réactive-le. Puis **Valeurs par défaut** (avec confirmation). | La fenêtre liste 103 valeurs en 19 catégories, chacune avec sa plage et sa valeur par défaut. Le changement s'entend à la réactivation (ici, une montée plus lente) ; *Valeurs par défaut* remet tout. | | |
| 9 | ⚠️ **Larsen.** Volume du Mac bas, casque enlevé. Choisis le **micro intégré** en entrée et les **haut-parleurs du Mac** en sortie. Active le son. | Un avertissement conseille le casque avant d'activer (FR-012). Si un larsen démarre, GuitarFX coupe le son en moins d'une demi-seconde environ et le dit (SC-005). | | |
| 10 | Écran de 768 pixels de haut : *Réglages Système › Moniteurs*, choisis la résolution la plus « grand texte » proposée (si aucune ne fait 768 pixels de haut ou moins, note la hauteur). Regarde la fenêtre principale, puis *Avancé…*. | Rien n'est coupé : tous les boutons et la liste des sons sont visibles, la fenêtre *Avancé* se fait défiler. Remets ta résolution après. | | |
| 11 | **SoundCraft** : sur une piste avec le câble en entrée, ajoute **GuitarFX** en **CLAP**, joue, choisis un son ; puis en **VST3** ; puis en **Audio Unit**. Enregistre le projet, ferme SoundCraft, rouvre le projet. | Les trois formats se chargent et sonnent comme l'application ; leur fenêtre a les trois onglets et la liste des sons. Le projet rouvert redonne le même son (SC-012). | | |
| 12 | **GarageBand** ou **Logic** : sur une piste audio avec le câble, ajoute *GuitarFX* (*Audio Units › TuxGuitarAdvanced › GuitarFX*). Joue, choisis un son, enregistre, rouvre. | Il se charge, sonne comme l'application, et le projet rouvert redonne le même son. | | |
| 13 | Désinstalle : dans le Terminal, `/Applications/GuitarFX.app/Contents/Resources/guitarfx-uninstall` (il demande ton mot de passe). | `GuitarFX.app` et les trois plugins ont disparu ; `~/Library/Application Support/GuitarFX` (tes réglages et tes sons) est **toujours là** (FR-026). Une réinstallation retrouve *Essai F17*. | | |
| 14 | **Ton avis** : le son est-il le même que dans TuxGuitarAdvanced ? Le délai te gêne-t-il au câble Rocksmith ? | Ta réponse est écrite dans `final-report.md`. | | |

## Sur demande seulement (sonores ou longues)

Dis-moi si tu veux les lancer ; je préviens avant.

| # | Ce que c'est | Commande |
|---|---|---|
| S1 | **Sonde de délai** (SC-002), **audible** : 20 clics forts par réglage dans la sortie. L'**entrée doit entendre la sortie** : un micro devant les haut-parleurs (le micro USB de la spec 022, ou le micro intégré), ou la sortie casque branchée dans le câble par un adaptateur, volume bas. Le câble avec le casque sur les oreilles ne marche pas : rien ne revient. | `cd guitarfx && cargo run --release -p guitarfx-probe -- --audible --input "<entrée>" --output "<sortie>" --report ../specs/027-standalone-amp/benchmark/latency.md` |
| S2 | **Endurance** (SC-006) : une heure, rien d'audible, le Mac éveillé. | `cd guitarfx && GUITARFX_ENDURANCE_REPORT=$PWD/../specs/027-standalone-amp/benchmark/endurance.md caffeinate -i cargo test --release -p guitarfx-app --test endurance -- --ignored --nocapture` |

## Pour les testeurs Linux et Windows

GuitarFX a les mêmes fonctions sur les trois systèmes ; son installation, son démarrage sans Java et sa désinstallation sont vérifiés automatiquement (`guitarfx.yml`), mais personne n'y a encore joué. Un retour dans une [issue](https://github.com/krl91/tuxguitaradvanced/issues), avec le système, sa version, l'entrée audio et la STAN, est précieux.

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| X1 | Installe (`setup.exe` sous Windows ; `.deb` ou `tar.gz` et `install.sh` sous Linux), puis étapes 2 à 6 ci-dessus. | Les mêmes résultats ; dis ton entrée audio et le délai affiché. | | |
| X2 | Le plugin en VST3 ou CLAP dans ta STAN (Reaper, Bitwig…) : étape 11. | Il se charge, sonne comme l'application, le projet rouvert redonne le son. | | |
| X3 | Désinstalle (*Paramètres › Applications* sous Windows ; `apt remove guitarfx` ou `install.sh --uninstall` sous Linux). | Le programme et les plugins ont disparu, tes réglages restent. | | |
