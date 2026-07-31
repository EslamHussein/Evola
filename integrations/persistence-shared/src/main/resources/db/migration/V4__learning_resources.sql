CREATE TABLE learning_resources (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    learner_id UUID NOT NULL REFERENCES learners(id),
    title TEXT NOT NULL,
    source_type TEXT NOT NULL,
    storage_path TEXT,
    extracted_text TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'UPLOADED',
    language TEXT,
    cefr_level TEXT,
    topics TEXT,
    overview_summary TEXT,
    overview_model_used TEXT,
    analysis_truncated BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_learning_resources_learner ON learning_resources (learner_id, created_at DESC);

CREATE TABLE learning_resource_generated_content (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    resource_id UUID NOT NULL REFERENCES learning_resources(id),
    goal TEXT NOT NULL,
    content TEXT NOT NULL,
    model_used TEXT NOT NULL,
    times_served INT NOT NULL DEFAULT 0,
    last_served_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (resource_id, goal)
);
