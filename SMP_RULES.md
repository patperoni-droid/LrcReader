# SMP_RULES.md
CRITICAL — SMP SPECIFICATION

This document is the authoritative specification of the SMP system.

All features involving:
- song structure
- import/export
- storage
- playback
- editing

MUST comply with this document.

If there is any conflict between:
- implementation
- other documentation

👉 SMP_RULES.md prevails.
## Purpose
- This file defines the target rules for the future `.smp` system.
- `AGENTS.md` remains the source for the current codebase architecture.
- This file is the source for future decisions involving a song as a complete portable unit.

## Core Principle
- A `.smp` file is not “just a zip”.
- A `.smp` file represents one complete song unit.
- All reasoning must happen at song scope, not at single-file scope.

## Song Unit Definition
- A song unit is the full set of assets and metadata required to work on one song.
- A song unit may include:
  - audio
  - lyrics
  - chords
  - annotations
  - MIDI cues
  - DMX cues
  - prompter content
  - per-song settings/config
  - non-destructive Arrangement project and virtual variants
- Missing parts are allowed.
- The absence of one asset does not change the fact that the song unit remains the canonical object.

## Allowed `.smp` Payload
- Expected payload names may include:
  - `audio.mp3`
  - `lyrics.lrc`
  - `chords.lrc`
  - `config.json`
  - `midi_cues.json`
  - `annotations.json`
  - `prompteur.txt` or `prompteur.json`
  - `dmx_cues.json`
  - `arrangement.json`
  - `arrangement_variants.json`
- File names inside the archive are transport names.
- Transport names must not become the long-term identity model of the app.

### Virtual Arrangement Variants

- A virtual Arrangement variant is not a complete standalone song because it does not own audio.
- It has its own Library `songId`, title, and Arrangement structure at runtime.
- It may own variant-specific assets such as lyrics, chords, timeline data, annotations, and per-variant settings.
- For transport, it must be embedded in the `.smp` of its source SongUnit.
- The source audio must appear only once in that parent archive, while variant-specific assets are transported with their owning variant entry.
- Exporting a virtual variant as an independent audio-less `.smp` is forbidden.
- Import must normalize the parent first, then recreate its variants as separate internal SongUnits.
- Runtime playback must use only those normalized folders and must never read the variants manifest from the archive.
- A variant cannot remain valid without its source SongUnit.
- Deleting a source SongUnit from the Library must also delete all of its virtual variants and remove every playlist reference to those deleted SongUnits, after an explicit confirmation that reports the cascade.
- Deleting one virtual variant must never delete its source SongUnit or sibling variants.
- Removing a source song occurrence from a playlist must not remove occurrences of its variants.
- A playlist may reference a virtual variant without referencing its source song, provided that the source SongUnit still exists in normalized Library storage.

## Current Project Reality
- The current beta is file-centric:
  - audio is mainly under `SPL_Music/BackingTracks/audio`
  - sidecar lyrics/chords are mainly under `SPL_Music/BackingTracks/Lyrics` and `Accords`
  - portable JSON config is under `SPL_Music/Config`
  - prompter content currently lives as separate text-song entities (`prompter://<id>`)
- A lot of existing persistence is keyed by URI or relative path:
  - `TrackSettingsStore`
  - `TitleAliasesStore`
  - `CueMidiStore`
  - `PlaylistStateStore`
  - `SessionPrefs`
  - `LibraryIndexCache`
  - `LrcStorage`
  - `NotesConfigStore`
- The `.smp` rollout must coexist with this system before replacing it.

## Target Internal Model
- All imports must normalize into one internal song model.
- External inputs must never remain the primary source after normalization.
- Recommended canonical model:

```text
SongUnit
  id: stable internal song id
  displayTitle: user-facing title
  storageFolder: internal folder for this song
  audio: optional asset
  lyrics: optional normalized lyrics asset
  chords: optional normalized chords asset
  annotations: optional normalized annotation asset
  midi: optional normalized MIDI cue asset
  dmx: optional normalized DMX cue asset
  prompter: optional normalized prompter asset
  settings: optional normalized per-song settings asset
  sourceInfo: optional import provenance/debug info
```

## Canonical Asset Buckets
- Future logic must reason in these buckets:
  - `audio`
  - `lyrics`
  - `chords`
  - `annotations`
  - `midi`
  - `dmx`
  - `prompter`
  - `settings`
- If an external source has a different shape, it must be mapped into these buckets.

## Import Rules

### Supported Inputs
- `mp3` only
- `mp3` + external files (`.lrc`, etc.)
- `mp3` with embedded tags
- `mp3` coming from another app such as Musicolet
- `.smp`

### Required Normalization
- `mp3` only
  - create a song unit with `audio`
  - other buckets are empty
- `mp3` + external sidecars
  - attach discovered sidecars to the same song unit
- `mp3` with embedded lyrics/tags
  - extract embedded data once
  - convert it into normalized internal assets
  - do not treat embedded tags as the ongoing source of truth
- Musicolet or other app-origin files
  - treat them as external imports
  - import available metadata, then normalize into the internal model
- `.smp`
  - unzip once during import
  - validate the payload
  - normalize into the same internal model as every other source
  - do not special-case playback/search/edit around the zip itself

### Import Invariants
- Every import path must converge to the same internal representation.
- Post-import behavior must not depend on the original source format.
- A future AI should ask “what song unit was created?” not “what file was imported?”.

## Runtime Storage Rules
- Live runtime must work on unpacked internal storage, not inside the `.smp` archive.
- `.smp` is a transport/import/export format.
- Do not read from the zip during normal playback, editing, search, or live operations.

## Target Internal Storage Shape
- Target direction: one internal folder per song.
- Example target layout:

```text
SPL_Music/Tracks/<song-id>/
  audio.mp3
  lyrics.lrc
  chords.lrc
  annotations.json
  midi_cues.json
  dmx_cues.json
  prompteur.txt
  settings.json
  source.json
```

- The app owns this folder layout.
- Original import names may be kept in metadata if needed, but not as the runtime contract.

## Identity Rules
- The canonical identity of a song must be stable.
- Identity must not depend on:
  - original SAF URI
  - original file name
  - temporary import location
  - `.smp` file name
- Rename must update display metadata, not song identity.
- Move must change storage location only if needed, not song identity.
- Export/import across devices must preserve song identity when possible, or at least preserve song coherence as one unit.

## Search and Selection Rules
- Search must reason on song units, not only on raw files.
- Search/index data may be derived from:
  - song title
  - audio file name
  - lyrics text
  - chord text
  - annotations
  - prompter text
- The library UI should eventually expose one selectable song unit, not disconnected files belonging to the same song.
- A future AI should avoid introducing features that expose `audio`, `lyrics`, `chords`, `midi` as unrelated library entries for the same song.

## Playback Rules
- Playback entrypoint should operate on a song unit, then resolve the needed assets.
- Audio playback still uses the normalized audio asset.
- Lyrics/chords/prompter/cues should be resolved from the same song unit.
- The player must not need to know whether the song originally came from:
  - raw mp3
  - mp3 + sidecars
  - embedded tags
  - `.smp`

## Edit / Modify Rules
- Editing lyrics, chords, cues, annotations, settings or prompter content modifies the song unit.
- Changes must be written back to normalized internal assets.
- External import formats are not edited in place.
- Do not build features that write only one asset and forget the song-level consistency implications.

## Rename / Move / Delete Rules
- Rename is a song-level operation.
  - It must consider audio, lyrics, chords, cues, annotations, settings, prompter and derived indexes.
- Move is a song-level operation.
  - It must move the whole song folder or its logical equivalent.
- Delete is a song-level operation.
  - It must remove the full song unit or provide an explicit partial-delete policy.
- Partial asset deletion is allowed only if the feature explicitly operates on one bucket and leaves the song unit valid.
- The default assumption for library actions must be full-song coherence.

## Export Rules
- Export produces a `.smp` from the normalized internal song model.
- Export must not depend on the original import source.
- Export is a serialization step:
  - collect current normalized assets
  - write them into the transport archive
- Export must reflect the current edited state of the song, not stale source files.

## Device Transfer Rules
- Phone -> tablet transfer is a song-unit transfer.
- The archive must contain enough information to rebuild the same song unit on the target device.
- After import on another device, the song should remain coherent even if absolute paths and URIs change.

## Coexistence Rules
- The old system and the future `.smp` system must coexist for a transition period.
- No big-bang rewrite.
- Legacy file-based flows must keep working while the normalized model is introduced.
- New code should prefer an adapter layer:
  - legacy raw files -> normalized song unit
  - `.smp` import -> normalized song unit
- Progressive rollout is required:
  - phase 1: inspect `.smp`, validate structure, import into internal storage
  - phase 2: normalize more metadata buckets
  - phase 3: export `.smp`
  - phase 4: move more UI/business logic from file-level to song-level

## Performance Rules
- Do not slow down live playback for `.smp`.
- Do not add zip reads on the hot path.
- Do not turn every player/search action into a full archive parse.
- Any normalization or extraction should happen at import time or in background preparation steps.

## Compatibility Rules
- The current beta must remain stable.
- Any `.smp` introduction must be incremental and reversible.
- Existing screens that are still URI/path-centric must not be broken abruptly.
- If a new song id layer is introduced, legacy URI/path keyed stores need bridging instead of immediate removal.

## High-Risk Current Components For SMP Work
- Storage and import:
  - `MainActivity.kt`
  - `ui/library/LibraryScreen.kt`
  - `ui/library/SetupInstallScreen.kt`
  - `core/ImportAudioManager.kt`
  - `core/InternalStoragePaths.kt`
  - `core/LibraryIndexCache.kt`
- Audio/lyrics resolution:
  - `core/audio/AudioEngine.kt`
  - `core/LrcStorage.kt`
  - `core/lyrics/LyricsResolver.kt`
  - `core/PlaybackRouter.kt`
- Per-song config keyed by URI/relative path:
  - `core/config/TrackSettingsStore.kt`
  - `core/config/TitleAliasesStore.kt`
  - `core/config/PlaylistStateStore.kt`
  - `core/CueMidiStore.kt`
  - `core/config/NotesConfigStore.kt`
  - `core/SessionPrefs.kt`
- Existing SMP placeholder:
  - `smp/SmpManager.kt` currently only lists zip entries; it is not yet a song-unit normalizer

## Required Architectural Direction
- Introduce an explicit song-level abstraction before spreading `.smp` support widely.
- Future code should prefer a layer such as:

```text
ExternalSource -> Normalizer -> SongUnit -> Runtime Features -> Exporter
```

- Runtime features should consume `SongUnit` or an equivalent track-level object.
- Importers and exporters are the only places that should know external transport formats in detail.

## Decision Checklist For Future Changes
- Does this change operate on one file or on the whole song unit?
- If the user renames the song, what happens to all buckets?
- If the user deletes the song, what happens to all buckets?
- If the song is exported to `.smp`, will this data be preserved?
- If the song is imported from `.smp`, where is it normalized?
- Is the change tied to a temporary URI/path that should really be a stable song id?
- Does the change still work after phone -> tablet transfer?
- Does the change keep legacy mode working during the coexistence period?
- Does the change avoid zip access in the live path?

## What Future Code Must Avoid
- Do not treat `.smp` as the runtime working directory.
- Do not make raw embedded tags the long-term source of truth.
- Do not make persistent features depend only on original import URIs.
- Do not implement rename/move/delete at audio-file scope if the intent is song scope.
- Do not export stale source files when normalized internal assets were edited later.

## Short Operational Summary
- `.smp` is transport.
- Internal normalized song storage is runtime.
- The song unit is the canonical object.
- Import/export are format boundaries.
- Rename/move/delete/edit/search/playback must preserve song coherence.

## Time-Based Execution Model

All dynamic behaviors attached to a song unit must be time-based.

This includes:
- MIDI cues
- DMX cues
- future automation features

Rules:
- All cues must be defined using `timeMs`
- Runtime triggering must depend only on playback position
- No cue system should depend on UI state or line index (lyrics/chords)

Principle:
Playback time is the only source of truth for execution.


## Unified Cue Model

All time-based actions must follow a unified cue model.

Generic structure:

```text
Cue
  timeMs: Long
  type: String
  payload: Map<String, Any>
