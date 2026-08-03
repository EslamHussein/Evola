-- M4 Automatic Lesson Generation (01_PRODUCT_SPEC.md §1.6): repurposes the M2/M3 single-shot
-- extraction pipeline (whole document -> one blob of vocabulary+grammar+exercises, which broke on
-- large documents - see the A2 - Wortschatz.pdf production failure) into the real spec pipeline:
-- materials split into `lessons` rows (schema already exists from V5, unused until now). Verified
-- only 1 throwaway row in extraction_cache and 0 rows depending on its old columns elsewhere.

-- Was only ever a bookkeeping pointer for the old single-shot pipeline's cache-hit fast path -
-- getMaterial() now joins `lessons` by material_id directly, no longer needs it.
ALTER TABLE materials DROP COLUMN extraction_cache_id;

ALTER TABLE extraction_cache
  DROP COLUMN vocabulary,
  DROP COLUMN grammar,
  DROP COLUMN exercises,
  DROP COLUMN confidence,
  ADD COLUMN segments TEXT NOT NULL DEFAULT '[]',
  ADD COLUMN detected_language VARCHAR(10),
  ADD COLUMN unsupported_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE extraction_cache ALTER COLUMN segments DROP DEFAULT;

-- Chunk ranges [[start,end],...] that exhausted retries, for retry-the-remainder-only on reprocess.
ALTER TABLE extraction_jobs ADD COLUMN failed_ranges TEXT;
