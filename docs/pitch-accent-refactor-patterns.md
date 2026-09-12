# Refactor patterns from `PitchAccentDiagram`, and where to apply them

**Audience:** an implementation agent working in this repo with no memory of the session that produced
these patterns. Everything you need is either in this file, in the referenced commits, or in
`CLAUDE.md` (**authoritative for style — read it first**; several of the patterns below are now written
into its "Code quality" list, so treat this document as the sequencing and the candidate inventory, not
as a second rulebook).

**How to use this document.** Not a big-bang refactor. Step 0 is an audit you hand back for sign-off;
Steps 1–3 are worth doing now; Steps 4–5 are optional and large. One pattern per commit, behaviour
preserved unless the step says otherwise and the user agreed to the visible change.

**Read history, not just the current tree.** The exemplar commits contain before/after for each pattern:
`549d06b`, `56781fe`, `3bf2a5c`, `e1fb1ab` (`git show <sha>`). Note that `c5a1c4b` later removed the
weblio scraping pipeline (parser, scrape worker, cache DAO, "Check now" affordance), so some code those
commits touched no longer exists — the *patterns* survive, their pitch-accent motivation sometimes no
longer does. Each section says which.

## 0. The patterns, with the exemplar

### P1 — One state type per question the UI is actually asking

`emptyList()`/`null` must not stand in for "we don't know yet". `PitchAccentUiState`
(`designsystem/subjectdetail/PitchAccentUiState.kt`) and the per-reading `ReadingPitchAccent` render a
*different* caption for each state instead of a bare reading. Before, an empty list meant both "never
looked up" and "confirmed absent", and the two were indistinguishable.

**Smell:** `List<…> = emptyList()` in a UI state or repository return type alongside an `isLoading` flag
or `errorMessage`; a composable that renders nothing for `isEmpty()`.

**Audit lead:** `PitchAccentUiState.Loading` / `ReadingPitchAccent.Pending` now have **no producer** —
`PitchAccentRepository` is bundled-dictionary-only and emits `Available` or `Unavailable` — so the
pending branch is unreachable. Decide whether to prune it or keep it for a future live source; don't
leave it ambiguous.

**Note:** serialization DTOs and Room entities also default their lists to `emptyList()`; that is a
deserialization concern, not a UI state. Leave them alone.

### P2 — Fuse values that are only meaningful together

The unit of pitch data is *(word, reading)*. The code carried `reading: String` **and** a word-level
`PitchAccentUiState` as two arguments and rejoined them inside the renderer. It now carries one
`ReadingPitchAccent` (the reading plus its own answer), produced by a pure `forReading(...)` projection
(`PitchAccentUiState.kt`), and the renderer does no matching at all.

**Smell:** two or three fields on a UI state always written in the same `copy(...)` and cleared together;
a composable that takes `a` and `b` and immediately looks `b` up in `a`.

### P3 — One component owns each state→content decision

`PitchAccentDiagram` owns "the patterns, or a caption saying why not", the copy for each state, and its
own test tags; callers (`QuizQuestionContent`, `SubjectAnswerSections`) compose it and own nothing about
that decision. The arrangement logic was deleted from every consumer.

**Smell:** the same `when (state)` / `isEmpty()` branch re-implemented across screens; user-facing copy
duplicated per call site; test tags passed in by callers instead of owned by the component.

### P4 — Screen-wide wiring travels by `CompositionLocal`, not parameters

`LocalPronunciationAudioPlayer` (`designsystem/subjectdetail/ReadingRow.kt`) is provided once at each
platform's app root and replaced player parameters drilled through three or four composables. It is
nullable with a `null` default, and **offering the value *is* the affordance** — `null` means "this
surface doesn't offer it", so no consumer needs a boolean switch. (The same shape was used for the
since-removed "Check now" local.)

**Smell:** a callback or setting appearing in more than ~3 composable signatures on the way to one use
site.

**Guardrail:** one or two hops is fine. Don't introduce a local for a one-hop value, and don't use one to
hide a dependency the caller should be explicit about.

### P5 — Observe the source of truth; never cache a copy in phase state

The quiz held pitch-accent snapshots (`Phase.Quiz.answerPitchAccents`, a batch-level
`pitchAccentsBySubjectId` map) while the detail sheet observed the repository, so the two could disagree
and the quiz needed a per-batch refetch fan-out. Both now derive from `PitchAccentRepository`'s flow
keyed on what is on screen — review observes the current question's word, lesson derives the map from
one flow per word in play — and the copied fields are gone. (The original motivation was cache writes
from scraping; that pipeline is gone, but the shape is still the right one for any DB-backed value, and
it is what makes the study card and quiz agree by construction.)

**Smell:** a field in a phase/uiState fetched once with `.first()` or a `fetch…()` helper; a value that
would be stale after a write elsewhere.

**Guardrail:** only DB-backed projections convert. A value that is *computed* (stroke-order data decoded
from bundled assets) or an *event record* (rank change, last answer) is correctly a snapshot.

### P6 — Prefer "offering X is the affordance" to flags

`hasAudio: Boolean` + `onPlayReading` became just the nullable callback — there was no way for the two to
disagree honestly. The test is "can any call site legitimately pass the other value?"; `showPitchAccent`
stayed because it is a real user preference.

### P7 — Expected outcomes are values, not exceptions or null

**Audit-only; the original exemplar is gone.** `WeblioEntry.Page | NotFound` (a 404 cached as a
confirmed absence rather than retried forever) was removed with the scraping pipeline. The pattern
stands, and the repo already has the models to mirror: `ApiResult` and `DrainOutcome`.

**Smell:** `runCatching` used to mean "there's no data", a nullable return whose null carries two
meanings, or `try/catch` as control flow on an expected path.

## 1. Step 0 — audit (no code, hand back a table)

Produce `file:line | pattern | what changes | user-visible? | size`, using these greps as a starting
point, and get sign-off before writing code. Flag anything user-visible: several patterns convert
silence into a caption, which needs an explicit yes.

```bash
# P1: list-valued UI state next to loading/error flags
grep -rn "emptyList()\|emptyMap()" shared/src/commonMain --include=*.kt | grep -v "/network/\|/database/"
grep -rn "isLoading\|errorMessage" shared/src/commonMain --include=*.kt

# P2: fields co-written in one copy(...)
grep -rn "it.copy(" shared/src/commonMain/kotlin/com/crazyfluff/shellfstudy/shared/feature --include=*.kt

# P4: how far does each callback/setting travel?
for s in onSubjectClick onRelatedSubjectClick showPitchAccent restrictAudioToMp3 showStrokeOrder \
         autoPlayStrokeOrder hideContextSentenceTranslations; do
  printf "%-32s %s\n" "$s" "$(grep -rn "$s" shared/src --include=*.kt | wc -l | tr -d ' ')"
done

# P5: snapshot fields in phases
grep -rn "\.first()\|suspend fun fetch" shared/src/commonMain/kotlin/com/crazyfluff/shellfstudy/shared/feature --include=*.kt

# P7: runCatching standing in for "no data"
grep -rn "runCatching" shared/src/commonMain --include=*.kt
```

Candidate leads from the survey already done (**verify each before trusting it**):

- `PitchAccentUiState.Loading` / `ReadingPitchAccent.Pending`: no producer left after `c5a1c4b` (P1).
- `SubjectAnswerSections.kt` (shared meaning/reading section) vs `SubjectDetailContent` / `LessonScreen`:
  check for duplicated rendering and copy (P3), and whether its `showPitchAccent = false` default is a
  real preference or a leftover.
- Related subjects: `SubjectDetailContent.resolve()` does `mapNotNull`, so "no related subjects" and
  "related subjects not cached" look identical (P1). `LessonViewModel.relatedSubjectsById` is fetched per
  batch (P5); `SubjectDetailViewModel.relatedSubjects` may already be derived — verify.
- `QuizQuestionUiState.answerReading` + `answerPitchAccents` + `answerReadingAudio`: written together at
  grade time in both quiz ViewModels, cleared together — one value (P2).
- `onSubjectClick` (33 references) and `onRelatedSubjectClick` (11): the most-drilled callbacks (P4).
- Settings flags (`showPitchAccent` 19, `restrictAudioToMp3` 23, `showStrokeOrder`, `autoPlayStrokeOrder`)
  threaded from the ViewModels' display-settings bags to leaf composables (P4).
- Per-screen loading/error/empty rendering in `LessonScreen`, `ReviewScreen`, `DashboardScreen`,
  `SearchOverlay`, `LastSessionSummaryScreen`: duplicated copy and retry UI (P3).

## 2. Suggested sequence

Each step: one commit, tests green, KDoc updated, report what changed.

**Step 1 — fuse the answer hint (P2, small, behaviour-preserving).** Collapse
`QuizQuestionUiState.answerReading` / `answerPitchAccents` / `answerReadingAudio` into one value (e.g.
`AnswerReadingHint(reading, pitchState, audio)`), set and cleared as a unit in `ReviewViewModel` and
`LessonViewModel`, consumed by `QuizQuestionContent`. Existing ViewModel tests already assert the fields
together — adapt them, don't weaken them.

**Step 2 — make "related subjects" a real state (P1 + P3, medium, user-visible).** A state type
distinguishing *none* from *not cached yet*, and one `RelatedSubjectsSection` rendering it (including a
caption for "not cached") instead of call sites silently dropping missing ids. Confirm the copy with the
user first.

**Step 3 — observe the lesson's related subjects (P5, medium).** Replace the per-batch
`relatedSubjectsById` snapshot with an observation of the subject DAO keyed on the items in play,
following the pitch-accent map's `flatMapLatest`-over-items-in-play shape. Prove it in the payoff-test
style: write to the fake DAO while the same batch is on screen and assert the uiState updates with no new
batch.

**Step 4 — optional: settings off the parameter lists (P4, large).** Provide the subject-display settings
through a screen-level local (nullable default, so `null` disables the affordance) and delete the
threaded parameters. Measure the diff before/after.

**Step 5 — optional: shared loading/error/empty sections (P3, large, user-visible).** One component per
case owning the copy, tags and retry affordance; screens compose it. `SubjectAnswerSections.kt` shows the
shape already exists for subject content — audit before adding more.

## 3. Guardrails

- **Do not** touch DTO/entity default lists, `ApiResult`, `DrainOutcome`, or one-hop parameters.
- **Do not** convert a P5 snapshot that is computed rather than DB-backed, or an event record.
- **Do not** delete a test to make a refactor pass. If an assertion no longer describes behaviour, say so
  and rewrite it to the new truth.
- **Do not** widen scope inside a step; add new candidates to the audit table instead.
- `CLAUDE.md` wins on style: one `StateFlow<UiState>` per ViewModel, no `!!`, guard clauses over nesting,
  KDoc explains *why*, no magic strings for user-facing copy outside the state type that owns it.

## 4. Verification protocol

```bash
./gradlew :shared:testAndroidHostTest    # commonTest + androidMain on the JVM (fast)
./gradlew :app:testDebugUnitTest         # Robolectric: ViewModels, repositories, Composable screens
```

- Pure logic (projections like `forReading`) goes in `shared/src/commonTest`; composable and ViewModel
  tests go in `app/src/test` (Robolectric, `@Config(sdk = [35])`).
- Force one genuinely fresh run before reporting numbers — `rm -rf
  app/build/test-results/testDebugUnitTest && ./gradlew :app:testDebugUnitTest --no-build-cache` — and
  read totals from the XML, not the console summary.
- A UI-visible step can't be fully verified here: list exactly what a human should look at on a device
  (which screen, which state, what changes).
- Known flake, unrelated: `SyncOrchestratorTest > syncAll returns Success once every resource syncs
  successfully` (MockWebServer dispatcher appending to an unsynchronised `mutableListOf`). Re-run before
  blaming your change; don't "fix" it by weakening the assertion.

## 5. What to report back

Per step: the pattern applied, files changed, the exact new type/signature, which tests you changed and
why, anything you decided this document didn't cover, fresh totals for both suites, and anything you
believe is wrong or unfinished. If a step turns out to be a bad idea once you're in the code, stop and
say so with the evidence — the patterns are a lens, not a mandate.
