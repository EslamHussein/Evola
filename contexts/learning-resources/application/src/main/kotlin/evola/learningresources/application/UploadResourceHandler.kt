package evola.learningresources.application

import evola.core.application.Command
import evola.core.application.UseCase
import evola.core.kernel.LearnerId
import evola.core.kernel.LearningResourceId
import evola.learningresources.domain.LearningResource
import evola.learningresources.domain.SourceType

data class UploadResourceCommand(
    val learnerId: LearnerId,
    val title: String,
    val sourceType: SourceType,
    val bytes: ByteArray,
) : Command<LearningResourceId>

class UploadResourceHandler(
    private val repository: LearningResourceRepository,
    private val textExtractor: ResourceTextExtractor,
    private val fileStorage: ResourceFileStorage,
) : UseCase<UploadResourceCommand, LearningResourceId> {

    override suspend fun handle(input: UploadResourceCommand): LearningResourceId {
        val id = LearningResourceId.new()
        val text = textExtractor.extract(input.bytes, input.sourceType)
        val storagePath = if (input.sourceType == SourceType.PDF) {
            fileStorage.save(id, input.title, input.bytes)
        } else {
            null // TEXT_NOTE's content already lives in extractedText — nothing to write to disk
        }

        val resource = LearningResource.upload(
            id = id,
            learnerId = input.learnerId,
            title = input.title,
            sourceType = input.sourceType,
            storagePath = storagePath,
            extractedText = text,
        )
        repository.save(resource)
        return id
    }
}
