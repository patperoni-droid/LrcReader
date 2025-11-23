## Prompt pour nouveau chat

Je suis Patrick.
Je développe une appli Android Kotlin/Compose appelée **LrcReader_EXO**.

C’est un lecteur de playback avec :
- Paroles synchronisées (PlayerScreen, MANU/AUTO, prompteur)
- Playlists rapides + playlists complètes
- Mode DJ
- Fond sonore automatique entre les morceaux
- Sauvegarde globale en JSON (BackupManager)
- Réglage gain & tempo par morceau

Je vais t’envoyer les fichiers :
- `MainActivity.kt`
- `PlayerScreen.kt`
- `HomeScreen.kt`
- `MoreScreen.kt`
- et d’autres si besoin.

Tu m’aides à :
- corriger les bugs
- nettoyer le code
- ajouter des fonctionnalités
- sans casser ce qui marche déjà.
Réponds avec du code complet, prêt à coller.# Plan du projet — LrcReader_EXO


Application Android (Kotlin + Jetpack Compose) pour :
- Lire des playbacks
- Afficher des paroles synchronisées (.lrc)
- Gérer des playlists rapides & complètes
- Mode DJ / crossfade
- Fond sonore automatique
- Sauvegarde/restauration globale en JSON
- Réglages par morceau (gain, tempo)
- Outils bonus : accordeur, édition de points d’entrée/sortie, etc.

---

## 1. Navigation & activité principale

### 1.1. Activité principale

- `exo/MainActivity.kt`
- Initialise :
- `MediaPlayer`
- `LoudnessEnhancer`
- `DjEngine.init(context)`
- Restauration auto : `AutoRestore.restoreIfNeeded(context)`
- Gère l’onglet sélectionné (`BottomTab`) via :
- `selectedTab: BottomTab`
- `SessionPrefs.getTab(...)` / `SessionPrefs.saveTab(...)`
- Gère l’état global :
- `currentPlayingUri`
- `isPlaying`
- `parsedLines: List<LrcLine>`
- `currentTrackGainDb`
- `currentTrackTempo`
- `currentLyricsColor`
- `selectedQuickPlaylist`
- `openedPlaylist`
- Applique le volume & tempo :
- `applyGainToPlayer(db)`
- `applyTempoToPlayer(speed)`
- Fonction centrale de lecture :
- `playWithCrossfade(uriString, playlistName?)`
- Appelle `crossfadePlay(...)`
- Charge les paroles via `LrcStorage.loadForTrack(...)` puis `parseLrc(...)`
- Gère :
- démarrage / arrêt du fond sonore (`FillerSoundManager`)
- erreur de lecture
- fin naturelle

### 1.2. Tabs / Bottom navigation

- `ui/BottomTabsBar.kt` (nom à adapter selon ton projet)
- Barre du bas (Home / Lecteur / Quick / Library / All / More / DJ / Tuner)

- `ui/BottomTab.kt` (ou similaire)
- `sealed class BottomTab { Home, Player, QuickPlaylists, Library, AllPlaylists, More, Dj, Tuner }`

- Conversion tab <-> String (dans `MainActivity.kt`) :
- `tabKeyOf(tab: BottomTab): String`
- `tabFromKey(key: String): BottomTab`

---

## 2. Écrans UI

### 2.1. Accueil

- `ui/HomeScreen.kt`
Rôle :
- Écran d’accueil “pro”, inspiré Suno.
- Fond dégradé bleu/violet.
- Gros boutons :
- **Mode Lecteur** → ouvre l’onglet Player
- **Mode Concert** → active `DisplayPrefs.setConcertMode(true)` + Player
- **Mode DJ / Mix** → onglet Dj
- **Accordeur** → onglet Tuner
- Petits chips en bas :
- Profil (placeholder)
- Tutoriel → onglet More
- Notes → actuellement redirigé vers `BottomTab.More` (première version)
- Réglages → onglet More

Composants :
- `NeonCardButton(...)` : grosses cartes néon.
- `SmallBottomChip(...)` : petites icônes/labels en bas.

---

### 2.2. Lecteur (Player)

- `ui/PlayerScreen.kt`

Rôle :
- Écran principal de lecture (paroles + musique).
- Gère :
- Paroles en mode **MANU** (scroll manuel + centrage ligne)
- Mode **AUTO** = prompteur à défilement continu
- Mode **Concert** (DisplayPrefs)
- Contrôles lecture : Play/Pause, Prev, Next
- TimeBar (seek)
- Résumé “DJ” du morceau :
- Gain (dB) + tempo (speed)

Principaux éléments :

- Suivi de lecture :
- `durationMs`, `positionMs`, `isDragging`, `dragPosMs`
- `LaunchedEffect(isPlaying, parsedLines)` pour :
- mettre à jour durée & position
- calculer `currentLrcIndex` (ligne active)
- Affichage des paroles “MANU” :
- `LyricsArea(...)`
- Utilise `scrollState`, `lyricsBoxHeightPx`, `baseTopSpacerPx`, `lineHeightPx`
- `currentLrcIndex` pour surligner la ligne en cours
- clic sur une ligne → `seekAndCenter(targetMs, index)`
- Affichage des paroles “AUTO / prompteur” :
- `PrompterArea(...)`
- Texte unique construit via `buildPrompterText(parsedLines, rawLyricsText)`
- Scroll continu via `prompterScrollState` + `LaunchedEffect(isPrompterRunning, ...)`
- Vitesse réglable (`PrompterPrefs.getSpeed/saveSpeed`)
- Header :
- `ReaderHeader(...)`
- Toggle mode concert (icône “Tune”)
- Bouton MANU/AUTO
- Bouton Mix (ouvre `TrackMixScreen`)
- Bouton Éditer (ouvre `LyricsEditorSection`)
- Mix :
- `TrackMixScreen(...)`
- Gain dB → utilise `TrackVolumePrefs`, `applyGainToPlayer(...)`
- Tempo (speed) → `TrackTempoPrefs`, `applyTempoToPlayer(...)`
- Éditeur de paroles :
- `LyricsEditorSection(...)`
- Onglets texte / lignes / timing (à adapter selon implémentation)
- Utilise `LrcStorage.saveForTrack(...)`
- `onSaveSortedLines(sortedLines)` → met à jour `parsedLines`

---

### 2.3. Écran “Plus” (Paramètres)

- `ui/MoreScreen.kt`

Rôle :
- Menu “Paramètres” avec sous-écrans :
- Fond sonore (nappe)
- Sauvegarde/Restauration
- Édition de titre
- (Placeholders : Interface / Audio / Avancé)

Structure :
- `MoreScreen(...)` :
- Gère une `MoreSection` :
- `Root`, `Backup`, `Filler`, `Edit`
- `MoreRootScreen(...)` :
- Boutons :
- 🎧 Fond sonore → `FillerSoundScreen`
- 💾 Sauvegarde / Restauration → `BackupScreen`
- 🛠 Édition de titre → `EditSoundScreen`

---

### 2.4. Sauvegarde / Restauration

- Dans `ui/MoreScreen.kt` : `BackupScreen(...)`

Rôle :
- Exporter l’état complet en JSON.
- Importer un JSON pour restaurer.
- Naviguer dans un dossier de sauvegarde (SAF / DocumentTree).

Fonctionnement :
- `BackupManager.exportState(context, null, emptyList())` → JSON complet.
- `BackupManager.importState(context, json, onDone)` → restaure l’état.

Export :
- Bouton **Enregistrer dans…**
- Génère un JSON
- Utilise `ActivityResultContracts.CreateDocument("application/json")`
- Nom par défaut proposé : `lrc_backup_YYYY-MM-DD_HH-mm-ss.json`
- L’utilisateur peut modifier le nom.
- Ecriture dans le `Uri` via `saveJsonToUri(...)`
- Bouton **Partager**
- `shareJson(context, fileName, json)` → envoie le JSON via les apps de partage.

Dossier de sauvegarde :
- Sélection via `ActivityResultContracts.OpenDocumentTree()`
- Permissions persistantes `takePersistableUriPermission(...)`
- Sauvegarde du dossier dans `BackupFolderPrefs`.

Listing des sauvegardes :
- `DocumentFile.fromTreeUri(...)` + filtre `.json`
- Affichage des fichiers + bouton **Importer**
- Lit le JSON
- Appelle `BackupManager.importState(...)`

---

### 2.5. Fond sonore (nappe)

- `ui/MoreScreen.kt` : `FillerSoundScreen(...)`
- `core/FillerSoundManager.kt`
- `core/FillerSoundPrefs.kt`

Rôle :
- Lancer une “nappe” audio en fond après la fin d’un titre.
- Permettre de tester/preview.
- ON/OFF et volume dédié.

Fonctionnalités :
- `Switch` pour activer/désactiver le fond sonore (`FillerSoundPrefs.isEnabled/setEnabled`)
- Choix d’un dossier audio via `OpenDocumentTree`
- Dossier stocké dans `FillerSoundPrefs.getFillerFolder/saveFillerFolder`
- Volume :
- Slider UI (0..1), mappé en volume réel via courbe `u³` (pour meilleure finesse en bas)
- Sauvegarde dans `FillerSoundPrefs.saveFillerVolume`
- Application via `FillerSoundManager.setVolume(real)`
- Preview :
- Bouton **▶︎ Écouter / Arrêter l’écoute**
- `FillerSoundManager.startIfConfigured(context)`
- `FillerSoundManager.fadeOutAndStop(200)`

---

### 2.6. Édition de titre (points d’entrée/sortie)

- `ui/MoreScreen.kt` : `EditSoundScreen(...)`
- `core/EditSoundPrefs.kt`

Rôle :
- Choisir un fichier audio et régler :
- Point d’entrée (startMs)
- Point de sortie (endMs)
- Sauvegarder ces valeurs par URI.

Fonctionnalités :
- Choix du fichier audio (`GetContent("audio/*")`)
- Lecture simple via `MediaPlayer` local :
- Lecture du segment complet
- Lecture des 10 dernières secondes
- Bouton Stop
- Sliders pour :
- Start
- End
- Sauvegarde via `EditSoundPrefs.save(context, uri, startMs, endMs)`

---

### 2.7. DJ

- `ui/DjScreen.kt`
- `core/dj/DjEngine.kt`

Rôle :
- Interface pour le mode DJ :
- Crossfade entre deux decks
- Gestion du tempo, etc. (selon implémentation actuelle)

Initialisation :
- `DjEngine.init(context)` dans `MainActivity.onCreate`

---

### 2.8. Accordeur (Tuner)

- `ui/TunerScreen.kt`

Rôle :
- Analyse du micro pour afficher la note jouée (guitare & co).

Navigation :
- Depuis `HomeScreen` → on sélectionne `BottomTab.Tuner`
- Dans `TunerScreen`, callback `onClose()` renvoie à `BottomTab.Home`.

---

### 2.9. Playlists / Library

Fichiers (noms à adapter selon ton projet) :

- `ui/QuickPlaylistsScreen.kt`
- Affiche les playlists “rapides”
- `onPlaySong(uri, playlistName, color)` :
- Appelle `playWithCrossfade(...)`
- Sauvegarde la playlist rapide sélectionnée (`SessionPrefs.saveQuickPlaylist`)
- Sauvegarde la couleur des paroles
- `ui/LibraryScreen.kt`
- Affichage général de la “bibliothèque” (tous les titres), selon ton implémentation.
- `ui/AllPlaylistsScreen.kt`
- Liste toutes les playlists
- `onPlaylistClick(name)` → ouvre `PlaylistDetailScreen`
- `ui/PlaylistDetailScreen.kt`
- Affiche le contenu d’une playlist donnée
- `onPlaySong(uri)` → `playWithCrossfade(uri, openedPlaylist)`

Références :
- `core/PlaylistRepository.kt` (ou similaire)
- `PlaylistRepository.version` (StateFlow) utilisé dans `MainActivity` (`val repoVersion by PlaylistRepository.version`).

---

## 3. Core & logique métier

### 3.1. Sauvegardes & préférences

- `core/BackupManager.kt`
- `exportState(context, ...)` → JSON complet
- `importState(context, json, onDone)` → applique les données

- `core/BackupFolderPrefs.kt`
- Stocke `Uri` du dossier de sauvegarde

- `core/SessionPrefs.kt`
- Onglet actuel (`TAB_HOME`, `TAB_PLAYER`, etc.)
- Playlist rapide sélectionnée
- Playlist “AllPlaylists” actuellement ouverte

- `core/TrackVolumePrefs.kt`
- Volume d’un titre (en dB) par URI
- `core/TrackTempoPrefs.kt`
- Tempo d’un titre (speed) par URI

- `core/DisplayPrefs.kt`
- Mode concert ON/OFF

- `core/PrompterPrefs.kt`
- Vitesse du prompteur

- `core/AutoRestore.kt`
- Restauration automatique d’un éventuel état précédent

---

### 3.2. Paroles & LRC

- `core/LrcLine.kt`
- Modèle d’une ligne de paroles :
- `timeMs`
- `text`

- `core/LrcStorage.kt`
- Sauvegarde/surcharge des .lrc spécifiques à un morceau :
- `saveForTrack(context, trackUri, lines)`
- `loadForTrack(context, trackUri): String?`

- `core/parseLrc.kt`
- Parse les fichiers LRC en `List<LrcLine>`

- `ui/PlayerScreen.kt`
- Utilise `parseLrc(...)` + `LrcStorage` pour afficher les paroles.

---

### 3.3. Audio & Crossfade

- `core/crossfadePlay.kt`
- Gère le crossfade entre le morceau courant et le nouveau
- Charge les paroles via callback `onLyricsLoaded(...)`
- Gère erreurs, fin naturelle, etc.

- `core/pauseWithFade.kt`
- Utilisé dans `PlayerScreen` pour faire une pause avec fondu sonore.

- `core/FillerSoundManager.kt`
- Gestion de la nappe (fond sonore)
- `startIfConfigured(context)`
- `fadeOutAndStop(durationMs)`
- `setVolume(value)`

---

## 4. Ce qu’il faut envoyer à ChatGPT dans un nouveau chat

### 4.1. Prompt “base projet”

À envoyer en premier :

> Je suis Patrick.
> Je développe une appli Android Kotlin/Compose appelée **LrcReader_EXO**.
> C’est un lecteur de playback pour la scène, avec :
> - Paroles synchronisées (PlayerScreen, mode MANU / AUTO prompteur, mode Concert)
> - Playlists rapides + playlists complètes
> - Mode DJ (DjScreen + DjEngine)
> - Fond sonore automatique entre les morceaux
> - Sauvegarde/restauration globale en JSON (BackupManager)
> - Réglage par morceau : gain (dB) + tempo
>
> Je vais t’envoyer les fichiers clés (MainActivity, PlayerScreen, HomeScreen, MoreScreen, etc.).
> Aide-moi à corriger les bugs, nettoyer le code et ajouter des fonctionnalités,
> **sans casser ce qui marche déjà**.
> Réponds avec du code complet, prêt à coller.

### 4.2. Fichiers à envoyer selon ce que tu veux faire

- **Navigation / nouveau écran / bug global**
- `exo/MainActivity.kt`
- `ui/BottomTabsBar.kt` + `BottomTab`
- L’écran concerné

- **Paroles / synchro / prompteur**
- `ui/PlayerScreen.kt`
- `core/LrcLine.kt`
- `core/parseLrc.kt`
- `core/LrcStorage.kt`

- **Sauvegarde / JSON / backup**
- `ui/MoreScreen.kt` (partie `BackupScreen`)
- `core/BackupManager.kt`
- `core/BackupFolderPrefs.kt`
- `com.patrick.lrcreader.saveJsonToUri` / `shareJson` si besoin

- **Fond sonore / nappe**
- `ui/MoreScreen.kt` (partie `FillerSoundScreen`)
- `core/FillerSoundManager.kt`
- `core/FillerSoundPrefs.kt`

- **Édition points d’entrée/sortie**
- `ui/MoreScreen.kt` (partie `EditSoundScreen`)
- `core/EditSoundPrefs.kt`

- **Mode DJ**
- `ui/DjScreen.kt`
- `core/dj/DjEngine.kt`

- **Accordeur**
- `ui/TunerScreen.kt`

- **Playlists**
- `ui/QuickPlaylistsScreen.kt`
- `ui/LibraryScreen.kt`
- `ui/AllPlaylistsScreen.kt`
- `ui/PlaylistDetailScreen.kt`
- `core/PlaylistRepository.kt` (ou équivalent)

---

## 5. Convention interne (pour toi)

Pour t’y retrouver vite dans le code, tu peux ajouter en haut de chaque fichier :

```kotlin
// [MAP] UI / Player : écran lecteur principal (paroles, MANU/AUTO, mix)