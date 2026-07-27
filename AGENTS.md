# AGENTS.md

## Scope

- Android project (single module :app)
- Namespace: com.patrick.lrcreader.exo
- UI: Jetpack Compose only
- Main entry point: MainActivity.kt

---

## CRITICAL — Mandatory Reading

Before any structural or important decision, read and strictly follow:

- /docs/00_PROJECT_RULES.md
- /docs/01_SMP_ARCHITECTURE.md
- /docs/02_LIVE_STABILITY_RULES.md
- /docs/03_RELEASE_SAFETY_RULES.md
- SMP_RULES.md
- SMP_SPEC_AGENT.md

If there is any conflict:
👉 SMP_RULES.md prevails

---

## DOCUMENT LOADING POLICY (CRITICAL)

Always read:
- /docs/00_PROJECT_RULES.md

Then load ONLY relevant documents depending on the task:

For Player / audio:
- /docs/Features/FEATURE_PLAYER.md

For Playlist:
- /docs/Features/FEATURE_PLAYLISTS.md

For Timeline / MIDI / DMX:
- /docs/Features/FEATURE_TIMELINE.md

For Network / LocalLink / device pairing:
- /docs/03_SMP_NETWORK_ARCHITECTURE.md
- /docs/Features/FEATURE_SMP_SYNC.md

For Library / import / files:
- /docs/Features/FEATURE_LIBRARY.md

For SMP / architecture / storage:
- /docs/01_SMP_ARCHITECTURE.md
- /docs/02_LIVE_STABILITY_RULES.md
- /docs/03_RELEASE_SAFETY_RULES.md
- SMP_RULES.md
- SMP_SPEC_AGENT.md

Rule:
👉 Never load unrelated documents unless strictly necessary

---

## IMPORTANT — PROMPTING RULES

Always generate structured Codex prompts:

1. Diagnose the issue using real code (when not trivial)
2. Apply a minimal and safe patch
3. Validate with compilation or tests

Rules:
- Never refactor unless explicitly required
- Never modify unrelated features
- Focus only on relevant parts of the system
- Minimize token usage by loading only necessary docs

---

## BUILD

- Flavors: labo, concert
- Build types: debug, release

Validation command:
./gradlew :app:compileLaboDebugKotlin

---

## RUNTIME OVERVIEW

- MainActivity = central orchestrator
- Player → ExoPlayer (source of truth: time)
- DJ → separate engine
- Timeline → based only on timeMs

State:
- singletons (object)
- Compose state
- StateFlow

---

## CORE PRINCIPLES

- Never break the stable beta
- Always prefer minimal patch
- No unnecessary refactor
- Backward compatibility required

Priority:
👉 Stability > Performance > Features

---

## LIVE PERFORMANCE RULES

- No heavy processing during playback
- No I/O on main thread
- No reading from .smp zip in runtime
- All data must be local and ready before playback

---

## SMP MODEL (CRITICAL)

- 1 song = 1 SongUnit
- .smp = transport format only
- Runtime works on normalized internal storage

Lifecycle:
👉 IMPORT → NORMALIZE → USE → EXPORT

---

## IDENTITY RULES

- Song identity = songId
- Never depend on:
  - file name
  - URI
  - external path

---

## TIMELINE RULE

- All time-based logic uses timeMs
- Source of truth = ExoPlayer

Forbidden:
- lineIndex
- UI state dependency

---

## INTENT / RUNTIME / OUTPUT SEPARATION

Always separate:

- Intent → data (MIDI, DMX, annotations)
- Runtime → timeline execution
- Output → MIDI / DMX

No coupling allowed.

---

## STORAGE RULES

- Work on internal normalized data
- Never use .smp archive at runtime
- No fake folders
- Filesystem must reflect reality only

---

## I18N RULES

Forbidden:
- hardcoded strings

Required:
- values/strings.xml (FR)
- values-en/strings.xml
- values-es/strings.xml

---

## WORKFLOW WITH CODEX

Strict order:

1. Diagnose
2. Minimal patch
3. Validation
4. Commit

Architecture before implementation:

Any strategic SMP evolution must exist as architecture before it exists as code.

When a feature affects the global architecture, multiple modules, or creates a major new product capability, the work order becomes:

Product vision
↓
UX reasoning
↓
Architecture
↓
Documentation
↓
Diagnosis
↓
Minimal patch
↓
Validation
↓
Commit

Implementation starts only after the architecture has been validated.

Bug fixes and micro-patches are not affected by this rule.

This complements:
- minimal patch;
- stability before features;
- targeted documentation;
- architecture before refactor.

Extended SMP workflow:

This does not replace the existing workflow. It clarifies the difference between local development history and remote validation on GitHub.

Official order:

Product vision
↓
UX reasoning
↓
Architecture, when needed
↓
Documentation, when needed
↓
Diagnosis
↓
Minimal patch
↓
Compilation
↓
Validation / Tests
↓
Commit
↓
Patch completed

For roadmap milestones, stabilized features, long interruptions, Google Play preparation, or when the creator explicitly asks to save remotely, the workflow continues with:

Push GitHub
↓
Remote repository verification
↓
GitHub Actions check
↓
Milestone completed

Local validation:

Before any commit:
- compilation must pass;
- required tests must pass;
- functional validation must be done when needed.

Commit:

The commit is a daily development tool.

It must be:
- frequent;
- small;
- coherent;
- easy to revert.

Its purpose is to build a clean local history that makes it possible to return to any meaningful step.

Remote validation:

Push GitHub is a remote validation step, not an automatic requirement after every micro-patch.

It is recommended:
- at the end of a roadmap step, such as V2.1 or V2.2;
- when a feature is stabilized;
- before a long development interruption;
- before a Google Play publication;
- when the creator explicitly asks: "on sauvegarde".

Important:

A failing GitHub workflow does not mean that the push failed.

For a micro-patch, expected final report format:

Diagnosis: OK

Patch: OK

Compilation: OK

Local validation: OK

Commit: created

Push GitHub: not requested

For a milestone, expected final report format:

Diagnosis: OK

Patch: OK

Compilation: OK

Local validation: OK

Commit: created

- Push GitHub: successful
- Remote branch: verified
- GitHub Actions: successful or failing

Final reports must clearly separate:

- Push GitHub: successful, not requested, or not attempted
- GitHub Actions: successful, failing, or not checked

Final principle:

The commit is the development history.

The push is the remote backup and validation of important milestones.

Both are complementary, but they do not have the same role.

An SMP micro-patch is considered complete when local validation is complete and a coherent commit exists.

An SMP milestone is considered complete when local validation is complete, remote backup is confirmed, and GitHub Actions status is known.

Rules:
- No manual code editing
- No premature commit
- Keep logs unless clearly obsolete

---

## FORBIDDEN

- fragile logic
- hidden deletion
- duplicate storage logic
- unnecessary SAF complexity
- runtime zip access

---

## WHERE TO START

- Startup → MainActivity
- Player → AudioEngine / PlayerScreen
- Timeline → event system
- Library → LibraryScreen
- Config → core/config
- Lyrics → LrcStorage

---

## FINAL RULE

If a change introduces:
- instability
- regression
- unnecessary complexity

👉 It must be rejected

---

END
