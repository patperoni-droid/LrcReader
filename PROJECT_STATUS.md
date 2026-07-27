# PROJECT STATUS — Stage Music Player

Tableau de bord synthétique de l'état réel du projet.
Dernière vérification : **27 juillet 2026**

Les détails restent dans `SMP_RULES.md`, `SMP_SPEC_AGENT.md` et `docs/Features/`.

## Légende

- ✅ Stable
- 🟡 En évolution ou en validation
- 🔴 À développer

## État général

- **Version déclarée** : `0.4.2-beta` — `versionCode 6`
- **Projet** : bêta fonctionnelle en stabilisation
- **Moteur SMP** : ✅ identité, transport et runtime normalisé
- **Téléphone** : ✅ base live stable et protégée
- **Tablette** : 🟡 cœur stable, Arrangement encore en évolution

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
| Arrangement tablette | 🟡 En évolution |
| SMP Sync | 🟡 Transfert manuel stable ; V2 prévue |
| Indicateur de source audio | 🟡 Validation visuelle attendue |
| Annotations, réglages, MIDI/DMX des variantes | 🔴 À développer |

## Documentation

- ✅ Structure, règles SMP et Features cœur récentes à jour
- ✅ Architecture et stabilité live documentées
- 🟡 Quelques statuts historiques de Features restent à nettoyer
- 🟡 La roadmap existe, mais les futurs chantiers restent dispersés

## Prochains grands chantiers identifiés

- données avancées des variantes : annotations, Timeline, réglages, MIDI et DMX ;
- poursuite et stabilisation de l'Arrangement tablette ;
- synchronisation d'une sauvegarde existante — Backup V2 ;
- simplification de SMP Sync V2 et de la reconnexion des appareils.

## Observations de l'agent

- après validation, `AGENTS.md` devrait référencer cette page comme point d'entrée de statut ;
- `FEATURE_TEMPO_ARRANGEMENT.md` contient des statuts d'étapes désormais dépassés.
- `FEATURE_BACKGROUND_SOUND.md` indique encore « architecture en cours de validation »
  alors que le comportement principal est utilisé et validé.
- `FEATURE_BACKUP_V2.md` doit être réévalué avant reprise du chantier.
- `docs/roadmap/` ne contient encore que son guide.
- la fiche de release `0.4.1` attend toujours le commit publié et les bugs connus.

## Règle de maintenance

Mettre cette page à jour uniquement après une validation majeure ou une release.
