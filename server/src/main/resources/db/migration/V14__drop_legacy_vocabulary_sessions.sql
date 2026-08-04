-- Cleanup (design handoff Phase 7 flagged this as deferred): vocabulary_sessions/
-- vocabulary_session_items were left in place, unused, when the pack/stage session model shipped,
-- pending proof that the new architecture worked in production. That's now confirmed (real packs,
-- real AI grading, real writes verified against production) and no code references either table
-- anymore. Their few pre-existing rows were exported to
-- scratchpad/vocab_sessions_export_2026-08-04.sql before this migration ran.

DROP TABLE IF EXISTS vocabulary_session_items;
DROP TABLE IF EXISTS vocabulary_sessions;
