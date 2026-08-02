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

## M3 — Material Upload rebuild

Goal: replace the current paste-text stopgap with the spec's real upload flow.

**Backend**
- [ ] Real multipart `POST /materials/upload` (`file`, `goal_id`) — local disk storage
- [ ] Server-side MIME validation (not extension-based): PDF/DOCX only
- [ ] 25MB size cap
- [ ] Content-hash duplicate detection surfaced to the user (reprocess-or-skip prompt)
- [ ] Corrupted/password-protected/scanned-no-text rejection before queueing
- [ ] `GET /materials/{id}/status`, `POST /materials/{id}/reprocess`

**Mobile**
- [ ] Real file picker → multipart upload (not text paste)
- [ ] Upload progress screen, resumable on nav-away for large files
- [ ] Processing-failed screen (retry / re-upload, scanned-PDF-specific messaging)

---

## M4 — Automatic Lesson Generation

Goal: get this pipeline solid before building on top of it — everything downstream inherits
lesson quality directly. Highest-risk milestone in the roadmap.

**AI pipeline** (`04_AI_PROMPTS.md` §1)
- [ ] Heuristic heading-detection pass first (regex/structural markers) — skip the LLM call when
      confident
- [ ] LLM segmentation fallback on ~6–8k token chunks with overlap, merged across chunks
- [ ] Enforce lesson granularity (~15–25 vocab-worthy words) and the 60-lesson cap in application
      code, not the prompt alone
- [ ] Language/unsupported-content detection → `unsupported_content` status
- [ ] Retry ×3 with backoff; partial-success keeps completed lessons usable
- [ ] Tiered model use: cheaper/faster model here (per your decision)
- [ ] Extends the existing DB-polling worker pattern (per your decision — no new job-queue infra)

**Mobile**
- [ ] Lessons Ready Summary screen (partial-success state)

---

## M5 — Lesson Selection

- [ ] `GET /goals/{id}/lessons`
- [ ] Sequential lesson list, ungated, with per-lesson completion %
- [ ] "Still preparing" disabled row, all-complete state
- [ ] Current-lesson derivation (first lesson not at 100%)

---

## M6 — Vocabulary Learning + shared mastery/SRS module

Goal: build the mastery/SRS module carefully here — Grammar (M7) reuses it unchanged.

**AI pipeline** (`04_AI_PROMPTS.md` §2)
- [ ] Extraction prompt + schema validation
- [ ] Case-insensitive dedup against existing `vocabulary_items`

**Backend — shared module**
- [ ] Mastery state machine: `new → learning → reviewing → mastered`
- [ ] Fixed interval ladder `[1, 3, 7, 16, 35]` days (not SM-2 — parameterized by item type)
- [ ] `POST /lessons/{id}/vocabulary/session` — mixed new+due assembly
- [ ] `POST /vocabulary-sessions/{id}/answer`, `.../complete`

**Mobile**
- [ ] Vocabulary Session Start, Drill (multiple-choice + typed-recall, tolerant matching),
      Summary, List screens
- [ ] Incorrect-answer resurfacing (not immediately), zero-usable-vocabulary fallback
- [ ] *(Audio/TTS playback deferred per your decision — not in this pass)*

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
