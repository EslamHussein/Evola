package evola.tutoring.domain

/**
 * Small hardcoded seed list, same "curriculum as seed data" philosophy as the vocabulary seed
 * migration — no AI call needed to decide which scenario to offer, only to run it once chosen.
 */
data class SpeakingScenario(val title: String, val description: String)

object SpeakingScenarioCatalog {
    val scenarios: List<SpeakingScenario> = listOf(
        SpeakingScenario("Job interview", "You are being interviewed for a job in Germany. Answer the interviewer's questions."),
        SpeakingScenario("Doctor's appointment", "You are visiting a doctor to describe a minor illness or injury."),
        SpeakingScenario("Renting an apartment", "You are calling a landlord to ask about an apartment for rent."),
        SpeakingScenario("Ordering at a restaurant", "You are ordering food and drinks at a German restaurant."),
        SpeakingScenario("At the bank", "You are opening a bank account at a German bank branch."),
    )

    /** Deterministic rotating pick — no AI call to decide. */
    fun pickFor(seed: Int): SpeakingScenario = scenarios[Math.floorMod(seed, scenarios.size)]

    fun findByTitle(title: String): SpeakingScenario? = scenarios.firstOrNull { it.title == title }
}
