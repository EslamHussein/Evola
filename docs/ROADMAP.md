# Evola — Implementation Roadmap & Progress Tracker

This file is the single place to check what's actually done vs. planned. It's committed to git,
so it updates alongside the code — check it any time without needing to ask.

Rule for every milestone below: **it isn't checked off until every applicable layer is real** —
backend schema/API, mobile UI + states, AI pipeline (where involved), and at least a manual
verification pass. No moving to the next milestone with the current one half-finished.

Full gap analysis / feature inventory / clarifying-question backstory: see the published roadmap
artifact from this session (link in chat history) and `~/Downloads/files/` (the product spec kit).

Legend: `[x]` done and verified · `[~]` in progress · `[ ]` not started

---

## Local-First pivot — the app is now fully on-device (serverless) ✅

**Direction change (post-M8):** Evola no longer depends on a backend. Everything runs and is stored
on the device, single-user, no login. The old Ktor+Postgres backend (`:server`) and its
persistence module (`:integrations:persistence-shared`) are **retired and deleted from the repo**
(and the server that hosted them has been deleted).
The only network dependency that remains is Anthropic itself (Claude can't run on a phone) — the app
calls the Anthropic API **directly** with a Claude key the user stores **locally** (encrypted,
on-device; entered in Profile). Viable precisely because the app is pre-launch and effectively
single-user.

What made this a swap-in-place rather than a rewrite: the earlier Clean-Arch pass had already put
every screen behind a **repository interface** + `ApiResult`/`DataError` boundary, so each
`Http*Repository` was replaced by a `Local*Repository` implementing the identical interface — **zero
ViewModel or screen changes.**

- [x] **Phase 0** — SQLDelight foundation: plugin + `DatabaseDriverFactory` expect/actual + `.sq`
      schema (the server's V1–V15 collapsed into one initial on-device schema) + typed queries.
- [x] **Phase 1** — pure logic ported to `:shared/commonMain`: `MasterySrs`, `LessonSegmenter`,
      `computeStreak` (were server-only).
- [x] **Phase 2** — on-device encrypted `SecureStore` (Android EncryptedSharedPreferences; iOS
      NSUserDefaults) + Anthropic-key entry in Profile.
- [x] **Phase 3** — on-device `AnthropicClient` (raw Ktor → `api.anthropic.com`, key from the secure
      store) + the segmentation/vocabulary/grammar extraction pipelines ported as `commonMain`
      extractors (haiku for extraction, sonnet for grammar answer-key validation).
- [x] **Phase 4** — on-device file text extraction: `FileTextExtractor` (magic-byte MIME sniff) with
      Android (PdfBox-Android + ZIP DOCX) and iOS (PDFKit) actuals.
- [x] **Phase 5** — the five `Local*Repository` implementations over SQLDelight (Goals, Lessons,
      Materials-with-extraction-orchestration, Vocabulary pack/7-stage, Grammar per-topic session);
      `AppModule` flipped `Http*` → `Local*`. Unit-tested via an in-memory JDBC driver (Goals 3,
      Grammar 3, Vocabulary 4 — the session engines are covered without an emulator or network).
- [x] **Phase 6** — single-user, no login: deleted the auth screens + token lifecycle; first run
      goes straight to Goal Setup, gated only by whether an active goal exists locally.
- [x] **Phase 7** — retired the backend: deleted `:server` + `:integrations:persistence-shared` +
      the now-dead client HTTP/auth/session code; unwired both from `settings.gradle.kts`; CI no
      longer builds/tests a server or spins up Postgres. The hosting server has since been deleted.

Compiles green on `:composeApp` (Android + iOS) and `:shared` (jvm + tests) with **no server
running anywhere**.

**Live on-device verification (Android emulator, Pixel_7a) — passed.** Cold start → onboarding (no
login) → created a goal (written to SQLDelight, routed to Main) → Home dashboard aggregated from the
local DB → entered an Anthropic key in Profile (encrypted `SecureStore`) → pasted a short German
passage in "Entire" mode → **on-device extraction fired a real `api.anthropic.com` call and wrote 5
vocabulary items (with Arabic/IPA/tags/related-words) to the local DB** → Resource Details and the
Lesson Details hub rendered the real counts (and an honest "No grammar topics" for the tiny text) →
the vocabulary pack session ran through Discover / Recognition (RTL Arabic multiple-choice) / Reverse
Recall with each answer persisting. The remaining stages (Stage-6 AI grade, pack completion, grammar
session) are covered by the passing `LocalVocabularyRepositoryTest` / `LocalGrammarRepositoryTest`.

The old hosting server has been deleted — the app has no backend to run or maintain.

**Known constraint:** an Anthropic key on the device is safe only for a single-user/personal build;
distributing the app would need BYO-key-per-user or a thin proxy (which reintroduces a server).

---

## M0 — Housekeeping

Goal: clear the ground before touching real product features, so there's exactly one `Goal`
model and one mastery model in the repo, not two conflicting ones.

- [x] Delete dead pre-pivot modules: `contexts/`, `core/`, `integrations/ai-gateway/` (kept
      `integrations/persistence-shared/` — `:server` actually depends on its `DatabaseFactory`)
- [x] Remove corresponding entries from `settings.gradle.kts`
- [x] Delete orphaned unused packages inside `:shared` (`evola.core.*`, `evola.tutoring.*`,
      `evola.vocabulary.domain.*`, old `Goal.kt`, old `MasteryScore.kt`) — none were imported by
      any live auth/materials code
- [x] Verify full build still compiles: `:server`, `:shared` (jvm + iosSimulatorArm64),
      `:composeApp` (android) — all green
- [x] Commit the cleanup
- [x] Add a minimal CI workflow (build + test on push, `.github/workflows/ci.yml`) — Postgres
      service container on GitHub Actions, compiles `:server`/`:shared`/`:composeApp` (android),
      runs `:server` tests. iOS not covered (needs a macOS runner — deferred with iOS generally)
- [x] Add first automated tests for `AuthService` (10 tests: register, duplicate email, weak
      password, login success/failure, lockout at 5 fails, window-expiry reset, password reset
      confirm success/expired-token, refresh/logout) and `MaterialService` (3 tests: new upload
      queues a job, duplicate normalized content dedupes to one job, per-user material listing) —
      all 13 passing against a real Postgres running the actual Flyway migrations

---

## M1 — Full schema-reset migration

Goal: bring in the complete remaining schema in one migration, even though most tables stay
unused until their own milestone — avoids incremental migrations mid-build (the kit's own advice).

> **Discovered while writing M0 tests:** `V1__init.sql` already created an old `goals` table
> (`exam_type`, `target_date`, `readiness_overall`, `readiness_by_skill`) plus orphaned
> `mastery_scores` and `study_plans` tables — the same abandoned exam-readiness model as the
> `Goal.kt` deleted in M0, but on the **live DB schema**, not just in Kotlin. Only throwaway test
> data exists in these tables (confirmed via local dev Postgres), so M1 needs to `DROP` and
> recreate `goals` with the kit's real shape (`goal_text`, `title`, `is_active`), and drop
> `mastery_scores`/`study_plans` outright — nothing downstream uses them.

- [x] `DROP TABLE` the old `goals`, `mastery_scores`, `study_plans` (exam-readiness model, superseded)
- [x] `goals` table, kit shape (+ unique partial index enforcing one active goal per user)
- [x] `lessons` table
- [x] `vocabulary_items`, `vocabulary_progress`, `vocabulary_sessions` tables
- [x] `grammar_topics`, `grammar_exercises`, `grammar_progress`, `grammar_sessions` tables
- [x] `daily_activity` table
- [x] Extend `Tables.kt` with matching Exposed table objects for all of the above
- [x] Verify migration applies cleanly — confirmed against both the `evola_test` test database
      and the real local dev `evola` database (server boots, Flyway applies v5, `/health` OK,
      18 tables present, no data loss on the 6 pre-existing test users/materials)
- [ ] *(Deferred to M3 by design — not part of this migration): `materials.goal_id` linkage,*
      *added when the real upload flow needs it, to keep this migration scoped to net-new tables*

---

## M2 — Onboarding + Goal Setup + navigation shell ✅ done

Goal: unblock everything downstream — nothing has a `goal_id` to attach to until this exists.

**Backend**
- [x] `POST /goals`, `PATCH /goals/{id}`, `GET /goals/{id}` + `GET /goals/active` (convenience
      lookup, not in the kit verbatim — needed so the client can discover its goal without
      already knowing the id) — all authenticated via the existing JWT principal
- [x] `onboarding_completed` flips true only once Welcome is seen **and** a goal exists — the
      server sets it as a side effect of the first successful goal creation, since the client's
      own navigation guarantees Welcome was shown before Goal Setup is reachable
- [x] 8 `GoalServiceTest` cases (auto-title, explicit title, duplicate-active-goal rejection,
      validation bounds, update scoped to owner, `getActiveGoal`) — 21/21 tests passing project-wide

**Mobile**
- [x] Onboarding Welcome screen (static explainer + Continue)
- [x] Goal Setup screen — freeform `goal_text` (3–200 chars, live truncation warning), optional
      `title` (auto-generated as "My {first ~5 words} Journey" if blank)
- [x] `onboarding_completed` gating wired into `App.kt` startup routing (verified: killing and
      relaunching the app mid-onboarding correctly resumes at Welcome, not Login or Materials)
- [x] Persistent 5-tab bottom bar: Home · Goals · Study · Materials · Profile — Materials tab
      reuses the existing `MaterialsListScreen`; its add/detail sub-screens hide the tab bar
- [x] Goal editing from Profile (inline edit form) without repeating onboarding

**Verified live on the Android emulator** against a local dev server: register → Welcome →
Goal Setup (auto-title) → Home/Goals/Study/Materials/Profile all render correctly → edit goal →
sign out → log back in skips onboarding entirely and lands on Home with the persisted, edited
goal. Auto-login (from the earlier session-persistence work) and onboarding-gating confirmed
working together correctly on a killed-and-relaunched app too.

**Deployed and verified (2026-08-02):** a curl smoke test of the full
register → create-goal → get-active-goal → users/me flow, then a fresh Android install confirmed
login correctly skips onboarding and lands on Home with the persisted goal. *(This milestone
predates the local-first pivot; the backend it deployed has since been retired.)*

---

## M3 — Material Upload rebuild ✅ done

Goal: replace the current paste-text stopgap with the spec's real upload flow.

**Backend**
- [x] Real multipart `POST /materials/upload` (`file`, `goal_id`) — local disk storage
      (`uploads/<material-id>.{pdf,docx}`, path configurable via `UPLOADS_DIR`)
- [x] Server-side MIME validation from file content (magic bytes), never the client's declared
      type or extension — PDF via `%PDF-` header, DOCX via zip-with-`word/document.xml` check
- [x] 25MB size cap
- [x] Content-hash duplicate detection, scoped per-user (not global) — returns 409 with
      `existing_material_id`, client shows a "view existing material?" prompt
- [x] Corrupted/password-protected/no-extractable-text all rejected before queueing, each with
      its own error code (`PASSWORD_PROTECTED`, `CORRUPTED_FILE`, `NO_EXTRACTABLE_TEXT` — the
      latter two aren't in the kit's literal contract but the spec's own edge cases require
      distinguishable messaging for them)
- [x] `GET /materials/{id}/status` (status + page count — true live page-by-page progress
      tracking for very large files is *not* implemented, scoped out same as the spec's own
      "not designed yet" admission for that edge case), `POST /materials/{id}/reprocess`
- [x] All materials routes now authenticated (JWT) + ownership-checked — closes a pre-existing
      gap where `/api/materials` had no auth at all
- [x] `materials.goal_id`/`file_ref`/`mime_type`/`size_bytes`/`page_count` added via
      `V6__materials_goal_linkage_and_file_metadata.sql` (deferred from M1 by design)
- [x] 10 new `MaterialServiceTest` cases using real generated PDF/DOCX bytes (PDFBox/POI) —
      valid upload, unsupported type, oversized, no-extractable-text, goal ownership, per-user
      duplicate detection, cross-user non-conflict, listing scoping, reprocess gating — 38/38
      tests passing project-wide

**Mobile**
- [x] Real file picker (`expect`/`actual`, Android via `ActivityResultContracts.OpenDocument`
      restricted to PDF/DOCX MIME types; iOS deferred with a placeholder, same precedent as
      `SessionStorage.ios.kt`) → real multipart upload, not text paste
- [x] Add Material screen rewritten: pick file → shows filename → upload, with distinct error
      messages per rejection reason and a duplicate-file dialog (view existing / cancel)
- [x] Material Detail screen adds a Retry button on the failed state, wired to
      `POST /materials/{id}/reprocess`
- [ ] *(Not implemented: resumable upload progress for very large files — same scoped-out
      "300+ page book" edge case as the status-progress field above)*

**Verified live end-to-end on the Android emulator** against the real local dev server (files
pushed onto the emulator via adb + media-scanner broadcast so the system picker could see them):
register → onboarding → goal → Materials tab → real system file picker (correctly filtered to
PDF/DOCX only) → pick a real DOCX → upload → live "Analyzing..." status → polls to completion →
real extracted vocabulary and grammar rendered from the real Anthropic API call. Server-side also
verified directly via curl with real PDF and DOCX files (generated via macOS `cupsfilter`/
`textutil`): upload, status, detail, list, duplicate-rejection (409 + existing id), unsupported-
type rejection, and unauthenticated-request rejection (401) all behaved exactly as designed.

Deployed and verified end-to-end (backend since retired in the local-first pivot).

---

## M4 — Automatic Lesson Generation

Goal: get this pipeline solid before building on top of it — everything downstream inherits
lesson quality directly. Highest-risk milestone in the roadmap.

Triggered by a real production bug: uploading a 146-page German-Arabic glossary
(`A2 - Wortschatz.pdf`) always failed under the old M2/M3 single-shot pipeline (whole document in
one model call, fixed `maxTokens`, truncated/unparseable JSON on both model tiers). Fixed by
replacing that pipeline with the real spec design below — confirmed live: the same material now
produces 60 real lessons in production.

**AI pipeline** (`04_AI_PROMPTS.md` §1)
- [x] Heuristic heading-detection pass first (regex/structural markers) — skip the LLM call when
      confident. Guards against false positives (title-page/edition-line matches) by rejecting any
      resulting segment that's implausibly large, not just requiring 2+ markers.
- [x] LLM segmentation fallback on ~16k-char chunks (conservative for a 6-8k token target - real
      mixed-script content tokenizes far denser than plain-prose estimates) with overlap, merged
      across chunks
- [x] Enforce lesson granularity (~15–25 vocab-worthy words) and the 60-lesson cap in application
      code, not the prompt alone (`LessonSegmenter.mergeAndCap`)
- [x] Language/unsupported-content detection → `unsupported_content` status. Only the *whole
      material*, not a single chunk, is marked unsupported - a front-matter/copyright chunk being
      flagged doesn't invalidate lessons found in the rest of a real document.
- [x] Retry ×3 with backoff per chunk; partial-success keeps completed lessons usable, `reprocess()`
      retries only the failed chunk ranges
- [x] Tiered model use: `claude-haiku-4-5` only (per your decision)
- [x] Extends the existing DB-polling worker pattern (per your decision — no new job-queue infra) -
      repurposes `extraction_jobs`/`extraction_cache` (V7/V8 migrations) instead of new tables

**Mobile**
- [x] Lessons Ready Summary screen (partial-success state) - replaces the old vocab/grammar/exercise
      display in `MaterialDetailScreen.kt`

---

## M5 — Lesson Selection

- [x] `GET /goals/{id}/lessons` - ordered by (created_at, number), giving "grouped by source,
      appended at the end" for later-uploaded materials with no join needed
- [x] Sequential lesson list, ungated, with per-lesson completion % - vocab/grammar progress is
      honestly 0% until M6/M7 populate real content, not a faked number
- [x] "Still preparing" disabled row, all-complete state - every lesson today is `pending` (M6/M7
      haven't shipped), so this is the actual live behavior, not just a code path
- [x] Current-lesson derivation (first lesson not at 100%) - surfaced as a "Continue" badge on
      that row within the list itself (dashboard placement deferred to M8)

Confirmed live: the Study tab now shows all 60 real lessons from `A2 - Wortschatz.pdf` in
production, in order, each disabled with "Still preparing...".

---

## M6 — Vocabulary Learning + shared mastery/SRS module ✅ done

Goal: build the mastery/SRS module carefully here — Grammar (M7) reuses it unchanged.

**AI pipeline** (`04_AI_PROMPTS.md` §2)
- [x] Extraction prompt + schema validation - per-lesson `claude-haiku-4-5` call, auto-queued the
      moment a lesson is materialized (both the LLM-segmentation path and the cache-hit fast path)
- [x] Case-insensitive dedup against existing `vocabulary_items`, scoped to the lesson's goal

**Backend — shared module**
- [x] Mastery state machine: `new → learning → reviewing → mastered` (`MasterySrs.kt`, pure
      functions, no DB access - the literal shared module Grammar/M7 will reuse unchanged)
- [x] Fixed interval ladder `[1, 3, 7, 16, 35]` days
- [x] `POST /lessons/{id}/vocabulary/session` — resumes an incomplete session or assembles new
      (this lesson, cap 12) + due-review (other lessons, cap 15) + mastered-fallback
- [x] `POST /vocabulary-sessions/{id}/answer`, `.../complete`

**Mobile**
- [x] Vocabulary Session (consolidated Start/Drill/Summary state machine), typed-recall (tolerant
      client-side matching) + both multiple-choice directions, List screen with mastery badges
- [x] Incorrect-answer resurfacing (new session-item occurrence a few positions later, not
      immediately), zero-usable-vocabulary and nothing-due-today fallback messaging
- [x] *(Audio/TTS playback deferred per your decision — not in this pass)*

Confirmed live: a real lesson from `A2 - Wortschatz.pdf` (already segmented in production since M4)
flipped from `pending` to `ready` with real extracted German vocabulary via the deployed extraction
worker. Full session/answer/complete flow verified via curl against production on a controlled
test lesson: 13 items answered (12 pool + 1 resurfaced after a deliberate wrong answer), 92.3%
accuracy computed correctly, all answered items' `mastery_state` advanced to `learning`. Verified
on the Android emulator against local: Study tab → Lesson Home → vocabulary session → both drill
types → list screen with badges, including a live tolerant-match check (a single-character-typo
answer was still accepted).

---

## M6.5 — Design handoff: Add Resource → AI Wizard → Resource/Lesson Details → Pack-based Vocabulary Session

Goal: a design handoff (`design_handoff_evola_mobile/`) specifies a redesigned "add material → study
a lesson" path — Add Resource, a new 4-step AI Analysis Wizard, restyled Resource Details, a new
Lesson Details hub, and a fundamentally different pack/7-stage Vocabulary Learning Session. Recreated
using the app's existing light EvolaTheme (not the handoff's dark "Nocturne" system). Arabic is the
real target native-language going forward, with RTL support and new extraction fields (IPA,
related words, difficulty/frequency, memory tip). Where the design assumes backend capability that
doesn't exist yet (Grammar, Reading/Writing/Speaking/Listening, manual lesson ranges), those
affordances render per the design but locked/"coming soon" rather than faked — Grammar's locked row
is exactly what M7 below unlocks, Progress's locked row is what M8 unlocks. Full plan:
`/Users/eslam.megali/.claude/plans/project-evola-fuzzy-cocoa.md`.

- [x] Phase 0 — design-system foundations (spacing tokens, chip/tile/tag/ring components, RTL text,
      Arabic font check) — verified on-emulator against EvolaTheme + Noto Naskh Arabic
- [x] Phase 1 — Add Resource redesign (type grid) + real Text-paste ingestion (keeps existing DOCX)
      — `POST /materials/upload-text` rejoins the existing dedup/segmentation/vocab pipeline;
      verified end-to-end (paste → lesson ready) via curl against production
- [x] Phase 2 — AI Wizard backend: real entire-doc/auto-detect org modes, real AI-instructions
      prompt interpolation — V11 migration; ai_instructions interpolates into vocabulary
      extraction only (not the shared, content-hash-cached segmentation prompt - see commit for
      why the original segmentation_key idea was dropped); verified live in production
- [x] Phase 3 — AI Wizard UI (4 steps) + the processing/loading screen the handoff's own README
      flags as a gap — Start Analysis is the real upload point now; verified full walkthrough
      on-emulator + confirmed persisted fields in Postgres
- [x] Phase 4 — vocabulary extraction schema expansion: Arabic `meaning_ar`, IPA, related words,
      difficulty/frequency ratings, memory tip — V12 migration; verified live in production
      (real extraction populated every new field) and on-emulator (Vocabulary List renders
      correct RTL Arabic script alongside the new tags)
- [x] Phase 5 — Resource Details redesign (progress ring, status tags, meta stats) — lesson-card tap
      navigation deferred to Phase 6 (no real destination exists until then); verified on-emulator
      and in production that vocab_count/vocab_progress compute and render correctly
- [x] Phase 6 — new Lesson Details hub (8 section rows), retires `LessonHomeScreen`, unifies the two
      different navigation paths into a lesson — new `GET /lessons/{id}` resolves ownership through
      the lesson's material rather than its goal, so both the Materials tab (Resource Details →
      lesson card) and the Study tab (flat lesson list → lesson row) fetch and render identical
      state for the same lessonId; only Vocabulary is unlocked, the other 7 rows are honest
      `LockedRow` "Coming soon" placeholders for M7 (Grammar)/M8 (Progress) to unlock later; verified
      on-emulator that both entry paths land on identical data and that back-navigation returns each
      to its own origin
- [x] Phase 7 — vocabulary session backend redesign: pack-of-~5-words × 7-fixed-stages model,
      one-mastery-update-per-word rule, Free Production AI grading — V13 migration
      (`vocabulary_packs`/`vocabulary_pack_words`/`vocabulary_stage_answers` + bookmark/
      marked-difficult columns on `vocabulary_progress`; `vocabulary_sessions`/
      `vocabulary_session_items` left in place, unused); reuses all 4 existing route paths with
      repurposed bodies (`POST .../session` starts/resumes a pack, `POST .../answer` gains a
      `stage_index`, `POST .../complete` unchanged shape); new `PATCH /vocabulary-items/{id}/flags`;
      Stages 2-4 validate via the shared `isTolerantMatch`, Stage 5 via a new deterministic
      sentence-overlap match, Stage 6 (Free Production) via a real Anthropic call
      (`FreeProductionGrader`, logged via `model_call_log`, no rate limit yet per the plan's
      explicit cost-risk sign-off) - exactly one `MasterySrs` update per word, evaluated once all 7
      stages are answered, not per-stage; verified with 14 new server unit tests (pack assembly
      caps, stage sequencing, the one-mastery-update rule, Stage 5 fuzzy-match edge cases, a fake
      `VocabularyGrader` for Stage 6) and a full local curl walkthrough with a real Anthropic key
      (all 7 stages, real AI feedback, correct 20% pack accuracy). **Breaking, on purpose**: the
      wire shape changes for all 4 routes, so the old (Phase 3-era) `VocabularySessionScreen`
      client breaks until Phase 8 replaces it - shipped this way deliberately, matching the plan's
      own phase-by-phase sequencing, since this is still a pre-launch/internal build
- [x] Phase 8 — vocabulary session UI redesign for the pack/stage model + pack summary screen —
      new `VocabularyPackSessionScreen`/`VocabularyPackSessionViewModel` (replaces
      `VocabularySessionScreen`/`VocabularySessionViewModel`) + `PackSummaryScreen`; one composable
      per stage (Discover/Recognition/Reverse Recall/Partial Recall/Sentence Completion/
      Translation/Free Production) sharing a `SegmentedProgressBar` header and a
      Check→reveal→Continue/"Finish pack" footer; gender badge shape (circle/diamond/square) never
      color-only; bookmark and "mark as difficult" icons wired to the real
      `PATCH /vocabulary-items/{id}/flags` endpoint; "Show/Hide AI explanation" reuses the single
      extracted `memory_tip` field rather than a second AI call, per the plan's own flagged
      simplification; audio button stays visual-only (no real TTS, out of scope); verified on-emulator
      end-to-end with real data - all 7 stages for one word (including real AI grading and its
      genuinely useful grammar feedback), word-to-word advancement, and both flag toggles confirmed
      via direct DB queries after tapping
- [x] Phase 9 — cross-cutting regression pass, full verification: confirmed `VocabularyListScreen`
      still renders every Phase 4 field, confirmed no dangling `LessonHomeScreen`/old
      `VocabularySessionItem`/`drillType` references anywhere in the tree, full `./gradlew
      :server:test` + `:composeApp` Android compile + an iOS `compileKotlinIosSimulatorArm64`
      build-only check (all green - the file-picker stub and no-op-TTS `expect`/`actual`s still
      resolve), and one final full-stack production check confirming Lesson Details (Phase 6) and
      pack-session resume (Phase 7/8) report consistent state for the same lesson against the
      already-deployed server. M6.5 is fully shipped.

---

## M7 — Grammar Learning + mandatory answer-key validation ✅ done

Goal: the mandatory validation pass is a correctness guarantee, not a nice-to-have — this is the
single biggest trust risk in the whole spec if skipped. Full design in
`/Users/eslam.megali/.claude/plans/project-evola-fuzzy-cocoa.md` (prepended at the top of that
file). Architecturally simpler than M6.5's pack/stage Vocabulary redesign — Grammar stays a
per-topic flat session (the whole exercise list returned in one `POST`), matching
`03_API_CONTRACT.md`'s Grammar section almost exactly. `grammar_topics`/`grammar_exercises`/
`grammar_progress`/`grammar_sessions` already exist (M1 reset migration), empty, unused, and are
already declared 1:1 in `Tables.kt` — only two new tables needed (`grammar_extraction_jobs`,
`grammar_session_answers`, V15).

**AI pipeline** (`04_AI_PROMPTS.md` §3)
- [x] Topic extraction (0–3 per lesson, never forced) — `claude-haiku-4-5`
- [x] Exercise generation (multiple-choice + fill-in-blank only) — `claude-haiku-4-5`
- [x] **Second, independent model call validating every exercise before it's ever stored** —
      `claude-sonnet-5` (decided; logged as `modelTier = "LARGE"`)
- [x] Discard invalid exercises; <3 valid → explanation-only + one recap question (reuses the
      exercise-generation prompt with a `recapMode` flag rather than a new prompt)
- [x] Auto-queued in parallel with vocabulary extraction per lesson (new `onGrammarJobQueued`
      callback, mirroring `onVocabJobQueued`) — never touches `lessons.status`

**Backend**
- [x] `MasterySrs.onPartialCorrect` (new, additive-only) implements the two-consecutive-correct
      advancement rule via a caller-side parity branch in `GrammarService` — `onCorrect`/
      `onIncorrect` themselves stay unchanged
- [x] `GET /lessons/{id}/grammar`, `POST /grammar-topics/{id}/exercise-session`,
      `POST /grammar-sessions/{id}/answer`, `POST /grammar-sessions/{id}/complete`
- [x] Client self-grades and reports `correct` (same trust model vocabulary used pre-redesign)
- [x] Fixes a real bug found during planning: `goals.Lesson.completionPct` branches on
      `grammarProgress == 0f` (indistinguishable from "topic exists at 0% mastery") instead of
      `grammarCount == 0` — corrected as part of this milestone, plus the same corrected formula
      added to `materials.Lesson`

**Mobile**
- [x] `GrammarTopicListScreen`/`GrammarExerciseSessionScreen` (built from the pre-redesign
      vocabulary session template), empty state when 0 topics
- [x] Fixes a real, currently-latent bug in `LessonDetailScreen`'s `SectionRow`: it hardcoded the
      Vocabulary callbacks for every unlocked section regardless of `section.key` — invisible before
      this milestone since only Vocabulary was ever unlocked; would have misrouted Grammar's row
      straight into the vocabulary session

Confirmed live via curl against production: a real German lesson (Präteritum, Possessivpronomen,
Trennbare Verben) yielded 3 topics and 17 validated exercises (5–6, 6, 6 per topic) after 2 were
discarded by the mandatory `claude-sonnet-5` validation pass — `model_call_log` shows all three new
task types (`GRAMMAR_TOPIC_EXTRACTION`/`GRAMMAR_EXERCISE_GENERATION` at `modelTier=SMALL`,
`GRAMMAR_ANSWER_VALIDATION` at `modelTier=LARGE`, 19 validation calls for 17 kept exercises) running
in parallel with vocabulary extraction on the same lesson. A full answer/complete loop against
production confirmed: the two-consecutive-correct rule (first correct → mastery unchanged, second
consecutive correct → advances one stage), a wrong answer dropping mastery immediately regardless of
streak, idempotent replay of an already-answered exercise returning the stored snapshot instead of
re-applying `MasterySrs`, and `complete` computing `exercises_completed`/`accuracy` correctly (5
answered, 1 wrong → 80%). Verified on the Android emulator against local: both the Materials-tab and
Study-tab entry points reach the identical `LessonDetailScreen`; tapping the unlocked Grammar row
correctly opens the topic list (not the vocabulary session — the exact routing bug this milestone
fixed); a full multiple-choice + fill-in-blank exercise session with correct/incorrect branching and
a completion summary; the Resource Details ring updating live to the combined vocab+grammar formula.
90/90 server tests passing (`MasterySrsTest`, `GrammarServiceTest`, extended `MaterialServiceTest`/
`GoalServiceTest`). V15 migration deployed and applied cleanly (backend since retired).

---

## M8 — Progress Dashboard ✅ done

The smallest milestone so far — a pure aggregation of M6/M7, no AI calls, and **no new migration**
(`daily_activity` existed since V5 but had never been written to). The one real design problem was
the local-timezone streak rule: the client (the only party that knows its timezone) computes and
sends its own local date on both the write path (session completion) and the read path (dashboard
fetch) via `kotlinx-datetime`, so a session at 23:58 vs 00:02 lands on the right calendar day.

- [x] `GET /goals/{id}/progress` — `overall_pct` (average of every lesson's own combined vocab+
      grammar completion, the M7-corrected `grammarCount` formula reused), `current_lesson_id`
      (first lesson still < 100%, null once all done), `streak_days`, `today_completed`
- [x] Streak calculated in the user's **local timezone**, resets 0 the day *after* a missed day
      (pure, unit-tested `computeStreak`); `today_completed` + streak read from `daily_activity`
- [x] `daily_activity` writes idempotently on both vocabulary and grammar session completion —
      both `complete` endpoints gained an optional `{"local_date": "..."}` body (older clients
      that send none fall back to the server's UTC date)
- [x] Home tab becomes the real dashboard: empty state for a 0-lesson goal, readiness dial +
      streak row + "Continue Lesson N" once there's something to study, all-complete celebration
      when every lesson is done. Continue reuses the Study tab's existing lesson stack.
- [x] The signature gauge/dial is `CircularProgressRing` (built in M6.5 Phase 0), reused as-is at
      160dp for the dashboard hero — one shared `percent`-prop component, not re-drawn per screen.

Confirmed live via curl against production: a fresh goal returns the honest zero state
(`overall_pct 0`, null current lesson, streak 0); completing a real grammar session with
`local_date=2026-08-04` flipped `today_completed` to true and `streak_days` to 1, with
`current_lesson_id` pointing at the still-incomplete lesson. Streak-across-a-boundary verified
locally: seeding 08-02/08-03/08-04 reads as streak 3 on 08-04 (today done), still 3 on 08-05 (not
yet done — the streak only resets the *following* day), and 0 on 08-06 (a full day's gap). Verified
on the Android emulator: the Home dashboard renders the readiness dial, streak row with the fire
icon and "Not done yet"/"Done today" pill, and the "Continue Lesson N: Title" CTA; tapping Continue
switches to the Study tab and lands on the same `LessonDetailScreen` the lesson list itself uses.
105/105 server tests green (new `DailyActivityTest` + `getProgress`/`daily_activity` coverage). No
migration to deploy — just the new jar.

---

## Vocabulary Engine Replacement — Lingvist-style flat SRS queue ✅ done

Full replacement of the M6.5 pack/7-stage vocabulary session (Discover → Recognition → Reverse
Recall → Partial Recall → Sentence → Translation → Free Production, in packs of ~5 words) with a
Lingvist-style flat, priority-ordered SRS queue: exactly two card types (a one-time Intro card, a
Fill-in-the-Blank drill), repeats > due-review > new words in priority, and a 5-status word
lifecycle (`unseen → introduced → learning → review → mastered`) on a fixed 1/3/7/14/30-day
interval ladder. A real cost win alongside the redesign: the retired engine's Free Production stage
needed one AI grading call per word per pack; the new engine grades Fill-Blank deterministically via
the existing `isTolerantMatch` — **zero per-answer AI calls** anywhere in the vocabulary flow. The
only AI cost is still the one-time-per-lesson extraction call, which gained one new field
(`grammar_note`, a per-sentence grammar tip) at no extra call.

- [x] `VocabularySrs.kt` — new pure scheduler (`shared/.../vocabulary/`), deliberately separate from
      `MasterySrs` (Grammar's own 4-stage ladder, completely untouched by this change)
- [x] `Vocabulary.sq` rewritten: `vocabulary_progress` moves to the 5-status model;
      `vocabulary_packs`/`vocabulary_pack_words`/`vocabulary_stage_answers` dropped; new
      `vocabulary_sessions` + `vocabulary_session_queue` (a persisted, ordered card queue — same
      resumable-session guarantee every other session in this app already has)
- [x] `LocalVocabularyRepository` rewritten: queue assembly (due-review this lesson first, then
      elsewhere, then new words each paired with an Intro card immediately before their first
      Fill-Blank), wrong answers splice a repeat card a few queue positions later (never
      immediately next, reusing this project's own established resurfacing precedent)
- [x] `VocabularyFreeProductionGrader`/`AiVocabularyFreeProductionGrader` deleted entirely — no
      replacement needed, the new engine has no AI-graded card type
- [x] Compose UI: `VocabularySessionScreen`/`VocabularySessionViewModel` (renamed from
      `VocabularyPackSessionScreen`/`ViewModel`) render exactly the two new card types;
      `SessionSummaryScreen` (renamed from `PackSummaryScreen`) keeps the same stat-card shape
- [x] `LocalProgress.lessonVocabProgress` redesigned around the new 5-status ladder (same
      status-index/(count-1) shape `lessonGrammarProgress` already uses against `MasterySrs`)

Verified: `VocabularySrsTest` (new, ladder transitions incl. the introduced→learning floor) +
`LocalVocabularyRepositoryTest` (rewritten — intro-before-fill-blank pairing, correct-answer
advancement, incorrect-answer floor-at-learning, repeat-card requeue timing, session completion) +
`LocalGoalsRepositoryTest`'s progress cases, all green. `:shared` and `:composeApp` compile clean on
both Android and iOS (`compileKotlinIosSimulatorArm64`). Fresh install boots cleanly on the rewritten
schema on the Android emulator (confirms the new `CREATE TABLE` set is valid at runtime, not just at
compile time) — a full live session walkthrough with real extracted vocabulary is a user follow-up,
since it requires the user's own Anthropic key entered in Profile.

---

## Home dashboard: vocabulary breakdown (Not started / Learning / Mastered) ✅ done

Small follow-up to the vocabulary engine replacement: the Home dashboard's readiness ring gained a
row of three stat tiles underneath it, bucketing every word in the goal by its current SRS status
(`VocabularySrs.STATUSES`) - `unseen` → Not started, `mastered` → Mastered, everything between
(`introduced`/`learning`/`review`) → Learning. The ring stays the headline "how close am I" number;
the tiles are "what it's made of," not a replacement.

- [x] `GoalProgress` gains a `VocabularyBreakdown(notStarted, inProgress, mastered)` field
- [x] New `wordStatusesByGoal` query (items → lessons → progress, scoped to the whole goal)
- [x] `HomeScreen`'s new `VocabularyBreakdownRow` composable, reusing the same stat-card shape as
      the session summary screen

Verified live on the Android emulator with real extracted vocabulary from earlier in the same
session: a goal at 15% readiness correctly showed 7 Not started / 3 Learning / 0 Mastered for its
10-word lesson.

---

## Per-word difficulty ladder for new words (Intro→Recognition→WordBank→Hint→Blind) ✅ done

Extends the flat SRS queue engine: a **new** word now walks its own 5-step difficulty ladder instead
of a single Intro→Fill-Blank pairing - `intro → recognition (multiple-choice) → wordbank (tap the
term from a word bank) → hint (typed, pre-filled with the first half of the term) → blind (typed,
no scaffolding)`. Correct answers advance the word to the next harder step, spliced in as the very
next card; wrong answers re-queue the same step, appended past every existing row so it resurfaces
later without disrupting anything already queued. Due-for-review words (and the mastered-fallback
pool) skip the ladder entirely and open straight on `blind`, exactly as before this change - no code
path needed updating there.

A genuine simplification came out of this pass: the original flat-queue engine's wrong-answer
placement logic (`queuePositionAtOffset` + `shiftQueuePositionsFrom`, "a few positions later, never
immediately next") was more complex than needed. It's replaced uniformly by "append past every
existing row" - trivially satisfies "not immediately next," needs no position-shifting, and the two
old queries are deleted as dead code.

- [x] `vocabulary_session_queue` gains a `choices` column (JSON-encoded, persisted so a resumed
      session doesn't reshuffle recognition/wordbank options)
- [x] `VocabularyCard` sealed type grows from 2 to 5 variants (`Intro`, `Recognition`, `WordBank`,
      `Hint`, `Blind`); `VocabularyRepository.submitFillBlank` splits into `submitChoice`
      (Recognition/WordBank) and `submitTyped` (Hint/Blind)
  - [x] `LocalVocabularyRepository`: a word only ever has one queued card at a time - the next rung
      is spliced in dynamically (`answeredPosition + 1`) rather than the whole ladder being
      pre-inserted, so a wrong answer genuinely can't skip ahead
- [x] Every graded step (not just the final one) still drives the word's cross-session SRS status
      and next-review date, exactly like the single Fill-Blank answer always did - confirmed as the
      intended design with the user rather than assumed
- [x] Recognition/WordBank distractors reuse the existing `allUserVocab` query - no new AI call, no
      `VocabularyExtractor` changes

Verified: `LocalVocabularyRepositoryTest` covers a full correct ladder walk (confirms mastery climbs
learning→review→mastered one rung at a time), a wrong answer at any rung (confirms no premature
advancement, confirms the repeat doesn't appear immediately next but does resurface), and a
due-for-review word opening straight on `blind`. `:shared` + `:composeApp` compile clean on Android
and iOS. Fresh install boots cleanly on the schema change (required a wipe - no versioned migrations
exist in this local-only DB, and the old `card_type`/missing `choices` column would otherwise crash
at runtime). A full live ladder walkthrough with real vocabulary is a user follow-up, since it needs
the Anthropic key re-entered in Profile after the wipe.

---

## M9 — Hardening

- [ ] Real email provider for password reset + verification (currently server-logged only)
- [ ] iOS Keychain-backed session storage (currently `NSUserDefaults` placeholder) + first
      simulator verification pass
- [ ] Analytics events (currently zero anywhere in the app)
- [ ] Crash reporting
- [ ] Close remaining test-coverage gaps

---

## Decisions already locked in (so we don't re-litigate mid-build)

| Decision | Answer |
|---|---|
| Language scope | German-only for MVP |
| File storage | On-device (local-first) |
| iOS | Deferred — Android-only verification through M8 |
| Job queue | Keep the existing DB-polling worker, no Redis/BullMQ |
| AI model tiering | `claude-haiku-4-5` for extraction/generation, `claude-sonnet-5` for grammar answer-key validation (M7) |
| Cross-user extraction caching | Per-user only, not global by content hash |
| TTS | Skipped for now, no voice in this build |
