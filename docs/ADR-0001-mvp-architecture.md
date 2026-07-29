# ADR-0001: Milestone 1 Architecture (Lean MVP)

**Status:** Accepted for Milestone 1
**Scope:** First vertical slice of the Evola AI German tutor — vocabulary + spaced repetition + one AI-generated exercise type, delivered via Telegram.

This is a scoped-down adaptation of the original architecture proposal (see `GermanAIBot/docs/ADR-0001-architecture.md` for the full long-term vision: ~12 bounded contexts, object storage, writing/speaking assessment, conversation practice, exam simulation). That document remains the north star for where this system grows toward. This ADR records what is actually built now, and why, so the gap between "vision" and "built" is explicit rather than implied.

## What's unchanged from the original ADR

- Clean Architecture (Ports & Adapters): Domain → Application → Infrastructure → Presentation, dependencies point inward only.
- Modular Monolith with Gradle-module-enforced boundaries (illegal cross-module dependencies are compile-time impossible).
- Telegram is an adapter, never a dependency of the core (D2) — zero tutoring logic in `presentation/telegram-bot`.
- AI access lives behind an `AiTutorPort` (D3) — no direct OpenAI SDK calls from domain/application code.
- Learner identity decoupled from channel identity from day one (D4) — a `Learner` aggregate, with Telegram user ID as one `ExternalIdentity`.
- Spaced repetition is a deterministic, non-AI domain service — never LLM-driven.
- Kotlin + Coroutines, Ktor, PostgreSQL via Exposed (type-safe SQL, not full ORM), Docker for local Postgres parity.

## What's deferred, and why

| ADR module | This milestone | Rationale |
|---|---|---|
| Curriculum | Hardcoded seed list via a Flyway migration into `vocabulary_items` | No topic sequencing/prerequisites needed yet; promote to a real context once ordering matters |
| Grammar Mastery | Not built | Nothing in this slice touches grammar |
| Mistake Tracking | Folded into `review_history` rows (`was_correct = false`) | Superset of what a future `MistakeRecorded` event would carry; extraction later is mechanical, not a redesign |
| Spaced Repetition Scheduling | A pure domain service (`Sm2Scheduler`) inside `contexts/vocabulary/domain`, written generically against `SrsState` + a quality score — not "words" | No second consumer (Grammar Mastery) yet to justify its own module; lifting it out later is copy-and-repoint |
| Exercise Generation | Built now as its own thin module (no real `domain/` yet) | Flagged in the original ADR as AI-heavy and a likely future extraction candidate; keeping the seam now is cheap |
| Conversation Practice, Writing Assessment, Speaking Assessment, Exam Simulation, Progress & Analytics | Not built | Out of scope for this milestone |
| `integrations/object-storage-gateway` | Not built | No audio in this milestone |
| `presentation/client-api` | Not built | Only one channel exists |
| `scheduler/` (background job runtime) | Not built | Due reviews are computed on-demand (`WHERE next_review_at <= now()`) when the learner sends `/review`, not via a proactive scan/push |

## AI cost-reduction principles (applied from day one)

1. **Deterministic logic wherever an LLM isn't required.** Review grading (`ReviewGrader`) and spaced-repetition scheduling (`Sm2Scheduler`) are pure functions — no LLM call, no network round-trip, no cost, no latency.
2. **Cheap model tier by default, escalate only when a context actually needs it.** `AiTutorPort`'s model choice is configurable per operation (env-driven), not hardcoded — reserving a stronger model tier for future contexts (Writing/Speaking Assessment) that genuinely need deeper reasoning.
3. **Cache generated content aggressively.** `ai_generated_content` caches by `(vocabulary_item_id, exercise_type)` — an example sentence for a word is generated once, ever, and reused across all learners. No TTL, because the content doesn't go stale.
4. **The port shape stays intent-based** (`generateExercise(...)`), never "send this raw prompt" — keeping prompt engineering and model/provider choice fully inside the `ai-gateway` integration module.

## Module structure built for Milestone 1

```
core/domain-kernel, core/application-kernel
contexts/learner-identity/{domain,application,infrastructure}
contexts/vocabulary/{domain,application,infrastructure}
contexts/exercise-generation/{application,infrastructure}
integrations/ai-gateway, integrations/persistence-shared
presentation/telegram-bot
composition-root
```

`AiTutorPort` lives in `integrations/ai-gateway` (a shared capability, not owned by a single bounded context). `contexts/exercise-generation/application` depends only on its interface/DTOs — never the concrete OpenAI adapter, which is wired solely in `composition-root`.

## Next slices after Milestone 1

Grammar Mastery (mirrors Vocabulary's mastery lifecycle), promoting Mistake Tracking to its own context + domain event once a second consumer needs it, Writing Assessment (first context needing a stronger model tier), then Conversation Practice and Exam Simulation last (they compose across everything else).
