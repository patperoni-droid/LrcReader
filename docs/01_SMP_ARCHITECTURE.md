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

Level metadata:
- SMP V1 currently keeps the UI LUFS wording for compatibility
- this is waveform-based level estimation / normalization, not a broadcast-compliant ITU-R BS.1770 / EBU R128 LUFS implementation
- automatic SMP level data and manual gain corrections are song metadata
- the effective live gain must be stored per songId and applied from normalized internal storage
- live playback applies prepared gain values only; it must not perform heavy level analysis

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

Song families are a UX layer over existing SongUnits:
- each choice remains an independent SongUnit with its own songId
- a playlist family resolves to the currently active real songId before playback
- there is no audio merge, no runtime duplication, and no SongUnit model change

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

Field-validated device roles:

Phone:
- preparation
- editing
- rehearsal
- backup / emergency device
- mobility

Tablet:
- main stage device
- wider live overview
- playlist visible alongside Player/lyrics
- comfortable reading during performance

Recommended workflow:
- prepare on phone
- periodically sync to tablet
- use tablet as the main live cockpit

Architecture implication:
- sync must produce complete local runtime data on the tablet
- live playback must never depend on the phone being present
- the tablet must be able to run the show from normalized internal storage only

Tablet split navigation rule:
- tablet stage mode is based on a Split Layout, not on two independent screens
- left pane is the fixed Playlist pane
- right pane hosts the active live destination
- the Playlist is not a destination screen in this mode; it is the stable left part of the Split Layout
- the default right-pane destination is Lyrics
- in tablet split mode, `Lyrics` always means the scrolling playback lyrics: `Playlist | Lyrics`
- `Lyrics` never means the lyrics editor; the editor is a secondary right-pane destination
- Lyrics is the home state of the right pane
- compatible right-pane destinations may include Lyrics, Library, Track Console, Settings, Background Sound, DJ, Main Bus, Tuner, Lyrics Editor, and future tablet-safe panels
- the cockpit menu is the preferred navigation mechanism between right-pane destinations
- every compatible right-pane destination must avoid trapping the user and must provide a simple visible path back to `Playlist | Lyrics`
- some tools may leave the Split Layout and use a Fullscreen mode when they need the full tablet width
- Arrangement, Timeline, and future wide editing tools may use Fullscreen instead of the Split Layout
- the tablet Arrangement fullscreen tool may expose the Playlist as an internal collapsible left panel without leaving Arrangement
- opening or closing this panel changes only the visible Arrangement viewport; it must not change playback, track identity, segment order, zoom, or persisted Arrangement data
- selecting track B in this panel while track A is active keeps Arrangement A displayed and makes the official Play control yellow
- Arrangement B becomes authoritative only after the yellow Play action launches B through the standard Playback pipeline
- fullscreen tools must return explicitly to the live cockpit without changing playback state
- phone UX remains the default behavior and must not inherit tablet split assumptions
- on a fresh tablet install, tablet split mode is enabled automatically after tablet detection
- existing tablet split preferences are never overwritten; if the user has already enabled or disabled the mode, that saved choice wins

Tablet split roadmap:
- study an option to invert the split, with Playlist on the left or on the right
- field motivation: a right-handed musician with the tablet on the right may reach the playlist faster without releasing the guitar chord or losing sustain

Phone mode protection rule:
- the phone remains the SMP reference platform
- tablet mode is an interface extension and must never degrade phone behavior
- every tablet-related change must be explicitly guarded by tablet mode or Split Layout conditions
- existing phone behavior must remain unchanged unless a task explicitly asks to change it
- before committing a tablet navigation change, validate compilation, tablet behavior, and phone behavior
- minimum phone checks: Library opening, Search opening, Settings opening, return to Player, and main navigation
- if phone impact is uncertain, stop the patch, diagnose, and request validation
- principle: Phone = stable base; Tablet = extension

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
