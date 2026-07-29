package evola.exercisegeneration.application

import evola.core.kernel.VocabularyItemId
import evola.integrations.aigateway.ExerciseType
import evola.integrations.aigateway.GeneratedExercise

interface ExerciseCache {
    suspend fun find(vocabularyItemId: VocabularyItemId, exerciseType: ExerciseType): GeneratedExercise?
    suspend fun store(vocabularyItemId: VocabularyItemId, exerciseType: ExerciseType, exercise: GeneratedExercise)
}
