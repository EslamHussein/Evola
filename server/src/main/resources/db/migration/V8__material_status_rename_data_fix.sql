-- V7 renamed the MaterialStatus enum values (ANALYZING->PROCESSING, ANALYZED->READY,
-- NEEDS_REVIEW->UNSUPPORTED_CONTENT) in code but missed migrating existing row data - a material
-- still holding the old string value fails to deserialize client-side ("No enum constant ...
-- MaterialStatus.ANALYZED"), caught live in production on a pre-M4 material (sample.pdf).

UPDATE materials SET status = 'PROCESSING' WHERE status = 'ANALYZING';
UPDATE materials SET status = 'READY' WHERE status = 'ANALYZED';
UPDATE materials SET status = 'UNSUPPORTED_CONTENT' WHERE status = 'NEEDS_REVIEW';
