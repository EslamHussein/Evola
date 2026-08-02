-- Full schema reset per 02_DATABASE_SCHEMA.sql, replacing the pre-pivot exam-readiness
-- goals/mastery_scores/study_plans tables (exam_type/target_date/readiness_by_skill) with the
-- real product spec's freeform-goal model, plus the lessons/vocabulary/grammar/progress schema
-- that everything from Onboarding through the Progress Dashboard is built on. Only throwaway
-- test data existed in the dropped tables (verified: 0 rows in all three).

DROP TABLE IF EXISTS study_plans;
DROP TABLE IF EXISTS mastery_scores;
DROP TABLE IF EXISTS goals;

CREATE TABLE goals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    goal_text VARCHAR(200) NOT NULL,
    title VARCHAR(60),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_goals_user ON goals (user_id);
-- Enforce single active goal per user at MVP (relax when multi-goal ships).
CREATE UNIQUE INDEX idx_goals_one_active_per_user ON goals (user_id) WHERE is_active;

CREATE TABLE lessons (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    material_id UUID NOT NULL REFERENCES materials(id) ON DELETE CASCADE,
    goal_id UUID NOT NULL REFERENCES goals(id) ON DELETE CASCADE,
    number INTEGER NOT NULL,
    title VARCHAR(150) NOT NULL,
    status TEXT NOT NULL DEFAULT 'pending',
    source_text_ref TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_lessons_material ON lessons (material_id);
CREATE INDEX idx_lessons_goal_order ON lessons (goal_id, number);

CREATE TABLE vocabulary_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lesson_id UUID NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    term VARCHAR(100) NOT NULL,
    meaning VARCHAR(200) NOT NULL,
    gender VARCHAR(20),
    example_sentence TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_vocab_items_lesson ON vocabulary_items (lesson_id);

CREATE TABLE vocabulary_progress (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    vocabulary_item_id UUID NOT NULL REFERENCES vocabulary_items(id) ON DELETE CASCADE,
    mastery_state TEXT NOT NULL DEFAULT 'new',
    correct_streak INTEGER NOT NULL DEFAULT 0,
    interval_index SMALLINT NOT NULL DEFAULT 0,
    next_review_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_reviewed_at TIMESTAMPTZ,
    UNIQUE (user_id, vocabulary_item_id)
);
CREATE INDEX idx_vocab_progress_due ON vocabulary_progress (user_id, next_review_at);

CREATE TABLE vocabulary_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    lesson_id UUID NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    items_count INTEGER NOT NULL DEFAULT 0,
    accuracy NUMERIC(5,2)
);
CREATE INDEX idx_vocab_sessions_user ON vocabulary_sessions (user_id, started_at);

CREATE TABLE grammar_topics (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lesson_id UUID NOT NULL REFERENCES lessons(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    explanation TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_grammar_topics_lesson ON grammar_topics (lesson_id);

-- Only exercises that passed automated answer-key validation should ever land here - validation
-- happens in the AI pipeline (M7), not enforced at the DB layer, but never write an unvalidated one.
CREATE TABLE grammar_exercises (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic_id UUID NOT NULL REFERENCES grammar_topics(id) ON DELETE CASCADE,
    type TEXT NOT NULL,
    prompt TEXT NOT NULL,
    answer_key TEXT NOT NULL,
    distractors TEXT, -- JSON array of strings, multiple_choice only
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_grammar_exercises_topic ON grammar_exercises (topic_id);

-- Mirrors vocabulary_progress exactly by design (spec §2.1: shared mastery/SRS module).
CREATE TABLE grammar_progress (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    topic_id UUID NOT NULL REFERENCES grammar_topics(id) ON DELETE CASCADE,
    mastery_state TEXT NOT NULL DEFAULT 'new',
    correct_streak INTEGER NOT NULL DEFAULT 0,
    interval_index SMALLINT NOT NULL DEFAULT 0,
    next_review_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_reviewed_at TIMESTAMPTZ,
    UNIQUE (user_id, topic_id)
);
CREATE INDEX idx_grammar_progress_due ON grammar_progress (user_id, next_review_at);

CREATE TABLE grammar_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    topic_id UUID NOT NULL REFERENCES grammar_topics(id) ON DELETE CASCADE,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    accuracy NUMERIC(5,2)
);
CREATE INDEX idx_grammar_sessions_user ON grammar_sessions (user_id, started_at);

-- Powers streak + "today completed" (spec §1.10). activity_date is the user's LOCAL date, not
-- server UTC - written by application code that already knows the client's timezone offset.
CREATE TABLE daily_activity (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    activity_date DATE NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT false,
    UNIQUE (user_id, activity_date)
);
CREATE INDEX idx_daily_activity_user_date ON daily_activity (user_id, activity_date DESC);
