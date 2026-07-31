package evola.tutoring.domain

/**
 * Small hardcoded seed list, same "curriculum as seed data" philosophy as the vocabulary seed
 * migration and [SpeakingScenarioCatalog] — no AI call needed to pick a topic, only to generate
 * an exercise for it once chosen.
 */
object GrammarTopicCatalog {
    val topics: List<String> = listOf(
        "weil-clauses (verb-final word order)",
        "Dativ prepositions",
        "Perfekt tense formation",
        "Adjective endings",
        "Modal verbs",
    )

    fun pickFor(seed: Int): String = topics[Math.floorMod(seed, topics.size)]
}
