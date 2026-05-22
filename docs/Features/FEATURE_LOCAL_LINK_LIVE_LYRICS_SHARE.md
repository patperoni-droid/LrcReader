# FEATURE — LOCAL LINK / LIVE LYRICS SHARE

CRITICAL — FUTURE LIVE FEATURE, ARCHITECTURE ONLY

This document defines the target architecture for sharing synchronized lyrics to a secondary phone on a local network.

This is not a backup or library synchronization feature.

---

## Product Goal

The goal is to let a secondary phone display lyrics during a live performance.

- Phone A = live master
- Phone B = remote lyrics screen
- Phone B may be empty, with only SMP installed
- Phone B does not need Phone A's Library
- Phone A sends the current song lyrics once, then only sends the live clock

Phone A remains the only live authority.

Phone B never controls playback, playlist order, autoplay, Define Next, live chain, or audio.

---

## Layer Separation

The feature must be split into three independent layers:

```text
MainActivity / Player clock
        |
        v
LiveLyricsShare
        |
        v
LocalLink
        |
   local Wi-Fi
        |
        v
LocalLink
        |
        v
LiveLyricsShare receiver screen
```

### LocalLink

LocalLink is the local connection layer only.

Responsibilities:
- local network connection
- QR pairing
- IP / port / token / sessionId
- heartbeat
- ping / pong
- reconnection

LocalLink must not know:
- Player
- lyrics
- playlist
- `.smp`
- autoplay
- live chain
- AudioEngine

LocalLink is transport only.

### LiveLyricsShare

LiveLyricsShare uses LocalLink.

Responsibilities:
- on song change, Phone A sends one `lyrics_packet`
- while playing, Phone A sends `clock` messages
- Phone B keeps the received lyrics in memory
- Phone B renders lyrics from `timeMs`

Phone A sends:
- `songId` as informative identity
- title
- lyrics lines or LRC text
- `timeMs`
- `isPlaying`
- `seq`

Phone B:
- does not need the song in its local Library
- does not need a `.smp`
- does not request audio
- does not request screen data
- does not control Phone A

### FutureBackupSync

FutureBackupSync is a separate future feature.

Responsibilities:
- inventory
- diff
- missing or modified `.smp`
- playlists
- `state.json`
- full backup phone preparation

FutureBackupSync must not depend on LiveLyricsShare.

LiveLyricsShare must not depend on FutureBackupSync.

The live lyrics share path must remain lightweight even if backup sync is never implemented.

---

## Network Messages

Messages should be versioned and small.

The recommended V1 format is JSON over a local TCP JSON-lines connection or WebSocket. The exact transport remains open, but the message model must stay transport-independent.

### hello

Sent during pairing / connection setup.

```json
{
  "type": "hello",
  "version": 1,
  "sessionId": "session-123",
  "token": "short-lived-token",
  "deviceName": "SMP Pro"
}
```

### lyrics_packet

Sent once at the beginning of a song, and again only when the song changes or the receiver explicitly needs a resync packet.

Recommended V1: send parsed lines to avoid parsing work on the receiver during live.

```json
{
  "type": "lyrics_packet",
  "version": 1,
  "songId": "song_123",
  "title": "Song title",
  "format": "parsed_lrc",
  "lines": [
    { "timeMs": 1200, "text": "First line" },
    { "timeMs": 4500, "text": "Second line" }
  ],
  "durationMs": 184000,
  "seq": 42
}
```

Alternative allowed later:

```json
{
  "type": "lyrics_packet",
  "version": 1,
  "songId": "song_123",
  "title": "Song title",
  "format": "lrc_text",
  "lrc": "[00:01.20]First line\n[00:04.50]Second line",
  "seq": 42
}
```

### clock

Sent periodically while the share session is active.

`timeMs` must come from Phone A's ExoPlayer current position.

```json
{
  "type": "clock",
  "version": 1,
  "songId": "song_123",
  "timeMs": 45210,
  "isPlaying": true,
  "seq": 43
}
```

Rules:
- `clock` must never contain full lyrics
- `clock` must never contain audio data
- Phone B ignores a `clock` whose `songId` does not match the active `lyrics_packet`
- pause and resume use the same message with `isPlaying`

### ping

Used by LocalLink for heartbeat.

```json
{
  "type": "ping",
  "version": 1,
  "seq": 44
}
```

### receiver_status

Sent by Phone B to report receiver state.

```json
{
  "type": "receiver_status",
  "version": 1,
  "state": "ready",
  "activeSongId": "song_123",
  "seq": 45
}
```

Suggested states:
- `ready`
- `ok`
- `missing_packet`
- `desynced`
- `disconnected`

Receiver status is informational only. It must never block Phone A.

---

## Runtime Behavior

### Song Start

When Phone A starts or changes a song:

1. Resolve current `songId` and title.
2. Resolve or use already loaded lyrics.
3. Build a single `lyrics_packet`.
4. Send it through LocalLink.
5. Start or continue sending `clock` messages.

The lyrics packet must be prepared outside the critical audio path.

If lyrics are unavailable, Phone A may send a valid empty packet with the title, or skip the packet and keep playback unaffected.

### Receiver Display

Phone B stores the latest `lyrics_packet` in memory.

For each `clock`:

1. Check `songId`.
2. Update local remote time state.
3. Use `timeMs` to resolve the active line.
4. Render lyrics in a dedicated receiver screen.

Phone B may continue locally for a short grace period during temporary packet loss, then recalc on the next `clock`.

### Song Change

On song change:

- Phone A sends a new `lyrics_packet`.
- Phone B replaces the in-memory packet.
- Any old `clock` messages for the previous song are ignored.

---

## V1 Roadmap

### Step 1 — LocalLink Minimal

- local server on Phone A
- local client on Phone B
- QR pairing
- IP / port / token / sessionId
- ping / pong
- reconnect without affecting playback

### Step 2 — Pure Message Models

- `HelloMessage`
- `LyricsPacketMessage`
- `ClockMessage`
- `PingMessage`
- `ReceiverStatusMessage`

Models must be independent from Compose, Player, AudioEngine, and storage.

### Step 3 — LiveLyricsShareMaster

- observes current song state from the app runtime
- reads `timeMs` from ExoPlayer only
- sends `lyrics_packet` on song change
- sends `clock` at a stable interval
- never waits for Phone B before playback continues

### Step 4 — LiveLyricsShareReceiver

- dedicated receiver mode
- receives packets
- keeps lyrics in memory
- displays lyrics from `timeMs`
- handles missing packet / disconnect states

### Step 5 — UI V1

Phone A:
- "Share lyrics" entry point
- QR pairing screen
- connection status

Phone B:
- "Receive lyrics" entry point
- QR scan / pairing
- full-screen lyrics display
- keep screen awake

### Step 6 — Live Validation

Must validate:
- Phone A playback continues if Phone B disconnects
- no audio or screen streaming
- no lyrics re-send on every tick
- song change sends exactly one new lyrics packet
- pause / resume follows `isPlaying`
- receiver recovers after network loss

---

## Absolute Rules

- Phone A must never depend on the network.
- Phone B slow, absent, disconnected, or desynchronized must never block Phone A.
- Never stream screen.
- Never stream audio.
- Never require Phone B to have the local Library.
- Never send full lyrics on every tick.
- Never read or parse heavy data at every position update.
- Never put networking in AudioEngine.
- Never put networking in the core Player playback pipeline.
- Never use `lineIndex` as the network truth.
- `timeMs` is the only synchronization truth.
- ExoPlayer on Phone A remains the only source of live time.
- LiveLyricsShare must not change autoplay, Define Next, live chain, playlist ordering, or timeline dispatch.

---

## Pro / Lite

Commercial capabilities must be isolated from live runtime logic.

SMP Pro:
- may act as master
- may emit LiveLyricsShare sessions
- may receive sessions

SMP Lite:
- may receive sessions only
- may display lyrics only

Rules:
- Pro / Lite checks must not enter AudioEngine.
- Pro / Lite checks must not enter Player timing code.
- Pro / Lite checks must not change message semantics.
- The receiver protocol should stay compatible between Lite and Pro.

---

## Open Questions

- Use TCP JSON lines or WebSocket for V1?
- What is the final `clock` frequency: 100 ms, 200 ms, or adaptive?
- V1 single receiver only, or multi-receiver-ready from the start?
- Should V1 show only lyrics, or also the next title?
- Should V1 use parsed LRC only, or allow raw LRC as a fallback?
- How should future chords sharing be represented: same `lyrics_packet` extension or separate `chords_packet`?
- Should receiver drift be corrected instantly or smoothed visually?

---

## Non-Goals

This feature does not implement:
- cloud sync
- screen mirroring
- audio streaming
- backup phone synchronization
- `.smp` transfer
- playlist transfer
- remote control of Phone A
- remote seek / play / pause commands

Backup phone synchronization belongs to FutureBackupSync and must be designed separately.
