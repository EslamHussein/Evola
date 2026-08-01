ALTER TABLE extraction_jobs ADD COLUMN content_text TEXT NOT NULL DEFAULT '';
ALTER TABLE model_call_log ADD COLUMN extraction_job_id UUID REFERENCES extraction_jobs(id);
