# PROJECT STATUS — MusiMio (SMP)

Tableau de bord synthétique de l'état réel du projet.
Dernière actualisation ciblée Lecteur, LEVELS et publication Git : **7 octobre 2026**. Sound Pads et variantes restent issus du jalon du 5 octobre ; les autres domaines de la vérification du 18 septembre. La navigation
par portions du Prompteur et la navigation simple de playlist à la pédale ont été
validées sur appareil réel.

Les règles générales restent dans [SMP_RULES.md](SMP_RULES.md) et
[SMP_SPEC_AGENT.md](SMP_SPEC_AGENT.md). Les comportements sont décrits dans
[docs/Features/](docs/Features/) et les travaux futurs priorisés dans
[docs/BACKLOG.md](docs/BACKLOG.md).

## Légende

- ✅ Stable
- 🟡 En évolution ou en validation
- 🔴 À développer

## État général

- **Version déclarée dans Gradle** : `0.5.0-beta` — `versionCode 15`, soit `0.5.0-beta-labo` pour Labo (ne préjuge pas de la version publiée sur Google Play)
- **Projet** : bêta fonctionnelle ; référence Labo actuelle validée, `stable` et `origin/stable` sur `0b6eb4b9191c275eaad2ac4e56fd85ce061bb3ce`, aucune publication Play revendiquée
- **Moteur SMP** : ✅ identité, transport et runtime normalisé
- **Téléphone** : ✅ base live stable ; ergonomie Arrangement harmonisée
- **Tablette** : ✅ cockpit et Arrangement de référence stables

## Fonctionnalités principales

| Fonction | État |
|---|---|
| SongUnit / Bibliothèque | ✅ Stable |
| Playlists et groupes | ✅ Stable ; navigation simple précédente/suivante à la pédale validée, appui long non garanti |
| Textes défilants autonomes | ✅ Catalogue, édition ChordPro et navigation tactile/pédale par portions validés |
| Variantes Arrangement | ✅ Stable |
| Paroles / accords des variantes | ✅ Stable |
| Transport SMP des familles SongUnit | ✅ Aller-retour complet certifié pour les données actuellement prises en charge |
| Import audio et SMP | ✅ Stable |
| Export / partage SMP | ✅ Stable |
| Sauvegarde / restauration | ✅ Sauvegarde et restauration stables ; mise à jour minimale disponible |
| Premier lancement | ✅ Stable |
| Sound Pads | ✅ V1 validée par le Créateur en Labo release ; disponible en Labo debug/release et Concert debug, absente en Concert release ; banque hors sauvegardes générales |
| Lecteur / Playback Control | ✅ Stable |
| ChordPro Audio Lyrics | 🟡 Éditeur Lyrics et rendu Lyrics/Grid présents ; validation sur appareils non refaite dans cet audit |
| Transpo / SPEED / Sync Pitch | ✅ Évolutions validées par le Créateur : barre à modes indépendants, SPEED `×0,80..×1,20` par pas `0,01`, icône Link et aide initiale globale ; accords `-11..+11`, pitch audio `-6..+6` |
| Bibliothèque LEVELS | ✅ Case dB cliquable, commandes `−1 / +1 dB` de `-24` à `+6 dB`, sans fader tiroir ; persistance existante ; aide FR/EN/ES sans référence LUFS |
| Track Console | Non exposé : accès masqué sur téléphone/tablette ; écran, Volume et infrastructure EQ conservés ; EQ fonctionnel et réactivation V2/V3 futurs |
| Fond sonore / DJ | ✅ Stable |
| Timeline | 🟡 Base stable ; variantes à développer |
| Arrangement tablette | ✅ Référence fonctionnelle |
| Arrangement téléphone | 🟡 Ergonomie harmonisée ; lecture directe à valider sur plusieurs appareils |
| Waveform téléphone / tablette | ✅ Aperçu échantillonné et précision temporelle conservée |
| SMP Sync | 🟡 Transfert manuel stable ; V2 prévue |
| Indicateur de source audio | 🟡 Validation visuelle attendue |
| YouTube | 🔴 Conception uniquement ; aucune bibliothèque ni lecture YouTube implémentée |
| Édition et exécution avancées des annotations / MIDI / DMX de variantes | 🔴 À développer ; leur transport SMP est déjà préservé |

## Documentation

- ✅ Structure, règles SMP et Features cœur à jour
- ✅ Architecture et stabilité live documentées
- ✅ Textes défilants, persistance des variantes et mise à jour minimale de bibliothèque documentés
- ✅ Backlog technique priorisé disponible dans `docs/BACKLOG.md`
- ✅ `PROJECT_STATUS.md` est le tableau de bord ; `docs/BACKLOG.md` est la référence des travaux futurs
- ✅ La spécification [ChordPro Audio Lyrics](docs/CHORDPRO_AUDIO_LYRICS_SPEC.md) distingue l’affichage des accords du pitch audio ; la [spécification YouTube](docs/YOUTUBE_LIBRARY_SPEC.md) décrit un projet non implémenté.
- Références actualisées : [Bibliothèque](docs/Features/FEATURE_LIBRARY.md),
  [Playlists](docs/Features/FEATURE_PLAYLISTS.md),
  [Player](docs/Features/FEATURE_PLAYER.md),
  [Export et sauvegarde](docs/Features/FEATURE_EXPORT_BACKUP.md) et
  [Persistance SMP](docs/SMP_PERSISTENCE_SPEC.md)

## Prochains grands chantiers identifiés

- validation finale de la lecture directe Arrangement sur plusieurs téléphones ;
- validation visuelle des textes défilants sur téléphone et tablette réels ;
- extension de la mise à jour minimale de bibliothèque à l'état global et aux suppressions ;
- retrait de l'ancien éditeur Arrangement lorsque plus aucun parcours ne l'utilisera ;
- audit final des écarts résiduels entre téléphone et tablette.

Les priorités moyennes et faibles sont détaillées uniquement dans `docs/BACKLOG.md`.

## Observations de l'agent

- Sur téléphone, le Player live reste en vue Lyrics sans boutons Lyrics/Grid ; la tablette expose les deux vues. L'éditeur synchronisé utilise les onglets Lyrics et Sync, sans onglet Accords séparé.
- Track Console est masqué via `showMixAction = false`, sans suppression de son code. SPEED a rejoint la barre live TRANSPO / SPEED ; Sync Pitch conserve sa logique avec une aide unique (`sync_pitch_help_seen`).
- La référence LUFS est abandonnée dans le parcours utilisateur. Le calcul hérité de repli encore présent dans LEVELS reste une dette signalée dans [FEATURE_LEVELS.md](docs/Features/FEATURE_LEVELS.md), pas une fonction de normalisation.
- PDF reste une idée possible pour V2, non implémentée.

- la lecture directe est le mode recommandé sur téléphone ; le pipeline WAV/Sampler reste disponible comme mode de compatibilité et n'est pas supprimé ;
- l'ancien éditeur Arrangement reste une dette contrôlée tant qu'un parcours de repli l'utilise ;
- les textes défilants sont des contenus autonomes, distincts des paroles `.lrc` synchronisées avec un morceau ;
- leur Prompteur avance ou recule de 65 % du viewport avec 35 % de chevauchement et
  une animation de 300 ms ; la correction manuelle reprend l'auto-scroll sans arrêter Play ;
- l'éditeur du Prompteur peut remplacer toutes les occurrences exactes d'un accord
  depuis sa palette, sur la base des ancres produites par `parseChordPro` ;
- la pédale est traitée comme un clavier Android, sans connexion Bluetooth propriétaire ;
  l'appui long dans la playlist n'est pas une fonction utilisateur garantie ;
- la commande **Mettre à jour la bibliothèque** actuelle republie les Familles SongUnit dans la sauvegarde de référence, mais ne représente pas encore tout le cycle V2 cible ;
- les projets V2 détaillés conservent leur propre documentation, mais leur priorité est décidée exclusivement dans le backlog.

## Règle de maintenance

Mettre cette page à jour uniquement après une validation majeure ou une release.

## Sound Pads — jalon local

[État V1, variantes, historique et validation](docs/soundpads/SOUNDPADS_PATCH3_INTERFACE.md). Quatre builds réussis et 346 tests ciblés par variante depuis la nouvelle stable ; échec global préexistant des couleurs Arrangement conservé. Ancienne stable sauvegardée, pas de merge artificiel lors du jalon local du 5 octobre. Le 7 octobre, `stable` a été publiée sur `origin/stable` par `--force-with-lease`, de `636d18ece90ea0516961746f43490aff2fad2806` vers `0b6eb4b9191c275eaad2ac4e56fd85ce061bb3ce`, sans pull/merge/rebase. Contrôles pré-push Labo/Concert réussis ; GitHub Actions non vérifiées. Cette publication Git ne constitue pas une publication Google Play.
