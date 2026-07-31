package evola.learningresources.application

import evola.core.application.Command
import evola.core.application.UseCase
import evola.core.kernel.CefrLevel
import evola.core.kernel.DomainError
import evola.core.kernel.DomainResult
import evola.core.kernel.LearnerVocabularyStateId
import evola.core.kernel.LearningResourceId
import evola.core.kernel.VocabularyItemId
import evola.integrations.aigateway.AiTutorPort
import evola.integrations.aigateway.ExtractVocabularyRequest
import evola.learningresources.domain.ResourceStatus
import evola.vocabulary.application.LearnerVocabularyStateRepository
import evola.vocabulary.application.VocabularyItemRepository
import evola.vocabulary.domain.LearnerVocabularyState
import evola.vocabulary.domain.VocabularyItem

data class ExtractVocabularyCommand(val resourceId: LearningResourceId) : Command<DomainResult<ExtractVocabularyResult>>

data class ExtractVocabularyResult(val addedCount: Int, val skippedCount: Int)

/**
 * Runs right after a resource is analyzed: turns its text into structured, per-learner vocabulary
 * items that immediately enter the SM-2 system (due now) — these words were explicitly requested
 * by uploading the resource, so there's no need to "discover" them via findNextUnseenFor later.
 * Idempotent: re-extracting an already-covered resource just skips words the learner already has.
 */
class ExtractVocabularyHandler(
    private val resourceRepository: LearningResourceRepository,
    private val vocabularyItemRepository: VocabularyItemRepository,
    private val stateRepository: LearnerVocabularyStateRepository,
    private val aiTutorPort: AiTutorPort,
) : UseCase<ExtractVocabularyCommand, DomainResult<ExtractVocabularyResult>> {

    override suspend fun handle(input: ExtractVocabularyCommand): DomainResult<ExtractVocabularyResult> {
        val resource = resourceRepository.findById(input.resourceId)
            ?: return DomainResult.Err(DomainError.NotFound("Resource not found"))
        if (resource.status != ResourceStatus.ANALYZED) {
            return DomainResult.Err(DomainError.ValidationFailed("Resource has not been analyzed yet"))
        }

        val extracted = aiTutorPort.extractVocabulary(
            ExtractVocabularyRequest(text = resource.extractedText.take(CHAR_LIMIT)),
        )

        var added = 0
        var skipped = 0
        for (item in extracted) {
            if (vocabularyItemRepository.findByOwnerAndWord(resource.learnerId, item.germanWord) != null) {
                skipped++
                continue
            }

            val vocabularyItem = VocabularyItem(
                id = VocabularyItemId.new(),
                germanWord = item.germanWord,
                englishTranslation = item.englishTranslation,
                cefrLevel = CefrLevel.fromCode(item.cefrLevel),
                partOfSpeech = item.partOfSpeech,
                article = item.article,
                pluralForm = item.pluralForm,
                exampleSentence = item.exampleSentence,
                topic = item.topic,
                synonyms = item.synonyms,
                relatedWords = item.relatedWords,
                ownerLearnerId = resource.learnerId,
            )
            vocabularyItemRepository.save(vocabularyItem)

            stateRepository.save(
                LearnerVocabularyState.newFor(
                    id = LearnerVocabularyStateId.new(),
                    learnerId = resource.learnerId,
                    vocabularyItemId = vocabularyItem.id,
                ),
            )
            added++
        }

        return DomainResult.Ok(ExtractVocabularyResult(addedCount = added, skippedCount = skipped))
    }

    companion object {
        const val CHAR_LIMIT = 20_000
    }
}
