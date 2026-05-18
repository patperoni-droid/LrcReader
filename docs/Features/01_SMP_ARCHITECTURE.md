# SMP ARCHITECTURE — Stage Music Player

CRITICAL — MUST BE UNDERSTOOD BEFORE ANY STRUCTURAL CHANGE

This document defines the core architecture of Stage Music Player.
All features must comply with this model.

⸻

CORE VISION

A song is NOT just an audio file.

A song is a complete unit containing:
- audio
- lyrics
- chords
- timeline data (MIDI, DMX, notes)
- metadata
- user adjustments (gain, pitch, tempo, etc.)

👉 One song = one structured entity

⸻

THE SMP MODEL

The .smp file is a TRANSPORT FORMAT only.

- It is a container (zip)
- It is NEVER used directly during live playback

Its purpose is:
- import / export
- backup
- transfer between devices

⸻

RUNTIME MODEL (CRITICAL)

At runtime, the application NEVER works on .smp files.

Instead:

👉 Every song is extracted and normalized into internal storage

Example:

/files/tracks/{songId}/
audio.mp3
lyrics.lrc
chords.lrc
midi.json
dmx.json
config.json

👉 This is the ONLY source used during playback

⸻

SONG IDENTIFICATION

Each song is identified by a unique ID:

songId

Used for:
- playback
- playlists
- settings
- linking all related data

👉 NEVER rely on filenames as identifiers

⸻

INTERNAL DATA PRINCIPLE

All inputs must be normalized into the internal model.

Supported sources:
- raw mp3
- mp3 with embedded lyrics
- .lrc files
- Musicolet imports
- .smp packages

👉 After import:
Everything becomes a unified internal structure

⸻

PLAYBACK MODEL

Playback must ALWAYS use:

- local files (no remote, no zip)
- preloaded or ready data
- ExoPlayer as the timing reference

👉 The timeline system depends on this stability

⸻

DATA FLOW (IMPORTANT)

Import →
Normalize →
Store internally →
Play →
Edit →
Export

At no point should the system bypass normalization.

⸻

EDITING PRINCIPLE

All editing operations:
- modify internal data only
- NEVER alter original source files

Examples:
- renaming a song → UI only
- gain adjustment → stored in prefs/config
- trim (IN/OUT) → stored as metadata

⸻

PERSISTENCE

All song-related data must:
- be stored per songId
- survive app restart
- remain consistent across features

👉 No duplicated storage logic allowed

Restore consistency:
- complete restore must rebuild runtime SongUnits, the Library index, and playlists before playback
- restored .smp packages must be imported into `/files/tracks/{songId}/`
- playlists may be persisted only after the refreshed Library index contains the referenced songIds
- playback must not be used as a metadata repair step

Title consistency:
- runtime SongUnits must never expose null, `"null"`, or blank display titles
- title fallback must end in a localized placeholder if no metadata is available

⸻

PLAYLIST MODEL

Playlists do NOT store files.

They store:
- references to songId
- ordering
- grouping metadata

👉 Playlist = structure, not data

⸻

TIMELINE INTEGRATION

Timeline elements (MIDI, DMX, notes):
- are tied to songId
- are time-based (milliseconds)
- are independent from UI (lyrics/chords)

👉 Timeline = universal sync layer

⸻

PERFORMANCE CONSTRAINTS (CRITICAL)

- No heavy computation during playback
- No file parsing during playback
- No structure rebuilding during playback

👉 Everything must be prepared BEFORE play

⸻

MULTI-DEVICE PRINCIPLE (FUTURE)

The system must support:

Phone → Tablet transfer

This requires:
- clean packaging (.smp)
- full reconstruction from package
- no hidden dependencies

⸻

FORBIDDEN PATTERNS

- Reading directly from .smp zip during playback
- Using filenames as IDs
- Duplicating song data across multiple locations
- Mixing UI state with persistent data
- Building structures dynamically during playback

⸻

FINAL PRINCIPLE

If a feature:
- breaks the internal model
- bypasses normalization
- or introduces runtime instability

👉 It must be rejected.
