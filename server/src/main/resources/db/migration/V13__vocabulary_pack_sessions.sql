-- Pack/stage vocabulary session architecture (design handoff Phase 7): replaces the old flat
-- drill-queue session model with packs of ~5 words, each walked through 7 fixed stages.
-- vocabulary_sessions/vocabulary_session_items are intentionally left in place, unused - a future
-- cleanup migration can drop them once this architecture is proven in production.

CREATE TABLE vocabulary_packs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    lesson_id UUID NOT NULL,
    pack_number INTEGER NOT NULL,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    items_count INTEGER NOT NULL,
    accuracy NUMERIC(5, 2)
);

CREATE TABLE vocabulary_pack_words (
    id UUID PRIMARY KEY,
    pack_id UUID NOT NULL REFERENCES vocabulary_packs(id),
    vocabulary_item_id UUID NOT NULL REFERENCES vocabulary_items(id),
    position INTEGER NOT NULL,
    -- JSON array of Arabic (or English-fallback) meaning choices for stage 1 (Recognition),
    -- computed once at pack creation so a resumed pack shows identical choices, not a reshuffle.
    recognition_choices TEXT
);

CREATE TABLE vocabulary_stage_answers (
    id UUID PRIMARY KEY,
    pack_word_id UUID NOT NULL REFERENCES vocabulary_pack_words(id),
    stage_index SMALLINT NOT NULL,
    user_response TEXT,
    correct BOOLEAN,
    answered_at TIMESTAMP NOT NULL
);

ALTER TABLE vocabulary_progress
    ADD COLUMN is_bookmarked BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN marked_difficult BOOLEAN NOT NULL DEFAULT FALSE;
