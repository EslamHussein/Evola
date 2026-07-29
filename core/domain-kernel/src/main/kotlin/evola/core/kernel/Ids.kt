package evola.core.kernel

import java.util.UUID

@JvmInline
value class LearnerId(val value: UUID) {
    companion object {
        fun new(): LearnerId = LearnerId(UUID.randomUUID())
    }
}

@JvmInline
value class VocabularyItemId(val value: UUID) {
    companion object {
        fun new(): VocabularyItemId = VocabularyItemId(UUID.randomUUID())
    }
}

@JvmInline
value class LearnerVocabularyStateId(val value: UUID) {
    companion object {
        fun new(): LearnerVocabularyStateId = LearnerVocabularyStateId(UUID.randomUUID())
    }
}
