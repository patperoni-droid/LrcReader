# PROJECT RULES — Stage Music Player

CRITICAL — MUST BE FOLLOWED

This document must be read before any code modification.
Any implementation that violates these rules must be rejected.

⸻

CORE PRINCIPLES

- Never break the stable beta.
- Always apply minimal and targeted patches.
- No unnecessary refactor.
- Every feature must remain backward compatible.
- Stability, performance, and data safety are top priority.

⸻

LIVE PERFORMANCE RULES (CRITICAL)

- No heavy processing on the main thread.
- No audio processing or structure building during live playback.
- All data must be prepared BEFORE playback.
- Never read or stream from .smp zip during live.
- ExoPlayer is the single source of truth for playback timing.

⸻

ARCHITECTURE RULES

- A song is a complete unit (audio + lyrics + chords + metadata).
- The system must always work on normalized internal data.
- No duplicate logic for persistence or storage.
- Always reuse existing pipelines when possible.

⸻

UI & I18N RULES

- No hardcoded strings in Kotlin / Compose.
- All visible text must use string resources.
- Add translations at least for:
    - French (FR)
    - English (EN)
    - Spanish (ES)

⸻

DEVELOPMENT WORKFLOW

- Always start with a diagnosis when the change is non-trivial.
- Then apply a minimal patch.
- Then validate (tests or compilation).
- No commit without validation.

⸻

DATA SAFETY RULES

- Never lose user data.
- Any destructive action must be explicit and confirmed.
- Editing must not break existing files or structure.

⸻

FINAL RULE

If there is a conflict between:
- simplicity
- performance
- stability

👉 ALWAYS choose stability.