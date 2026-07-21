# FEATURE — PLAYER (Audio + Lyrics + Chords)

CRITICAL — CORE RUNTIME COMPONENT

This document describes the Player system.
It is the central runtime component used during live performance.

All changes must preserve:
- stability
- timing accuracy
- readability
- predictability
- user control

Stability always has priority over feature richness.

⸻

# USER GOAL

The user must be able to:

- launch a song instantly
- see synchronized lyrics or chords
- control playback safely during live
- prepare the next track without stress
- improvise naturally during performance
- maintain a smooth and professional flow

The Player must feel:
- immediate
- reliable
- readable
- musical

⸻

# CORE RESPONSIBILITIES

The Player is responsible for:

- Audio playback (ExoPlayer)
- Timing reference (single source of truth)
- Lyrics / Chords synchronization
- Timeline event dispatch (MIDI / DMX)
- Playback transitions
- Define Next / Auto Play
- Live chaining
- Track-specific settings
- Lyrics readability

👉 The Player drives the entire live experience.

⸻

# ARCHITECTURE OVERVIEW

Main components:

- ExoPlayer (Media3)
- PlayerScreen (UI)
- MainActivity (state + player bridge)
- Timeline dispatcher (MIDI / DMX)
- PlaybackCoordinator
- Track settings stores

Additional isolated players may exist for:
- Arrangement preview
- Structure preview
- Editing tools

BUT:
- the live Player remains the main runtime authority

Tablet Arrangement transport routing:

- the official `Playback Control` remains the only visible transport bar;
- by default it controls the complete active song;
- selecting a block on the tablet Arrangement track temporarily targets that segment preview;
- Pause fully stops the targeted segment preview and Play restarts it from the segment beginning;
- touching the source waveform stops the targeted segment preview, releases the segment target, seeks the complete song to the touched position and keeps it paused until Play is pressed;
- Return to beginning stops the secondary preview, restores the complete song as transport target and seeks it to `00:00`;
- the main Player and an Arrangement segment preview must never play at the same time;
- this routing is tablet-only and does not change the phone Arrangement editor.

⸻

# PLAYBACK MODEL

- Playback MUST use ExoPlayer only
- currentPosition is the global time reference

👉 All systems depend on this:
- lyrics
- chords
- MIDI
- DMX
- transitions
- pre-end behavior

FORBIDDEN:
- secondary timing systems
- UI-based timing
- line-index-based timing

⸻

# PLAYBACK FLOW

1. User selects a track
2. Player resolves songId data
3. Local audio source is resolved
4. ExoPlayer prepares media
5. Playback starts

During playback:
- currentPosition is continuously read
- lyrics/chords update
- timeline events dispatch
- transitions remain synchronized

⸻

# PLAYBACK / BACKGROUND SOUND PRIORITY

Playback is always prioritary over Background Sound.

When a song starts:
- Background Sound must stop automatically

When a song stops:
- Background Sound may resume automatically only if this option is enabled

No unintended mix between Playback and Background Sound must be possible.

⸻

# PLAYER OPENING BEHAVIOR

The Player opening behavior is user-configurable.

Modes:

- Always
  - Player always opens

- Never
  - Playback starts without opening Player

- Automatic
  - Player opens only if the track requires visual playback UI
  - (lyrics, chords, player-dependent content)

Rules:
- must never delay playback
- must never block audio start
- must never break lyrics loading
- “Always” remains the safest fallback

If uncertainty exists:
👉 prefer preserving playback + lyrics visibility

⸻

# LYRICS & CHORDS

Lyrics and chords are independent layers.

Both are:
- time-based
- synchronized using timestamps
- dynamically switchable

Supported modes:
- Lyrics mode
- Chords mode

Switching must be instant.
No reload allowed.

Tablet split UI:
- the former dedicated `Lyrics / Chords` tab row is not shown on tablet split, to preserve vertical reading space
- the active display mode is controlled from the Player/Audio toolbar
- toolbar controls are explicit and localized:
  - `🎤 Lyrics / Paroles / Letras`
  - `🎸 Chords / Accords / Acordes`
- the active mode must be visually obvious through SMP color, background, and border treatment
- this toolbar-based model must remain extensible for a future simultaneous `Lyrics + Chords` display mode

Phone UI:
- existing phone Lyrics / Chords behavior remains unchanged

⸻

# SONG EDITOR SAVE

The unified song editor must preserve user work across its permanent tabs:
- Lyrics
- Chords
- Sync

Covered changes:
- lyric text
- lyric timestamps
- line colors
- chord text
- chord timestamps
- LRC import results
- line edit/delete operations

Rules:
- leaving the editor must not lose work
- switching tabs is not leaving the editor and must not block on full persistence
- the visible Save action saves the current editor session at song scope
- auto-save must run only after real changes
- pending changes must be flushed before returning to Player when needed
- lyrics writes must be serialized and atomic when using runtime files
- editor exit must wait for the final editor-session save before returning to Player
- lyrics and chords must use their official persistence paths and must never overwrite each other
- priority: never lose user song-editor work

⸻

# LYRICS PERSISTENCE PIPELINE

Runtime lyrics data is split by responsibility:

- `lyrics.lrc`
  - lyric text
  - timestamps

- SongUnit `config.json`
  - visual lyrics metadata
  - line colors

Rules:
- `songId` is the preferred stable identity
- never depend only on `file://audio.mp3` when `songId` is available
- never inject line colors into standard LRC text
- do not create a second competing lyrics store
- import/export LRC compatibility must remain intact

⸻

# LRC RUNTIME ROBUSTNESS

The Player must tolerate damaged LRC content.

Rules:
- parse LRC line by line and log invalid lines
- one invalid LRC line must never block the whole song
- runtime playback may ignore unsynchronized or invalid timestamp lines
- runtime playback may sort valid synchronized lines by `timestampMs`
- never silently rewrite or delete the original LRC file during runtime cleanup
- editor/import/export behavior must remain compatible with standard LRC files

⸻

# LYRICS READABILITY

The Player includes advanced readability behavior for live performance.

Purpose:
- improve readability during concerts
- support outdoor/daylight conditions
- reduce cognitive load while singing
- help anticipation of upcoming lyrics

⸻

## NORMAL MODE

In normal mode:

- current lyric line:
  - strongly highlighted
  - visually dominant

- next lyric line:
  - softly highlighted
  - lower intensity
  - anticipation aid for singers

- other lines:
  - reduced emphasis

Reason:
During live singing, performers naturally read the next line while singing the current one.

Rules:
- optional Guided Reading Colors may alternate lyric lines at runtime for live visual tracking
- manual lyric line colors always override guided alternating colors
- guided colors are UI/runtime only and must never rewrite LRC files
- global Lyrics Size controls Player lyric font size for device, tablet, and stage-distance differences
- Lyrics Size V1 is global only; no per-song lyric size override yet
- very large lyric sizes may still hit current fixed 3-line rendering limits
- future improvement may use dynamic wrapping / dynamic lyric row height
- next-line emphasis must remain softer than active line
- active line changes may use a light fade transition around 160 ms to soften visual jumps
- this fade must not affect lyrics timing, scrolling, or audio playback
- prompter visual stability has priority over decorative emphasis differences
- live readability remains the priority
- must not create visual overload
- must not affect timing logic
- must not affect scrolling

⸻

## READABILITY MODE (“ALL LINES ILLUMINATED”)

The Player includes a readability mode for difficult visibility situations.

Purpose:
- outdoor sunlight
- bright stage conditions
- distant reading situations

Behavior:
- all lyric lines remain fully readable
- active line remains identifiable through:
  - highlight color
  - font weight
  - slightly larger size

IMPORTANT:
When readability mode is enabled:
- keep existing behavior unchanged
- do NOT apply special next-line anticipation styling

Rules:
- must not affect synchronization
- must not affect timing
- must not affect audio playback
- state must persist across app sessions

UI:
- controlled from top-left Player header icon
- toggle must be immediate and reliable

⸻

# LYRIC LINE COLORS

Lyrics lines may optionally have display colors.

Purpose:
- visual structure markers
- spoken sections
- warnings
- audience interaction cues
- live navigation assistance

Rules:
- colors must remain readable
- default/no color must remain supported
- color persistence required after save/reopen
- color persistence required after song change
- color persistence required after app restart
- must not corrupt standard LRC compatibility
- metadata must remain separate from raw timing text
- read and write must use the same stable key
- SongUnit metadata is the expected runtime location

⸻

# LYRIC SYNC SELECTION UX

Inside lyric sync editor:

- long press on unselected line:
  - selects line

- long press on selected line:
  - deselects line

Rules:
- tap behavior unchanged
- edit/delete/color dialog preserved
- edit dialog must stay comfortable on phone
- color choices must use a compact palette
- touch workflow must remain direct
- no playback interruption allowed

⸻

# EDITOR → PLAYER SYNCHRONIZATION

After editing lyrics, returning to Player must show the latest data immediately.

Rules:
- flush pending auto-save before leaving editor when needed
- invalidate lyrics cache after text/timestamp/color changes
- Player must not render stale cached lyrics
- current song reload must preserve `songId` identity
- do not modify AudioEngine to fix lyrics/editor behavior

⸻

# TABLET LYRICS EDITING FOCUS

Tablet split mode may reduce the available vertical space when the Android keyboard is open.

In tablet split only, the lyrics editor may enter a focus editing mode while the user edits lyric text:
- the Playlist remains visible on the left
- the right pane keeps the lyrics editor active
- the SMP top bar may be temporarily hidden
- the `Paroles / Synchro` tabs may be temporarily hidden
- secondary controls such as `Afficher les timings` may be temporarily hidden
- the text editor and lyric list remain available

Focus mode rules:
- phone behavior must remain unchanged
- the editor must stay open until the user explicitly validates, cancels, or exits editing
- closing the Android keyboard restores the tablet editor chrome
- restoring the chrome must not close the editor
- user text must not be lost when focus mode changes
- save/close request tokens must be consumed once and must not replay after recomposition
- Synchro behavior must not regress while fixing the text edit tab

Purpose:
give the performer enough usable text-editing space on tablet without changing the stable phone editor.

⸻

# TIMELINE INTEGRATION

The Player drives timeline execution:

- MIDI cues
- DMX cues
- markers
- future runtime events

Rules:
- time-based only
- deterministic triggering
- independent from UI rendering

👉 The Player is the clock.

⸻

# TRACK SETTINGS (CRITICAL)

Each track may contain:

- gain
- pitch
- speed
- trim IN / OUT

Rules:
- stored per songId when available
- persistent
- applied at playback
- original files must never be modified
- legacy URI/path keys may exist but must not override stable song identity

Level workflow note:
- the current UI term LUFS refers to SMP V1 level preparation, not broadcast-compliant ITU-R BS.1770 / EBU R128 LUFS
- `Appliquer LUFS` prepares an automatic SMP level correction before live use
- manual gain corrections are stored per song and must be audible immediately on the active track
- Track Console must reflect the level actually applied to the current playback
- Player must apply prepared gain values only; it must not run heavy level analysis during live playback

Live gain controls:
- phone and tablet use the same business logic for per-song gain
- both paths must use the existing `currentTrackGainDb` / `onLiveGainDelta(...)` workflow
- both paths must persist through the existing per-song gain storage
- neither phone nor tablet may create a separate gain model

Tablet UX:
- tablet split Player may show a permanent live gain fader in the right pane
- the fader remains directly visible because the playlist is already separated in the split layout

Phone UX:
- phone Player may show a right-side slide-out gain drawer
- the drawer is closed by default
- a small right-edge handle/arrow opens or closes it
- closed state must keep lyrics readable and must not cover the main reading area
- open state contains the same vertical gain fader behavior as tablet where possible
- the drawer may remain open across song changes; the displayed value must update to the newly selected song's stored gain

Common rules:
- changing the fader stores the gain for the current song
- changing song must refresh the fader to the new song's stored gain
- current active range is `-24 dB` to `+6 dB`
- a visible future reserve may show `+6 dB` to `+12 dB`, but that zone is inactive until a future explicit boost feature exists

Stable gain pipeline:
- crossing around `0 dB` during active playback must never rebuild or replace the active Player
- neutral and negative gain use the regular ExoPlayer volume path
- positive gain uses the lightweight gain stage installed when the Player is created
- positive gain alone must never activate SoundTouch
- SoundTouch pipeline selection remains reserved for non-neutral pitch or speed
- the neutral path must never expose an input buffer still owned by ExoPlayer
- neutral sound, pitch, speed and repeated `-1 / 0 / +1 dB` crossings were validated on a real tablet

⸻

# TRANSITIONS

Supported transitions:

- Play
- Define Next
- Auto Play
- Crossfade
- Live transition fade

Rules:
- transitions must remain predictable
- no glitch allowed
- no timing drift allowed
- next track must immediately become active timeline source
- old track must stop driving lyrics/timeline after handoff

## Pitch/Speed Transition Guard (Live Critical)

When the current track OR the next track uses non-neutral pitch/speed:
- pitch != 0 semitone
- or speed != 1.0

The Player must NOT use double-player crossfade or audio overlap.

Required behavior:
- use a live-safe sequential transition
- apply a short fade-out
- stop, clear, and release/cleanup the old active player when needed
- reset controlled volume/gain/LUFS state before the next start
- apply playback parameters before `prepare()` / `play()`
- launch the next track through the standard Player pipeline

The standard pipeline must still apply:
- gain
- LUFS-derived gain
- pitch
- speed
- timeline handoff
- lyrics handoff

Rules:
- normal -> normal may keep the existing crossfade
- pitch/speed involved -> sequential transition only
- after sequential handoff, only one live audio player may remain audible
- the next track must become the only authoritative source for audio, lyrics, chords, and timeline

Reason:
Double-player transitions with pitch/speed can cause inherited playback parameters, stale gain/LUFS state, ghost players, old-track restart, or audible relaunch.

## Track Gain Pipeline Guard (Live Critical)

Saved per-track gain must be applied to the selected track, not to the previously playing track.

Rules:
- selecting a new track must launch it on the first tap
- the saved gain of the new track must be loaded before playback handoff
- positive, neutral and negative gain must use the gain-capable Player created before playback
- gain alone must never require a Player pipeline rebuild
- crossing `0 dB` must remain a lightweight gain update
- negative gain behavior must remain stable
- phone behavior and tablet behavior must share the same playback safety rules

Reason:
Pitch or speed may still require the dedicated SoundTouch pipeline, but gain no longer selects that pipeline. Keeping the gain stage ready from Player creation prevents stale instances, relaunches and audible interruptions around `0 dB`.

⸻

# LIVE PLAYLIST HANDOFF (CRITICAL)

The Player must preserve nextTrack integrity.

Rules:
- nextTrack must always resolve correctly
- no null next title
- autoplay survives recomposition
- playlist UI must never become playback truth
- live chaining must survive reorder/group operations

👉 Live continuity has priority over UI optimization.

⸻

# LIVE LIST / TEMPORARY QUEUE

The Player can interact with temporary live queues.

Purpose:
- prepare short live sequences dynamically
- improvise safely during performance
- maintain visible next-track continuity

Behavior:
If track A is playing and user adds B:
- A remains current
- B becomes effective next track
- B must be clearly visible as next
- B must autoplay after A

Rules:
- must not override manual Define Next
- must not create ghost nextTrack states
- must remain deterministic after adding C/D/etc.

⸻

# PRE-END RETURN (-10s)

Optional behavior:
- automatically returns to playlist screen
- around 10 seconds before track end

Purpose:
- prepare next transition
- reduce stress
- maintain live control

Rules:
- playback must continue uninterrupted
- timing must remain ExoPlayer-based
- must trigger once only
- feature must remain configurable

👉 UI/navigation behavior only.

⸻

# DEFINE NEXT (LIVE CRITICAL)

User can define the next track during playback.

Requirements:
- selected next track must be obvious
- autoplay handoff must be deterministic
- no ambiguity allowed

⸻

# AUTO PLAY

When enabled:
- tracks follow playlist order automatically

Must support:
- groups
- reorder
- live chaining

Behavior must remain deterministic.

⸻

# SEEK BEHAVIOR

Seek is allowed ONLY for navigation.

FORBIDDEN:
- loop engine based on seek
- arrangement runtime based on seek
- unstable seek-loop hacks

Seek must remain safe and predictable.

⸻

# SEARCH EXIT AFTER TRACK LAUNCH

When launching a track from search:
- search UI must close immediately

Purpose:
- avoid stale live search states
- restore playlist visibility
- preserve -10s workflow

Rules:
- must not affect playback
- must not affect Define Next
- must not clear playlist state

⸻

# UI REQUIREMENTS

The Player UI must remain:
- readable
- minimal
- stage-safe
- fast

Must provide:
- Play / Pause
- Next / Previous
- Define Next feedback
- Lyrics / Chords switch
- readability mode

FORBIDDEN:
- visual overload
- unstable animations
- clutter during live

⸻

# PERFORMANCE RULES

- lightweight recomposition only
- no blocking operations
- no heavy work during playback
- no unnecessary allocations

👉 Playback fluidity is critical.

⸻

# STATE MANAGEMENT

Player states must remain explicit:

- loading
- playing
- paused
- stopped

Rules:
- no hidden transitions
- no ambiguous playback states
- no UI-only playback truth

⸻

# ERROR HANDLING

Playback must never crash the live experience.

In case of issue:
- fail gracefully
- preserve UI responsiveness
- preserve playback if possible

⸻

# LYRICS DIAGNOSTICS

Useful temporary tags:

- `LYRICS_PIPELINE_TRACE`
- `LYRICS_COLOR_SAVE_DIAG`

Rules:
- diagnostics may be used to validate editor → save → Player reload
- logs must be reduced once stability is confirmed
- diagnostics must not become playback logic

⸻

# KNOWN PITFALLS

- timing desync not based on ExoPlayer
- unstable audio pipeline
- heavy Compose recompositions
- trim misalignment
- lost nextTrack after recomposition
- “Next: null”
- playlist UI becoming playback truth
- old timeline still active after transition
- pitch/speed transitions leaving a promoted/transition player alive after handoff
- autoplay breaking after reorder/group changes
- stale lyrics cache after editor changes
- lyrics text saved without matching color metadata
- line colors keyed differently between save and load
- relying only on `file://audio.mp3` when `songId` exists
- breaking Chords editor while fixing Lyrics editor

👉 All must be tested carefully.

⸻

# TEST SCENARIOS (MANDATORY)

Test at least:

- single-track playback
- Lyrics ↔ Chords switch during playback
- readability mode toggle during playback
- next-line anticipation rendering
- Define Next during playback
- Auto Play sequences
- Crossfade transitions
- Live transition fade
- nextTrack display integrity
- reorder/group persistence
- seek forward/backward
- trim IN / OUT
- pitch/speed
- pre-end return behavior
- search launch auto-close
- Opening modes (Always/Never/Automatic)
- Automatic mode with lyrics/no-lyrics tracks
- live queue creation during playback
- queue chaining stability
- lyric color persistence
- lyric text auto-save without pressing Save
- lyric timestamp auto-save
- lyric color persistence after song change
- lyric color persistence after app restart
- Player refresh immediately after leaving lyrics editor
- long-press deselection
- compact Synchro line edit dialog
- compact Synchro color palette
- readability mode under outdoor conditions

👉 Real-device testing is mandatory.

⸻

# FINAL RULE

The Player must ALWAYS feel:
- instant
- stable
- predictable
- musical

If a change introduces:
- instability
- confusion
- lag
- timing uncertainty
- UI stress

👉 It must be rejected.
