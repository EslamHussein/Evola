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

@JvmInline
value class TutoringSessionId(val value: UUID) {
    companion object {
        fun new(): TutoringSessionId = TutoringSessionId(UUID.randomUUID())
    }
}

@JvmInline
value class DialogueTurnId(val value: UUID) {
    companion object {
        fun new(): DialogueTurnId = DialogueTurnId(UUID.randomUUID())
    }
}

@JvmInline
value class DailySessionPlanId(val value: UUID) {
    companion object {
        fun new(): DailySessionPlanId = DailySessionPlanId(UUID.randomUUID())
    }
}

@JvmInline
value class LearningResourceId(val value: UUID) {
    companion object {
        fun new(): LearningResourceId = LearningResourceId(UUID.randomUUID())
    }
}

@JvmInline
value class LearningSessionRunId(val value: UUID) {
    companion object {
        fun new(): LearningSessionRunId = LearningSessionRunId(UUID.randomUUID())
    }
}
