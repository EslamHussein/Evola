ALTER TABLE vocabulary_items
    ADD COLUMN part_of_speech VARCHAR(30),
    ADD COLUMN grammatical_case VARCHAR(20),
    ADD COLUMN example_sentence_translation TEXT;
