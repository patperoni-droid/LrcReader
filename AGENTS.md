# AGENTS.md

## Scope
- Gradle project with a single application module: `:app`.
- Base namespace and application id: `com.patrick.lrcreader.exo`.
- Toolchain:
  - AGP `8.5.0`
  - Kotlin `2.0.0`
  - Java/Kotlin target `17`
  - `compileSdk 35`, `minSdk 23`, `targetSdk 35`
- UI stack: Jetpack Compose only.
- Main entrypoint: `MainActivity.kt`.

---

## Build Matrix
- Flavors:
  - labo
  - concert
- Build types: debug / release

---

## Main Runtime Architecture

- `MainActivity` = orchestrateur central
- Pas de DI
- Navigation manuelle
- État majoritairement via:
  - singletons (`object`)
  - Compose state
  - StateFlow

---

## Storage Model — SMP FIRST (UPDATED)

The application is now **SMP-first**.

### BackingTracks

- `BackingTracks` must contain **only `.smp` files**
- Legacy folders must NOT be created:
  - audio
  - lyrics
  - accords
  - videos
  - DJ

### Config

- `Config` must be created **lazily**
- never created on read
- created only on first real write

### Backups

- `Backups` remains valid

### Rule

Filesystem must reflect **real physical files only**

👉 No artificial folders  
👉 No automatic legacy recreation

---

## Files Explorer Philosophy (CRITICAL)

`Fichiers` must behave like **Google Files**

Rules:
- Show real filesystem only
- No filtering
- No hidden logic
- No fake folders

Capabilities:
- navigate freely (up/down)
- reach SAF root
- copy / move / delete
- support files + folders
- support multi-selection

Principle:
Reality > abstraction

---

## SAF Permissions Strategy

All SAF permissions must be persisted.

Rules:
- always call `takePersistableUriPermission`
- reuse previously authorized folders
- never reopen picker unnecessarily

System:
- transfer destinations are stored
- validated against persisted permissions
- reused automatically

Fallback:
- picker only if no valid folder available

---

## Onboarding Model (NEW)

Onboarding is split in two:

### 1. Music Access
- permission: `READ_MEDIA_AUDIO`
- used for:
  - DJ
  - audio browsing

### 2. Workspace (SMP)
- SAF folder chosen by user
- recommended: `Music`

Rules:
- never expose SAF jargon
- never ask multiple folders
- UX must be simple:
  - "I can access my music"
  - "I have a workspace"

---

## DJ Architecture (IMPORTANT)

Current:
- folder-based

Target:
- global audio access

Rules:
- must NOT depend on a folder
- must use media access
- folder mode = fallback only

---

## Persistence Model

### SharedPreferences
- operational prefs

### JSON (Config)
- portable state
- created lazily

### Room
- history only

### In-memory
- PlaylistRepository
- AudioEngine
- DjEngine

---

## Audio Model

- Player → ExoPlayer
- DJ → MediaPlayer x2
- Filler → separate
- Coordinator enforces exclusivity

---

## Timeline Is Source Of Truth

All time-based logic must rely on:
- `positionMs`

Never:
- UI state
- lineIndex

---

## Intent vs Output Architecture

- Intent = data (portable)
- Runtime = timeline
- Output = MIDI / DMX

No coupling allowed.

---

## LightCue System

- timeline-based
- no hardware dependency
- DMX later (Art-Net)

---

## Known Gotchas

- MainActivity = énorme
- duplication volontaire de state
- SAF complexe → toujours utiliser DocumentFile
- version catalog pas fiable

---

## Where To Start

- Startup → MainActivity
- Player → AudioEngine
- DJ → DjEngine
- Library → LibraryScreen
- Config → core/config
- Lyrics → LrcStorage

---

## Validation

- compile:
  `./gradlew :app:compileLaboDebugKotlin`

---

## Editing Rules

- patch minimal
- ne pas casser existant
- IO → Dispatchers.IO
- logs existants à conserver

---

## SMP Rules

- lire SMP_RULES.md avant toute décision

---

## Mandatory Agent Consultation

Toujours:
- diagnostic avant patch
- ne pas supposer
- vérifier le code

---

## UX RULES

### Recherche
- une seule loupe globale
- dépend de la page active

### Cohérence
- jamais doubler une feature UI

---

## Commit Workflow

- commits faits par Codex uniquement
- après validation utilisateur

Format:
- patch validé
- commit ciblé
- message propre

---