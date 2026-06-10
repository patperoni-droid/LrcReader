# LIVE STABILITY RULES — Stage Music Player

CRITICAL — LIVE PERFORMANCE SAFETY

This document defines all rules required to guarantee stable behavior during live performance.

Any implementation that violates these rules must be rejected.

⸻

CORE PRINCIPLE

During live playback, NOTHING must introduce instability.

If there is any doubt between:
- feature richness
- performance
- stability

👉 ALWAYS choose stability.

⸻

PLAYBACK SAFETY

- Playback must rely ONLY on local, ready-to-use files.
- No remote access, no streaming, no zip reading.
- Audio must be fully accessible before playback starts.

- ExoPlayer is the ONLY source of truth for:
  - playback position
  - timing
  - synchronization

👉 No alternative timing system allowed.

⸻

THREADING RULES (CRITICAL)

- No heavy computation on the main thread.
- No blocking operations during playback.
- No disk I/O on the main thread during live.

- Background work must:
  - be lightweight
  - be predictable
  - never interfere with playback timing

⸻

DATA PREPARATION

ALL required data must be prepared BEFORE playback:

- audio files resolved
- trims (IN / OUT) computed
- timeline data loaded (MIDI / DMX / notes)
- playback parameters ready (gain, pitch, speed)
- restored Library index and playlist titles ready after backup restore

👉 ZERO preparation during playback.

Level preparation rule:
- LUFS SMP / waveform-based level analysis is preparation work only
- level analysis may run at import, on an explicit user action, or during pre-concert preparation
- no heavy level analysis may run during live playback
- manual gain changes may be applied during playback only as lightweight volume updates to the active player
- manual gain updates must not restart the track or rebuild playback structures

Restore live rule:
- a restored backup must be immediately exploitable in live conditions
- Library titles and playlist titles must not require opening or playing a song to repair themselves
- playlists restored from another device must survive restart without manual cleanup

Instant Library rule:
- the last known Library song list must be available from persistent cache as early as possible
- if cached songs are available, background verification scans must not replace them with a temporary empty list
- already known songs must remain searchable while the scan refreshes
- if no cached or live data exists yet, the UI must show clear loading feedback instead of appearing blank
- scan freshness is secondary to immediate live access to known songs

⸻

TIMELINE SAFETY

- Timeline must rely strictly on ExoPlayer time.
- No recalculation or reconstruction during playback.
- No dynamic structure changes while playing.

- Event dispatch (MIDI / DMX) must:
  - be deterministic
  - avoid duplicate triggers
  - handle seek safely

⸻

AUDIO PROCESSING RULES

- No runtime audio transformation that risks glitches.
- Speed / pitch processing must use stable pipelines only.

- If a processing mode introduces:
  - crackles
  - latency
  - instability

👉 It must be disabled or replaced.

⸻

LIVE TRANSITION SAFETY (CRITICAL)

Transitions between tracks are live-critical behaviors.

Supported behaviors:
- autoplay chaining
- Define Next
- live red group chaining
- live transition fade
- crossfade

Rules:
- transitions must remain deterministic
- transitions must not depend on UI rendering state
- transitions must survive recomposition
- transitions must not depend on LazyColumn visible indexes
- nextTrack resolution must use stable references only
- “Prochain : null” is forbidden in live UI

During live transition fade:
- the new track becomes immediately authoritative for:
  - lyrics
  - chords
  - timeline
  - nextTrack logic
- the old track may continue audio fade-out only
- the old track must never continue driving timeline events

👉 Audio overlap is allowed.
👉 Timeline overlap is forbidden.

Pitch/speed transition guard:
- if the current track OR the next track has non-neutral pitch/speed, double-player crossfade is forbidden
- non-neutral means pitch != 0 semitone or speed != 1.0
- use a sequential live-safe transition instead:
  - short fade-out
  - full stop/clear/release or cleanup of the old audible player
  - controlled reset of volume/gain/LUFS state
  - playback parameters applied before `prepare()` / `play()`
  - launch through the standard Player pipeline
- normal -> normal may keep the existing crossfade
- after sequential handoff, only one live audio player may remain audible

Known pitfall:
Pitch/speed transitions must never leave a promoted/transition player alive after handoff.

👉 Stability live > advanced crossfade.

⸻

PLAYLIST / PLAYER LIVE RULE

Playlist ordering, autoplay, nextTrack resolution, and live chain behavior are considered LIVE-CRITICAL systems.

Any modification touching:
- playlist reorder
- groups
- autoplay
- nextTrack
- playback transitions
- player handoff
- viewport identity
- LazyColumn rendering

MUST validate:
- live chain continuity
- next title visibility
- autoplay sequence
- drag reorder
- Move to…
- real-device behavior

No UI optimization may break live continuity.

👉 Stability > convenience.

⸻

UI BEHAVIOR DURING LIVE

- UI must NEVER block playback.
- UI updates must be lightweight.

- No:
  - heavy recomposition
  - large list rebuilds
  - expensive animations during playback

👉 Smooth UI = safe live experience.

Tablet live navigation:
- in tablet split mode, the Playlist remains fixed on the left
- the right pane hosts the active destination and defaults to Lyrics
- `Lyrics` means the scrolling playback lyrics, not the lyrics editor
- compatible right-pane destinations include Lyrics, Library, Track Console, Settings, Background Sound, Lyrics Editor, and future tablet-safe panels
- the cockpit menu is the preferred way to navigate between compatible right-pane destinations
- every compatible right-pane destination must keep a clear path back to `Playlist | Lyrics`
- secondary panels such as Track Console, Library, Settings, Background Sound, or Lyrics Editor must not trap the user away from `Playlist | Lyrics`
- fullscreen tablet tools such as Arrangement or Timeline must be explicit and must return cleanly to the live cockpit
- tablet navigation changes must not alter phone behavior or playback state

⸻

STATE MANAGEMENT

- Playback state must be:
  - consistent
  - predictable
  - recoverable

- No hidden state transitions.
- No implicit behavior.

👉 Every state change must be explicit and controlled.

⸻

ERROR HANDLING

- No crash must reach the user during live.

- In case of error:
  - fail gracefully
  - preserve playback if possible
  - avoid stopping the audio engine

👉 The show must go on.

⸻

SEEK & TRANSITIONS

- Seek operations must be:
  - safe
  - controlled
  - limited

- NEVER use seek as a structural mechanism (looping, arrangement).

👉 Seek is for navigation, NOT for structure.

⸻

RESOURCE MANAGEMENT

- Memory usage must be stable.
- No uncontrolled allocations during playback.

- Avoid:
  - loading large assets dynamically
  - creating objects in tight loops

👉 Predictability over flexibility.

⸻

MULTI-SOURCE AUDIO (BUS PRINCIPAL)

- Only one main audio source must dominate at a time:
  - Backing track
  - DJ
  - Ambience

- Automatic behaviors must prevent:
  - unintended overlap
  - audio conflicts

👉 The mix must remain controlled at all times.

⸻

DEVICE VARIABILITY

- Behavior must NOT depend on device performance.
- No timing logic relying on device speed.

👉 Same result on all devices.

⸻

REAL DEVICE VALIDATION (MANDATORY)

Live-critical features must always be validated on a real physical device.

Emulator-only validation is insufficient for:
- drag & drop
- live transitions
- autoplay
- live chain
- recomposition-sensitive UI
- audio overlap
- nextTrack display

👉 Real stage behavior has priority over emulator behavior.

⸻

FINAL RULE

If a feature introduces even a small risk of:
- glitch
- delay
- instability
- unpredictability

👉 It must NOT be implemented in its current form.
