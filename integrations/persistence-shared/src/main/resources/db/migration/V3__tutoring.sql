-- Evolves Milestone 1's mastery tracking; SM-2 columns (easiness_factor/interval_days/repetitions) untouched.
ALTER TABLE learner_vocabulary_state
    ADD COLUMN total_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN total_lapses INT NOT NULL DEFAULT 0,
    ADD COLUMN consecutive_correct INT NOT NULL DEFAULT 0;

CREATE TABLE learner_tutoring_profile (
    learner_id UUID PRIMARY KEY REFERENCES learners(id),
    active_learning_mode TEXT NOT NULL DEFAULT 'VOCABULARY',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE tutoring_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    learner_id UUID NOT NULL REFERENCES learners(id),
    mode TEXT NOT NULL,
    focus_vocabulary_item_id UUID REFERENCES vocabulary_items(id),
    focus_grammar_topic TEXT,
    status TEXT NOT NULL DEFAULT 'IN_PROGRESS',
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ
);
CREATE INDEX idx_tutoring_sessions_learner ON tutoring_sessions (learner_id, started_at DESC);

CREATE TABLE dialogue_turns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES tutoring_sessions(id),
    turn_index INT NOT NULL,
    role TEXT NOT NULL,
    exercise_kind TEXT,
    content TEXT NOT NULL,
    correct_answer TEXT,
    explanation TEXT,
    was_correct BOOLEAN,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (session_id, turn_index)
);

CREATE TABLE tutoring_word_content (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vocabulary_item_id UUID NOT NULL REFERENCES vocabulary_items(id),
    kind TEXT NOT NULL,
    difficulty_tier TEXT,
    prompt_text TEXT NOT NULL,
    correct_answer TEXT NOT NULL,
    hint TEXT,
    explanation TEXT,
    model_used TEXT NOT NULL,
    times_served INT NOT NULL DEFAULT 0,
    last_served_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX idx_tutoring_word_content_key
    ON tutoring_word_content (vocabulary_item_id, kind, COALESCE(difficulty_tier, ''));

CREATE TABLE tutoring_grammar_content (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    grammar_topic TEXT NOT NULL,
    difficulty_tier TEXT NOT NULL,
    prompt_text TEXT NOT NULL,
    correct_answer TEXT NOT NULL,
    explanation TEXT,
    model_used TEXT NOT NULL,
    times_served INT NOT NULL DEFAULT 0,
    last_served_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (grammar_topic, difficulty_tier)
);

CREATE TABLE daily_session_plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    learner_id UUID NOT NULL REFERENCES learners(id),
    plan_date DATE NOT NULL,
    due_review_count INT NOT NULL,
    weak_vocabulary_item_ids TEXT NOT NULL,
    grammar_focus_topic TEXT,
    speaking_scenario_title TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (learner_id, plan_date)
);
