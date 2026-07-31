package evola.learningresources.application

import evola.core.kernel.LearningResourceId
import evola.integrations.aigateway.GeneratedLearningContent
import evola.learningresources.domain.LearningResource
import evola.learningresources.domain.SourceType

interface LearningResourceRepository {
    suspend fun findById(id: LearningResourceId): LearningResource?
    suspend fun save(resource: LearningResource)
}

fun interface ResourceTextExtractor {
    fun extract(bytes: ByteArray, sourceType: SourceType): String
}

interface ResourceFileStorage {
    suspend fun save(resourceId: LearningResourceId, fileName: String, bytes: ByteArray): String
}

interface LearningResourceContentCache {
    suspend fun find(resourceId: LearningResourceId, goal: String): GeneratedLearningContent?
    suspend fun store(resourceId: LearningResourceId, goal: String, content: GeneratedLearningContent)
}
