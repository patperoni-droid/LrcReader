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
- /docs/features/FEATURE_PLAYER.md

For Playlist:
- /docs/features/FEATURE_PLAYLISTS.md

For Timeline / MIDI / DMX:
- /docs/features/FEATURE_TIMELINE.md

For Network / LocalLink / device pairing:
- /docs/03_SMP_NETWORK_ARCHITECTURE.md
- /docs/Features/FEATURE_SMP_SYNC.md

For Library / import / files:
- /docs/features/FEATURE_LIBRARY.md

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

This does not replace the existing workflow. It completes it with remote validation on GitHub.

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
Push GitHub
↓
Remote repository verification
↓
GitHub Actions check
↓
Patch completed

Local validation:

Before any commit:
- compilation must pass;
- required tests must pass;
- functional validation must be done when needed.

Remote validation:

After the commit:
- push to GitHub;
- verify that the remote branch contains the latest commit;
- verify the GitHub Actions result.

Important:

A failing GitHub workflow does not mean that the push failed.

Final reports must clearly separate:

- Push GitHub: successful
- GitHub Actions: successful or failing

Expected final report format:

Compilation: OK

Local tests: OK

Commit: created

Push GitHub: successful

Remote branch: verified

GitHub Actions:
- OK, all tests green
or
- Warning, workflow failing, diagnosis required

Final principle:

An SMP development is considered finished only when:
- local validation is complete;
- remote backup is confirmed;
- GitHub Actions status is known.

This rule reinforces project safety and ensures that every change is correctly backed up before it is considered complete.

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
