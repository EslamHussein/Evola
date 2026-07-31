-- Structured multiple-choice/true-false/word-matching data for the Telegram Mini App: today every
-- exercise is plain text designed to be typed back in a chat, so the resolved options/pairs never
-- leave the AI response. Stored as JSON-array-as-TEXT, same convention as `synonyms`/`related_words`.
ALTER TABLE tutoring_word_content ADD COLUMN options TEXT;
ALTER TABLE tutoring_word_content ADD COLUMN match_pairs TEXT;
ALTER TABLE tutoring_grammar_content ADD COLUMN options TEXT;
