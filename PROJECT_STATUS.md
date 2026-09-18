# PROJECT STATUS — MusiMio (SMP)

Tableau de bord synthétique de l'état réel du projet.
Dernière vérification documentaire du code : **18 septembre 2026**. La navigation
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

- **Version déclarée dans Gradle** : `0.4.9-beta` — `versionCode 13` (ne préjuge pas de la version publiée sur Google Play)
- **Projet** : bêta fonctionnelle en stabilisation
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
| Lecteur / Playback Control | ✅ Stable |
| ChordPro Audio Lyrics | 🟡 Éditeur Lyrics et rendu Lyrics/Grid présents ; validation sur appareils non refaite dans cet audit |
| Transpo / Pitch audio / Sync Pitch | 🟡 Commandes et liaison présentes ; accords `-11..+11`, audio `-6..+6`, vitesse `0,5..2,0` ; validation live sur appareils non refaite |
| Track Console | 🟡 LEVEL et SPEED actifs ; EQ cinq bandes uniquement visuel, sans traitement sonore ni persistance |
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
- Le Pitch a été retiré de Track Console ; sa commande audio reste disponible par les contrôles du Lecteur. Sync Pitch et Transpo appartiennent à la barre live, non à Track Console.

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
