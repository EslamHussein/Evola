-- Structured, per-learner vocabulary library: resource-extracted words alongside the shared seed pool.
ALTER TABLE vocabulary_items
    ADD COLUMN article TEXT,
    ADD COLUMN plural_form TEXT,
    ADD COLUMN example_sentence TEXT,
    ADD COLUMN topic TEXT,
    ADD COLUMN synonyms TEXT,
    ADD COLUMN related_words TEXT,
    ADD COLUMN owner_learner_id UUID REFERENCES learners(id);

-- The global uniqueness constraint only makes sense for the shared seed pool (owner_learner_id
-- IS NULL) — two different learners' custom words with the same spelling must not collide, and a
-- learner may legitimately end up with a duplicate across resources (handled by an application-level
-- existence check instead, since Postgres treats every NULL as distinct from every other NULL).
ALTER TABLE vocabulary_items DROP CONSTRAINT vocabulary_items_german_word_key;
CREATE UNIQUE INDEX idx_vocabulary_items_seed_word ON vocabulary_items (german_word) WHERE owner_learner_id IS NULL;

CREATE TABLE learning_session_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    learner_id UUID NOT NULL REFERENCES learners(id),
    budget_type TEXT NOT NULL,
    budget_value INT NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at TIMESTAMPTZ,
    questions_asked INT NOT NULL DEFAULT 0,
    correct_count INT NOT NULL DEFAULT 0,
    incorrect_count INT NOT NULL DEFAULT 0,
    touched_vocabulary_item_ids TEXT NOT NULL DEFAULT '[]'
);
CREATE INDEX idx_learning_session_runs_learner ON learning_session_runs (learner_id, started_at DESC);
