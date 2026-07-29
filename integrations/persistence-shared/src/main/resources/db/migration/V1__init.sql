CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE learners (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE external_identities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    learner_id UUID NOT NULL REFERENCES learners(id),
    channel TEXT NOT NULL,
    external_id TEXT NOT NULL,
    display_name TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (channel, external_id)
);

CREATE TABLE vocabulary_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    german_word TEXT NOT NULL UNIQUE,
    english_translation TEXT NOT NULL,
    cefr_level TEXT NOT NULL,
    part_of_speech TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE learner_vocabulary_state (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    learner_id UUID NOT NULL REFERENCES learners(id),
    vocabulary_item_id UUID NOT NULL REFERENCES vocabulary_items(id),
    easiness_factor DOUBLE PRECISION NOT NULL DEFAULT 2.5,
    interval_days INT NOT NULL DEFAULT 0,
    repetitions INT NOT NULL DEFAULT 0,
    next_review_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (learner_id, vocabulary_item_id)
);
CREATE INDEX idx_lvs_due ON learner_vocabulary_state (learner_id, next_review_at);

CREATE TABLE review_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    learner_vocabulary_state_id UUID NOT NULL REFERENCES learner_vocabulary_state(id),
    reviewed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    learner_answer TEXT NOT NULL,
    was_correct BOOLEAN NOT NULL,
    quality_score INT NOT NULL,
    ef_before DOUBLE PRECISION NOT NULL,
    ef_after DOUBLE PRECISION NOT NULL,
    interval_before INT NOT NULL,
    interval_after INT NOT NULL
);

CREATE TABLE ai_generated_content (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vocabulary_item_id UUID NOT NULL REFERENCES vocabulary_items(id),
    exercise_type TEXT NOT NULL,
    content TEXT NOT NULL,
    model_used TEXT NOT NULL,
    times_served INT NOT NULL DEFAULT 0,
    last_served_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (vocabulary_item_id, exercise_type)
);
