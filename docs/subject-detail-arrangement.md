# Subject detail — arranging meaning / reading / mnemonic

Design note. **Nothing in this document is implemented yet.**

Scope: `SubjectDetailContent` (`shared/.../designsystem/subjectdetail/SubjectDetailContent.kt`), which is
rendered by `SubjectDetailSheet` — i.e. the review "swipe up for details" sheet, browse, search and
drill-down — and its near-duplicate in the lesson study pager
(`LessonScreen.kt` → `LessonMeaningSection` / `LessonReadingSection`).

## Decisions (locked)

| Question | Decision |
|---|---|
| Which proposal | **A, revised** — the headerless meaning sits directly under the subject's characters; the level/type + tag cluster follows it, then the headerless reading; everything answer-ish stays above the writing zone |
| Mnemonic handling | **A1, restyled in revision 3** — both blocks stay where they render, now titled with an accent bar + `labelLarge` rather than uppercase eyebrows, with a divider between them |
| Visual boundary | **None** — tight spacing only, no card or tint (**C** rejected) |
| Answer as pinned strip | **B deferred** — not part of this change |
| Lesson card | **Unified** — the lesson study card adopts the same composables, via the `subjectdetail` package |

## Current arrangement (revision 10)

```
        水                                     │
Water                                          │  headline cluster: 8dp between parts
みず  🔊                                        │  (glyph box trimmed to ~80% of the ink size)
Level 1 · Vocabulary   ①                       │  (level line at labelMedium, after the answers)
(pitch accent diagram)                         │
[transitive verb] [godan verb]                 ← part-of-speech tags last
──────────────────────────────
▍ Radicals / Kanji                 ← accent bar + labelLarge: the tile-grid sections only
  [tiles] · stroke order · writing practice
──────────────────────────────
Meaning mnemonic                   ← plain titleMedium SemiBold, no bar (revision 8)
  Looks like flowing water…
  hint, muted
──────────────────────────────
Reading mnemonic
  Sounds like mee-zoo…
  hint, muted
──────────────────────────────
Context sentences                  ← same plain heading
context sentences · visually similar · used in · stats
```

Mid-quiz with the meaning gated away, the same cluster collapses to:

```
        水
みず  🔊                           ← reading directly under the characters
Level 1 · Vocabulary   ①
```

Kanji reading rows keep their `on'yomi` / `kun'yomi` labels; radicals show the meaning alone.

### Headline order (revisions 4–7)

- **The level/type/SRS line follows the answers** (revision 7): characters → meaning → reading →
  level/type → tags. Revisions 4–6 moved this row around the top of the page (top right beside the
  glyph, then its own row between the meaning and the reading). Both were judged against browse mode,
  where the meaning fills the space; mid-quiz the meaning is gated away, and a row that sits between
  the answers then reads as a large empty gap between the word and its reading. Putting bookkeeping
  after the answers fixes it in every reveal state instead of only the one being looked at.
- **The line is `labelMedium` + `onSurfaceVariant`** on a full-width row, so it stays available without
  competing with the glyph. The `SrsStageChip` itself is unchanged — a shared component whose colour
  carries the stage, and shrinking it here would desync it from every other screen using it.
- **Part-of-speech tags sit below the reading** (revision 4) and close the cluster.
- The whole cluster is one 8dp column. There is **no** tighter grouping for characters + meaning
  (revision 9 tried 4dp and it was reverted): the gap came from the glyph's own box, not the
  arrangement. `SubjectGlyph` centres the character in a box, and the ink only fills ~55% of it, so
  roughly 20dp of empty box sat under the ink. Revision 10 added a `boxHeight` parameter and
  `headlineGlyphBoxHeight(size)` (80% of the ink size), which both headlines pass: the characters stay
  the same size while the box hugs them, closing the gap at the source. Every other `SubjectGlyph` call
  site (tiles, chips, quiz prompt) keeps the square default.
- This also settles the question revisions 2–6 kept reopening: the meaning and reading are adjacent.

### Section headings (revisions 3, 5, 8)

Three iterations landed here, and the last one undid most of the second:

| Revision | What it did |
|---|---|
| 3 | Mnemonic headings went from uppercase `labelMedium` eyebrow to `labelLarge` + a 4dp × 14dp accent bar — the treatment `RelatedSubjectsSection` already used — so the two blocks read as named sections, with a divider between them. |
| 5 | The prose headings (mnemonics, context sentences) moved up to `titleMedium` SemiBold, the "Stats" tier, so every heading on the page was one size. |
| 8 | **The accent bars came off the prose headings.** A coloured chip in front of prose competed with the words it introduced, and it only means anything where there is coloured content to match — which is the tile grids, not a paragraph. |

The current split:

| Component | Style | Used by |
|---|---|---|
| `SectionTitle` | `titleMedium` SemiBold, no bar | Stats, Meaning mnemonic, Reading mnemonic, Context sentences |
| `SectionAccentHeader` | `labelLarge` + accent bar | Radicals/Kanji, Visually similar, Used in |

- `SectionTitle` is now one component used by all four prose/stat headings, so they cannot drift; `Stats` was rewritten to use it rather than repeating the style inline.
- Because the mnemonic heading no longer needs a colour, `SubjectMnemonicZone` dropped its `subjectType` parameter entirely — the accent was its only use.
- The divider between the two mnemonic blocks stays: with the bars gone, that divider is what separates meaning from reading.
- Hints are still muted (`onSurfaceVariant`) — an aside about the mnemonic rather than more mnemonic.

Alternatives considered if the mnemonics still blend on device: a full-height accent rule down the left of
each block (blockquote style, stronger grouping, still card-free); a soft subject tint per block
(`subjectColor.copy(alpha = 0.10f)`, the tile idiom); or collapsing the mnemonics behind a disclosure
(A3), which removes the problem at the cost of state.

Two more things revision 2 settled:

- **The meaning is the headline, not a section.** It sits directly under the characters, so the meaning and the word are one unit.
- **The reading is part of the headline cluster too** (revision 3): it renders inside `SubjectHeadline`'s 8dp column rather than as a 16dp-spaced section sibling, so it follows the cluster parts instead of starting a new section — and it still sits above the writing zone, so checking an answer never means scrolling past a stroke-order diagram.

One thing left open across revisions 2–6 is now settled: the meaning and reading are adjacent (revision 7 put the level line after both), and the gated-meaning layout is the reason — see the trade-off note above.

The rest of this note keeps the first iteration's reasoning (§3 describes the pair-together layout, which this revision supersedes) and the spec that still applies.

---

## 1. What was wrong originally

Original section order (FULL reveal):

```
1. Headline        glyph (80dp) · level/type + SRS chip · part-of-speech chips
2. Writing zone    stroke-order diagram + writing-practice canvas
3. Components      radicals (kanji) / kanji (vocab)
4. Meaning         "Meaning" header → values → auxiliary meanings → "MEANING MNEMONIC" prose
5. Reading         divider → "Reading" header → values (+ pitch accent, audio) → "READING MNEMONIC" prose
6. Context sentences
7. Visually similar / Used in
8. Stats
```

Two problems, both about **vertical reach**:

1. **The answer is below the writing zone.** A kanji or vocabulary card with stroke order enabled
   opens showing the glyph, tags, a stroke-order diagram and a writing canvas before the meaning
   appears. In a quiz reveal that is the worst possible order: the one thing you opened the sheet to
   check is the one thing you have to scroll for.
2. **Two title lines buy nothing.** "Meaning" and "Reading" as `titleMedium` headers consume ~28dp
   each plus section spacing, and they only restate what the content already is — English word =
   meaning, kana = reading. This is the redundancy you noticed.

Estimated budget for a vocabulary card (≈700dp of sheet content height):

| Block | ≈ height |
|---|---|
| glyph + level/type + SRS | 110dp |
| POS chips (one wrapped row) | 40dp |
| stroke order | 130dp |
| writing practice | 170dp |
| "Meaning" header + values | 60dp |
| "Reading" header + reading + pitch | 90dp |
| **meaning/reading first pixel** | **≈ 300dp down** |

So the answer lands on the second screen of a two-screen sheet, and the mnemonic prose starts after
~365dp.

---

## 2. Constraints the arrangement has to respect

- **Two independent reveal gates.** `revealMeaning` and `revealReading` are computed separately from
  `DetailRevealMode` + `isAnswered` + `questionType`; in quiz mode exactly one of the pair is shown,
  and its mnemonic is shown with it. Any split has to keep value and mnemonic under the same gate.
  (Existing tests assert: reading quiz shows `みず` + reading mnemonic and *not* `Water`; meaning quiz
  the reverse; pre-answer shows neither.)
- **Hidden zones currently omit their headers too** (`if (!revealMeaning) return`), so dropping headers
  loses no signal in the hidden state — there is nothing "missing" to advertise. The sheet's own
  "Show all" button covers that.
- **Four shapes**, which want slightly different arrangements:

  | Type | Meaning | Reading |
  |---|---|---|
  | Radical | one word | none |
  | Kanji | one word + aux | on'yomi / kun'yomi / nanori rows |
  | Vocabulary | list + aux | readings each with audio + pitch diagram |
  | Kana vocabulary | list + aux | readings (skip — identical to the glyph) |

- **Spot in the peek state is fixed.** `SubjectDetailHandleHeight` (56dp) is the collapsed strip;
  callers reserve room for it. Nothing here should grow the always-present chrome.
- **Two implementations.** The same arrangement is currently written twice (sheet + lesson). Whatever
  is chosen should be extracted so the two cannot drift.

---

## 3. Proposal A — headerless answer pair under the tags *(first iteration, superseded by revision 2)*

Your idea, with the ordering consequence made explicit: the meaning/reading pair moves **above the
writing zone**, loses both headers, and the mnemonics stay down in the page where they are today.

```
┌──────────────────────────────────────┐
│                 水                    │  glyph 80dp
│  Level 1 · Vocabulary          ①     │  level/type · SRS chip
│  [transitive verb]  [godan verb]     │  POS chips  (unchanged)
│                                      │  ← 12dp
│  Water                               │  bodyLarge · SemiBold · onSurface
│  みず  🔊                             │  JapaneseText bodyLarge · onSurface
│  (aqua, H2O +2 more)                 │  auxiliary, expandable (unchanged)
│  ──────────────────────────────────  │
│  stroke order                        │
│  writing practice                    │
│  radicals / kanji                    │
│  ──────────────────────────────────  │
│  MEANING MNEMONIC                    │
│  Water is…                           │
│                                      │
│  READING MNEMONIC                    │
│  Sounds like mee-zoo.                │
│  ──────────────────────────────────  │
│  CONTEXT SENTENCES …                 │
└──────────────────────────────────────┘
```

Kanji variant — the type labels survive (they carry real information), the "Reading" header does not:

```
│                 水                    │
│  Level 1 · Kanji               ①     │
│                                      │
│  Water                               │
│  on'yomi   スイ                       │
│  kun'yomi  みず                       │
│  ──────────────────────────────────  │
│  RADICALS  [氵]                       │
│  stroke order / writing practice      │
│  ──────────────────────────────────  │
│  MEANING MNEMONIC …                   │
│  READING MNEMONIC …                   │
```

Radical / kana vocabulary — the pair collapses to what exists; no dangling header, no empty gap:

```
│                 亅                    │        │              水                       │
│  Level 1 · Radical             ①     │        │  Level 1 · Kana Vocabulary     ①      │
│                                      │        │  [expression]                        │
│  Barb                                │        │  みず  🔊                             │
│  ──────────────────────────────────  │        │  ──────────────────────────────      │
│  COMPONENTS …                        │        │  writing practice …                   │
```

Why this order, specifically meaning-then-reading with the pair *above* the writing zone:

- Everything under the tags is then "what this is and how it sounds", then "how it's drawn", then
  "how to remember it" — a value → form → story progression rather than form → story → value.
- Meaning first, reading second matches how the fields are listed everywhere else in the app
  (`DetailQuestionType`, quiz stats cards, lesson sections), so no new convention is introduced.
- The pair costs ~60–90dp, so glyph + tags + meaning + reading fit in the first ~250dp — comfortably
  the first screen even with a three-row kanji reading breakdown.

**Spec**

| Element | Style | Notes |
|---|---|---|
| Answer block container | `Column(spacedBy(6.dp))` | no background, no card; tagged `ANSWER_ZONE` |
| Meaning values | `bodyLarge`, `FontWeight.SemiBold`, `onSurface` | `joinToString(", ")`, tagged `MEANING_ANSWER` |
| Auxiliary meanings | unchanged `AuxiliaryMeaningsText` | directly under values |
| Reading (vocab) | `JapaneseText`, `bodyLarge`, `onSurface` | audio + pitch diagram unchanged; tagged `READING_ANSWER` |
| Reading (kanji rows) | label `labelMedium`/`onSurfaceVariant`, values `JapaneseText`/`bodyLarge` | label column went `80.dp → widthIn(min = 64.dp)`: enough for the longest label plus a gap, so all three rows keep their readings aligned |
| Reading (plain / kana vocab) | `JapaneseText`, `bodyLarge` | |
| Meaning↔reading gap | 6dp | one pair, not two sections |
| Gap to next section | existing 16dp outer spacing | |
| Mnemonic block | `SectionEyebrow` + `WkMnemonicText` | **eyebrows stay** — they are now the only thing identifying the prose |
| Mnemonic divider | one `HorizontalDivider`, inside `SubjectMnemonicZone` | rendered only when the zone has a block to show, so a blank/gated mnemonic leaves no dangling rule |

**Mnemonic handling (three choices; A1 chosen):**

- **A1 — keep them exactly where they render today ✅ chosen.** Two independent blocks with their
  existing "MEANING MNEMONIC" / "READING MNEMONIC" eyebrows, now immediately after the writing zone.
  Smallest diff, and each block stays on its own reveal gate.
- **A2 — merge into one "How to remember it" study block.** One divider + one eyebrow, the two
  mnemonics as paragraphs 12dp apart. Reads better as a unit and saves a section gap, but the halves
  still need separate gates, so the block would have to render per-half anyway.
- **A3 — collapsed by default** behind a "Show mnemonic" text button, so prose never pushes the
  contextual/related sections down. Best compactness, most state to own, and it fights the quiz-reveal
  flow where the mnemonic is part of the answer.

---

## 4. Proposal B — pin the answer above the scroll

A answers "where is the meaning when I open the sheet". B additionally answers "where is it after I've
scrolled down to read the mnemonic".

```
┌─ sheet ────────────────────────────────┐
│  ←                Show all         ✕   │  header row (not scrolling)
│                 水                     │
│  Level 1 · Vocabulary  ①  [godan verb] │
├────────────────────────────────────────┤  ← pinned, not scrolling
│  Water                                 │
│  みず 🔊                               │
├────────────────────────────────────────┤
│  stroke order / writing practice / …   │  scrolls under the strip
│  MEANING MNEMONIC …                    │
└────────────────────────────────────────┘
```

- Cost: ~70dp of permanently reserved height for a kanji/vocab (nothing when the field is gated, so
  the pre-answer quiz state is unaffected).
- Implementation shape: split `SubjectDetailContent` into a non-scrolling answer block + scrolling
  remainder, or promote the answer into `SubjectDetailSheet`'s body above `SubjectDetailContent`.
  The second keeps the scroll-offset bookkeeping (`initialScrollOffset`, per-subject `ScrollState`)
  untouched, which is the fiddly part of this file.
- Risk: the answer block would need to be part of the "glyph + tags" cluster visually, or it reads as
  stray chrome. In the lesson pager there is no scroll container to pin against in the same way — the
  lesson would keep plain A.

Recommendation: **do A now, keep B in the back pocket.** B's value is real but it changes the sheet's
structure, not just the arrangement, and A already removes the scroll-to-see-the-answer case.

---

## 5. Proposal C — answer as a tinted card

Same order as A, but the pair sits in a soft container keyed to the subject colour (reusing
`subjectColor(type).copy(alpha = 0.10f)`, which the app already uses for glyph/tiles):

```
│  [transitive verb]  [godan verb]       │
│  ╭──────────────────────────────────╮  │
│  │ Water                            │  │  tinted surface, 12dp radius, 12dp padding
│  │ みず 🔊                          │  │
│  ╰──────────────────────────────────╯  │
│  ───────────────────────────────────   │
```

- Pros: unmistakably "this is the answer"; useful in the quiz sheet where the tag chips above are
  already colourful; the card gives the pair a boundary that replaces the two headers with a single
  visual cue.
- Cons: costs ~24dp, adds a card to a page that is deliberately card-free (`SubjectMeaningZone`'s
  comment says plain sections keep the page lightweight for a screen users scroll constantly), and
  fights the `AkebiSelectableContainer` styling used for context sentences.
- Verdict: only if the headerless version reads as unstructured on device. C is a styling switch on
  top of A, not a competing arrangement.

---

## 6. Proposal D — one-line answer

For the narrow case where both values are short:

```
│  [godan verb]                          │
│  Water · みず 🔊                       │
```

- Saves a line, but mixes scripts on a baseline with no separation, breaks down as soon as the meaning
  is a list ("to be careful, to pay attention") or the reading is one of on/kun, and hides the audio
  button behind the text.
- Verdict: **no.** The kanji case is the one that most needs compactness and it is exactly the case
  this cannot serve.

---

## 7. Also considered, and rejected (or deferred)

| Idea | Why not |
|---|---|
| Meaning left / reading right, two columns | Fine for `水`/`みず`, breaks for multi-term meanings and for kanji reading breakdowns; a phone column is ~180dp wide at 24dp sheet padding. |
| Move the POS chips below the answer | The chips are the *context* for the answer ("it's a transitive godan verb"); putting them under it inverts reading order, and it contradicts the tag-then-answer intent. |
| Drop the SRS chip / level line to save the first screen | Costs information the study flow uses constantly (see the level-progress breakdown entry point). The answer moving up already buys the space. |
| Always hide mnemonics behind a disclosure | Fights quiz reveal (the mnemonic is part of the answer) and adds state; revisit only if prose length is still the main scroll driver. |
| Shrink the glyph from 80dp (sheet) / 96dp (lesson) | Independent ~16dp win, no information lost, but it changes the visual identity of the headline. Optional, cheap, unrelated to this arrangement. |
| Join multiple readings with `、` on one line when pitch accent is off | Legit micro-win (one line instead of N), but WaniKani vocabulary almost always has one reading; low priority. |
| Put components/radicals directly under the answer, above the writing zone | There is an argument (the mnemonic references the radicals), but it makes the second thing on the page a row of glyph tiles and pushes stroke order further down. Keep as-is. |

---

## 8. Accessibility / correctness notes

- **Headerless ≠ unlabelled — but the value must not be masked.** The plan was a
  `contentDescription = "Meaning"` on each half. That was **dropped on implementation**: a
  `contentDescription` on a semantics node replaces its text for TalkBack, so it would have hidden the
  very value it was labelling, and `mergeDescendants = true` without one would have folded the
  auxiliary-meanings text into the parent node — making the `AUXILIARY_MEANINGS_TEXT` tag unfindable in
  the merged semantics tree that the existing subject-detail and lesson tests click. The values are
  self-describing in reading order (English = meaning, kana = reading, plus the on'yomi/kun'yomi labels
  and the mnemonic eyebrows), so nothing was added. Revisit if a screen-reader user reports the field
  name being missed.
- **Added stable test tags** (`ANSWER_ZONE`, `MEANING_ANSWER`, `READING_ANSWER`) so tests can assert the
  pair's presence and position without depending on the `"Meaning"` string, which also appears in the
  stats cards and was previously ambiguous.
- **Gating unchanged:** `revealMeaning` still owns the meaning value *and* its mnemonic; `revealReading`
  the same. The split is positional only.
- **`showDividerAbove`** disappears (`SubjectReadingZone` no longer exists, since the two are one
  block). One divider now lives inside `SubjectMnemonicZone`, so the case "meaning revealed, reading
  not" just ends the answer block after the meaning — no stray rule.

---

## 9. What landed

`shared/.../designsystem/subjectdetail/SubjectAnswerSections.kt` (**new**; started life as `SubjectAnswerZone.kt` before revision 2 split the pair)

1. `SubjectMeaningAnswer(meanings, auxiliaryMeanings, resetKey)` — headerless meaning, tagged `MEANING_ANSWER`. Called from inside the headline so it lands under the characters.
2. `SubjectReadingAnswer(subjectType, readings, onyomi/kunyomi/nanoriReadings, pronunciationAudios, pitchAccents, showPitchAccent, restrictAudioToMp3)` — headerless reading, tagged `READING_ANSWER`. Both early-return when their values are empty, so callers only supply the reveal gate.
3. `SubjectMnemonicZone(subjectType, meaningMnemonic, meaningHint, readingMnemonic, readingHint, showMeaning, showReading)` plus its private `MnemonicBlock`. Revision 3: each block is titled with `SectionAccentHeader`, a divider separates the two blocks when both are shown, hints are muted, and the zone's own leading divider still renders only when it has something to show.
4. Moved here from `SubjectDetailContent.kt`: `AuxiliaryMeaningsText` (still public) and the reading rows/vocab list. `ReadingDisplayStyle` is now derived from `subjectType` alone, so callers can no longer pass a disagreeing `isVocabulary`/`hasReadingBreakdown`.

`shared/.../designsystem/components/SectionAccentHeader.kt` (**new in revision 3**)

5. `SectionAccentHeader(title, accent)` — the 4dp × 14dp rounded accent bar plus a `labelLarge` title, extracted from `RelatedSubjectsSection` so the mnemonic titles and the related-subject titles are literally the same composable.

`shared/.../designsystem/subjectdetail/SubjectDetailContent.kt`

6. `SubjectHeadline` now takes `showMeaning` and renders glyph → meaning (4dp) as one group, then a metadata group of level/type + SRS chip + POS chips 8dp below. Outer `Column` order: headline → reading → writing → components → mnemonics → context → similar → used-in → stats. `SubjectMeaningZone`, `SubjectReadingZone`, `KanjiReadingBreakdown`, `VocabularyReadingList`, `readingDisplayStyle`, `ReadingTypeRow` and the local `hasReadings` are gone. `SectionEyebrow` remains, now only for context sentences.
7. `AssignmentStats`/`reviewStats`/scroll-offset plumbing untouched: the reorder changes content height, but nothing in `SubjectDetailViewModel`'s `initialScrollOffset` bookkeeping depended on where the answer sat.

`shared/.../feature/lesson/LessonScreen.kt`

8. `LessonMeaningSection` / `LessonReadingSection` deleted; the study card builds the same headline (characters + meaning, then level/type + tags) and calls the same reading and mnemonic composables, so the two layouts can no longer drift. `hasReadingBreakdown` and the imports that only those sections used are gone.

Tests (`app/src/test/.../subjectdetail/SubjectDetailContentTest.kt`)

9. `levelAndType_sitOnTheirOwnLine_belowTheReading` (FULL reveal) — revision 7's guard: the level line's unclipped top is below the reading's bottom and its left edge is not pushed right of the meaning's (a full-width row, not a top-right annotation).
10. `reading_sitsAboveTheLevelLine_whenTheMeaningIsGatedAway` — the guard for the actual bug report: mid-quiz (`HIDE_UNTIL_ANSWERED`, reading question, answered) the meaning is absent, the reading's bottom is above the level line's top, and the meaning block really is gone (so the assertion tests what it claims). This ordering has now been rewritten three times — revision 2 asserted the level line *below the meaning*, revision 4 *above and right of it*, revision 6 *between the two* — and each rewrite is the test catching the layout move.
11. `vocabularyTags_sitBelowTheReading` — revision 4's other guard: the reading block's unclipped bottom is above the first part-of-speech chip.
12. `meaningAndReading_stayAboveTheWritingZone_andAreReachableWithoutScrolling` — with an available stroke order and much longer mnemonics, both are displayed without scrolling and the reading's unclipped bottom is above the stroke-order section's top.
13. `meaningAndReading_haveNoHeaders` — no `"Meaning"`/`"Reading"` titles, values still rendered, and the mnemonic blocks titled `"Meaning mnemonic"` / `"Reading mnemonic"` (sentence case, revision 3).
14. `kanjiWithReadingTypes_showsOnyomiAndKunyomiAsSeparateLabeledRows` — dropped the `performScrollTo()` calls and the now-false "below the meaning card" comment.
15. The reveal-gating tests are unchanged and still pass: they assert on values, and each value stays behind the same gate as its mnemonic.

Run: `./gradlew :shared:testAndroidHostTest :app:testDebugUnitTest` — green after each revision.

---

## 10. Open question resolved, plus one left open

- **Does anything rely on meaning/reading sitting past the fold?** No. The sheet's `initialScrollOffset`
  is keyed per subject and a fresh open starts at 0; the offset bookkeeping is only replayed on
  back-navigation, and no code depended on the answer's position within the content. The reorder landed
  in one pass with that plumbing untouched.
- **Should the reading join the meaning under the characters?** Left as-is for now — see
  **Current arrangement** at the top for the tradeoff.
