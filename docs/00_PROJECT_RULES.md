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

INSTRUMENTED TESTS AND USER DEVICES (CRITICAL)

By default, never run an instrumented Android test on a physical phone or tablet that contains
the user installation of Stage Music Player / MusiMio without the user's explicit prior approval.

This prohibition includes:
- `connectedAndroidTest` and every variant-specific connected test task, including
  `connectedLaboDebugAndroidTest`;
- `tests_device.sh` when it invokes a connected instrumented test task;
- any test task that may install, replace, clean, or uninstall the main application package;
- `adb uninstall`, `pm uninstall`, `pm clear`, and equivalent commands that may remove or reset
  application data.

Before running an instrumented test on a physical device, Codex must:
1. identify the targeted device;
2. identify the targeted `applicationId` / package;
3. determine whether the device contains a user installation that must be preserved;
4. determine whether the task may install, replace, clean, or uninstall that package;
5. stop and request explicit user approval whenever such a risk exists.

A standard Gradle task is not safe merely because it is standard. The option
`-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true` may prevent final automatic
uninstallation, but it does not isolate the user application or guarantee that its data cannot be
modified.

Use, in order of preference:
1. a dedicated emulator;
2. an isolated test device with no important user data;
3. a physical user device only after explicit approval and acknowledgement of the risk.

Kotlin compilation and JVM tests remain allowed when they do not interact with a device. Mandatory
real-device validation for live-critical behavior should be performed manually unless an isolated
instrumented-test environment is available.

⸻

FINAL RULE

If there is a conflict between:
- simplicity
- performance
- stability

👉 ALWAYS choose stability.
