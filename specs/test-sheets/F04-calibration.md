# F04 — Calibrer la reconnaissance pour une entrée

- **Spec Kit** : `specs/013-device-recognition-calibration`
- **Tâche fermée** : T049 [HUMAN]
- **Exigences** : SC-001, SC-002, SC-003 ; FR-020 (copie de diagnostic), FR-021 (image de tablature)
- **Matériel** : guitare, **micro intégré** du Mac, et le Rocksmith pour l'étape « autre appareil ». Pièce calme.
- **Partition de calibration** : `tests/basic_test.tg` (la pièce à jouer est affichée par la fenêtre elle-même).
- **Durée** : 25 minutes.

## Étapes

| # | Fais | Attendu | OK / KO | Ce que j'ai vu |
|---|---|---|---|---|
| 1 | Ouvre `tests/basic_test.tg`, ouvre la pratique, onglet **Entrée**, choisis le **micro intégré**. Regarde le **point** de couleur à côté de l'entrée. | **Orange** (« Non calibrée : réglages par défaut »). Le bouton **Oublier la calibration** est grisé. | | |
| 2 | Appuie sur **Calibrer la reconnaissance**. | La fenêtre **Calibrer la reconnaissance** s'ouvre avec « Étape 1 sur … » et propose **Calibrer** ou **Utiliser la configuration par défaut**. | | |
| 3 | **Calibrer**. Suis les étapes (accordage, conseils : avec un micro, il demande un endroit calme). | Chaque étape est claire. | | |
| 4 | Joue la pièce, un son après l'autre, avec une courte pause entre eux. Regarde l'**image de tablature** du son suivant. | « Écoute... n son(s) entendu(s) sur N » avance à chaque son. L'image montre la note (corde, case) ou l'accord suivant. **L'image est-elle assez claire ?** | | |
| 5 | À la question **« Cet enregistrement est-il assez bon pour être analysé ? »** : coche **Garder une copie de cet enregistrement pour le diagnostic**, puis **Oui, l'analyser**. | L'analyse tourne (« Recherche des meilleurs réglages… »), puis donne les taux par défaut et avec le profil, ou le message à **trois choix**. Un fichier apparaît dans `~/tuxguitar-calibration-takes/`. | | |
| 6 | Si un résultat est proposé, garde-le. | Le point devient **vert** (« Calibrée, xx % »), ou **rouge** si le taux est sous 60 %. **Oublier la calibration** est actif. | | |
| 7 | Quitte l'application, relance-la, ouvre la pratique, choisis le micro intégré. | Le point est **vert** à nouveau. | | |
| 8 | Choisis **une autre entrée** (le Rocksmith). | Point **orange**. Retour au micro intégré : **vert**. | | |
| 9 | **Refus** : relance **Calibrer de nouveau** et joue **trop peu** de sons (par exemple 3), puis attends. Recommence en jouant très fort près du micro pour saturer. | Refus avec la raison (« n sons ont été entendus, N étaient attendus » / « Le son est trop fort : il a saturé »), puis **Que voulez-vous faire ?** avec **Enregistrer de nouveau**, **Utiliser la configuration par défaut**, **Arrêter la calibration**. Chaque choix fait ce qu'il dit. | | |
| 10 | Pendant une calibration, ferme la fenêtre avec la croix. | Question **« Arrêter la calibration ? »**. *Non* : tu restes à la même étape. *Oui* : arrêt, rien n'est enregistré, l'ancien profil est intact (point toujours vert). | | |
| 11 | **Oublier la calibration** sur le micro intégré. | Une **confirmation** est demandée. Après *Oui* : point **orange**, et seul ce profil est effacé. | | |
| 12 | Avec le profil (refais une calibration si tu l'as oublié), joue `basic_test.tg` en mode **Attendre**. | Note si la reconnaissance te paraît meilleure, pareille ou pire qu'avec la configuration par défaut. (Mesuré le 2026-10-20 : aucun effet sur tes deux prises, le réglage par défaut reconnaissait déjà 13 sur 13 et 21 sur 21.) | | |

## À me dire

- La copie de diagnostic de l'étape 5 (son nom de fichier) : je lance la validation `benchmark/validation.txt` dessus.
- Ton avis : la calibration aide-t-elle avec le micro intégré ?
