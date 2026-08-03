ALTER TABLE vocabulary_items
    ADD COLUMN meaning_ar TEXT,
    ADD COLUMN ipa_pronunciation VARCHAR(100),
    ADD COLUMN related_words TEXT,
    ADD COLUMN difficulty_rating VARCHAR(20),
    ADD COLUMN frequency_rating VARCHAR(20),
    ADD COLUMN memory_tip TEXT;
