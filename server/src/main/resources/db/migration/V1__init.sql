-- Fresh schema for the Kotlin Multiplatform coaching app (pivot from the retired Telegram bot).
-- Only `materials`, `extraction_cache`, and `extraction_jobs` are read/written by Milestone 1's
-- vertical slice; the rest exist now so later milestones don't each need their own migration.

CREATE TABLE extraction_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_hash TEXT NOT NULL UNIQUE,
    vocabulary TEXT NOT NULL DEFAULT '[]',
    grammar TEXT NOT NULL DEFAULT '[]',
    exercises TEXT NOT NULL DEFAULT '[]',
    confidence REAL NOT NULL,
    model_version TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE materials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    filename TEXT NOT NULL,
    content_hash TEXT NOT NULL,
    extraction_cache_id UUID REFERENCES extraction_cache(id),
    status TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_materials_user ON materials (user_id);

CREATE TABLE extraction_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_hash TEXT NOT NULL,
    status TEXT NOT NULL,
    error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX idx_extraction_jobs_content_hash ON extraction_jobs (content_hash);

CREATE TABLE model_call_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_type TEXT NOT NULL,
    model_tier TEXT NOT NULL,
    input_tokens INT NOT NULL,
    output_tokens INT NOT NULL,
    cost_estimate DOUBLE PRECISION NOT NULL,
    cache_hit BOOLEAN NOT NULL,
    material_id UUID REFERENCES materials(id),
    user_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE mastery_scores (
    user_id UUID NOT NULL,
    item_id TEXT NOT NULL,
    score REAL NOT NULL,
    last_reviewed_at TIMESTAMPTZ NOT NULL,
    next_due_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, item_id)
);

CREATE TABLE goals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    exam_type TEXT NOT NULL,
    target_date DATE NOT NULL,
    readiness_overall REAL NOT NULL DEFAULT 0,
    readiness_by_skill TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_goals_user ON goals (user_id);

CREATE TABLE study_plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    goal_id UUID NOT NULL REFERENCES goals(id),
    sessions TEXT NOT NULL DEFAULT '[]',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
