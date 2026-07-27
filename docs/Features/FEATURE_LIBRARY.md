# FEATURE — LIBRARY

CRITICAL — SONG IMPORT AND FILE MANAGEMENT SYSTEM

This document defines the Library system.
The Library is responsible for browsing, importing, normalizing, and managing song-related files.

All changes must preserve data safety, performance, and user trust.

⸻

USER GOAL

The user must be able to:
- browse audio files
- import supported audio files or `.smp` SongUnits into the app
- manage live songs
- select multiple files
- assign songs to playlists
- rename display titles
- share a parent song or one of its virtual Arrangement variants
- keep original files safe

👉 Library = entry point for song preparation

⸻

CORE PRINCIPLE

The Library must never confuse:

- source files
- internal live songs
- playlists

Source files are the user’s original files.

Internal live songs are normalized song units used by the app.

Playlists only reference songs.

👉 Never mix these concepts.

⸻

SOURCE FILES

Source files may come from:
- device storage
- SAF folders
- Music folder
- user-selected workspace
- audio files
- sidecar files (.lrc, etc.)
- embedded lyrics
- Musicolet-like structures

Rules:
- original files must not be modified
- user folder structure must be respected
- no hidden destructive operation

⸻

INTERNAL SONG MODEL

After import, a song must be normalized into the internal model.

Internal song data may include:
- audio
- lyrics
- chords
- Arrangement structure and virtual variants
- timeline data
- metadata
- track settings

Each internal parent or variant SongUnit must be linked by `songId`.

For a virtual Arrangement variant:
- it has its own `songId`, title, runtime folder, Structure, lyrics, and chords
- its immutable `sourceSongId` identifies the parent SongUnit that owns the audio
- it must never duplicate the parent audio

👉 `songId` is the stable identity.

⸻

IMPORT FLOW

Import must follow this flow:

1. User selects source file(s)
2. App resolves readable access
3. App creates or updates internal song unit
4. App normalizes related data
5. App registers songId
6. Song becomes available for playback and playlist assignment

👉 No playback should depend on original external file state.

⸻

VISIBLE IMPORT ENTRY

The common Library action is:

`Importer…`

It is available on phone and tablet and opens a choice between:

- `Importer un morceau audio`
- `Importer un fichier SMP`

Audio import:
- accepts MP3, WAV, FLAC, M4A, AAC, OGG, and any format explicitly supported by the current import pipeline
- converts and normalizes the selected audio through the existing audio-to-SMP pipeline
- does not create a second import implementation

SMP import:
- opens the Android document picker
- reuses the official SMP validation, extraction, and normalization pipeline
- uses `songId`, never the selected file name, as identity

The currently implemented user entry point is the visible Library action and its Android picker. Direct external opening through an Android `ACTION_VIEW` intent is not part of the current validated flow.

⸻

SMP FILES

.smp files are transport packages.

Rules:
- .smp can be imported
- .smp must be extracted before use
- .smp must never be read directly during live playback
- importing a parent archive restores its valid virtual variants after the parent is ready
- old archives without Arrangement or variant data remain compatible

👉 Runtime uses extracted internal data only.

⸻

VIRTUAL ARRANGEMENT VARIANTS

A virtual variant is displayed in the Library as an independent SongUnit even though its audio remains owned by its parent.

User behavior:
- it can be selected, played, edited, assigned to a playlist, and shared
- its lyrics and chords are resolved by the variant `songId`
- editing variant lyrics or chords never modifies the parent
- the parent may exist in the Library without being visible in the same playlist

Sharing:
- the `Partager` action is enabled for a valid variant
- SMP packages the parent audio once and includes only the selected variant
- the selected variant keeps its `songId`, title, Structure, lyrics, chords, and line colors when present
- sharing does not create a WAV and does not modify the parent

Importing a shared variant:
- if the parent exists locally, that parent remains unchanged and only the selected variant is restored
- if the parent is absent, it is normalized first, then the selected variant is restored
- an existing variant with the same `songId` is updated without duplication
- the same variant `songId` attached to another `sourceSongId` is refused explicitly
- optional local variant assets absent from the archive are preserved

⸻

## SMP SYNC — MANUAL SONG TRANSFER

Library songs can be sent manually through SMP Sync.

Rules:
- the user explicitly selects the songs to send
- each selected song is transferred as its real SongUnit data
- `songId` remains the stable identity
- no filename, URI, or visual title may replace `songId` identity
- no automatic deletion is allowed

On the backup phone:
- import is manual and confirmed by the user
- if the same `songId` already exists, the received song data may update the existing runtime SongUnit
- associated data such as `lyrics.lrc`, `chords.lrc`, `config.json`, `arrangement.json`, and timeline files must be imported into normalized runtime storage
- playback must use the refreshed local runtime data, never the transfer package

👉 SMP Sync is a transfer/import workflow, not a live playback dependency.

⸻

SMP RESTORE CONSISTENCY

A full SMP restore must rebuild the usable app state before any playback:

1. import valid .smp files into the official runtime SongUnit storage
2. restore virtual variants from their parent archives
3. refresh/rebuild the Library index from runtime
4. validate restored songIds against that index
5. restore and persist playlists only after validation

Rules:
- restored .smp files must become runtime SongUnits directly
- parents and variants must both be visible in the refreshed index before playlist remapping
- playlist restore must never run against a stale Library index
- playback must not be required to hydrate Library metadata
- invalid transport noise files such as macOS `._*.smp` must be ignored

👉 A restored backup must be immediately usable from the Library.

⸻

TITLE RESOLUTION

No Library SongUnit may expose a display title that is:
- null
- `"null"`
- blank

Title fallback order:
1. display title / app alias
2. title from runtime metadata
3. audio metadata when available
4. archive or file name
5. localized placeholder

👉 The Library must never show `null` to the user.

⸻

FILE BROWSING

The Library may provide file browsing.

Rules:
- browsing must be clear
- virtual folders must be handled safely
- SAF URIs must not be treated like normal file paths
- invalid URI assumptions must be avoided

👉 Never call file APIs blindly on virtual or custom URIs.

⸻

SAF SAFETY

When using Android SAF:

Rules:
- SAF operations may be slow
- avoid heavy SAF work on main thread
- persist permissions when required
- handle missing permissions gracefully

👉 SAF must never freeze the live app.

⸻

FIRST LAUNCH / PERMISSIONS

First launch must ask only for what is strictly needed to create the usable Library workspace.

Official first-launch workflow:
- SMP shows one simple welcome screen
- the only SMP action is `Continue`
- SMP opens the Android SAF folder picker directly, initially positioned on the Music folder when Android allows it
- the user either confirms that folder or navigates to another folder
- Android asks for the SAF authorization
- after returning to SMP, the Demo Library is installed automatically
- SMP keeps the progress/loading state visible until the workspace is confirmed ready
- SMP then opens the Demo Library / playlist directly

Rules:
- avoid duplicate permission prompts between SMP UI and Android system popups
- the setup flow starts with a welcome screen, then immediately opens the SAF work folder selection
- SMP may explain that Android will ask the user to confirm `Use this folder`, but it must not add another setup decision
- Android media permissions must not be requested during first launch if the SAF workspace flow is enough
- the demo library is installed automatically after the SAF workspace is authorized
- once Demo installation starts, the welcome screen must never reappear during final workspace verification
- the user must not see a transient Player or main-screen flash before the Demo destination
- the orange development `Debug` action is not part of the normal first-launch UI
- importing personal music is a normal Library action, not a first-launch installation step

Removed first-launch decisions:
- no `Use Music folder` choice inside SMP
- no `Choose my folder` choice inside SMP
- no `Install demo` choice
- no `Import my music` choice

Deferred permissions:
- DJ must not request audio permission or a DJ folder until the user opens or uses DJ features
- filler / ambience sound must not request a folder until the user selects a custom source
- tuner / microphone must request `RECORD_AUDIO` only when the tuner or mini-tuner is used
- backup / restore must open SAF pickers only when the user starts the action

👉 Permissions belong to the moment of use, not to app installation.

⸻

SCAN / RESCAN

Library scanning must be safe and predictable.

Rules:
- scan must not block UI
- scan must not modify user files
- rescan should refresh visible data only
- avoid repeated expensive full scans

Instant startup rule:
- after a successful scan, the last known Library song list must be kept in persistent local cache
- on app restart, known songs must be displayed from cache as soon as possible
- if cached songs are available, a scan must run in the background and must never temporarily replace the visible Library with an empty list
- access to already known songs has priority over perfect scan freshness in live conditions
- if no cache or usable data exists yet, the UI must show clear loading feedback instead of appearing empty or frozen
- when a scan completes with no songs, the UI must show a real empty state, not an infinite loader

Live UX rule:
- in concert, the user must always understand whether the app is showing cached content, updating in background, loading for the first time, or truly empty
- no heavy scan or cache rebuild may run on the main thread

⸻

TABLET SPLIT LIBRARY

In tablet split mode, the Library may be shown as the right pane:

Playlist | Library

Rules:
- the Playlist remains visible on the left
- the Library remains a right-pane destination, not a full replacement of the live cockpit
- the cockpit menu must remain reachable from the Library
- the user must be able to return quickly to `Playlist | Lyrics`
- phone Library behavior remains the default and must not inherit tablet split assumptions

Compact tablet header:
- in compact tablet Library mode, the visible title `Library` and its subtitle are removed
- the mode controls are grouped on one line:
  - Songs
  - Lists
  - Lyrics
  - LUFS
  - storage/folder action
  - Library actions menu
  - cockpit menu
- the goal is to recover vertical space so more songs remain visible during live use
- the song rows are not reduced for now because a previous row-compaction attempt caused runtime instability
- future row compaction must be treated as a separate high-risk UI patch and validated on real tablet and phone

Library row actions:
- tapping the main row/title launches the song through the normal Player flow
- in tablet split mode, tapping the main row/title must return to `Playlist | Lyrics`
- tapping the row Play button is a local preview/control and must not force navigation away from the Library
- this separation keeps live navigation predictable while preserving quick audio checks

Search in tablet mode:
- Search is not an autonomous tablet destination
- Search is a filter mode for the active screen or pane
- Library + Search means filtering the Library list
- Playlist + Search means filtering the Playlist list
- opening Library alone must not automatically open Search or the keyboard
- phone search behavior remains unchanged

⸻

MULTI-SELECTION

User must be able to:
- select multiple files or songs
- import as batch
- assign to playlist
- move/copy/delete when allowed

Rules:
- order must be preserved when useful
- destructive operations must be confirmed
- multi-selection delete from live songs must reuse the normal runtime SongUnit deletion pipeline

⸻

RENAME BEHAVIOR

Renaming in the app must not rename original audio files by default.

Rules:
- display title can be changed
- original file remains untouched
- songId remains stable

👉 Rename = metadata/display operation unless explicitly stated otherwise.

⸻

DELETE BEHAVIOR

Delete actions must be explicit.

Possible delete targets:
- remove from playlist
- delete internal song data
- delete source file

Rules:
- these actions must never be confused
- destructive delete requires confirmation
- app must clearly explain what will be deleted
- deleting live songs from the Library removes active runtime SongUnits and playlist references only
- external `.smp` backup/export archives must never be deleted automatically by Library runtime deletion
- deleting a parent SongUnit also deletes all of its virtual variants and their playlist references after confirmation of the cascade
- deleting one virtual variant never deletes its parent or sibling variants
- removing a parent occurrence from a playlist does not delete or remove a variant occurrence

⸻

PLAYLIST ASSIGNMENT

The Library can assign songs to playlists.

Rules:
- playlist stores songId references only
- no audio duplication inside playlists
- assignment must preserve selected order
- a virtual variant is assigned using its own `songId`
- assigning a variant does not require assigning its parent to the same playlist
- the parent must still exist in normalized Library storage

⸻

SONG FAMILIES

The Library can group several existing SongUnits into a family.

Purpose:
- reduce visual duplication in the Library
- prepare live choices without changing the runtime song model
- keep each song/version as a real independent SongUnit

Creation:
- user multi-selects songs in the Library
- action: create family
- the first selected song gives the family name by default

Display:
- a family is shown as one compact collapsible block
- collapsed: only the active/current choice is visible
- expanded: the other choices appear as compact secondary lines
- no "Original" role is imposed

Examples:
- one song with versions: normal, short, acoustic, -1 tone
- one live moment with possible choices:
  Flamenco
  Bamboleo
  Volare Gipsy
  Historia de un amor

Rules:
- family = Library/Playlist UX grouping only
- no audio duplication
- no SongUnit merge
- selecting a choice always targets the real songId

⸻

LUFS PREPARATION PREVIEW

Detailed contract: `FEATURE_LUFS_PREPARATION.md`.

The Library LUFS tab provides an isolated preview tool for preparation and rehearsals.

Current naming note:
- the UI keeps the LUFS wording for compatibility and simplicity
- SMP V1 level preparation is based on waveform level estimation / normalization
- it is not a broadcast-compliant ITU-R BS.1770 / EBU R128 LUFS implementation
- the product goal is live-perceived consistency between songs

Purpose:
- compare the real perceived levels of songs quickly
- avoid waiting through long intros when checking LUFS preparation
- stay inside the Library preparation workflow
- calibrate playlists before concert by reaching representative passages quickly

Scope:
- this feature exists only in Library -> LUFS
- it reuses the Library LUFS preview player
- it is not part of the live Player pipeline

UX:
- simple tap on Play starts preview from 0s
- long press on Play opens a lightweight start-offset menu
- available start offsets are 20s, 40s, 60s, and 90s
- choosing an offset starts preview directly at that position
- manual `+1` / `-1` corrections are audible immediately during preview or current-song playback
- manual gain is saved per song

Rules:
- no impact on Player live
- no impact on timeline
- no impact on Define Next
- no impact on autoplay
- no impact on crossfade
- no impact on anti-blank / anti-silence behavior
- no new live audio engine behavior
- no playlist behavior change
- no heavy level analysis during live playback

👉 LUFS preview offsets are a Library preparation aid only.
👉 This page also acts as a playlist calibration workshop before concert.

⸻

PERFORMANCE RULES

Forbidden on main thread:
- large folder scans
- SAF recursive traversal
- audio metadata extraction
- SMP extraction
- waveform generation

Required:
- background processing
- progress feedback when needed
- cancellation or safe failure where possible

⸻

DATA SAFETY

The Library must protect user data.

Rules:
- never overwrite without explicit intent
- never silently delete
- never corrupt internal song units
- never break existing playlists during import/rescan

⸻

UI REQUIREMENTS

The Library UI must be:
- simple
- readable
- predictable
- close to a file manager when browsing files
- close to a song manager when browsing live songs

Must clearly distinguish:
- source files
- live songs
- playlists

⸻

KNOWN PITFALLS

- treating SAF URI as File path
- hiding valid files because of strict filters
- showing empty folders because virtual folders are misunderstood
- breaking playlists by changing songId
- resolving a variant by title instead of `songId`
- silently changing a variant `sourceSongId`
- replacing a local parent during targeted variant import
- deleting local variant lyrics or chords because an older archive does not contain them
- restoring playlists before variants are registered in the Library index
- modifying original files accidentally
- doing heavy I/O on main thread

⸻

TEST SCENARIOS (MANDATORY)

Test at least:

- browse device audio files
- import one MP3
- import one WAV or another supported audio format
- import MP3 with .lrc sidecar
- open `Importer…` and verify both Audio and SMP choices on phone and tablet
- import a parent `.smp`
- share and reimport a variant when its parent already exists
- share and reimport a variant after deleting its parent
- update an existing variant without duplication
- refuse a variant `songId` already attached to another parent
- preserve local variant lyrics/chords when absent from an older archive
- rename display title
- assign a parent and a variant independently to a playlist
- multi-select import
- rescan library
- missing permission case
- source file moved/deleted after import
- app restart after import
- delete a parent and confirm the variant/playlist cascade
- delete one variant and verify that its parent remains
- validate first launch on phone and tablet with no welcome or Player flash after Demo installation

👉 Must be tested on real device when possible.

⸻

FINAL RULE

The Library must ALWAYS protect:
- user files
- internal song identity
- playlist consistency
- live performance stability

If a change introduces:
- data loss
- unsafe file handling
- UI freeze
- broken song references

👉 It must be rejected.
