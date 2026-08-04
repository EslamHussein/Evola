-- M7 Grammar Learning: grammar_topics/grammar_exercises/grammar_progress/grammar_sessions already
-- exist (M1 reset migration), empty, unused, and need no changes - grammar_progress already
-- mirrors vocabulary_progress exactly (correct_streak/interval_index), so MasterySrs drops in
-- unchanged. Only two new tables are needed: a work queue for the extraction pipeline (mirrors
-- vocabulary_extraction_jobs) and a per-exercise answer record for sessions (NOT a snapshot of
-- session membership - a topic's exercise set is immutable after generation, so "this session's
-- exercises" is always derived as grammar_exercises WHERE topic_id = session.topic_id; this table
-- only tracks which have been answered, with what response, and the mastery snapshot immediately
-- after that answer, so a retried/duplicate POST is trivially idempotent without re-applying
-- MasterySrs).

CREATE TABLE grammar_extraction_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lesson_id UUID NOT NULL UNIQUE REFERENCES lessons(id) ON DELETE CASCADE,
    status TEXT NOT NULL,
    error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_grammar_extraction_jobs_status ON grammar_extraction_jobs (status);

CREATE TABLE grammar_session_answers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES grammar_sessions(id) ON DELETE CASCADE,
    exercise_id UUID NOT NULL REFERENCES grammar_exercises(id) ON DELETE CASCADE,
    response TEXT,
    correct BOOLEAN NOT NULL,
    mastery_state_after TEXT NOT NULL,
    next_review_at_after TIMESTAMPTZ NOT NULL,
    answered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (session_id, exercise_id)
);
CREATE INDEX idx_grammar_session_answers_session ON grammar_session_answers (session_id);
