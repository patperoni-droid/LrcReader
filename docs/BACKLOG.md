# BACKLOG — Stage Music Player

Référence unique des travaux volontairement laissés en attente.

Dernier audit : **28 juillet 2026**

Ce document fixe la priorité des chantiers. Les documents `Features`, `roadmap`,
`decisions` et SMP en décrivent le contexte ou les contraintes, mais ne changent
pas leur priorité.

## Règles de maintenance

- ajouter uniquement un besoin confirmé, une dette identifiée ou une validation encore requise ;
- ne pas transformer une simple idée en chantier engagé ;
- préciser le résultat attendu, le risque principal et la documentation source ;
- retirer un élément dès qu'il est validé et reporter son état dans `PROJECT_STATUS.md` ;
- toute mise en œuvre reste soumise au workflow diagnostic → patch minimal → validation → commit.

## Haute priorité

### Validation multi-téléphones de la lecture directe Arrangement

- **But** : confirmer que la lecture directe peut rester le mode recommandé sur téléphone.
- **Validation attendue** : premier démarrage, répétitions, transitions, changements de segments,
  interaction Waveform, mémoire et stabilité sur plusieurs modèles de téléphones.
- **Règle** : conserver le pipeline WAV/Sampler intact comme mode de compatibilité tant que cette
  validation n'est pas terminée.
- **Risque** : différences de décodage MP3 et de transitions selon l'appareil.
- **Référence** : `FEATURE_TEMPO_ARRANGEMENT.md`.

### Retrait de l'ancien éditeur Arrangement

- **But** : supprimer l'ancien éditeur à deux colonnes et son code uniquement lorsque plus aucun
  parcours réel ne l'utilise.
- **Prérequis** : inventorier les routes `Arrangement`, `ArrangementFromTempo` et tous les replis
  sans morceau SMP actif ; garantir un accès clair à l'éditeur canonique.
- **Risque** : supprimer le seul parcours encore disponible dans un cas limite.
- **Référence** : `FEATURE_TEMPO_ARRANGEMENT.md`.

### Validation visuelle de l'indicateur de source audio

- **But** : valider sur téléphone et tablette que Lecteur principal, Fond sonore et DJ désignent
  immédiatement et exclusivement la source réellement active.
- **Validation attendue** : reprise automatique du Fond sonore après Stop, prise de contrôle DJ,
  arrêt complet et absence de double indication.
- **Risque** : état visuel divergent de l'autorité audio réelle.
- **Références** : `FEATURE_PLAYER.md`, `FEATURE_BACKGROUND_SOUND.md`.

## Priorité moyenne

### Audit final Arrangement téléphone / tablette

- **But** : refaire une comparaison fonctionnelle et ergonomique après validation audio directe.
- **Périmètre** : création/édition des segments, Structure, variantes, Playback Control, Waveform,
  sauvegarde, navigation et comportements encore spécifiques à un appareil.
- **Risque** : réintroduire deux workflows métier distincts derrière des interfaces proches.
- **Référence** : `FEATURE_TEMPO_ARRANGEMENT.md`.

### Import SMP par ouverture Android `ACTION_VIEW`

- **But** : ouvrir un `.smp` depuis Files, Gmail ou une autre application.
- **Règle** : réutiliser exactement le pipeline d'import visible de la Bibliothèque, sans seconde
  logique d'identité ou de normalisation.
- **Risque** : permissions temporaires, Intent dupliqué ou import différent du parcours officiel.
- **Références** : `FEATURE_LIBRARY.md`, `FEATURE_EXPORT_BACKUP.md`.

### Backup V2 — synchroniser une sauvegarde existante

- **But** : reconstruire explicitement une sauvegarde de travail existante depuis la Bibliothèque
  interne, sans créer un nouveau dossier.
- **Prérequis** : nouveau diagnostic du format actuel ; les anciennes hypothèses de nommage et de
  sous-dossiers ne doivent pas être appliquées automatiquement.
- **Risque** : suppression SAF hors du dossier Export ciblé.
- **Référence** : `FEATURE_BACKUP_V2.md`.

### SMP Sync — robustesse et simplification restantes

- **But** : fiabiliser la reconnexion et réduire la dépendance aux informations IP/port.
- **Sous-chantiers identifiés** : traitement explicite du JSON invalide dans
  `SmpSyncHashing`, découverte/QR si retenus, transfert guidé plus simple.
- **Risque** : faux positifs d'analyse et automatisation destructive.
- **Référence** : `FEATURE_SMP_SYNC.md`.

### Deuxième écran V2.4 / V2.5

- **But** : appairage explicite, puis reprise robuste de la synchronisation après perte réseau.
- **Prérequis** : conserver LocalLink, le fallback manuel et l'indépendance du Playback local.
- **Risque** : rendre le réseau nécessaire au live ou accepter silencieusement un autre appareil.
- **Référence** : `FEATURE_SECOND_SCREEN_V2.md`.

### Compactage des lignes Bibliothèque tablette

- **But** : afficher davantage de morceaux sans réduire la fiabilité tactile.
- **Prérequis** : patch UI isolé et validation sur appareils réels ; une tentative précédente a
  provoqué une instabilité.
- **Risque** : crash ou dégradation de l'usage live.
- **Référence** : `FEATURE_LIBRARY.md`.

### Détail Waveform par plage visible

- **But** : charger davantage de précision uniquement sur la zone fortement zoomée.
- **Condition** : chantier à ouvrir seulement si l'aperçu 2 000 points validé se révèle insuffisant
  en édition réelle.
- **Risque** : double décodage lourd ou traitement pendant la lecture.
- **Références** : `FEATURE_WAVEFORM.md`, `FEATURE_PLAYER.md`.

### Réparer les tests unitaires hors périmètre Arrangement

- **Constat du 28 juillet 2026** : la suite Labo exécute 347 tests ; 340 passent et 7
  échouent dans `BackupManagerAutoBackupTest` et `PrompterKeyMappingTest`.
- **Causes observées** : `SystemClock.elapsedRealtime()` non simulé dans quatre tests JVM ;
  mappings de touches attendus mais non résolus dans trois tests Prompter.
- **But** : restaurer une suite globale verte par un chantier séparé, sans mélanger ces
  corrections à la clôture Arrangement.
- **Risque** : masquer une vraie régression future si ces échecs restent tolérés.

## Faible priorité

### Données avancées propres aux variantes

- **Annotations** : stockage runtime par `songId`, transport dans la valise du parent.
- **Timeline** : projection dans le temps cumulé de la Structure.
- **MIDI / DMX** : projection temporelle déterministe, sans dépendance UI.
- **Réglages propres** : gain, pitch, speed et futures métadonnées, sans modifier le parent.
- **Risque** : confusion entre temps source et temps cumulé de la variante.
- **Références** : `SMP_RULES.md`, `SMP_SPEC_AGENT.md`, `FEATURE_TIMELINE.md`.

### Enchaînement AUTO du Playback Control

- **Statut** : suspendu.
- **But éventuel** : armer sur tablette le prochain titre sans ajouter de silence.
- **Règle** : aucune reprise sans nouvelle validation produit et live ; aucun bouton téléphone.
- **Référence** : `FEATURE_PLAYBACK_CONTROL_V2.md`.

### Évolutions du workflow live

- contrôle au pied Bluetooth ;
- amélioration de la liste live et du prochain morceau ;
- étude d'un « Niveau Live » hors lecture fondé sur des passages représentatifs ;
- mode concert simplifié.
- **Références** : `FEATURE_LIVE_WORKFLOW.md`, `FEATURE_LUFS_PREPARATION.md`.

### Extensions de sauvegarde et de transfert

- sauvegarde complète dans une archive unique ;
- mode de restauration « me demander » fichier par fichier ;
- bloc-notes et prompteurs dans SMP Sync.
- **Références** : `FEATURE_EXPORT_BACKUP.md`, `FEATURE_SMP_SYNC.md`.

### Dette technique des grands orchestrateurs Compose

- **But** : extraire ponctuellement des responsabilités de `MainActivity` / `PlayerScreen`
  uniquement lorsqu'un chantier fonctionnel le justifie.
- **Règle** : aucun refactor global ni réécriture préventive.
- **Risque** : régression transversale dans le Player live.
- **Référence** : `docs/PlayerScreen.md`.
