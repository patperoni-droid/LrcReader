# AGENTS.md

## Scope
- Gradle project with a single application module: `:app`.
- Base namespace and application id: `com.patrick.lrcreader.exo`.
- Toolchain from current build scripts:
  - AGP `8.5.0`
  - Kotlin `2.0.0`
  - Java/Kotlin target `17`
  - `compileSdk 35`, `minSdk 23`, `targetSdk 35`
- UI stack: Jetpack Compose only. There is no Navigation component.
- Main entrypoint: `app/src/main/java/com/patrick/lrcreader/MainActivity.kt`.

## Build Matrix
- Product flavors:
  - `labo`
    - `applicationIdSuffix = ".labo"`
    - `versionNameSuffix = "-labo"`
    - Native SoundTouch can be enabled with `-PenableSoundTouchNative=true`
  - `concert`
    - `applicationIdSuffix = ".concert"`
    - `versionNameSuffix = "-concert"`
    - CMake forces `LRC_USE_SOUNDTOUCH=0`
- Build types: `debug`, `release`
- Important note: `gradle/libs.versions.toml` exists, but the active build scripts use hardcoded versions in `build.gradle.kts` and `app/build.gradle.kts`. Do not assume the version catalog is the source of truth.

## External Dependencies Actually Used
- Compose BOM + `ui`, `material3`, `animation`, `material-icons-extended`
- `androidx.activity:activity-compose`
- `androidx.appcompat:appcompat`
- `androidx.documentfile:documentfile`
- Media3: `exoplayer`, `extractor`, `ui`, `common`
- `androidx.lifecycle:lifecycle-viewmodel-compose`
- `androidx.work:work-runtime-ktx`
- Room: `room-runtime`, `room-ktx`, `room-compiler`
- Test stack:
  - JUnit 4
  - Mockito
  - Espresso
  - Compose UI tests
- Native/JNI:
  - CMake project under `app/src/main/cpp`
  - vendored SoundTouch sources under `app/src/main/cpp/soundtouch`

## Real Source Map
- `app/src/main/java/com/patrick/lrcreader`
  - `MainActivity.kt`
  - `ExportUtils.kt`
  - `smp/`
  - `core/`
  - `ui/`
- `app/src/main/cpp`
  - `soundtouch_bridge.cpp`
  - vendored SoundTouch sources
- `app/src/main/assets/demo`
  - demo audio, lyrics and chords used by setup/demo install flows
- `app/src/test`
  - mostly JVM tests for playlist logic, search, LRC timeline, library deletion helpers
- `app/src/androidTest`
  - small number of device/Compose tests
- Current scale:
  - `179` Kotlin files in `app/src/main/java`
  - `108` files in `core/`
  - `64` files in `ui/`
  - `29` C++ files in `app/src/main/cpp`

## Main Runtime Architecture
- `MainActivity.kt` is the central orchestrator.
  - It is large (`~1928` lines).
  - It owns startup, setup gating, session restore/persist, bottom-tab switching, overlays, player bootstrapping, and high-level coordination between Player, DJ, filler, tuner, search, notes and setup.
- There is no DI container.
  - No Hilt, Dagger or Koin.
  - Dependencies are mostly accessed through Kotlin `object` singletons and Compose locals/state.
- There is almost no ViewModel usage.
  - The notable exception is `ui/DjBrowserViewModel.kt`.
  - Most screen state lives in `remember`, `mutableStateOf`, singleton `object`s, or `StateFlow`.
- Navigation is manual.
  - `MainActivity` switches screens with a big `when (selectedTab)`.
  - Overlays are controlled by booleans and local state, not by a nav graph.

## Important Packages

### `core/`
- Shared application logic and state.
- Important hotspots:
  - `core/audio/AudioEngine.kt`
    - global ExoPlayer holder
    - speed/pitch logic
    - HQ vs EXO time-stretch mode
    - final bus/track/fade volume composition
  - `core/DjEngine.kt`
    - actually declares package `com.patrick.lrcreader.core.dj`
    - uses two `MediaPlayer` decks + `Visualizer`
    - exposes global DJ state via `StateFlow`
  - `core/PlaybackCoordinator.kt`
    - single rule keeper for exclusivity between Player, DJ and Filler
  - `core/FillerSoundManager.kt`
    - filler/background playback coordination
  - `core/PlaylistRepository.kt`
    - in-memory playlist model and play/review/custom title metadata
  - `core/LrcStorage.kt`
    - lyrics/chords lookup and cache
  - `core/ImportAudioManager.kt`
    - SAF import into SPL folder structure
  - `core/BackupManager.kt`
    - export/import app state
  - `core/SessionPrefs.kt`
    - session restore/persist, with both prefs and JSON backing
  - `core/InternalStoragePaths.kt`
    - internal SPL directory creation + legacy case compatibility

### `core/config/`
- Portable JSON stores under `SPL_Music/Config`.
- Key stores:
  - `TrackSettingsStore`
  - `TitleAliasesStore`
  - `NotesConfigStore`
  - `MidiCuesConfigStore`
  - `PlaylistStateStore`
  - `SessionPrefs` uses the generic atomic JSON helper too
- IO helpers:
  - `ConfigJsonAtomicFileIo.kt`
  - `TrackSettingsAtomicIo.kt`
  - `TrackSettingsPathResolver.kt`

### `core/history/`
- Only Room-backed persistence in the app.
- Tracks play history via `HistoryDatabase`, `HistoryDao`, `HistoryRepository`.

### `ui/`
- Compose screens and UI helpers.
- Large files to treat as high-risk:
  - `ui/QuickPlaylistsScreen.kt` (`~2372` lines)
  - `ui/library/LibraryScreen.kt` (`~1305` lines)
  - `ui/PlayerScreen.kt`
  - `ui/DjScreen.kt`
- `ui/library/` is the main storage-facing UI area:
  - setup
  - SAF folder picking
  - library browsing
  - import/move/delete/rename
  - demo library installation

## Storage Model
- The app supports two storage modes:
  - `SAF`
  - `INTERNAL`
- Mode switch lives in `core/StorageModePrefs.kt`.
- Root selection and permissions are handled by:
  - `BackupFolderPrefs.kt`
  - `BackupFolderPrefsSaf.kt`
  - `BackupFolderPrefsInternal.kt`
- The logical file tree expected by many features is based on `SPL_Music`:
  - `SPL_Music/BackingTracks/audio`
  - `SPL_Music/BackingTracks/Lyrics`
  - `SPL_Music/BackingTracks/Accords`
  - `SPL_Music/DJ`
  - `SPL_Music/Config`
  - `SPL_Music/Backups`
- Internal storage compatibility is intentionally messy:
  - `InternalStoragePaths.ensureSplRoot()` creates both lowercase and uppercase folder variants.
  - It mirrors files between legacy and new casing.
  - Do not “clean this up” casually; multiple parts of the app rely on this compatibility layer.

## Persistence Model
- Persistence is split across multiple mechanisms.

### SharedPreferences
- Used for many operational prefs and fallbacks:
  - setup flags
  - storage mode
  - folder URIs
  - many feature prefs
  - legacy/fallback session and text-song data

### JSON files under `SPL_Music/Config`
- Used for portable state that should survive device/workspace migration.
- Stores include:
  - session state
  - track settings
  - title aliases
  - text songs
  - playlist ordering metadata
  - notes config
  - MIDI cues config
- SAF writes are direct writes to the final file.
  - The code does not rely on rename-based atomicity for SAF providers.
  - Be careful when changing write semantics.

### Room
- Only used for play history.
- Changing history should not require touching playlist/session persistence.

### In-memory global state
- `PlaylistRepository` is still RAM-first for the live playlist model.
- Other important RAM-first/global objects:
  - `AudioEngine`
  - `DjEngine`
  - `PlaybackCoordinator`
  - `MeterManager`
  - `TunerEngine`
  - `LibrarySnapshot`

## Audio Model
- Player path:
  - Media3 `ExoPlayer`
  - `AudioEngine` is the single owner
  - lyrics can come from sidecar LRC or embedded tags
- DJ path:
  - dual `MediaPlayer` decks
  - crossfade and queue in `DjEngine`
- Filler path:
  - separate background/filler logic
- `PlaybackCoordinator` enforces “one master source at a time”.
  - If you start playback in one subsystem without updating the coordinator, regressions are likely.
- Native time-stretch:
  - `SoundTouchBridge` loads JNI conditionally
  - `AudioEngine` must tolerate native unavailable/stub mode
  - Never assume SoundTouch is present just because the C++ sources exist

## Search and Lyrics Routing
- Audio and non-audio entries coexist.
- `PlaybackRouter` routes:
  - real audio URIs
  - virtual `prompter://<id>` items
  - unknown/group header items
- Lyrics resolution path:
  - `LyricsResolver`
  - `LrcStorage`
  - `LyricsMemoryCache`
  - embedded lyrics listener in `core/audio/EmbeddedLyricsListener.kt`

## Known Structural Gotchas
- Package/path mismatches exist.
  - `app/src/main/java/com/patrick/lrcreader/MainActivity.kt` declares `package com.patrick.lrcreader.exo`
  - `app/src/main/java/com/patrick/lrcreader/core/DjEngine.kt` declares `package com.patrick.lrcreader.core.dj`
  - Search by symbol/package, not only by filesystem path.
- `MainActivity.kt` and several screens are “god files”.
  - Make minimal, local changes.
  - Read surrounding state and side effects before editing.
- State is duplicated on purpose in some places.
  - Example: `SessionPrefs`, `TextSongRepository`, `TitleAliasesStore` keep legacy/fallback behavior alongside portable JSON.
  - Do not remove fallback code without checking migration needs.
- Track-scoped JSON stores depend on relative path resolution.
  - `TrackSettingsPathResolver` derives keys from the current library root and URI.
  - Renaming or moving files can orphan settings if you do not migrate keys.
- Many SAF checks normalize or alias folder names.
  - Do not replace `DocumentFile` logic with raw string assumptions.
- Build scripts and version catalog are partially divergent.
  - If updating dependency versions, inspect both `build.gradle.kts` and `gradle/libs.versions.toml`.
- The repo contains non-source clutter:
  - `app/src/main/java/com/patrick/lrcreader/ui/.DS_Store`
  - `app/src/main/poubelle/`
  - committed build artifact `app/concert/release/app-concert-release.aab`
  - ignore these unless the task is explicitly about them

## Where To Start By Change Type
- App startup, overlays, tab restore, session issues:
  - `MainActivity.kt`
  - `SessionPrefs.kt`
- Player / speed / pitch / HQ / mix:
  - `core/audio/AudioEngine.kt`
  - `core/ExoCrossfadePlay.kt`
  - `ui/PlayerScreen.kt`
- DJ:
  - `core/DjEngine.kt`
  - `ui/DjScreen.kt`
  - `ui/DjBrowserViewModel.kt`
- Filler / background sound:
  - `core/FillerSoundManager.kt`
  - `ui/FillerSoundScreen.kt`
- Library / SAF / import / rename / delete:
  - `ui/library/LibraryScreen.kt`
  - `ui/library/LibraryBackendSaf.kt`
  - `ui/library/LibraryBackendInternal.kt`
  - `core/ImportAudioManager.kt`
  - `ui/library/SetupInstallScreen.kt`
  - `ui/library/SetupDemoInstaller.kt`
- Portable config and per-track metadata:
  - `core/config/*`
- Lyrics / chords / sidecar files:
  - `core/LrcStorage.kt`
  - `core/lyrics/LyricsResolver.kt`
  - `ui/LyricsEditorSection.kt`
- Backup / restore:
  - `core/BackupManager.kt`
  - `ui/BackupScreen.kt`
  - `core/AutoBackupScheduler.kt`
  - `core/AutoBackupWorker.kt`

## Validation Commands
- Fast compile:
  - `./gradlew :app:compileLaboDebugKotlin`
- Default local gate:
  - `./tests.sh`
- Device/instrumented tests:
  - `./tests_device.sh`
- Both flavors smoke build:
  - `./qa_smoke.sh`
- Smoke build including native HQ path:
  - `./qa_smoke.sh --hq`
- Pre-push validation:
  - `./tools/validate_before_push.sh`
- Gradle aggregate task:
  - `./gradlew :app:ci`

## Practical Editing Rules For This Repo
- Prefer extending existing singletons/stores over introducing parallel state.
- For file operations:
  - SAF path: use `DocumentFile` and persisted URI permissions
  - INTERNAL path: use `InternalStoragePaths` and `File`
- For long-running IO during UI flows, use coroutines and `Dispatchers.IO`.
- Preserve existing logging tags around complex flows.
  - Important tags include `BOOTSTEP`, `NEXT`, `BUS`, `AUDIO_TS`, `LRC_STORAGE`, `LrcDebug`
- If changing setup/library/import flows, test both `SAF` and `INTERNAL` assumptions before concluding the fix is safe.

## SMP Forward Rules
- For any work related to portable songs, import/export, track normalization, rename/move/delete coherence, or future song-folder storage, read `SMP_RULES.md` first.
- `AGENTS.md` describes the current architecture; `SMP_RULES.md` describes the target song-unit model and coexistence constraints for `.smp`.


## Timeline Is The Source Of Truth

- The timeline is the central execution model of the app.
- All time-based behaviors must be derived from playback position (`positionMs`).

This includes:
- lyrics display (LRC)
- MIDI cues
- future LightCue system
- any time-synchronized feature

Rules:
- Do NOT gate runtime logic on UI state (e.g. active tab, visible screen).
- Do NOT depend on derived indexes (e.g. lineIndex) if time-based data is available.
- Always prefer direct time-based triggering.

Principle:
Time → decision → action


## Intent vs Output Architecture

The system must strictly separate:

1. Intent layer
  - user-defined data (MIDI cues, LightCue, etc.)
  - time-based and portable

2. Runtime engine
  - timeline-driven triggering
  - no knowledge of hardware

3. Output layer
  - MIDI dispatch
  - future DMX / Art-Net
  - simulator

Rules:
- No business logic must depend on output protocol (MIDI, DMX, etc.)
- Output layers are replaceable adapters
- Intent data must remain hardware-agnostic

Principle:
The app controls meaning, not devices.

## LightCue System (Forward Architecture)

A new system will be introduced for lighting control.

Key points:
- LightCue is equivalent to MIDI cues but for lighting
- defined by:
  - timeMs
  - type (color, ambiance, effect)
  - intensity
  - fadeMs

V1:
- internal simulator only
- no hardware dependency

V2:
- output via Art-Net (Wi-Fi)
- mapping to DMX profiles

Rules:
- LightCue must follow the same timeline logic as MIDI
- must NOT depend on lyrics lineIndex
- must work fully without external hardware

Goal:
Provide a simple lighting system for musicians without exposing DMX complexity.

## Mandatory Agent Consultation

Before any non-trivial decision:

- Read and respect:
  - AGENTS.md
  - SMP_RULES.md
  - any SMP spec file

- Ensure consistency with:
  - current architecture
  - SMP model
  - live performance constraints

Rules:
- Do not introduce structural changes without validation
- Prefer diagnostic before patch
- Avoid assumptions when code can be inspected

Principle:
Codex sees → we validate → then we act


⸻

RÈGLES UX GLOBALES — COHÉRENCE INTERFACE

RECHERCHE (TRÈS IMPORTANT)

- Il existe un seul point d’entrée visible pour la recherche dans l’application :
  la loupe du menu du bas.

- Cette loupe est globale, mais son action dépend toujours de la page active.

- Exemple :
  - si la page active est Playlist → la recherche agit sur Playlist
  - si la page active est Bibliothèque → la recherche agit sur Bibliothèque

- Les écrans/pages ne doivent jamais ajouter leur propre loupe locale
  sauf demande explicite du produit.

- Une page peut afficher localement son champ de recherche,
  mais uniquement en réponse à l’action déclenchée par la loupe globale.

- Toute correction liée à la recherche doit respecter cette règle UX,
  même si le problème semble local à une seule page.

⸻

COHÉRENCE DES POINTS D’ENTRÉE

- Lorsqu’une action existe déjà comme point d’entrée global (ex : recherche),
  il est interdit de créer un second point d’entrée local pour corriger un problème.

- Toujours privilégier la cohérence globale de l’application
  plutôt qu’une correction locale rapide.

⸻

COMMIT WORKFLOW

- Tous les commits doivent être réalisés par Codex
- L’utilisateur ne fait plus de commit manuel

Règle :
- Après chaque patch validé (tests OK + validation utilisateur),
  Codex doit :
  - proposer un commit
  - limiter le commit aux fichiers concernés
  - générer un message de commit propre

- Le commit ne doit être exécuté qu’après validation explicite de l’utilisateur

Objectif :
- centraliser les commits
- éviter les oublis
- garder un historique propre et homogène
