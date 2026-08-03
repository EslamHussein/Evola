-- M6 Vocabulary Learning (01_PRODUCT_SPEC.md §1.8, 04_AI_PROMPTS.md §2): the first milestone to
-- actually write to vocabulary_items/vocabulary_progress/vocabulary_sessions (created empty by the
-- M1 reset migration). These two new tables are implementation-level additions not spelled out in
-- 02_DATABASE_SCHEMA.sql - a work queue for the per-lesson extraction pipeline (mirrors
-- extraction_jobs' role for lesson segmentation), and per-session-item state needed for session
-- resumability and answer resurfacing, neither of which fit in vocabulary_sessions' own summary-only
-- columns (started_at/completed_at/items_count/accuracy).

CREATE TABLE vocabulary_extraction_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lesson_id UUID NOT NULL UNIQUE REFERENCES lessons(id) ON DELETE CASCADE,
    status TEXT NOT NULL,
    error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_vocab_extraction_jobs_status ON vocabulary_extraction_jobs (status);

-- position is mutable: a resurfaced occurrence of the same vocabulary_item_id gets a new row with
-- a later position rather than replacing the original, so "wrong twice -> resurfaced later, not
-- immediately" doesn't need to know the whole sequence upfront. choices is persisted (not
-- recomputed) so a resumed session shows identical multiple-choice options, not a reshuffle.
CREATE TABLE vocabulary_session_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES vocabulary_sessions(id) ON DELETE CASCADE,
    vocabulary_item_id UUID NOT NULL REFERENCES vocabulary_items(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    drill_type TEXT NOT NULL,
    choices TEXT,
    answered_correctly BOOLEAN,
    answered_at TIMESTAMPTZ
);
CREATE INDEX idx_vocab_session_items_session ON vocabulary_session_items (session_id, position);
