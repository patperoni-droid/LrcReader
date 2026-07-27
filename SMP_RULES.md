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
- This file defines the authoritative rules of the current `.smp` system.
- `AGENTS.md` defines the project workflow and the documents that must be loaded for each task.
- This file is the source of truth for every current or future decision involving a song as a complete portable unit.
- The implementation may contain legacy compatibility bridges, but they must never override the rules defined here.

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
  - `audio.mp3`, `audio.wav`, `audio.flac`, `audio.m4a`, `audio.aac`, `audio.ogg` or another explicitly supported normalized audio entry
  - `lyrics.lrc`
  - `chords.lrc`
  - `config.json`
  - `timeline.json`
  - `waveform.json`
  - `grid.json`
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
- It is nevertheless normalized as a distinct Library SongUnit with its own `songId`, title, storage folder, and Arrangement structure at runtime.
- Its immutable `sourceSongId` identifies the single parent SongUnit that owns the source audio.
- A variant `songId` must differ from its `sourceSongId`.
- A variant may own variant-specific assets such as lyrics, chords, line colors, timeline data, annotations, and per-variant settings.
- Variant-specific `lyrics.lrc` and `chords.lrc` belong to the variant `songId`; editing them must never modify the parent assets.
- For transport, it must be embedded in the `.smp` of its source SongUnit.
- The source audio must appear only once in that parent archive, while variant-specific assets are transported with their owning variant entry.
- Exporting a virtual variant as an independent audio-less `.smp` is forbidden.
- Sharing a variant must resolve its parent and create a complete parent-backed archive containing only the selected variant in `arrangement_variants.json`.
- A targeted variant share must identify that variant through `selectedVariantId`, which is always a `songId`.
- Sharing the parent keeps the normal behavior and may transport all of its variants.
- Import must normalize the parent first, then recreate its variants as separate internal SongUnits.
- If a targeted variant archive is imported and the parent `songId` already exists locally, the local parent is the reference and must not be replaced or modified by that targeted import.
- If that parent does not exist, the complete parent SongUnit is normalized first, then the selected variant is restored.
- If a variant with the same `songId` already exists under a different `sourceSongId`, restoration must fail explicitly. Parent reassignment is forbidden.
- Restoring a variant is additive and selective: an asset carried by the archive is restored; a local asset absent from the archive is preserved.
- Runtime playback must use only those normalized folders and must never read the variants manifest from the archive.
- A variant cannot remain valid without its source SongUnit.
- Deleting a source SongUnit from the Library must also delete all of its virtual variants and remove every playlist reference to those deleted SongUnits, after an explicit confirmation that reports the cascade.
- Deleting one virtual variant must never delete its source SongUnit or sibling variants.
- Removing a source song occurrence from a playlist must not remove occurrences of its variants.
- A playlist may reference a virtual variant without referencing its source song, provided that the source SongUnit still exists in normalized Library storage.

## Current Project Reality
- The current SMP runtime is based on normalized SongUnits stored under the application internal storage.
- `.smp` import, export, individual sharing, complete backup, and complete restore are implemented.
- Virtual Arrangement variants are visible as distinct Library SongUnits and can be played, edited, shared, assigned to playlists, backed up, and restored.
- Complete restore rebuilds parents and variants before remapping and persisting playlists.
- Legacy URI/path-based data and external audio import flows still exist through compatibility bridges.
- These bridges must converge toward the normalized SongUnit model and must never make an external URI, path, title, or file name the canonical identity.

## Current Internal Model
- All imports must normalize into one internal song model.
- External inputs must never remain the primary source after normalization.
- Canonical model:

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
  sourceSongId: immutable parent id for a virtual Arrangement variant only
```

## Canonical Asset Buckets
- Current and future logic must reason in these buckets:
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
- audio formats accepted by the existing Android/SMP audio import pipeline, including MP3 and WAV
- supported audio + external sidecar files (`.lrc`, etc.)
- supported audio with embedded tags
- supported audio coming from another app such as Musicolet
- `.smp`

### Required Normalization
- supported audio only
  - create a song unit with `audio`
  - other buckets are empty
- supported audio + external sidecars
  - attach discovered sidecars to the same song unit
- supported audio with embedded lyrics/tags
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
- Import identity decisions must use `songId`, never the archive name, source path, title, or URI.
- An archive with a valid existing `songId` updates or preserves that logical SongUnit according to the explicit import/restore mode; it must never create a second logical identity from its file name.
- An agent should ask “what SongUnit was created or updated?” not “what file was imported?”.

## Runtime Storage Rules
- Live runtime must work on unpacked internal storage, not inside the `.smp` archive.
- `.smp` is a transport/import/export format.
- Do not read from the zip during normal playback, editing, search, or live operations.

## Internal Storage Shape
- Runtime uses one internal folder per SongUnit.
- Canonical layout:

```text
/files/tracks/<song-id>/
  config.json
  meta.json
  audio.<supported-format>
  lyrics.lrc
  chords.lrc
  timeline.json
  waveform.json
  grid.json
  annotations.json
  midi_cues.json
  dmx_cues.json
  prompteur.txt
  arrangement.json
```

- Files other than runtime identity/configuration are optional.
- The app owns this folder layout.
- Original import names may be kept in metadata if needed, but not as the runtime contract.
- A virtual variant has its own folder but does not duplicate the parent audio.

## Identity Rules
- `songId` is the absolute and stable identity of every parent or variant SongUnit.
- Identity must not depend on:
  - original SAF URI
  - original file name
  - temporary import location
  - `.smp` file name
- Rename must update display metadata, not song identity.
- Move must change storage location only if needed, not song identity.
- Export/import across devices must preserve `songId`.
- For a virtual variant, `sourceSongId` is the immutable identity of its parent.
- The pair `variant songId -> sourceSongId` must never be silently changed.
- Duplicate variant ids inside an archive, a variant id equal to its parent id, or a parent mismatch must invalidate the variant restoration.

## Search and Selection Rules
- Search must reason on song units, not only on raw files.
- Search/index data may be derived from:
  - song title
  - audio file name
  - lyrics text
  - chord text
  - annotations
  - prompter text
- The Library exposes selectable SongUnits, not disconnected assets belonging to the same song.
- An agent must avoid introducing features that expose `audio`, `lyrics`, `chords`, or `midi` as unrelated Library entries for the same SongUnit.

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
- Editing variant lyrics or chords writes only to the variant folder identified by its `songId`.
- Editing a variant Arrangement must preserve its unrelated local assets.
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
- Exporting a parent may include its `arrangement.json` and all valid virtual variants in `arrangement_variants.json`.
- Sharing one variant packages the parent audio once, includes only the requested variant, and records its `songId` as `selectedVariantId`.
- Export must fail safely if the requested variant, its parent, its structure, or its source audio cannot be resolved.

## Restore and Backup Rules
- A complete backup serializes the current normalized SongUnits and application state; it must not depend on runtime archive reads.
- Virtual variants travel inside the `.smp` archive of their parent and must not duplicate audio.
- Complete restore order is mandatory:
  1. validate and normalize parent SongUnits;
  2. restore their virtual variants;
  3. refresh the Library index;
  4. remap playlist references by `songId`;
  5. restore playlist order, internal groups, colors, occurrences, and other transported state.
- Playlist restoration must never run against a stale Library index.
- Playback must never be required to repair a missing Library or playlist title.
- A targeted variant import with an existing local parent modifies only the selected variant data carried by the archive.
- Variant assets absent from an archive must be preserved locally; this rule applies to future optional variant assets as they are added.
- Existing archives without `arrangement.json`, `arrangement_variants.json`, `selectedVariantId`, variant lyrics, or variant chords remain readable. Missing historical data cannot be reconstructed if it was never transported.
- Variant publication uses temporary and backup folders. If publication fails, previously published variants must be restored.
- If a newly imported parent cannot complete variant restoration, the importer must attempt to remove that new parent. A rollback failure must be reported explicitly and never hidden.

## Validated Integrity Scenarios
- Existing parent + targeted shared variant: the parent remains unchanged and the variant is restored.
- Missing parent + targeted shared variant: the parent is normalized, then the variant is restored.
- Existing variant with the same `songId` and same parent: it is updated without duplication.
- Existing variant with the same `songId` but another `sourceSongId`: restoration is refused explicitly.
- Archive without optional variant lyrics or chords: existing local variant assets are preserved.
- Complete backup/restore: parents, variants, playlists, internal playlist groups, order, and references are rebuilt without requiring playback.
- Parent and variant remain distinct playlist targets; removing one playlist occurrence does not remove the other.
- These engine rules are common to phone and tablet.

## Device Transfer Rules
- Phone -> tablet transfer is a song-unit transfer.
- The archive must contain enough information to rebuild the same song unit on the target device.
- After import on another device, the song should remain coherent even if absolute paths and URIs change.

## Coexistence Rules
- The normalized SMP system and remaining legacy compatibility flows coexist.
- No big-bang rewrite.
- Legacy file-based import flows must keep working through adapters.
- New code must use an adapter layer:
  - legacy raw files -> normalized song unit
  - `.smp` import -> normalized song unit
- New persistent features must be added to the normalized model and transport without reviving file-name or URI identity.

## Performance Rules
- Do not slow down live playback for `.smp`.
- Do not add zip reads on the hot path.
- Do not turn every player/search action into a full archive parse.
- Any normalization or extraction should happen at import time or in background preparation steps.

## Compatibility Rules
- The current beta must remain stable.
- Any `.smp` evolution must be incremental, backward compatible, and reversible where possible.
- Existing screens that are still URI/path-centric must not be broken abruptly.
- Legacy URI/path-keyed stores need bridging instead of abrupt removal.

## High-Risk Current Components For SMP Work
- Storage and import:
  - `MainActivity.kt`
  - `ui/library/LibraryScreen.kt`
  - `ui/MoreScreen.kt`
  - `smp/SmpImporter.kt`
  - `smp/SmpSecureImportPipeline.kt`
  - `smp/SmpWorkspaceArchiveStore.kt`
  - `smp/SmpLibraryScanner.kt`
  - `smp/ArrangementVariantStore.kt`
  - `smp/ArrangementVariantsArchiveCodec.kt`
  - `smp/SmpExporter.kt`
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

## Required Architectural Direction
- Preserve the existing explicit song-level abstraction.
- All code must continue to follow:

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
- If the SongUnit is a variant, is its `sourceSongId` preserved and validated?
- If the archive omits an optional variant asset, is the local asset preserved?
- If a playlist references a variant, is the parent and variant index ready before remapping?
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
- Do not duplicate parent audio for a virtual variant.
- Do not silently reparent a variant.
- Do not replace a local parent during targeted variant import.
- Do not delete local optional variant data merely because an older archive does not transport it.
- Do not restore playlists before parents and variants are visible in the refreshed Library index.

## Short Operational Summary
- `.smp` is transport.
- Internal normalized song storage is runtime.
- The song unit is the canonical object.
- `songId` is absolute identity.
- `sourceSongId` is the immutable parent identity of a virtual variant.
- Variant assets belong to the variant and travel in the parent archive.
- Import/export are format boundaries.
- Complete restore rebuilds SongUnits before playlists.
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
```
