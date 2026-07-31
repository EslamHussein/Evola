-- Mini App session config (question-type filter + difficulty override) must be fixed for the life
-- of a session so every question in it honors the learner's choice, not just the first one.
ALTER TABLE learning_session_runs ADD COLUMN allowed_kinds TEXT NOT NULL DEFAULT '[]';
ALTER TABLE learning_session_runs ADD COLUMN difficulty_override TEXT;
