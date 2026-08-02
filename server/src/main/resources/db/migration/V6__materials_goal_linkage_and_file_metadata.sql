-- Material Upload rebuild (01_PRODUCT_SPEC.md §1.5): materials now belong to a goal and carry
-- real file metadata (mime type, size, disk path, page count) instead of being paste-text rows
-- with no owner beyond the user. Only throwaway test data predates this (verified: 6 materials /
-- 4 extraction_jobs / 2 extraction_cache / 4 model_call_log rows locally, 0 everywhere in prod),
-- so this clears it rather than trying to backfill a goal_id that never existed.
TRUNCATE TABLE materials, extraction_jobs, extraction_cache, model_call_log CASCADE;

ALTER TABLE materials
  ADD COLUMN goal_id UUID NOT NULL REFERENCES goals(id) ON DELETE CASCADE,
  ADD COLUMN file_ref TEXT NOT NULL,
  ADD COLUMN mime_type VARCHAR(100) NOT NULL,
  ADD COLUMN size_bytes BIGINT NOT NULL,
  ADD COLUMN page_count INTEGER;

CREATE INDEX idx_materials_goal ON materials (goal_id);
