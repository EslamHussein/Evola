ALTER TABLE materials
    ADD COLUMN organization_mode VARCHAR(20) NOT NULL DEFAULT 'auto',
    ADD COLUMN ai_instructions TEXT,
    ADD COLUMN resource_type VARCHAR(30),
    ADD COLUMN content_text TEXT;
