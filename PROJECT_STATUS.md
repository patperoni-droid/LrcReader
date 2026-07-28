# PROJECT STATUS — Stage Music Player

Tableau de bord synthétique de l'état réel du projet.
Dernière vérification : **28 juillet 2026**

Les détails restent dans `SMP_RULES.md`, `SMP_SPEC_AGENT.md` et `docs/Features/`.
Les travaux futurs priorisés sont centralisés dans `docs/BACKLOG.md`.

## Légende

- ✅ Stable
- 🟡 En évolution ou en validation
- 🔴 À développer

## État général

- **Version déclarée** : `0.4.2-beta` — `versionCode 6`
- **Projet** : bêta fonctionnelle en stabilisation
- **Moteur SMP** : ✅ identité, transport et runtime normalisé
- **Téléphone** : ✅ base live stable ; ergonomie Arrangement harmonisée
- **Tablette** : ✅ cockpit et Arrangement de référence stables

## Fonctionnalités principales

| Fonction | État |
|---|---|
| SongUnit / Bibliothèque | ✅ Stable |
| Playlists et groupes | ✅ Stable |
| Variantes Arrangement | ✅ Stable |
| Paroles / accords des variantes | ✅ Stable |
| Import audio et SMP | ✅ Stable |
| Export / partage SMP | ✅ Stable |
| Sauvegarde / restauration | ✅ Stable |
| Premier lancement | ✅ Stable |
| Lecteur / Playback Control | ✅ Stable |
| Fond sonore / DJ | ✅ Stable |
| Timeline | 🟡 Base stable ; variantes à développer |
| Arrangement tablette | ✅ Référence fonctionnelle |
| Arrangement téléphone | 🟡 Ergonomie harmonisée ; lecture directe à valider sur plusieurs appareils |
| Waveform téléphone / tablette | ✅ Aperçu échantillonné et précision temporelle conservée |
| SMP Sync | 🟡 Transfert manuel stable ; V2 prévue |
| Indicateur de source audio | 🟡 Validation visuelle attendue |
| Annotations, réglages, MIDI/DMX des variantes | 🔴 À développer |

## Documentation

- ✅ Structure, règles SMP et Features cœur à jour
- ✅ Architecture et stabilité live documentées
- ✅ Backlog technique priorisé disponible dans `docs/BACKLOG.md`
- ✅ `PROJECT_STATUS.md` est le tableau de bord ; `docs/BACKLOG.md` est la référence des travaux futurs

## Prochains grands chantiers identifiés

- validation finale de la lecture directe Arrangement sur plusieurs téléphones ;
- retrait de l'ancien éditeur Arrangement lorsque plus aucun parcours ne l'utilisera ;
- audit final des écarts résiduels entre téléphone et tablette.

Les priorités moyennes et faibles sont détaillées uniquement dans `docs/BACKLOG.md`.

## Observations de l'agent

- la lecture directe est le mode recommandé sur téléphone ; le pipeline WAV/Sampler reste disponible comme mode de compatibilité et n'est pas supprimé ;
- l'ancien éditeur Arrangement reste une dette contrôlée tant qu'un parcours de repli l'utilise ;
- les projets V2 détaillés conservent leur propre documentation, mais leur priorité est décidée exclusivement dans le backlog.

## Règle de maintenance

Mettre cette page à jour uniquement après une validation majeure ou une release.
