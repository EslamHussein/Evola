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

**Verified live on the Android emulator** against the real local dev server (Hetzner redeploy
blocked — no working SSH credentials in this session, see note below): register → Welcome →
Goal Setup (auto-title) → Home/Goals/Study/Materials/Profile all render correctly → edit goal →
sign out → log back in skips onboarding entirely and lands on Home with the persisted, edited
goal. Auto-login (from the earlier session-persistence work) and onboarding-gating confirmed
working together correctly on a killed-and-relaunched app too.

**Deployed to production (2026-08-02).** SSH access was restored (the key on file didn't match
any key on this machine — fixed via Hetzner rescue mode: injected a fresh key, mounted the real
disk, added it to `authorized_keys`, rebooted back to normal). `:server:installDist` output was
synced to `/opt/evola-server/{bin,lib}` and the `evola-server` systemd service restarted; Flyway
applied migration v5 automatically on boot. Verified live: curl smoke test of the full
register → create-goal → get-active-goal → users/me flow against
`https://46-224-177-47.sslip.io`, then a fresh Android install (pointed at production, not local)
confirmed login correctly skips onboarding and lands on Home with the persisted goal.

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

Deployed to production: `:server:installDist` synced to `/opt/evola-server`, service restarted,
V6 migration applied automatically on boot.

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
- [ ] Phase 6 — new Lesson Details hub (8 section rows), retires `LessonHomeScreen`, unifies the two
      different navigation paths into a lesson
- [ ] Phase 7 — vocabulary session backend redesign: pack-of-~5-words × 7-fixed-stages model,
      one-mastery-update-per-word rule, Free Production AI grading
- [ ] Phase 8 — vocabulary session UI redesign for the pack/stage model + pack summary screen
- [ ] Phase 9 — cross-cutting regression pass, full verification, production deploy

---

## M7 — Grammar Learning + mandatory answer-key validation

Goal: the mandatory validation pass is a correctness guarantee, not a nice-to-have — this is the
single biggest trust risk in the whole spec if skipped.

**AI pipeline** (`04_AI_PROMPTS.md` §3)
- [ ] Topic extraction (0–3 per lesson, never forced)
- [ ] Exercise generation (multiple-choice + fill-in-blank)
- [ ] **Second, independent model call validating every exercise before it's ever stored** —
      stronger model tier (per your decision)
- [ ] Discard invalid exercises; <3 valid → explanation-only + one recap question

**Backend**
- [ ] Reuses M6's mastery module unchanged
- [ ] Two-consecutive-correct advancement rule (not single-answer)
- [ ] `GET /lessons/{id}/grammar`, session/answer/complete endpoints

**Mobile**
- [ ] Grammar Topic Explanation (empty state when 0 topics), Exercise, Topic Summary screens

---

## M8 — Progress Dashboard

- [ ] `GET /goals/{id}/progress` — overall %, current lesson, streak, today-completed
- [ ] Streak calculated in the user's **local timezone**, resets on a missed day
- [ ] `daily_activity` writes on session completion
- [ ] Empty state (no lesson started) and stale-data fallback
- [ ] The signature gauge/dial component (first real use — shared, `percent`-prop component)

---

## M9 — Hardening

- [ ] Real email provider for password reset + verification (currently server-logged only)
- [ ] iOS Keychain-backed session storage (currently `NSUserDefaults` placeholder) + first
      simulator verification pass
- [ ] Analytics events (currently zero anywhere in the app)
- [ ] Crash reporting
- [ ] Monitoring/alerting on the Hetzner box
- [ ] Close remaining test-coverage gaps

---

## Decisions already locked in (so we don't re-litigate mid-build)

| Decision | Answer |
|---|---|
| Language scope | German-only for MVP |
| File storage | Local disk on the Hetzner box |
| iOS | Deferred — Android-only verification through M8 |
| Job queue | Keep the existing DB-polling worker, no Redis/BullMQ |
| AI model tiering | Cheap model for extraction/generation, strong model for answer-key validation |
| Cross-user extraction caching | Per-user only, not global by content hash |
| TTS | Skipped for now, no voice in this build |
